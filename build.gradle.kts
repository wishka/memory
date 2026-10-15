// Версии плагинов — в одном месте; подключаются в модуле app.
//
// AGP 9 собирает Kotlin сам («встроенный Kotlin»): отдельный плагин
// org.jetbrains.kotlin.android больше не нужен. Версию Kotlin задаёт
// kotlin-gradle-plugin в buildscript ниже; плагин компилятора Compose
// обязан быть той же версии — меняйте обе строки вместе.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
