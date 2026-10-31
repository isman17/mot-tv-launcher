pluginManagement {
    repositories {
        // Local file repo first: on this VM, Java TCP is blocked by the runtime,
        // so dependencies are pre-fetched with curl into ~/workspace/tools/maven-local
        // and builds run with --offline. Remote repos below remain for normal machines.
        maven { url = uri("file:///home/hatch/workspace/tools/maven-local") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("file:///home/hatch/workspace/tools/maven-local") }
        google()
        mavenCentral()
    }
}
rootProject.name = "MotTvLauncher"
include(":app")
