package com.squareup.spice.model.traversal

import com.squareup.spice.model.Node
import com.squareup.spice.model.Slice

/**
 * A visitor pattern, to help structure orderly traversals of the graph. Takes a lambda to offer
 * more values (e.g. from a dependency relationship), and a lambda to act on the visited node.
 *
 * The visit method takes a slice of the graph and one or more root nodes.  Implementations should
 * only rely on the public contract of Slice.
 */
interface NodeVisitor {
  fun visit(slice: Slice, vararg roots: Node)
}
