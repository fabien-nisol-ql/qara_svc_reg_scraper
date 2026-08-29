rootProject.name = "qara-reg-scraper-svc"

val libBaseDir = System.getProperty("LIB_BASE_DIR") ?: "../"
val localLib = file("${libBaseDir.trimEnd('/')}/qara_lib_mn")

if (localLib.exists()) {
    System.err.println("🔗 Using local qara_lib_mn via composite build from $libBaseDir")
    includeBuild(localLib)
} else {
    System.err.println("🔗 $localLib does not exist. Falling back to maven dependency")
}
