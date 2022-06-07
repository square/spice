package com.squareup.spice.model.traversal

import com.squareup.spice.model.Node
import com.squareup.spice.model.Slice

/**
 * A visitor which visits each node from the root, according to its dependency relationships.
 * That is to say, it follows the [Slice.dependenciesOn] (rdeps) relationship when selecting new nodes,
 * queued up breadth-first.
 */
class BreadthFirstReverseDependencyVisitor(
  moreError: (slice: Slice, address: String, error: Throwable) -> Sequence<String> =
    { _, _, t -> throw t },
  nodeError: (slice: Slice, address: String, error: Throwable) -> Node? =
    { _, _, t -> throw t },
  on: (Node) -> Unit
) : BreadthFirstNodeVisitor(
  more = { slice: Slice, node: Node -> slice.dependenciesOn(node).asSequence() },
  moreError = moreError,
  nodeError = nodeError,
  on = on
)
