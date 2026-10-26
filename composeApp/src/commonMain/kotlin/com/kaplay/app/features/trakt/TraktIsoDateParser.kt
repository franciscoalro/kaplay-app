package com.kaplay.app.features.trakt

import com.kaplay.app.core.time.parseZonedIsoDateTimeToEpochMs

internal fun parseTraktIsoDateTimeToEpochMs(value: String): Long? =
    parseZonedIsoDateTimeToEpochMs(value)
