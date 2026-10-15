// Сервер Memory: Kotlin + Ktor 3 (Netty), PostgreSQL через JDBC.
// Kotlin той же версии, что в приложении (build.gradle.kts в корне).
plugins {
    kotlin("jvm") version "2.4.21"
    application
}

group = "ru.wishka.memory"
version = "1.0"

val ktor = "3.2.3"

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktor")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktor")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("com.zaxxer:HikariCP:6.3.0")
    // Тот же org.json, что у Android: записи и сообщения — один JSON
    implementation("org.json:json:20240303")
    implementation("ch.qos.logback:logback-classic:1.5.18")

    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("ru.wishka.memory.server.MainKt")
}

tasks.test {
    // Для PgStoreTest — база из CI (пусто — эти проверки пропускаются)
    listOf("TEST_DATABASE_URL", "TEST_DB_USER", "TEST_DB_PASSWORD").forEach { k -> System.getenv(k)?.let { environment(k, it) } }
    testLogging { events("failed", "skipped"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
