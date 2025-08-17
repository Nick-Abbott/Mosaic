/*
 * Mosaic Core module build.gradle.kts
 * This module contains the main Mosaic functionality
 */

// This module inherits all configuration from the root build.gradle.kts
// Additional module-specific configuration can be added here

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  testImplementation(kotlin("test"))
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

// ktlint configuration
ktlint {
  version.set("1.0.1")
  android.set(false)
  verbose.set(true)
  filter {
    exclude { element -> element.file.path.contains("build/") }
  }
  ignoreFailures.set(false)
  reporters {
    reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
    reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.HTML)
  }
}

// detekt configuration
detekt {
  config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml"))
  buildUponDefaultConfig = true
  allRules = false
  autoCorrect = true
  ignoreFailures = false
  parallel = true
}

// kover configuration
koverReport {
  filters {
    excludes {
      classes("**.*Test*")
      classes("**.*Test")
      classes("**.*Tests")
    }
  }

  verify {
    rule {
      isEnabled = true
      entity = kotlinx.kover.gradle.plugin.dsl.GroupingEntityType.APPLICATION

      bound {
        minValue = 80
        metric = kotlinx.kover.gradle.plugin.dsl.MetricType.LINE
        aggregation = kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
      }

      bound {
        minValue = 80
        metric = kotlinx.kover.gradle.plugin.dsl.MetricType.BRANCH
        aggregation = kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
      }
    }
  }
}
