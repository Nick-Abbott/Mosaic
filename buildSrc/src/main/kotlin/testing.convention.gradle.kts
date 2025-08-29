import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
  id("org.jetbrains.kotlinx.kover")
}

tasks.withType<Test> {
  useJUnitPlatform()
  finalizedBy("koverHtmlReport")
}

dependencies {
  add("testImplementation", kotlin("test"))
  add("testImplementation", "org.junit.jupiter:junit-jupiter:5.10.2")
}

kover {
  reports {
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
