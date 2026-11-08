package com.kaplay.app.features.addons

/** Identifies the enabled addon/catalog that produced a content result. */
data class ContentOrigin(
    val manifestUrl: String,
    val addonId: String? = null,
    val catalogId: String? = null,
)