package com.squareup.spice.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.squareup.spice.model.Dependency

/**
 * Custom deserializer/serializer for Dependency, allowing users to specify deps in the yaml with
 * metadata, but not have to put map/field metadata markers if there isn't any. e.g. both of these
 * are supported, and write out the same way.
 *
 * ```
 * ---
 * variants:
 *   debug:
 *     deps:
 *       - /foo/bar
 *       - /bar/baz: [exported_all]
 *       - maven://blah.foo:bar
 *       - maven://blah.baz:baz: [exported_children]
 * ```
 *
 * See [Dependency.tags] for details on the semantics. The parsing system simply treats them as
 * strings.
 */
class DependencyDeserializer : StdDeserializer<Dependency>(Dependency::class.java) {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Dependency {
    with(p.codec) {
      val node: JsonNode = readTree(p)
      return if (node.isTextual) {
        // Handle simple strings without ":"
        Dependency(node.asText())
      } else {
        // Handle "- /foo/bar: [blah]" treating the target as the field key.
        val field = node.fieldNames().next()
        val tags = node[field].asIterable().map { it.asText() }.toList()
        Dependency(target = field, tags = tags)
      }
    }
  }
}

class DependencySerializer : StdSerializer<Dependency>(Dependency::class.java) {
  override fun serialize(dep: Dependency, gen: JsonGenerator, provider: SerializerProvider) {
    gen.writeStartObject()
    gen.writeArrayFieldStart(dep.target)
    dep.tags.forEach { gen.writeObject(it) }
    gen.writeEndArray()
    gen.writeEndObject()
  }
}
