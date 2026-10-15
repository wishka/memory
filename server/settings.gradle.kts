// Сервер Memory — отдельная сборка (не часть Android-проекта).
// Из корня репозитория: ./gradlew -p server test installDist
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "memory-server"
