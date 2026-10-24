package app.awn.quran.feature.hifz.method

/** The starting values of each method and the language-based first choice. */
object MethodDefaults {
    private const val THREE_BY_THREE_AYAHS = 5
    private const val CHAIN_AYAHS = 3
    private const val THREE_TIMES = 3
    private const val CHAIN_TIMES = 20
    private val languagesUsingSabaq = setOf("bn", "ur")

    fun settingsFor(method: HifzMethodId): MethodSettings =
        when (method) {
            HifzMethodId.ThreeByThree ->
                MethodSettings(
                    ayahsPerDay = THREE_BY_THREE_AYAHS,
                    revisionSpacing = spacingFor(method),
                )
            HifzMethodId.AyahChain ->
                MethodSettings(ayahsPerDay = CHAIN_AYAHS, revisionSpacing = spacingFor(method))
            else -> MethodSettings(revisionSpacing = spacingFor(method))
        }

    /**
     * The spacing a method starts with: the Manzil cycle for Sabaq, Sabqi, Manzil, as taught in
     * madrasas, and the memory model for the others, which is how they always scheduled revision.
     */
    fun spacingFor(method: HifzMethodId): RevisionSpacing =
        if (method == HifzMethodId.SabaqSabqiManzil) RevisionSpacing.ManzilCycle
        else RevisionSpacing.Adaptive

    /** How many times the audio repeats during practice. */
    fun repeatCountFor(method: HifzMethodId): Int =
        if (method == HifzMethodId.AyahChain) CHAIN_TIMES else THREE_TIMES

    /** The method ticked on first open: Sabaq, Sabqi, Manzil for Bangla and Urdu readers. */
    fun firstChoiceFor(languageCode: String): HifzMethodId =
        if (languageCode in languagesUsingSabaq) HifzMethodId.SabaqSabqiManzil
        else HifzMethodId.FixedAyahs
}
