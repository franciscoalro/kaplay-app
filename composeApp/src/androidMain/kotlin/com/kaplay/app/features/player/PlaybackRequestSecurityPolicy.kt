package com.kaplay.app.features.player

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

internal data class PlaybackTopLevelRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

internal class PlaybackRequestSecurityContext private constructor() {
    companion object {
        fun create(
            primary: PlaybackTopLevelRequest,
            additional: Iterable<PlaybackTopLevelRequest> = emptyList(),
        ): PlaybackRequestSecurityContext {
            @Suppress("UNUSED_VARIABLE")
            val inputs = listOf(primary) + additional
            return PlaybackRequestSecurityContext()
        }
    }
}

internal fun PlaybackRequestSecurityContext.secure(request: Request): Request =
    throw UnsupportedOperationException("Playback request security policy is not implemented")

internal class PlaybackRequestSecurityInterceptor(
    private val context: PlaybackRequestSecurityContext,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(context.secure(chain.request()))
}
