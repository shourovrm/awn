package app.awn.quran.core.data.fakes

import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.CatalogueItem
import app.awn.quran.core.data.packs.Credit
import app.awn.quran.core.data.packs.PackEntry
import app.awn.quran.core.data.packs.PackKind

private const val SHA256_LENGTH = 64

/** A catalogue row for tests; the defaults are an English translation that is not installed. */
@Suppress("LongParameterList") // Mirrors the fields of CatalogueItem.
fun fakeCatalogueItem(
    id: String,
    group: CatalogueGroup = CatalogueGroup.Translation,
    language: String? = "en",
    downloadBytes: Long = 1_000_000L,
    installedBytes: Long? = null,
    isInstalled: Boolean = false,
    isBundled: Boolean = false,
    hasUpdate: Boolean = false,
    requiredFreeBytes: Long = downloadBytes,
): CatalogueItem =
    CatalogueItem(
        id = id,
        name = "Name of $id",
        group = group,
        language = language,
        downloadBytes = downloadBytes,
        requiredFreeBytes = requiredFreeBytes,
        isInstalled = isInstalled,
        installedBytes = installedBytes,
        isBundled = isBundled,
        hasUpdate = hasUpdate,
        entry =
            PackEntry(
                id = id,
                kind =
                    if (group == CatalogueGroup.PageType) PackKind.Script else PackKind.Translation,
                formatVersion = 1,
                sha256 = "f".repeat(SHA256_LENGTH),
                downloadUrl = "https://example.org/$id",
                credit = Credit("Credit", "https://example.org", "Not stated"),
            ),
    )
