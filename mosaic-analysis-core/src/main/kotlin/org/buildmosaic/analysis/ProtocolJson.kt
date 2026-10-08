package org.buildmosaic.analysis

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Bounds apply before JSON parsing, including resources expanded from compressed JAR entries. */
object ProtocolJson {
  const val MAX_NESTING = 128
  const val MAX_BYTES = 16 * 1024 * 1024
  internal val json =
    Json {
      classDiscriminator = "kind"
      encodeDefaults = true
      explicitNulls = true
    }

  internal fun parse(
    bytes: ByteArray,
    label: String,
  ): JsonElement {
    require(bytes.size <= MAX_BYTES) { "Mosaic $label exceeds size limit" }
    val text =
      try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
      } catch (failure: CharacterCodingException) {
        throw IllegalArgumentException("Malformed Mosaic $label UTF-8", failure)
      }
    return try {
      JsonStructure(text).validate()
      json.parseToJsonElement(text)
    } catch (failure: SerializationException) {
      throw IllegalArgumentException("Malformed Mosaic $label: ${failure.message}", failure)
    }
  }

  internal inline fun <reified T> decode(element: JsonElement): T =
    try {
      val serializer = kotlinx.serialization.serializer<T>()
      json.decodeFromJsonElement(serializer, element).also {
        require(element == json.encodeToJsonElement(serializer, it)) { "Invalid Mosaic JSON field types" }
      }
    } catch (failure: SerializationException) {
      throw IllegalArgumentException("Malformed Mosaic contract: ${failure.message}", failure)
    }

  internal fun hash(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

/** kotlinx.serialization rejects unknown fields but normally collapses duplicate JSON keys. */
private class JsonStructure(private val text: String) {
  private var cursor = 0

  fun validate() {
    value(0)
    whitespace()
    require(cursor == text.length) { "Trailing Mosaic JSON content" }
  }

  private fun whitespace() {
    while (cursor < text.length && text[cursor] in " \n\r\t") cursor++
  }

  private fun take(character: Char): Boolean {
    whitespace()
    if (cursor >= text.length || text[cursor] != character) return false
    cursor++
    return true
  }

  private fun string(): String {
    whitespace()
    val start = cursor
    require(take('"')) { "Expected Mosaic JSON string" }
    while (cursor < text.length) {
      when (text[cursor++]) {
        '\\' -> cursor++
        '"' -> return ProtocolJson.json.decodeFromString<String>(text.substring(start, cursor)).also(::validateUnicode)
      }
    }
    throw IllegalArgumentException("Truncated Mosaic JSON string")
  }

  private fun validateUnicode(value: String) {
    value.forEachIndexed { index, character ->
      if (character.isHighSurrogate()) {
        require(index + 1 < value.length && value[index + 1].isLowSurrogate()) { "Malformed Mosaic JSON Unicode" }
      } else if (character.isLowSurrogate()) {
        require(index > 0 && value[index - 1].isHighSurrogate()) { "Malformed Mosaic JSON Unicode" }
      }
    }
  }

  private fun value(depth: Int) {
    require(depth <= ProtocolJson.MAX_NESTING) { "Mosaic JSON exceeds nesting limit" }
    whitespace()
    require(cursor < text.length) { "Truncated Mosaic JSON" }
    when (text[cursor]) {
      '{' -> objectValue(depth)
      '[' -> {
        cursor++
        if (take(']')) return
        do {
          value(depth + 1)
        } while (take(','))
        require(take(']')) { "Malformed Mosaic JSON array" }
      }
      '"' -> string()
      else -> {
        val start = cursor
        while (cursor < text.length && text[cursor] !in ",]} \n\r\t") cursor++
        require(cursor > start) { "Malformed Mosaic JSON value" }
      }
    }
  }

  private fun objectValue(depth: Int) {
    cursor++
    val keys = mutableSetOf<String>()
    if (take('}')) return
    do {
      val key = string()
      require(keys.add(key)) { "Duplicate Mosaic JSON field $key" }
      require(take(':')) { "Malformed Mosaic JSON object" }
      value(depth + 1)
    } while (take(','))
    require(take('}')) { "Malformed Mosaic JSON object" }
  }
}
