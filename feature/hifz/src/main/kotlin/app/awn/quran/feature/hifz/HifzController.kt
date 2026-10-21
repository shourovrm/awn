package app.awn.quran.feature.hifz

import app.awn.quran.core.data.ScriptId
import app.awn.quran.feature.hifz.data.HifzRepository
import app.awn.quran.feature.hifz.method.HifzMethodId
import app.awn.quran.feature.hifz.method.HifzPlan
import app.awn.quran.feature.hifz.method.HifzProgress
import app.awn.quran.feature.hifz.method.MethodDefaults
import app.awn.quran.feature.hifz.method.MethodSettings
import app.awn.quran.feature.hifz.method.MistakeRecord
import app.awn.quran.feature.hifz.method.PageLayoutFamily
import app.awn.quran.feature.hifz.method.PageStatus
import app.awn.quran.feature.hifz.method.PageTableLoader
import app.awn.quran.feature.hifz.method.PartGrade
import app.awn.quran.feature.hifz.method.PartKind
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the Hifz tab can show. */
sealed interface HifzScreenState {
    data object Loading : HifzScreenState

    /** No plan yet: the tab offers the choice of a method. */
    data object NoPlan : HifzScreenState

    data class Ready(val state: HifzState) : HifzScreenState
}

/** The Hifz tab's state and everything it can do; one per process, shared with the Quran screen. */
// The controller is the one place the tab and the practice bar both call, so it has many entries.
@Suppress("TooManyFunctions")
class HifzController(
    private val repository: HifzRepository,
    private val tables: PageTableLoader,
    private val scripts: Flow<ScriptId>,
    private val scope: CoroutineScope,
) {
    private val mutablePractice = MutableStateFlow<PracticeRequest?>(null)

    /** The part being practised on the Quran screen, null when none is. */
    val practice: StateFlow<PracticeRequest?> = mutablePractice.asStateFlow()

    private val family: Flow<PageLayoutFamily> =
        scripts.map { PageLayoutFamily.of(it) }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val screenState: StateFlow<HifzScreenState> =
        family
            .flatMapLatest { currentFamily -> screenStateFor(currentFamily) }
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                HifzScreenState.Loading,
            )

    private fun screenStateFor(currentFamily: PageLayoutFamily): Flow<HifzScreenState> {
        val today = repository.today()
        val weekAgo = today.minusDays(WEAK_SPOT_DAYS).atStartOfDay()
        val sinceMillis = weekAgo.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return combine(
            repository.observePlan(),
            repository.observeRecords(),
            repository.observeFinishedOn(today),
            repository.observeMistakesSince(sinceMillis),
        ) { plan, records, finished, mistakes ->
            if (plan == null) {
                HifzScreenState.NoPlan
            } else if (plan.printLayout != currentFamily) {
                // The reader changed print; the records follow it, then the next emission is ready.
                moveToPrint(plan, currentFamily)
                HifzScreenState.Loading
            } else {
                HifzScreenState.Ready(
                    HifzStateBuilder.build(
                        plan,
                        tables.load(currentFamily),
                        records,
                        finished,
                        mistakes,
                        today,
                    ),
                )
            }
        }
    }

    private fun moveToPrint(plan: HifzPlan, newFamily: PageLayoutFamily) {
        scope.launch {
            val oldTable = tables.load(plan.printLayout)
            val newTable = tables.load(newFamily)
            val moved = HifzProgress.remap(oldTable, newTable, repository.records())
            repository.replaceRecords(moved, plan.copy(printLayout = newFamily))
        }
    }

    /** Starts or replaces the plan with [method]; page records stay. */
    fun chooseMethod(
        method: HifzMethodId,
        settings: MethodSettings = MethodDefaults.settingsFor(method),
    ) {
        scope.launch {
            val script = scripts.first()
            val existing = repository.plan()
            val plan =
                HifzPlan(
                    method = method,
                    settings = settings,
                    cursor = existing?.cursor,
                    printLayout = PageLayoutFamily.of(script),
                    startedOn = existing?.startedOn ?: repository.today(),
                )
            repository.savePlan(plan)
        }
    }

    fun markPages(pages: IntRange, status: PageStatus) {
        scope.launch { repository.markPages(pages, status) }
    }

    /** Opens [kind] for practice and returns the request, or null when today has no such part. */
    @Suppress("ReturnCount") // Guards: tab not ready, no such part today.
    fun startPractice(kind: PartKind): PracticeRequest? {
        val ready = screenState.value as? HifzScreenState.Ready ?: return null
        val request = HifzStateBuilder.practiceFor(ready.state, kind) ?: return null
        mutablePractice.value = request
        return request
    }

    /** Opens the part the primary button offers, or returns null when today is done. */
    @Suppress("ReturnCount") // Guards: tab not ready, day done.
    fun startNextPart(): PracticeRequest? {
        val ready = screenState.value as? HifzScreenState.Ready ?: return null
        val next = ready.state.assignment.nextPart() ?: return null
        return startPractice(next)
    }

    /** Leaves practice without recording anything. */
    fun cancelPractice() {
        mutablePractice.value = null
    }

    /** Records the finished practice with the user's [grade] and leaves practice. */
    fun finishPractice(grade: PartGrade) {
        val request = mutablePractice.value ?: return
        mutablePractice.value = null
        scope.launch {
            val lesson = request.lesson
            if (request.kind == PartKind.NewLesson && lesson != null) {
                val plan = checkNotNull(repository.plan()) { "Practice ended without a plan." }
                repository.finishLesson(tables.load(plan.printLayout), lesson, grade)
            } else {
                repository.finishRevision(request.kind, request.pages, grade)
            }
        }
    }

    fun recordMistakes(mistakes: List<MistakeRecord>) {
        scope.launch { repository.recordMistakes(mistakes) }
    }

    fun today(): LocalDate = repository.today()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val WEAK_SPOT_DAYS = 7L
    }
}
