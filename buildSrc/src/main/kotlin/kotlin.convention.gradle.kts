import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  kotlin("jvm")
}

kotlin {
  jvmToolchain(21)
  compilerOptions {
    // Keep the established Kotlin module name and internal JVM member suffixes.
    moduleName.set(project.name)
    jvmTarget.set(JvmTarget.JVM_17)
    languageVersion.set(KotlinVersion.KOTLIN_2_4)
    apiVersion.set(KotlinVersion.KOTLIN_2_4)
  }
}

java {
  targetCompatibility = JavaVersion.VERSION_17
  sourceCompatibility = JavaVersion.VERSION_17
}

