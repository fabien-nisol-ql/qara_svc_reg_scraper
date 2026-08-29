// versioning.gradle.kts

fun String.runCommand(): String? {
    return try {
        val process = ProcessBuilder(*split(" ").toTypedArray())
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()

        if (exitCode == 0) output else null
    } catch (e: Exception) {
        null
    }
}

val versionFromGit: String by extra {
    val defaultVersion = "0.1.0"
    val tag = "git describe --tags --abbrev=0".runCommand()?.trim() ?: defaultVersion
    val describe = "git describe --tags --always".runCommand()?.trim() ?: tag
    val isExactTag = describe == tag

    if (isExactTag) {
        tag
    } else {
        val shortHash = "git rev-parse --short HEAD".runCommand()?.trim() ?: "dev"
        val branch = "git rev-parse --abbrev-ref HEAD".runCommand()?.trim() ?: "dev"

        val suffix = when {
            branch == "main" -> "m"
            branch.startsWith("hotfix-") -> "h"
            else -> "d"
        }

        "$tag-$suffix$shortHash"
    }
}
