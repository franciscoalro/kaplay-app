package com.kaplay.app.features.addons

/**
 * Public metadata/catalog addon used as the app's general movie and series index.
 *
 * This is intentionally not persisted as a user addon and it has no `stream`
 * resource. Playback remains owned by the installed addons/plugins, so adding a
 * catalog cannot make a stream provider appear available.
 */
internal const val GENERAL_CATALOG_MANIFEST_URL =
    "https://v3-cinemeta.strem.io/manifest.json"

internal const val GENERAL_CATALOG_MANIFEST_ID = "com.linvo.cinemeta"

/**
 * Cinemeta follows the Stremio generic contract: movies use `movie`, series use
 * `series`, and both use IMDb ids (`tt...`). `tv` is reserved for the embedded
 * Tomato contract and is not substituted here.
 */
internal fun generalCatalogAddon(): ManagedAddon =
    ManagedAddon(
        manifestUrl = GENERAL_CATALOG_MANIFEST_URL,
        manifest = AddonManifest(
            id = GENERAL_CATALOG_MANIFEST_ID,
            name = "Cinemeta",
            description = "Public movie and series catalogs",
            version = "3",
            resources = listOf(
                AddonResource(
                    name = "catalog",
                    types = listOf("movie", "series"),
                ),
                AddonResource(
                    name = "meta",
                    types = listOf("movie", "series"),
                    idPrefixes = listOf("tt"),
                ),
            ),
            types = listOf("movie", "series"),
            idPrefixes = listOf("tt"),
            catalogs = listOf(
                generalCatalog("movie"),
                generalCatalog("series"),
            ),
            transportUrl = GENERAL_CATALOG_MANIFEST_URL,
        ),
    )

/** Adds the public catalog for catalog/meta consumers without persisting it. */
internal fun List<ManagedAddon>.withGeneralCatalog(): List<ManagedAddon> =
    if (any { it.manifest?.id == GENERAL_CATALOG_MANIFEST_ID }) {
        this
    } else {
        listOf(generalCatalogAddon()) + this
    }

private fun generalCatalog(type: String): AddonCatalog =
    AddonCatalog(
        type = type,
        id = "top",
        name = "Popular",
        extra = listOf(
            AddonExtraProperty(name = "search"),
            AddonExtraProperty(name = "skip"),
        ),
    )
