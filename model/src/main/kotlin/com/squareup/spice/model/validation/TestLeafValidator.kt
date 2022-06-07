package com.squareup.spice.model.validation

import com.squareup.spice.model.InvalidGraph
import com.squareup.spice.model.Node
import com.squareup.spice.model.Node.TestNode
import com.squareup.spice.model.Slice
import com.squareup.spice.model.Workspace

object TestLeafValidator : Validator {

  override fun validate(workspace: Workspace, slice: Slice, vararg roots: Node) {
    val testNodes = workspace.nodes.filterIsInstance<TestNode>()
    with(testNodes.filterNot { it.address.matches(TestNode.VALID_ADDRESS_PATTERN) }.toList()) {
      if (isNotEmpty()) {
        throw InvalidGraph(
          "InvalidGraph: Test nodes have invalid addresses:\n${
            joinToString("") { "  - ${it.address}\n" }
          }"
        )
      }
    }

    val testDependants = workspace.nodes.filterIsInstance<TestNode>().flatMap { node ->
      slice.dependenciesOn(node).asSequence().map { it.target to node.address }
    }.toSet()
    if (testDependants.isNotEmpty()) {
      throw InvalidGraph(
        "InvalidGraph: Tests may not be the target of a dependency:\n${
          testDependants.joinToString("") { (src, dest) -> "  $src -> $dest" }
        }"
      )
    }
  }
}
