pluginManagement {
    repositories {
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        maven("https://dl.google.com/dl/android/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        maven("https://dl.google.com/dl/android/maven2/")
        mavenCentral()
    }
    versionCatalogs { create("libs") }
}

rootProject.name = "wxscript"
include(
    ":app",
    ":libs:common:bsh",
    ":libs:common:reflekt",
    ":libs:common:stubs",
)
