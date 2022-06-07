package com.squareup.spice.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.squareup.spice.collections.DeterministicMap
import com.squareup.spice.collections.DeterministicSet
import com.squareup.spice.collections.emptyDeterministicMap
import com.squareup.spice.collections.emptyDeterministicSet
import com.squareup.spice.collections.mutableDeterministicMapOf
import com.squareup.spice.collections.plus
import com.squareup.spice.collections.toDeterministicMap

/** A simple interface for merging. */
interface Mergeable<T : Mergeable<T>> {
  /**
   * Merges another object into a copy of this one, with implementers choosing the strategy
   * for merging. The object returned may be one of the two objects merged, if the merge would leave
   * it unchanged and it is immutable.
   */
  fun merge(other: T): T
}

/**
 * Represents a module in the workspace, generally some definitional metadata, as well as a set
 * of tools which are applied to this module (by templates/client-apps) and variants. Every module
 * must have at least one variant, though it can have many, as defined in the workspace.
 *
 * A module may have a name and namespace if tools which care about those will be used (say, maven
 * artifact publisher, documentation generator, etc.)
 *
 * Modules parsed from `module.spice.yml` files will be merged with the module defaults specified in
 * the [WorkspaceDocument.definitions]. Merge will start with the definitions as the base, and the loaded
 * module overriding those defaults with the following heuristic.:
 * ```
 *   name:                        # replace
 *   namespace:                   # replace
 *   tools: [ a ]                 # append
 *   variants:                    # merge section
 *     main:                      # merge if exists, new if not
 *       srcs: [ a/path ]         # replace (within "main")
 *         tests:                 # merge
 *           unit:                # merge if exists, new if not
 *             srcs: [ b/path ]   # replace
 *             deps: [ a, b ]     # append
 * ```
 */
data class ModuleDocument(
  /**
   * The name of this module. If null, it will be inferred by most tools from the workspace name
   * and the local directory name (e.g. "foo-mymodule")
   */
  val name: String? = null,
  /**
   * A namespace, analogous to a gradle group or maven group_id.
   */
  val namespace: String? = null,

  /**
   * An ordered set of tools to be applied to this module.
   */
  val tools: DeterministicSet<String> = emptyDeterministicSet(),

  /**
   * An ordered map of variant configurations, keyed by name.
   *
   * Each module must have at least one variant.
   */
  val variants: DeterministicMap<String, VariantConfiguration> = emptyDeterministicMap(),

  /**
   * Describes if this is expected to be a complete module or if it requires merging with defaults.
   */
  val complete: Boolean = false
) : Mergeable<ModuleDocument> {
  /**
   * Merges the current configuration with the given defaults module
   *
   * TBD - fully document the semantics here, referencing examples in the tests themselves.
   */
  fun mergeDefaults(definitions: ModuleDocument): ModuleDocument {
    if (complete) throw IllegalStateException("Tried to merge definitions into a complete module.")
    return definitions.merge(this)
  }

  /**
   * Merges a [ModuleDocument] into the receiver, using the receiver as a base (default) and
   * merging in the more specific [ModuleDocument] information.
   *
   * Importantly, [name] and [namespace] are replaced if specified in the incoming object,
   * while [tools] are unioned, and [variants] are merged
   */
  override fun merge(other: ModuleDocument): ModuleDocument {
    return ModuleDocument(
      name = other.name ?: name,
      namespace = other.namespace ?: namespace,
      tools = tools + other.tools,
      variants = variants.merge(other.variants),
      complete = true
    )
  }
}

/**
 * Merges a map of named [Mergeable] types into the receiver, where the receiver contains the
 * defaults, and the supplied mergeables override. If the mergeable exists in the defaults, they
 * are merged, based on the logic in [Mergeable.merge], otherwise the config supplied is
 * used as-is, and added to the map. The result in a union of the two.
 */
internal inline fun <reified T : Mergeable<T>> Map<String, T>.merge(
  configs: Map<String, T?>
): DeterministicMap<String, T> =
  mutableDeterministicMapOf<String, T>().also { union ->
    union.putAll(this)
    configs.forEach { (name, mergeable) ->
      val default = union[name]
      when {
        default == null && mergeable == null -> { /* nothing to do */ }
        default == null -> union[name] = mergeable!!
        mergeable == null -> union[name] = default // redundant, but for completeness
        else -> union[name] = default.merge(mergeable)
      }
    }
  }.toDeterministicMap()

/**
 * Configures a variant of a given node in the graph.
 * This configuration includes srcs and deps and any tests.
 */
data class VariantConfiguration constructor(
  val srcs: List<String> = listOf(),
  val deps: List<Dependency> = listOf(),
  val tools: List<String> = listOf(),
  val tests: DeterministicMap<String, TestConfiguration> = emptyDeterministicMap()
) : Mergeable<VariantConfiguration> {

  /**
   * This is a completely ridiculous construct used to work around Jackson's really weird failures
   * in the cases of null handling of lists.
   */
  @JsonCreator
  constructor(
    srcs: List<String>?,
    deps: List<Dependency>?,
    tools: List<String>?,
    tests: DeterministicMap<String, TestConfiguration>?,
    @Suppress("UNUSED_PARAMETER") ignore_this_fake_value: Boolean?
  ) : this(
    srcs ?: emptyList(),
    deps ?: emptyList(),
    tools ?: emptyList(),
    tests ?: emptyDeterministicMap<String, TestConfiguration>()
  )

  override fun merge(other: VariantConfiguration): VariantConfiguration {
    return VariantConfiguration(
      srcs = if (other.srcs.isNullOrEmpty()) this.srcs else other.srcs,
      deps = deps + other.deps,
      tests = tests.merge(other.tests)
    )
  }
}

/** Holds the configuration information for a test node */
data class TestConfiguration(
  val srcs: List<String> = listOf(),
  val deps: List<Dependency> = listOf(),
  val tools: List<String> = listOf()
) : Mergeable<TestConfiguration> {
  /**
   * Merges a [TestConfiguration] into the receiver, using the receiver as a base (default) and
   * merging in the more specific [TestConfiguration].
   *
   * Importantly, [srcs] are replaced if specified in the incoming object, while [deps] and [tools] are
   * concatenated.
   */
  override fun merge(other: TestConfiguration): TestConfiguration {
    return TestConfiguration(
      srcs = if (other.srcs.isNullOrEmpty()) this.srcs else other.srcs,
      deps = this.deps + other.deps,
      tools = this.tools + other.tools
    )
  }
}

/**
 * Holds a dependency target address and its metadata
 */
data class Dependency(

  /** The target of a dependency, as a string address */
  val target: String,
  /**
   * String tags which are interpreted by tooling.
   *
   * Such tags don't have inherent semantic meaning to Spice, but may have meaning to specific
   * tools or templates. An example would be using a tag (say, "transitive" or "export_all") to
   * singal to a gradle template that this dependency should carry with it all of its transitive
   * closure - that is, be an "api dependency" (in AGP terms). General practice is to have a semantic
   * default for un-tagged deps interpreted by a tool/template, and then configure where there's a
   * semantic different (to reduce noise in the human readable output.
   */
  val tags: List<String> = listOf()
)
