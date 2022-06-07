package com.squareup.spice.builders

import com.squareup.spice.collections.DeterministicMap
import com.squareup.spice.collections.MutableDeterministicMap
import com.squareup.spice.collections.MutableDeterministicSet
import com.squareup.spice.collections.mutableDeterministicMapOf
import com.squareup.spice.collections.mutableDeterministicSetOf
import com.squareup.spice.collections.toDeterministicSet
import com.squareup.spice.model.Dependency
import com.squareup.spice.model.ModuleDocument
import com.squareup.spice.model.TestConfiguration
import com.squareup.spice.model.ToolDefinition
import com.squareup.spice.model.VariantConfiguration

/** ModuleDocumentBuilder defines a fluent interface for creating ModuleDocument instances.
 *
 * Example:
 *
 * val bobMerrill = ModuleDocumentBuilder.build("songs") {
 *   namespace = "music"
 *
 *   tools {
 *     uses("heyMambo")
 *   }
 *   variants {
 *     "deanMartin" {
 *        srcs = listOf("src/dean")
 *        tools {
 *          uses("jazz")
 *        }
 *        tests {
 *           "apricooSoul" {
 *              srcs = listOf("src/testSoul")
 *           }
 *        }
 *        deps {
 *          add("@danzon://a.charanga")
 *        }
 *     }
 *     "carlaBoni" {
 *        srcs = listOf("src/carla")
 *        tools {
 *          uses("recordedHit")
 *        }
 *        deps {
 *          add("latin.Symbolics".tag {
 *            +"transitive"
 *          })
 *        }
 *     }
 *   }
 * }.build()
 *
 * assertThat(bobMerill).isEqualTo(
 *     ModuleDocument(
 *        name = "songs",
 *        namespace = "music",
 *        tools = listOf("heyMambo"),
 *        variants = mapOf(
 *            "deanMartin" to VariantConfiguration(
 *                srcs = listOf("src/dean"),
 *                tools = listOf("jazz"),
 *                deps = listOf(Dependency("@danzon://a.charanga")),
 *                tests = mapOf(
 *                    "apricooSoul" to TestConfiguration(
 *                        srcs = listOf("src/testSoul")
 *                )
 *            ),
 *            "carlaBoni" to VariantConfiguration(
 *                srcs = listOf("src/carla"),
 *                tools = listOf("recordedHit").
 *                deps = listOf(Dependency("latin.Symbolics", listOf("transitive"))
 *           )
 *        )
 *     )
 * )
 *
 *
 */
@SpiceBuilderScope
class ModuleDocumentBuilder(var name: String? = null) {

  companion object {
    fun build(name: String?, config: ModuleDocumentBuilder.() -> Unit) = ModuleDocumentBuilder(
      name
    ).apply(config).build()
  }

  var namespace: String? = null
  val tools = ToolSet()
  var variants = VariantConfigurationMap()

  fun build() = ModuleDocument(
    name,
    namespace,
    tools.toDeterministicSet(),
    variants
  )

  @SpiceBuilderScope
  class ToolSet(private val tools: MutableDeterministicSet<String> = mutableDeterministicSetOf()) :
    Set<String> by tools {
    class Scope(val accept: (String) -> Unit) {
      fun uses(name: String) {
        accept(name)
      }

      fun uses(tools: Iterable<String>) {
        tools.forEach(accept)
      }

      fun uses(tool: ToolDefinition) {
        accept(tool.name)
      }
    }

    operator fun contains(o: ToolDefinition) = contains(o.name)

    operator fun invoke(config: Scope.() -> Unit): ToolSet {
      Scope { tools.add(it) }.apply(config)
      return this
    }

    operator fun invoke(vararg tools: String) {
      this.tools.addAll(tools)
    }

    override fun toString(): String {
      return tools.toString()
    }

    fun toDeterministicSet() = tools.asSequence().sorted().toDeterministicSet()
  }

  @SpiceBuilderScope
  class VariantConfigurationMap(
    private val variants: MutableDeterministicMap<String, VariantConfiguration> =
      mutableDeterministicMapOf()
  ) :
    MutableDeterministicMap<String, VariantConfiguration> by variants {

    @SpiceBuilderScope
    class Scope(private val accept: (String, VariantConfiguration) -> Unit) {
      operator fun String.invoke(config: Builder.() -> Unit) {
        accept(this, Builder().apply(config).build())
      }

      fun set(v: Pair<String, VariantConfiguration>) {
        accept(v.first, v.second)
      }
    }

    @SpiceBuilderScope
    class Builder {
      var srcs: List<String> = listOf()
      val tools: ToolSet = ToolSet()

      val deps = DependencyList()

      val tests = TestMap()

      fun build() =
        VariantConfiguration(srcs.sorted(), deps.sortedBy { it.target }, tools.sorted(), tests)
    }

    operator fun invoke(config: Scope.() -> Unit) {
      Scope { n, v -> variants[n] = v }.apply(config)
    }
  }

  @SpiceBuilderScope
  class TestMap(
    private val tests: MutableDeterministicMap<String, TestConfiguration> =
      MutableDeterministicMap.ByHash()
  ) : DeterministicMap<String, TestConfiguration> by tests {
    class Scope(val accept: (String, TestConfiguration) -> Unit) {
      operator fun String.invoke(config: Builder.() -> Unit) {
        accept(this, Builder().apply(config).build())
      }
    }

    class Builder {
      var srcs: List<String> = emptyList()
      var tools: ToolSet = ToolSet()
      var deps: DependencyList = DependencyList()

      fun build() = TestConfiguration(srcs.sorted(), deps.sortedBy { it.target }, tools.sorted())
    }

    operator fun invoke(config: Scope.() -> Unit) {
      Scope { n, c -> tests[n] = c }.apply(config)
    }
  }

  @SpiceBuilderScope
  class DependencyList(
    private val deps: MutableList<Dependency> = mutableListOf()
  ) : List<Dependency> by deps {
    class Scope(val accept: (Dependency) -> Unit) {
      fun String.tag(add: TagSet.() -> Unit) =
        Dependency(this, TagSet().apply(add).toList())

      fun add(vararg ds: Dependency) {
        ds.forEach(accept)
      }

      fun add(vararg t: String) {
        t.map { it.tag {} }.forEach(accept)
      }

      inner class TagSet(val tags: MutableSet<String> = mutableSetOf()) : Set<String> by tags {
        operator fun String.unaryPlus() {
          tags.add(this)
        }

        operator fun plus(t: String): TagSet {
          tags.add(t)
          return this
        }

        fun toList() = tags.sorted().toList()
      }
    }

    operator fun invoke(config: Scope.() -> Unit): DependencyList = apply {
      Scope { deps.add(it) }.apply(config)
    }
  }
}

fun module(config: ModuleDocumentBuilder.() -> Unit): ModuleDocument {
  return ModuleDocumentBuilder().apply(config).build()
}
