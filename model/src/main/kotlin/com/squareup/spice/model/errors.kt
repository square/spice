package com.squareup.spice.model

/** An error thrown if an graph fails a validator */
open class InvalidGraph @JvmOverloads constructor(
  reason: String,
  cause: Throwable? = null
) : RuntimeException(reason, cause)

/**
 * An error thrown if a graph contains a dependency cycle (that is, is not a directed-acyclic-graph).
 */
class CyclicReferenceError(
  val variant: String,
  val root: String,
  val path: List<String>
) : InvalidGraph(message(variant, root, path)) {
  companion object {
    private fun message(variant: String, root: String, path: List<String>): String {
      val fromText = if (root == path.first()) ":" else " from ${path.first()}:"
      val pathText = path.joinToString("") { "   - $it\n" }
      return "Cycle detected in '$variant' at \"$root\"$fromText\n$pathText"
    }
  }
}

/**
 * An error thrown if graph validation finds dangling references.
 */
class IncompleteGraph(missingRefs: Map<String, Set<String>>) : InvalidGraph(
  "Incomplete graph had dependency targets which could not be found in the graph:\n" +
    missingRefs.entries.joinToString("") { (target, rdeps) ->
      "  - $target (referenced by ${rdeps.joinToString()})\n"
    }
)

/**
 * An error thrown if an address could not be materialized. If there is an underlying cause (say a
 * file not found error) it will be carried as the [cause] property.
 */
class NoSuchAddress @JvmOverloads constructor(
  val address: String,
  cause: Throwable? = null
) : InvalidGraph("No such address found in graph: $address", cause)

/**
 * An error thrown if an address is unsupported, or invalid in some other way. This can be because
 * it is incoherently structured, or if it represents an external workspace node that is not supported
 * by this graph implementation.
 */
class InvalidAddress @JvmOverloads constructor(
  val address: String,
  reason: String? = null
) : InvalidGraph("Unsupported address: $address ${reason?.let{"($reason)"} ?: ""}")
