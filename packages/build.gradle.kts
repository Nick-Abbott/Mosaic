import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

subprojects {
  apply(plugin = "maven-publish")

  extensions.configure<PublishingExtension> {
    publications {
      create<MavenPublication>("mavenJava") {
        from(components["java"])
      }
    }
    repositories {
      mavenLocal()
    }
  }
}
