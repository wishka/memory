pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // LiveKit тянет пару библиотек с JitPack (audioswitch)
        maven("https://jitpack.io")
    }
}

rootProject.name = "Memory"
include(":app")
