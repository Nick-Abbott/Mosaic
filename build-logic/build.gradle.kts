plugins {
    `kotlin-dsl`
}

group = "com.abbott.mosaic"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(kotlin("gradle-plugin", libs.versions.kotlin.get()))
    implementation(libs.ktlint.gradle)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.kover.gradle.plugin)
}
