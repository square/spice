package com.squareup.spice.model

/**
 * A relationship (with metadata) to a target [GraphNode]. These are returned by [Slice.dependenciesOf]
 * and represent a dependency relationship between two nodes.
 *
 * The [tags] are stored metadata which don't affect Spice's understanding of the graph, but may
 * be understood by tools to imply something about the semantics of these relationships. [tags] are
 * not part of the key - that is, there aren't two different a->b edges, with different tag-sets.
 * Edges do not hold their source, and are temporary objects in the context of a particular request.
 *
 * > TODO: Decide if graph edges should be source--{variant}-->target, and cacheable.
 */
interface Edge {
  /** The node to which this graph edge points */
  val target: String
  val tags: List<String>
}

/**
 * A simple [Edge] implementation.
 */
data class SimpleEdge(
  override val target: String,
  override val tags: List<String>
) : Edge
