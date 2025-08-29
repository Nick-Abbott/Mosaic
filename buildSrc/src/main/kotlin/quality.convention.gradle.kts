plugins {
  id("org.jlleitschuh.gradle.ktlint")
  id("io.gitlab.arturbosch.detekt")
}

ktlint {
  version.set("1.0.1")
  android.set(false)
  verbose.set(true)
  filter {
    exclude { it.file.path.contains("build/") }
  }
  ignoreFailures.set(false)
  reporters {
    reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
    reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.HTML)
  }
}

detekt {
  config.setFrom(files("${rootProject.projectDir}/gradle/config/detekt/detekt.yml"))
  buildUponDefaultConfig = true
  allRules = false
  autoCorrect = true
  ignoreFailures = false
  parallel = true
}
