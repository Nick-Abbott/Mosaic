description = "Compiler-independent semantic analysis for Mosaic contracts"

plugins {
  id("kotlin.convention")
  id("quality.convention")
  id("testing.convention")
}

dependencies {
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.1")
}
