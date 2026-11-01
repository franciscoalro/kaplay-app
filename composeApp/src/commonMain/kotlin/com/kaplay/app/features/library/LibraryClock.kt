package com.kaplay.app.features.library

internal expect object LibraryClock {
    fun nowEpochMs(): Long
}
