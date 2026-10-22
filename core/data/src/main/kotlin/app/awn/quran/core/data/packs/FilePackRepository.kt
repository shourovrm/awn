package app.awn.quran.core.data.packs

import app.awn.quran.core.data.CatalogueItem
import app.awn.quran.core.data.InstalledPack
import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.PackSize
import app.awn.quran.core.data.ScriptId
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Suppress("TooManyFunctions") // Interface members plus the install steps.
/**
 * Installs packs from the data release into the app's files folder and reports what is installed.
 * Bundled packs count as installed from the start and cannot be removed.
 */
class FilePackRepository(
    private val catalog: ManifestCatalog,
    private val storage: PackStorage,
    private val databases: ReadOnlyDatabases,
    private val downloader: PackDownloader = PackDownloader(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val supportedFormatVersion: Int = ManifestCatalog.SUPPORTED_FORMAT_VERSION,
) : PackRepository {

    private val installMutex = Mutex()
    private val changeCounter = MutableStateFlow(0)

    override fun installed(): Flow<List<InstalledPack>> = changeCounter.map {
        withContext(ioDispatcher) { scanInstalled() }
    }

    override suspend fun manifest(kind: String): List<PackEntry> =
        when (kind.removeSuffix(".json")) {
            "scripts" -> catalog.scriptPacks
            "translations" -> catalog.translationPacks
            "reciters" -> catalog.timingPacks
            "learn-audio" -> catalog.learnAudioPacks
            "models" -> emptyList()
            else -> throw IllegalArgumentException("Unknown manifest '$kind'.")
        }.map { it.entry }

    override suspend fun sizeOf(id: String): PackSize? {
        val record = catalog.findPack(id) ?: return null
        val installedBytes =
            withContext(ioDispatcher) {
                storage.locate(record)?.let { location -> storage.sizeOnDisk(location) }
            }
        return PackSize(downloadBytes = record.bytes, installedBytes = installedBytes)
    }

    override suspend fun catalogue(): List<CatalogueItem> =
        withContext(ioDispatcher) {
            catalog.allPacks.mapNotNull { record -> catalogueItemOf(record) }
        }

    override fun freeBytes(): Long = storage.freeBytes()

    override suspend fun install(entry: PackEntry, onProgress: (Float) -> Unit) {
        val record = requireRecord(entry.id)
        val steps = withContext(ioDispatcher) { installSteps(record, entry) }
        val totalBytes = steps.sumOf { step -> step.record.bytes }
        var finishedBytes = 0L
        for (step in steps) {
            val bytesBeforeStep = finishedBytes
            installOne(step.record, step.entry) { fraction ->
                onProgress((bytesBeforeStep + fraction * step.record.bytes) / totalBytes)
            }
            finishedBytes += step.record.bytes
        }
        changeCounter.update { it + 1 }
        onProgress(1f)
    }

    /**
     * What to download for [record]: the packs it requires that are missing or out of date, then
     * it.
     */
    private fun installSteps(record: PackRecord, entry: PackEntry): List<InstallStep> =
        requirementsToInstall(record).map { required -> InstallStep(required, required.entry) } +
            InstallStep(record, entry)

    private fun requirementsToInstall(record: PackRecord): List<PackRecord> =
        record.requires.map(::requireRecord).filter { required ->
            storage.locate(required) == null ||
                PackUpdate.isAvailable(storage.installedSha(required), required.entry.sha256)
        }

    private suspend fun installOne(
        record: PackRecord,
        entry: PackEntry,
        onProgress: (Float) -> Unit,
    ) {
        withContext(ioDispatcher) {
            installMutex.withLock {
                val partFile = File(storage.downloadsDirectory, "${record.fileName}.part")
                requireFreeSpace(record)
                downloadAndVerify(entry, record, partFile, onProgress)
                place(record, partFile)
                partFile.delete()
                storage.recordInstalledSha(record, entry.sha256)
            }
        }
    }

    override suspend fun remove(id: String) {
        val record = requireRecord(id)
        require(!storage.isBundled(record)) {
            "The pack '$id' ships with the app and cannot be removed."
        }
        withContext(ioDispatcher) {
            installMutex.withLock {
                databases.close(storage.locationOf(record))
                storage.delete(record)
                removeRequirementsNobodyUses(record)
            }
        }
        changeCounter.update { it + 1 }
    }

    /** Deletes the packs [removed] required when no other installed pack requires them. */
    private fun removeRequirementsNobodyUses(removed: PackRecord) {
        for (requiredId in removed.requires) {
            val required = requireRecord(requiredId)
            if (storage.isBundled(required)) continue
            val isStillRequired =
                catalog.allPacks.any { other ->
                    other.entry.id != removed.entry.id &&
                        requiredId in other.requires &&
                        storage.locate(other) != null
                }
            if (!isStillRequired) {
                databases.close(storage.locationOf(required))
                storage.delete(required)
            }
        }
    }

    @Suppress("ReturnCount") // Each early return is a distinct "not installed" case.
    override fun fontFile(script: ScriptId, page: Int): File? {
        val record = catalog.scriptPacks.firstOrNull { it.entry.id == script.packId } ?: return null
        val location = storage.locate(record) ?: return null
        return when (record.layout) {
            PackFileLayout.FontArchive -> {
                require(page in FIRST_PAGE..LAST_PAGE) {
                    "Page must be 1..$LAST_PAGE but was $page."
                }
                File(location, "p$page.ttf").takeIf { it.isFile }
            }
            else -> location
        }
    }

    override fun learnAudioFile(clipId: String): File? {
        val location = catalog.learnAudioPacks.firstOrNull()?.let { storage.locate(it) }
        // The clip id comes from lesson files; the extracted folder only holds names that the
        // archive check accepted, so a path that is not one of them finds nothing.
        return location
            ?.let { File(it, "$clipId.opus") }
            ?.takeIf {
                it.isFile && it.canonicalPath.startsWith(location.canonicalPath)
            }
    }

    /**
     * Maps a manifest record to its catalogue row; records outside every group are skipped. The
     * download size and the free space needed count the required packs that are still to come, so a
     * print with its own layout pack shows the whole cost; the row is installed only when they are
     * on the phone too.
     */
    private fun catalogueItemOf(record: PackRecord): CatalogueItem? {
        val group = CatalogueGrouping.groupOf(record, storage.isBundled(record)) ?: return null
        val location = storage.locate(record)
        val toInstall = requirementsToInstall(record)
        val requirements = record.requires.map(::requireRecord)
        return CatalogueItem(
            id = record.entry.id,
            name = record.displayName,
            group = group,
            language = record.language,
            downloadBytes = record.bytes + toInstall.sumOf { it.bytes },
            requiredFreeBytes =
                requiredFreeBytesOf(record) + toInstall.sumOf { requiredFreeBytesOf(it) },
            isInstalled = location != null && requirements.all { storage.locate(it) != null },
            installedBytes = location?.let { storage.sizeOnDisk(it) },
            isBundled = storage.isBundled(record),
            hasUpdate = location != null && hasUpdate(record, requirements),
            entry = record.entry,
        )
    }

    /**
     * True when the installed copy of [record], or of a pack it requires, has an older checksum.
     */
    private fun hasUpdate(record: PackRecord, requirements: List<PackRecord>): Boolean =
        (listOf(record) + requirements).any { pack ->
            storage.locate(pack) != null &&
                PackUpdate.isAvailable(storage.installedSha(pack), pack.entry.sha256)
        }

    private fun requiredFreeBytesOf(record: PackRecord): Long =
        PackSpace.requiredFreeBytes(
            record.layout,
            record.bytes,
            storage.partialDownloadBytes(record),
        )

    /** Fails before any byte is downloaded when the phone cannot hold the pack. */
    private fun requireFreeSpace(record: PackRecord) {
        val required = requiredFreeBytesOf(record)
        val free = storage.freeBytes()
        if (PackSpace.shortfall(required, free) > 0) {
            throw PackInstallException(
                record.fileName,
                "needs $required bytes but only $free are free",
                reason = PackFailureReason.NoSpace,
                neededBytes = required,
                freeBytes = free,
            )
        }
    }

    private fun requireRecord(id: String): PackRecord =
        requireNotNull(catalog.findPack(id)) { "There is no pack '$id' in the manifests." }

    private suspend fun downloadAndVerify(
        entry: PackEntry,
        record: PackRecord,
        partFile: File,
        onProgress: (Float) -> Unit,
    ) {
        downloader.download(entry.downloadUrl, partFile, record.fileName, record.bytes) {
            written,
            total ->
            if (total > 0) onProgress(written.toFloat() / total * DOWNLOAD_SHARE)
        }
        if (partFile.length() != record.bytes) {
            partFile.delete()
            throw PackInstallException(
                record.fileName,
                "expected ${record.bytes} bytes but received ${partFile.length()}",
                reason = PackFailureReason.Damaged,
            )
        }
        val actualHash = PackDownloader.sha256Of(partFile)
        if (actualHash != entry.sha256) {
            partFile.delete()
            throw PackInstallException(
                record.fileName,
                "SHA-256 mismatch: expected ${entry.sha256} but got $actualHash; download discarded",
                reason = PackFailureReason.Damaged,
            )
        }
    }

    @Suppress("ThrowsCount") // Each throw names a different rejected condition.
    private fun place(record: PackRecord, verifiedFile: File) {
        val destination = storage.locationOf(record)
        try {
            when (record.layout) {
                PackFileLayout.Database -> {
                    val version = PackFiles.databaseFormatVersion(verifiedFile, record.fileName)
                    if (version > supportedFormatVersion) {
                        verifiedFile.delete()
                        throw PackInstallException(
                            record.fileName,
                            "format_version $version is newer than the supported $supportedFormatVersion",
                        )
                    }
                    databases.close(destination)
                    moveInto(verifiedFile, destination)
                }
                PackFileLayout.SingleFont -> moveInto(verifiedFile, destination)
                PackFileLayout.FontArchive -> extractArchive(record, verifiedFile, destination)
                PackFileLayout.AudioArchive -> extractArchive(record, verifiedFile, destination)
            }
        } catch (error: PackInstallException) {
            throw error
        } catch (error: IOException) {
            throw PackInstallException(
                record.fileName,
                "could not install: ${error.message}",
                error,
                PackFailureReason.of(error),
            )
        }
    }

    private fun extractArchive(record: PackRecord, archive: File, destination: File) {
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        temporary.deleteRecursively()
        destination.parentFile?.mkdirs()
        val extractedCount =
            if (record.layout == PackFileLayout.AudioArchive) {
                PackFiles.extractAudioArchive(archive, temporary, record.fileName)
            } else {
                PackFiles.extractFontArchive(archive, temporary, record.fileName)
            }
        if (record.memberCount != null && extractedCount != record.memberCount) {
            temporary.deleteRecursively()
            throw PackInstallException(
                record.fileName,
                "archive holds $extractedCount files but the manifest lists ${record.memberCount}",
            )
        }
        destination.deleteRecursively()
        Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    private fun moveInto(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun scanInstalled(): List<InstalledPack> =
        catalog.allPacks.mapNotNull { record ->
            val location = storage.locate(record) ?: return@mapNotNull null
            InstalledPack(
                id = record.entry.id,
                kind = record.kindLabel,
                formatVersion = record.entry.formatVersion,
                bytes = storage.sizeOnDisk(location),
            )
        }

    /** One download of an install: a pack and the manifest entry it is checked against. */
    private class InstallStep(val record: PackRecord, val entry: PackEntry)

    private companion object {
        const val FIRST_PAGE = 1
        const val LAST_PAGE = 604

        /** Share of the progress bar for the download; the rest is verification and unpacking. */
        const val DOWNLOAD_SHARE = 0.95f
    }
}
