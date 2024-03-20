package shared

import com.google.gson.JsonElement
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.EOFException
import java.io.Reader

fun parseJsonTree(json: Reader): Json =
  JsonTreeParser.read(JsonReader(json))?.let(::JsonRoot) ?: JsonRoot(emptyMap<String, Any>())

/**
 * Custom implementation of gson parser that creates tree structure from whole json.
 * We could use [com.google.gson.JsonParser] but it wraps everything into [JsonElement]
 * and therefore it is little slower then our [JsonTreeParser]
 * This parser directly creates nested [List] and [Map] instances without any additional wrappers.
 * Downside is that returned value is [Any] and has to be type-casted before can be used
 */
private object JsonTreeParser: TypeAdapter<Any?>() {
  override fun write(out: JsonWriter?, value: Any?) {}
  override fun read(reader: JsonReader): Any? {
    val peek = try {
      reader.peek()
    } catch (e: EOFException) {
      return null
    }

    return when (peek) {
      JsonToken.STRING  -> reader.nextString()
      JsonToken.NUMBER  -> reader.nextString()
      JsonToken.BOOLEAN -> reader.nextBoolean()
      JsonToken.NULL    -> { reader.nextNull(); null }
      JsonToken.BEGIN_ARRAY -> {
        val array = ArrayList<Any?>()
        reader.beginArray()
        while (reader.hasNext()) { array.add(read(reader)) }
        reader.endArray()
        array
      }
      JsonToken.BEGIN_OBJECT -> {
        val obj = hashMapOf<String, Any?>()
        reader.beginObject()
        while (reader.hasNext()) { obj[reader.nextName()] = read(reader) }
        reader.endObject()
        obj
      }

      JsonToken.END_DOCUMENT,
      JsonToken.NAME,
      JsonToken.END_OBJECT,
      JsonToken.END_ARRAY, null -> throw IllegalArgumentException()
    }
  }
}

/**
 * Exception used when parsed json does not contain expected value
 */
class JsonMismatch(message: String): RuntimeException(message)

val Null: String? = null

/**
 * Helpers for convenient reading of values from parsed tree structure created by [JsonTreeParser]
 * This sealed interface primarily exist to provide descriptive error message when parsing fails.
 */
sealed interface Json
private class JsonRoot(val element: Any): Json
private class JsonItem(val element: Any?, val parent: Json, val index: Int): Json
private class JsonPath(val parent: Json, val key: String): Json

operator fun Json.get(key: String): Json = JsonPath(this, key)

val Json.string : String     get() = nullString ?: fail()
val Json.int    : Int        get() = nullInt    ?: fail()
val Json.long   : Long       get() = nullLong   ?: fail()
val Json.bool   : Boolean    get() = nullBool   ?: fail()
val Json.double : Double     get() = nullDouble ?: fail()
val Json.list   : List<Json> get() = nullList   ?: fail()

val Json.nullString : String?     get() = parse { it as String? }
val Json.nullDouble : Double?     get() = parse { (it as String?)?.toDouble() }
val Json.nullInt    : Int?        get() = parse { (it as String?)?.toInt() }
val Json.nullBool   : Boolean?    get() = parse { it as Boolean? }
val Json.nullLong   : Long?       get() = parse { (it as String?)?.toLong() }
val Json.nullList   : List<Json>? get() = parse { it as List<Any?> }?.mapIndexed { i, v -> JsonItem(v, this, i) }

val Json.exists: Boolean get() = element != null
fun <A> Json.ifExists(action: (Json) -> A): A? = takeIf { exists || it.element != null }?.let(action)

private inline fun <R> Json.parse(parse: (Any?) -> R): R? =
  try { parse(element) } catch(e: Exception) { null }

private fun Json.fail(): Nothing =
  throw JsonMismatch("Failed to parse key '${path()}', " + "actual value: ${element ?: "element not found"}")

private val Json.element: Any? get() = when(this) {
  is JsonPath -> (parent.element as? Map<*, *>)?.get(key)
  is JsonItem -> element
  is JsonRoot -> element
}

private tailrec fun Json.path(keys: List<String> = emptyList()): String = when(this) {
  is JsonPath -> parent.path(listOf(key) + keys)
  is JsonItem -> parent.path(listOf("[${index}]") + keys)
  is JsonRoot -> keys.joinToString("/")
}

/**
 * Custom DSL builder for direct creation of JSONs.
 * It doesn't creates whole string at once but "emits" individual values as the code executes.
 * Because of this it can write directly into some stream or buffer without the need to "build" whole
 * json string in memory at once.
 */
class JsonBuilder(private val emit: (String) -> Unit) {
  private var first = true

  operator fun String.rangeTo(v: String?)  = append(if(v != null) "\"${v.replace("\"", "\\\"").replace("\'", "\\\'")}\"" else "null") // Ked je tam " tak to rozbije struktúru jsonu
  operator fun String.rangeTo(v: Number?)  = append(v)
  operator fun String.rangeTo(v: Boolean?) = append(v)
  operator fun String.rangeTo(block: JsonBuilder.() -> Unit) { key(); jsonBlock(block, emit) }
  operator fun String.rangeTo(block: List<Any?>)             { key(); items(block) }

  // nested json block must be executed lazily in lambda to ensure correct order of whole json
  fun json(block: JsonBuilder.() -> Unit): () -> Unit = { jsonBlock(block, emit) }

  private fun String.key() =
    emit(if (first.apply { first = false }) "\"$this\":" else ",\"$this\":")

  private fun String.append(value: Any?) =
    emit(if (first.apply { first = false }) "\"$this\":$value" else ",\"$this\":$value")

  private fun items(block: List<Any?>) {
    emit("[")
    block.forEachIndexed { i, v ->
      if(i != 0) emit(",")
      when (v) {
        is List<*>     -> items(v)
        is Function<*> -> (v as () -> Unit).invoke()
        is String      -> emit("\"${v}\"")
        else           -> emit(v.toString())
      }
    }
    emit("]")
  }
}

inline fun jsonBlock(block: JsonBuilder.() -> Unit, noinline emit: (String) -> Unit) {
  emit("{")
  JsonBuilder(emit).run(block)
  emit("}")
}

fun jsonString(block: JsonBuilder.() -> Unit): String {
  val result = StringBuilder()
  jsonBlock(block, result::append)
  return result.toString()
}

fun String.asJson() = parseJsonTree(reader())