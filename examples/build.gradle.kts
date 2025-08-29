plugins {
  id("kotlin.convention") apply false
  id("quality.convention") apply false
//  id("com.abbott.mosaic.testing") apply false
}

subprojects {
  apply(plugin = "kotlin.convention")
  apply(plugin = "quality.convention")
//  apply(plugin = "com.abbott.mosaic.testing")
}

