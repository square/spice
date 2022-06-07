package com.squareup.spice.builders

import com.squareup.spice.collections.MutableDeterministicMap
import com.squareup.spice.collections.MutableDeterministicSet
import com.squareup.spice.collections.mutableDeterministicMapOf
import com.squareup.spice.collections.mutableDeterministicSetOf
import com.squareup.spice.collections.toDeterministicSet
import com.squareup.spice.model.ToolDefinition

/** ToolDefinitionList encapsulates building ToolDefinition instances.
 *
 * Example:
 *
 * val merrilBob = ToolDefinitionSet() {
 *   "hey.mambo" {
 *      not = listOf("tarantella")
 *      any = listOf("enchilada", "baccala")
 *      all = listOf("Siciliano")
 *      properties { set("mambo" to "italiano") }
 *   }
 * }
 *
 * assertThat(merrilBob).containsExactly(
 *   ToolDefinition(
 *     name = "hey.mambo",
 *     not = listOf("tarantella"),
 *     any = listOf("enchilada", "baccala"),
 *     all = listOf("Siciliano"),
 *     properties = mapOf("mambo" to "italiano")
 *   )
 * )
 *
 *
 */
class ToolDefinitionSet(
  private val tools: MutableDeterministicSet<ToolDefinition> = mutableDeterministicSetOf()
) :
  Set<ToolDefinition> by tools {

  companion object {
    fun defineTool(
      name: String,
      definition: Builder.() -> Unit
    ) = Builder(name).apply(definition)
      .build()
  }

  class Scope(val accept: (ToolDefinition) -> Unit) {
    operator fun String.invoke(config: Builder.() -> Unit) {
      accept(Builder(this).apply(config).build())
    }
  }

  class Builder(var name: String) {
    var any: List<String> = listOf()
    var all: List<String> = listOf()
    var not: List<String> = listOf()
    val properties: MutableDeterministicMap<String, String> = mutableDeterministicMapOf()
    fun properties(vararg properties: Pair<String, String>) {
      this.properties.putAll(properties)
    }

    fun properties(properties: Map<String, String>) {
      this.properties.putAll(properties)
    }

    fun properties(declaration: DeclarationScope<String, String>.() -> Unit) {
      DeclarationScope<String, String> { (k, v) -> properties[k] = v ?: "" }.apply(declaration)
    }

    fun build() = ToolDefinition(name, any.sorted(), all.sorted(), not.sorted(), properties)
  }

  operator fun invoke(definedTools: Iterable<ToolDefinition>) {
    tools.addAll(definedTools)
  }

  operator fun invoke(config: Scope.() -> Unit) {
    Scope { tools.add(it) }.apply(config)
  }

  fun toDeterministicSet() = sortedBy { it.name }.toDeterministicSet()
}
