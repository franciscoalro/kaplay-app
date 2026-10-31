package com.kaplay.app.features.player

import java.io.IOException
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

internal data class PlaybackTopLevelRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

internal class PlaybackRequestSecurityContext private constructor(
    private val primaryAllowsHttpChildren: Boolean,
    private val explicitHttpTopLevelUrls: Set<String>,
    private val sensitiveHeadersByHttpsOrigin: Map<PlaybackOrigin, List<SensitiveResource>>,
) {
    companion object {
        fun create(
            primary: PlaybackTopLevelRequest,
            additional: Iterable<PlaybackTopLevelRequest> = emptyList(),
        ): PlaybackRequestSecurityContext {
            val primaryUrl = primary.url.trim().toHttpUrlOrNull()
            val explicitHttpUrls = linkedSetOf<String>()
            val sensitiveByOrigin = linkedMapOf<PlaybackOrigin, MutableList<SensitiveResource>>()

            sequenceOf(primary)
                .plus(additional.asSequence())
                .forEach { topLevel ->
                    val url = topLevel.url.trim().toHttpUrlOrNull() ?: return@forEach
                    when (url.scheme.lowercase()) {
                        "http" -> explicitHttpUrls += url.toString()
                        "https" -> {
                            val sensitive = topLevel.headers.mapNotNull { (name, value) ->
                                name.takeIf(::isSensitiveHeaderName)?.let {
                                    SensitiveHeader(name = it.lowercase(), value = value)
                                }
                            }.toSet()
                            if (sensitive.isNotEmpty()) {
                                val path = url.encodedPath
                                val prefix = path.substringBeforeLast('/', missingDelimiterValue = "")
                                    .let { if (it.isBlank()) "/" else "$it/" }
                                sensitiveByOrigin.getOrPut(url.playbackOrigin()) { mutableListOf() }
                                    .add(
                                        SensitiveResource(
                                            exactPath = path,
                                            childPrefix = prefix,
                                            headers = sensitive,
                                        ),
                                    )
                            }
                        }
                    }
                }

            return PlaybackRequestSecurityContext(
                primaryAllowsHttpChildren = primaryUrl?.scheme.equals("http", ignoreCase = true),
                explicitHttpTopLevelUrls = explicitHttpUrls,
                sensitiveHeadersByHttpsOrigin = sensitiveByOrigin.mapValues { (_, value) -> value.toList() },
            )
        }
    }

    internal fun secureRequest(request: Request): Request {
        val url = request.url
        return when (url.scheme.lowercase()) {
            "http" -> secureHttpRequest(request)
            "https" -> request.withAllowedSensitiveHeaders(allowedSensitiveHeadersFor(url))
            else -> request
        }
    }

    private fun allowedSensitiveHeadersFor(url: HttpUrl): Set<SensitiveHeader> {
        val resources = sensitiveHeadersByHttpsOrigin[url.playbackOrigin()].orEmpty()
        val exact = resources.filter { it.exactPath == url.encodedPath }
        if (exact.size == 1) return exact.single().headers
        if (exact.size > 1) return emptySet()

        val childMatches = resources.filter { url.encodedPath.startsWith(it.childPrefix) }
        if (childMatches.isEmpty()) return emptySet()
        val longestPrefix = childMatches.maxOf { it.childPrefix.length }
        val best = childMatches.filter { it.childPrefix.length == longestPrefix }
        return if (best.size == 1) best.single().headers else emptySet()
    }

    private fun secureHttpRequest(request: Request): Request {
        val url = request.url
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            throw IOException("Cleartext playback URL contains user-info credentials")
        }
        repeat(url.querySize) { index ->
            if (url.queryParameterName(index).lowercase() in sensitiveQueryNames) {
                throw IOException("Cleartext playback URL contains a sensitive query parameter")
            }
        }
        if (!primaryAllowsHttpChildren && url.toString() !in explicitHttpTopLevelUrls) {
            throw IOException("HTTPS playback cannot downgrade to an unapproved cleartext URL")
        }
        return request.withAllowedSensitiveHeaders(emptySet())
    }
}

internal fun PlaybackRequestSecurityContext.secure(request: Request): Request = secureRequest(request)

internal class PlaybackRequestSecurityInterceptor(
    private val context: PlaybackRequestSecurityContext,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(context.secure(chain.request()))
}

private data class PlaybackOrigin(
    val scheme: String,
    val host: String,
    val port: Int,
)

private data class SensitiveHeader(
    val name: String,
    val value: String,
)

private data class SensitiveResource(
    val exactPath: String,
    val childPrefix: String,
    val headers: Set<SensitiveHeader>,
)

private fun HttpUrl.playbackOrigin(): PlaybackOrigin = PlaybackOrigin(
    scheme = scheme.lowercase(),
    host = host.lowercase(),
    port = port,
)

private fun Request.withAllowedSensitiveHeaders(allowed: Set<SensitiveHeader>): Request {
    val securedHeaders = Headers.Builder()
    headers.forEach { (name, value) ->
        if (name.equals("Referer", ignoreCase = true) && isUnsafeReferer(value)) return@forEach
        val sensitive = isSensitiveHeaderName(name)
        val registered = SensitiveHeader(name = name.lowercase(), value = value) in allowed
        if (!sensitive || registered) securedHeaders.add(name, value)
    }
    return newBuilder().headers(securedHeaders.build()).build()
}

private fun isUnsafeReferer(value: String): Boolean {
    val referer = value.trim().toHttpUrlOrNull() ?: return true
    return referer.username.isNotEmpty() || referer.password.isNotEmpty() ||
        (0 until referer.querySize).any { referer.queryParameterName(it).lowercase() in sensitiveQueryNames }
}

private fun isSensitiveHeaderName(name: String): Boolean =
    name.lowercase() in sensitiveHeaderNames

private val sensitiveHeaderNames = setOf(
    "authorization",
    "cookie",
    "proxy-authorization",
    "x-api-key",
    "api-key",
)

private val sensitiveQueryNames = setOf(
    "token",
    "access_token",
    "auth",
    "authorization",
    "api_key",
    "apikey",
    "password",
    "passwd",
    "secret",
)
