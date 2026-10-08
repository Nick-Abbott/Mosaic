package org.buildmosaic.analysis

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.security.MessageDigest

/** Disposable shard provenance. Artifact fingerprints are deliberately absent from packaged contracts. */
@Serializable
data class ExtractionEnvironment(
  val production: ProductionContext,
  val analysisArtifactHash: String,
  val compilerArtifactHash: String,
) {
  init {
    require(listOf(analysisArtifactHash, compilerArtifactHash).all { Regex("[0-9a-f]{64}").matches(it) }) {
      "Invalid Mosaic extraction artifact fingerprint"
    }
  }
}

object ExtractionEnvironmentCodec {
  fun encode(environment: ExtractionEnvironment): ByteArray =
    (ProtocolJson.json.encodeToString(environment) + "\n").toByteArray(Charsets.UTF_8)

  fun decode(bytes: ByteArray): ExtractionEnvironment =
    ProtocolJson.decode(
      ProtocolJson.parse(bytes, "extraction environment"),
    )

  /** Directory support is for in-process compiler fixtures; production executes resolved JARs. */
  fun artifactHash(artifact: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    if (artifact.isDirectory) {
      artifact.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(artifact).invariantSeparatorsPath }.forEach {
        digest.update(it.relativeTo(artifact).invariantSeparatorsPath.toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        it.inputStream().use { input -> update(digest, input) }
      }
    } else {
      artifact.inputStream().use { input -> update(digest, input) }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  private fun update(
    digest: MessageDigest,
    input: java.io.InputStream,
  ) {
    val buffer = ByteArray(8192)
    while (true) {
      val read = input.read(buffer)
      if (read < 0) return
      digest.update(buffer, 0, read)
    }
  }
}
