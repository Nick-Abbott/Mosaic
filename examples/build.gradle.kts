plugins {
  id("kotlin.convention") apply false
  id("quality.convention") apply false
  id("testing.convention") apply false
}

subprojects {
  apply(plugin = "kotlin.convention")
  apply(plugin = "quality.convention")
  apply(plugin = "testing.convention")
}

