package slopproof.tools

import org.gradle.api.GradleException

/** One release asset for one platform. [entry] is the executable inside an archive; null for a plain binary. */
data class ToolAsset(
    val url: String,
    val sha256: String,
    val entry: String?,
)

/** A tool pinned to one release, with its assets keyed by platform (`windows-x64`, `linux-arm64`, ...). */
data class NativeTool(
    val name: String,
    val version: String,
    val released: String,
    val assets: Map<String, ToolAsset>,
)

private val SHA256 = Regex("[0-9a-fA-F]{64}")
private const val GITHUB = "https://github.com/"

/** Parses gradle/native-tools.toml. [source] names the file in error messages. */
fun parseToolManifest(
    text: String,
    source: String,
): Map<String, NativeTool> {
    val tables = parseTomlTables(text, source)
    val toolNames = tables.keys.filter { it.isNotEmpty() && '.' !in it }
    return toolNames.associateWith { name -> nativeTool(name, tables, source) }
}

/** The pinned release of [tool]; fails with the manifest path when the tool is not pinned there. */
fun pinnedTool(
    tools: Map<String, NativeTool>,
    tool: String,
): NativeTool = tools[tool] ?: throw GradleException("slopproof: tool \"$tool\" is not pinned in gradle/native-tools.toml")

/** The pinned asset of [tool] for [platform], or null when the release has none for that platform. */
fun assetOf(
    tools: Map<String, NativeTool>,
    tool: String,
    platform: String,
): ToolAsset? = pinnedTool(tools, tool).assets[platform]

private fun nativeTool(
    name: String,
    tables: Map<String, Map<String, String>>,
    source: String,
): NativeTool {
    val header = tables[name] ?: throw GradleException("$source: missing [$name] table")
    val assets = tables.filterKeys { it.startsWith("$name.") }.mapKeys { it.key.removePrefix("$name.") }
    return NativeTool(
        name = name,
        version = required(header, "version", "[$name]", source),
        released = required(header, "released", "[$name]", source),
        assets = assets.mapValues { (platform, values) -> asset(name, platform, values, source) },
    )
}

private fun asset(
    tool: String,
    platform: String,
    values: Map<String, String>,
    source: String,
): ToolAsset {
    val where = "[$tool.$platform]"
    val url = required(values, "url", where, source)
    val sha256 = required(values, "sha256", where, source)
    if (!url.startsWith(GITHUB)) {
        throw GradleException("$source: $where url must start with $GITHUB (got \"$url\"); -Pslopproof.tools.mirror rewrites that prefix")
    }
    if (!SHA256.matches(sha256)) {
        throw GradleException("$source: $where sha256 must be 64 hex characters (got ${sha256.length} characters)")
    }
    return ToolAsset(url = url, sha256 = sha256.lowercase(), entry = values["entry"])
}

private fun required(
    values: Map<String, String>,
    key: String,
    where: String,
    source: String,
): String = values[key] ?: throw GradleException("$source: $where needs `$key`")

/** Where the pinned tools live, relative to the project root. */
const val NATIVE_TOOLS_MANIFEST = "gradle/native-tools.toml"
