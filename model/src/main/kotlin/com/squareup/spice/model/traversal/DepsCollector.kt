package com.squareup.spice.model.traversal

/**
 * A [NodeVisitor] which does a breadth-first traversal, gathering the list of dependency
 * addresses from the given starting nodes.
 *
 * This is a stateful object, and so repeated calls to visitor() will result in more addresses
 * being added ot the tail of the list - if that is undesirable, create a new one.
 */
class DepsCollector(
  private val mutableDeps: MutableList<String> = mutableListOf(),
  private val visitor: BreadthFirstDependencyVisitor =
    BreadthFirstDependencyVisitor { mutableDeps.add(it.address) }
) : NodeVisitor by visitor {
  val deps: List<String> get() = mutableDeps.toList()
}
