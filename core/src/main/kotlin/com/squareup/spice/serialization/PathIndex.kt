package com.squareup.spice.serialization

import com.squareup.spice.model.FindResult
import com.squareup.spice.model.FindResult.GeneralFindResult
import com.squareup.spice.model.FindResult.TestFindResult
import com.squareup.spice.model.FindResult.VariantFindResult
import com.squareup.spice.model.InvalidAddress
import com.squareup.spice.model.Node.ModuleNode
import com.squareup.spice.model.Node.TestNode
import com.squareup.spice.model.util.toSetMultimap
import java.util.concurrent.ConcurrentHashMap

/**
 * A simple tree index for fast lookups of addresses for module membership. Since addresses are in
 * effect a hierarchical filesystem, and since modules cannot be defined "above" other modules,
 * this creates a nice tree structure. Variant lookups and Test node lookups are all leaves off
 * the module, and are left to a secondary lookup once the module is found.
 */
class PathIndex {
  private data class Branch(
    val children: MutableMap<String, Branch> = ConcurrentHashMap()
  ) {
    var leaf: Leaf? = null
  }

  private data class Leaf(
    val module: GeneralFindResult,
    val variants: Map<String, Set<VariantFindResult>>,
    val tests: Map<String, Set<TestFindResult>>
  )

  private val root = Branch()

  fun addNode(node: ModuleNode, variants: Map<String, Set<String>>, tests: List<TestNode>) {
    var current: Branch = root
    for (element in node.path) {
      with(current.leaf) {
        if (this != null)
          throw InvalidAddress(
            node.address,
            "Path is below existing address ${this.module.node.address}"
          )
      }
      current = current.children.getOrPut(element) { Branch() }
    }
    if (current.children.isNotEmpty())
      throw InvalidAddress(
        node.address, "Path is above existing addresses ${current.children.map { it.key }}"
      )
    when {
      current.leaf == null -> {
        current.leaf = Leaf(
          GeneralFindResult(node),
          variants.mapValues { (_, variantSet) ->
            variantSet.map { VariantFindResult(node, it) }.toSet()
          },
          tests.flatMap { testNode -> testNode.config.srcs.map { it to TestFindResult(testNode) } }
            .toSetMultimap()
        )
      }
      current.leaf!!.module.node == node -> {
        /* found a node at this address, but they're equal, so let it pass. */
      }
      else -> throw IllegalArgumentException("Node already registered at address ${node.address}")
    }
  }

  fun nodeForPath(path: String): Sequence<FindResult<*>> {
    val leaf = leafForPath(path) ?: return sequenceOf()
    val module = leaf.module.node
    val variants = leaf.variants.flatMap { (src, result) ->
      if (path.startsWith("${module.address}/$src")) result else setOf()
    }.toSet()
    val tests = leaf.tests.flatMap { (src, result) ->
      if (path.startsWith("${module.address}/$src")) result else setOf()
    }.toSet()
    return (variants + tests).ifEmpty { setOf(leaf.module) }.asSequence()
  }

  /**
   * Returns a module for a given path, if that path is contained within a module
   */
  private fun leafForPath(path: String): Leaf? {
    // TODO: Validate path and reject bad (incoherent, not just wrong) paths.
    var current: Branch? = root
    val localPath = if (path.startsWith('/')) path.substring(1) else path
    val elements = localPath.split("/").toMutableList()
    for (element in elements) {
      current = current?.children?.get(element)
      if (current == null) return null
      if (current.leaf != null) return current.leaf
    }
    return null
  }

  /**
   * Returns a module for a given path, if that path is contained within a module
   */
  fun moduleForPath(path: String): ModuleNode? {
    return leafForPath(path)?.module?.node
  }
}
