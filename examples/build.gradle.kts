import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.ktlint) apply false
}

subprojects {
  repositories {
    mavenCentral()
  }
  
  // Apply ktlint to all subprojects
  apply(plugin = "org.jlleitschuh.gradle.ktlint")
  
  configure<KtlintExtension> {
    version.set("1.0.1")
    android.set(false)
    verbose.set(true)
    filter {
      exclude { it.file.path.contains("build/") }
    }
    ignoreFailures.set(false)
    reporters {
      reporter(ReporterType.PLAIN)
      reporter(ReporterType.CHECKSTYLE)
      reporter(ReporterType.HTML)
    }
  }
}
