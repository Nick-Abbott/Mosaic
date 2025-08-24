plugins {
    id("org.jetbrains.kotlinx.kover")
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy("koverHtmlReport")
}

dependencies {
    add("testImplementation", kotlin("test"))
    add("testImplementation", "org.junit.jupiter:junit-jupiter:5.10.0")
}

koverReport {
    verify {
        rule {
            isEnabled = true
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
