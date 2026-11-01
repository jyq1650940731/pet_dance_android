package app.petdance.domain

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val LOOPBACK = setOf("127.0.0.1", "localhost", "0.0.0.0")

/**
 * 本机存储返回的地址常常是 127.0.0.1。模拟器和局域网真机访问不到电脑的回环地址，
 * 这里改写成当前 API 主机，路径和签名保持不变。
 */
fun rewriteMediaUrl(url: String?, apiBase: String): String? {
    if (url.isNullOrBlank()) return null
    val base = apiBase.toHttpUrlOrNull() ?: return url
    val absolute = if (url.startsWith("/")) base.newBuilder().encodedPath("/").build().toString().trimEnd('/') + url else url
    val parsed = absolute.toHttpUrlOrNull() ?: return url
    if (parsed.host !in LOOPBACK) return absolute
    if (parsed.host == base.host && parsed.port == base.port && parsed.scheme == base.scheme) return absolute
    return parsed.newBuilder()
        .scheme(base.scheme)
        .host(base.host)
        .port(base.port)
        .build()
        .toString()
}
