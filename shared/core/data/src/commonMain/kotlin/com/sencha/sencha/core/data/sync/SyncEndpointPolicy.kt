package com.sencha.sencha.core.data.sync

import io.ktor.http.Url

object SyncEndpointPolicy {
    fun validate(baseUrl: String, isDebug: Boolean, allowlistedHosts: Set<String>): Result<String> {
        return runCatching {
            val url = Url(baseUrl)
            val scheme = url.protocol.name
            if (scheme != "https") {
                val host = url.host
                val allowed = isDebug && allowlistedHosts.any { it.equals(host, ignoreCase = true) }
                if (!allowed) {
                    error("HTTPS is required for sync endpoints")
                }
            }
            url.toString().trimEnd('/')
        }
    }
}
