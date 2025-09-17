import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
  id("org.jetbrains.kotlinx.kover")
}

tasks.withType<Test> {
  useJUnitPlatform()
  finalizedBy("koverHtmlReport")
}

val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
  add("testImplementation", kotlin("test"))
  add("testImplementation", libs.findLibrary("kotlinx.coroutines.test").get())
}

kover {
  reports {
    filters {
      excludes {
        classes(
          "kotlin.jvm.internal.*",
          "**\$DefaultImpls",
        )
      }
    }

    verify {
      rule {
        minBound(80, CoverageUnit.LINE, AggregationType.COVERED_PERCENTAGE)
      }
      rule {
        minBound(80, CoverageUnit.BRANCH, AggregationType.COVERED_PERCENTAGE)
      }
    }
  }
}
