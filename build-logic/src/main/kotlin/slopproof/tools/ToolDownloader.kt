package slopproof.tools

import org.gradle.api.GradleException
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.URISyntaxException
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

    /**
     * Writes the body of [url] to [target]. [describe] names the tool and its public source in errors. Errors never
     * print [url]: a mirror URL may carry a token, so the caller describes the GitHub URL instead.
     */
    fun download(url: String, target: File, describe: String) {
        val request = try {
            HttpRequest.newBuilder(URI.create(url)).GET().build()
        } catch (invalid: IllegalArgumentException) {
            throw GradleException("slopproof: $describe has an invalid download URL. Check -Pslopproof.tools.mirror.")
        }
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofFile(target.toPath()))
        } catch (failure: IOException) {
            // No cause attached: a redirect target chosen by the server can be printed by Gradle's "Caused by" output.
            throw GradleException(
                "slopproof: cannot download $describe (${failure.javaClass.simpleName}). " +
                    "Behind a proxy? Set HTTPS_PROXY to host:port, or point -Pslopproof.tools.mirror at an internal " +
                    "mirror of GitHub releases, or skip with -Pslopproof.tools.skip=true.",
            )
        }
        if (response.statusCode() != HTTP_OK) {
            target.delete()
            throw GradleException(
                "slopproof: $describe download returned HTTP ${response.statusCode()}. " +
                    "Check the URL pinned in gradle/native-tools.toml, or the mirror in -Pslopproof.tools.mirror.",
            )
        }
    }

    private companion object {
        const val HTTP_OK = 200
    }
}

/**
 * The proxy for all requests: HTTPS_PROXY (host:port, optionally with a scheme) when set, else the JVM default.
 * The value may carry credentials, so no error below echoes it: they name the variable only.
 */
internal fun proxySelector(env: Map<String, String>): ProxySelector {
    val proxy = env["HTTPS_PROXY"] ?: env["https_proxy"] ?: return ProxySelector.getDefault()
    val uri = try {
        URI(if ("://" in proxy) proxy else "http://$proxy")
    } catch (malformed: URISyntaxException) {
        throw GradleException("HTTPS_PROXY is not a valid URL (expected host:port, for example proxy.corp:8080)")
    }
    // JDK HttpClient cannot send proxy credentials from a URL, so fail instead of connecting unauthenticated.
    if (uri.rawUserInfo != null) {
        throw GradleException(
            "HTTPS_PROXY contains credentials, which slopproof does not send. Use -Djava.net.useSystemProxies=true " +
                "with the proxy set in the JVM, or an unauthenticated proxy or mirror.",
        )
    }
    if (uri.host == null || uri.port == -1) {
        throw GradleException("HTTPS_PROXY must be host:port (for example proxy.corp:8080)")
    }
    return ProxySelector.of(InetSocketAddress(uri.host, uri.port))
}
