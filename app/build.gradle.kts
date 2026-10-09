plugins {
    id("com.android.application")
    // Kotlin встроен в AGP 9 — отдельного плагина Kotlin нет
    id("org.jetbrains.kotlin.plugin.compose")
}

// Версия и ключи подписи приходят из окружения: их задают workflow
// (.github/workflows). Локальная сборка без переменных получает версию
// 0.1.0 (код 1), отладочную подпись, а release собирается неподписанным.
fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

android {
    namespace = "ru.wishka.memory"
    // Compose 1.12 требует compileSdk 37
    compileSdk = 37

    defaultConfig {
        applicationId = "ru.wishka.memory"
        minSdk = 26
        // Google Play с 31 августа 2026 принимает обновления с targetSdk 36+
        targetSdk = 36
        versionCode = env("VERSION_CODE")?.toInt() ?: 1
        versionName = env("VERSION_NAME") ?: "0.1.0"
    }

    signingConfigs {
        // Общий ключ разработки: с ним dev-сборки из CI ставятся поверх
        // друг друга. Файл и пароль — из секретов окружения dev
        // (.github/workflows/dev-build.yml). Без них — обычный отладочный
        // ключ машины, на которой идёт сборка.
        env("DEV_KEYSTORE_PATH")?.let { path ->
            create("dev") {
                storeFile = file(path)
                storePassword = env("DEV_KEYSTORE_PASSWORD")
                keyAlias = env("DEV_KEY_ALIAS") ?: "dev"
                keyPassword = env("DEV_KEYSTORE_PASSWORD")
            }
        }
        // Ключ выпуска — один на всю жизнь приложения: потерянный ключ
        // означает, что обновления больше не встанут поверх установленной
        // версии. Файл и пароли — из секретов окружения production.
        env("ANDROID_KEYSTORE_PATH")?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = env("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = env("ANDROID_KEY_ALIAS")
                keyPassword = env("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            signingConfigs.findByName("dev")?.let { signingConfig = it }
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // Kotlin берёт jvmTarget отсюда же — отдельный kotlinOptions не нужен
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
