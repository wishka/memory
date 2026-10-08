plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Версия и ключи подписи приходят из окружения: их задают workflow
// (.github/workflows). Локальная сборка без переменных получает версию
// 0.1.0 (код 1), отладочную подпись, а release собирается неподписанным.
fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

android {
    namespace = "ru.wishka.memory"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.wishka.memory"
        minSdk = 26
        targetSdk = 35
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
