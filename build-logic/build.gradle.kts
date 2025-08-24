plugins {
    `kotlin-dsl`
}

group = "com.abbott.mosaic"

version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jlleitschuh.gradle:ktlint-gradle:12.1.0")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.5")
    implementation("org.jetbrains.kotlinx:kover-gradle-plugin:0.7.5")
}
