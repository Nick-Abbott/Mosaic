package org.buildmosaic.analysis

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest

/** Mutations reach semantic decoding; checksum corruption is tested separately. */
internal fun withIntegrityHash(text: String): String {
  val envelope = Json.parseToJsonElement(text).jsonObject
  val payload = JsonObject(envelope + ("integrityHash" to kotlinx.serialization.json.JsonPrimitive("")))
  val hash =
    MessageDigest.getInstance("SHA-256").digest(Json.encodeToString(payload).toByteArray())
      .joinToString("") { "%02x".format(it) }
  return text.replace(Regex("\"integrityHash\":\"[0-9a-f]+\""), "\"integrityHash\":\"$hash\"")
}

internal fun withoutPayloadField(
  text: String,
  field: String,
): String {
  fun remove(element: JsonElement): JsonElement =
    when (element) {
      is JsonObject -> JsonObject(element.filterKeys { it != field }.mapValues { remove(it.value) })
      is JsonArray -> JsonArray(element.map { remove(it) })
      else -> element
    }
  val envelope = Json.parseToJsonElement(text).jsonObject
  val mutated = JsonObject(envelope + ("payload" to remove(envelope.getValue("payload"))))
  return withIntegrityHash(Json.encodeToString(mutated))
}
