package slopproof.tools

import org.gradle.api.GradleException

private val BARE_KEY = Regex("[A-Za-z0-9_-]+")
private val TABLE_NAME = Regex("[A-Za-z0-9_.-]+")

/**
 * Reads the TOML subset that gradle/native-tools.toml uses: `[dotted.table]` headers, `key = "value"`
 * string pairs and full-line comments. Anything else fails with its line number, because a typo that
 * silently dropped a pinned hash would defeat the checksum.
 */
internal fun parseTomlTables(text: String, source: String): Map<String, Map<String, String>> {
    val tables = linkedMapOf<String, MutableMap<String, String>>()
    var current = tables.getOrPut("") { linkedMapOf() }
    text.lines().forEachIndexed { index, raw ->
        val line = raw.trim()
        val where = "$source:${index + 1}"
        if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
        if (line.startsWith("[")) {
            val name = tableName(line, where)
            // A second [header] would reopen the table and quietly add keys to a pinned asset.
            if (name in tables) throw GradleException("$where: table [$name] is defined twice")
            current = tables.getOrPut(name) { linkedMapOf() }
            return@forEachIndexed
        }
        val (key, value) = keyValue(line, where)
        if (key in current) throw GradleException("$where: duplicate key \"$key\"")
        current[key] = value
    }
    return tables
}

private fun tableName(line: String, where: String): String {
    val name = line.removePrefix("[").removeSuffix("]").trim()
    if (!line.endsWith("]") || !TABLE_NAME.matches(name)) {
        throw GradleException("$where: expected a table header like [tool.platform] (got \"$line\")")
    }
    return name
}

private fun keyValue(line: String, where: String): Pair<String, String> {
    val separator = line.indexOf('=')
    val key = if (separator < 0) "" else line.substring(0, separator).trim()
    val quoted = if (separator < 0) "" else line.substring(separator + 1).trim()
    val isQuoted = quoted.length >= 2 && quoted.startsWith("\"") && quoted.endsWith("\"")
    if (!BARE_KEY.matches(key) || !isQuoted) {
        throw GradleException("$where: expected key = \"value\" (got \"$line\")")
    }
    val value = quoted.substring(1, quoted.length - 1)
    if ('"' in value) throw GradleException("$where: escaped quotes are not supported in values")
    return key to value
}
