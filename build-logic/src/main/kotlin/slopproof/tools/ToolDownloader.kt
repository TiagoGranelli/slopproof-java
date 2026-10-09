package slopproof.tools

import org.gradle.api.GradleException
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Downloads release assets over HTTPS. Java's HttpClient ignores HTTPS_PROXY, so it is read here;
 * otherwise the JVM proxy settings apply (Gradle's systemProp.https.proxyHost, or
 * -Djava.net.useSystemProxies=true on Windows).
 */
internal class ToolDownloader(env: Map<String, String> = System.getenv()) {
    private val client: HttpClient = HttpClient.newBuilder()
        .proxy(proxySelector(env))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    /** Writes the body of [url] to [target]. [describe] names the tool in errors, e.g. "vacuum 0.32.0 (linux-x64)". */
    fun download(url: String, target: File, describe: String) {
        val request = HttpRequest.newBuilder(URI.create(url)).GET().build()
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofFile(target.toPath()))
        } catch (failure: IOException) {
            throw GradleException(
                "slopproof: cannot download $describe from $url (${failure.javaClass.simpleName}). " +
                    "Behind a proxy? Set HTTPS_PROXY to host:port, or point -Pslopproof.tools.mirror at an internal " +
                    "mirror of GitHub releases, or skip with -Pslopproof.tools.skip=true.",
                failure,
            )
        }
        if (response.statusCode() != HTTP_OK) {
            target.delete()
            throw GradleException(
                "slopproof: $describe download returned HTTP ${response.statusCode()} for $url. " +
                    "Check the URL pinned in gradle/native-tools.toml, or the mirror in -Pslopproof.tools.mirror.",
            )
        }
    }

    private companion object {
        const val HTTP_OK = 200
    }
}

/** The proxy for all requests: HTTPS_PROXY (host:port, optionally with a scheme) when set, else the JVM default. */
internal fun proxySelector(env: Map<String, String>): ProxySelector {
    val proxy = env["HTTPS_PROXY"] ?: env["https_proxy"] ?: return ProxySelector.getDefault()
    val uri = URI(if ("://" in proxy) proxy else "http://$proxy")
    // The value may carry credentials, so the error names the variable and not its content.
    require(uri.host != null && uri.port != -1) { "HTTPS_PROXY must be host:port (for example proxy.corp:8080)" }
    return ProxySelector.of(InetSocketAddress(uri.host, uri.port))
}
