package com.squareup.spice.model.validation

import com.squareup.spice.collections.MutableDeterministicSet
import com.squareup.spice.collections.mutableDeterministicSetOf
import com.squareup.spice.model.CyclicReferenceError
import com.squareup.spice.model.Node
import com.squareup.spice.model.Slice
import com.squareup.spice.model.Workspace

/**
 * Scans the graph from given roots, determining if a node has been seen on that path of
 * the traversal.
 */
object DependencyCycleValidator : Validator {
  override fun validate(workspace: Workspace, slice: Slice, vararg roots: Node) {
    cycleCheck(slice, *roots)
  }

  private fun cycleCheck(
    slice: Slice,
    vararg nodes: Node,
    validated: MutableDeterministicSet<String> = mutableDeterministicSetOf(),
    path: MutableDeterministicSet<String> = mutableDeterministicSetOf()
  ) {
    // This is single-threaded, so a mutable set is used. Switch to copy-on-write if made concurrent
    nodes.forEach { node ->
      if (node.address in validated) return
      if (node.address in path)
        throw CyclicReferenceError(slice.variant, node.address, path.toList() + node.address)
      path.add(node.address)
      slice.dependenciesOf(node).forEach {
        cycleCheck(slice, slice.nodeAt(it.target), validated = validated, path = path)
      }
      path.remove(node.address)
      validated.add(node.address)
    }
  }
}
