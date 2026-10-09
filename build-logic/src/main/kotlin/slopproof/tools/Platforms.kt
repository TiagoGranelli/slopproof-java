package slopproof.tools

/**
 * The manifest key for a JVM `os.name` / `os.arch` pair, such as `windows-x64` or `macos-arm64`.
 * An unknown pair still gets a key, so the missing asset is reported by name at run time.
 */
fun platformKey(
    osName: String,
    osArch: String,
): String {
    val os =
        when {
            osName.startsWith("Windows") -> "windows"
            osName.startsWith("Mac") -> "macos"
            osName.startsWith("Linux") -> "linux"
            else -> osName.lowercase().replace(' ', '-')
        }
    val arch =
        when (osArch.lowercase()) {
            "amd64", "x86_64", "x64" -> "x64"
            "aarch64", "arm64" -> "arm64"
            else -> osArch.lowercase()
        }
    return "$os-$arch"
}

/** The installed file name: the tool name, with `.exe` on Windows, where the OS only runs `.exe` files. */
fun executableName(
    tool: String,
    platform: String,
): String = if (platform.startsWith("windows-")) "$tool.exe" else tool
