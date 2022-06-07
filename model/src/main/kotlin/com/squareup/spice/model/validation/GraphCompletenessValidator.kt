package com.squareup.spice.model.validation

import com.squareup.spice.model.IncompleteGraph
import com.squareup.spice.model.NoSuchAddress
import com.squareup.spice.model.Node
import com.squareup.spice.model.Slice
import com.squareup.spice.model.Workspace
import com.squareup.spice.model.traversal.BreadthFirstDependencyVisitor

/**
 * Scans the dependency graph from the supplied root nodes, ensuring every node and it's
 * transitive closure can be loaded.
 */
object GraphCompletenessValidator : Validator {
  override fun validate(workspace: Workspace, slice: Slice, vararg roots: Node) {
    val references: MutableMap<String, Set<String>> = linkedMapOf()
    BreadthFirstDependencyVisitor(nodeError = { errorSlice, address, throwable ->
      when (throwable) {
        is NoSuchAddress -> {
          references[address] = errorSlice.dependenciesOn(address).map { it.target }.toSet()
        }
        else -> throw throwable
      }
      null
    }) {}.visit(slice, *roots)
    if (references.isNotEmpty()) {
      throw IncompleteGraph(references)
    }
  }
}
