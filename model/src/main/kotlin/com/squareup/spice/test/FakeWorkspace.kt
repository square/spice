package com.squareup.spice.test

import com.squareup.spice.model.Edge
import com.squareup.spice.model.FindResult
import com.squareup.spice.model.FindResult.GeneralFindResult
import com.squareup.spice.model.FindResult.TestFindResult
import com.squareup.spice.model.FindResult.VariantFindResult
import com.squareup.spice.model.NoSuchAddress
import com.squareup.spice.model.Node
import com.squareup.spice.model.Node.ExternalNode
import com.squareup.spice.model.Node.ModuleNode
import com.squareup.spice.model.Node.TestNode
import com.squareup.spice.model.SimpleEdge
import com.squareup.spice.model.Slice
import com.squareup.spice.model.Workspace
import com.squareup.spice.model.WorkspaceDocument
import com.squareup.spice.model.util.toSetMultimap

/**
 * A small, in-memory Workspace designed only for testing, with canned data.
 *
 * While not as feature-full as [com.squareup.spice.serialization.FileWorkspace] it does parse out
 * test nodes from module documents in ModuleNodes, etc.
 *
 * It is not optimized for large scale, nor for multithreaded operation.
 */
class FakeWorkspace private constructor(
  override val document: WorkspaceDocument,
  private val nodeIndex: Map<String, Node>
) : Workspace {
  constructor(
    document: WorkspaceDocument,
    vararg nodes: Node
  ) : this(
    document,
    nodes.flatMap {
      val nodes = mutableListOf<Pair<String, Node>>()
      when (it) {
        is ModuleNode -> {
          val mergedModuleDocument = it.module.mergeDefaults(document.definitions)
          nodes.add(it.address to it.copy(it.address, mergedModuleDocument))
          mergedModuleDocument.variants.forEach { (variantName, variantConfig) ->
            variantConfig.tests.forEach { (testName, testConfig) ->
              with(TestNode(it.address, variantName, testName, testConfig)) {
                nodes.add(address to this)
              }
            }
          }
        }
        else -> nodes.add(it.address to it)
      }
      nodes
    }
      .also {
        val nodeMultiset = it.toSetMultimap().filterValues { v -> v.size > 1 }
        if (nodeMultiset.isNotEmpty()) throw IllegalArgumentException(
          "Duplicate addresses added to FakeWorkspace: ${nodeMultiset.keys}"
        )
      }
      .toMap()
  )

  override val variants: List<String> = document.definitions.variants.keys.toList()
  override val nodes get() = nodeIndex.values.asSequence()
  private val slices = variants.map { it to FakeSlice(it, this) }.toMap()
  private val testIndex: Map<String, Set<TestNode>> by lazy {
    nodes.filterIsInstance<TestNode>().map { it.address.split(":")[0] to it }
      .toList()
      .toSetMultimap()
  }

  override fun nodeAt(address: String) = nodeIndex[address] ?: throw NoSuchAddress(address)

  override fun findModule(path: String) = nodeIndex.values
    .filterIsInstance<ModuleNode>()
    .firstOrNull { node -> path.startsWith(node.address) }

  override fun findNode(path: String): Sequence<FindResult<*>> = nodeIndex.asSequence()
    .flatMap { (_, node: Node) ->
      when (node) {
        is ModuleNode -> {
          val nodeRoot = node.address
          node.module.variants.flatMap { (name, config) ->
            val srcs = if (config.srcs.any { path.startsWith("$nodeRoot/$it") }) {
              listOf(VariantFindResult(node, variant = name))
            } else listOf()
            val tests = testIndex[node.address]?.flatMap { testNode ->
              if (testNode.config.srcs.any { path.startsWith("$nodeRoot/$it") }) {
                listOf(TestFindResult(testNode))
              } else listOf()
            } ?: listOf()
            (srcs + tests).ifEmpty {
              if (path.startsWith(nodeRoot)) listOf(GeneralFindResult(node))
              else listOf()
            }
          }.asSequence<FindResult<*>>()
        }
        is TestNode -> sequenceOf() // skip because we're handling test nodes from a different index.
        else -> sequenceOf()
      }
    }

  override fun slice(variant: String) =
    slices[variant] ?: throw IllegalArgumentException("No such variant: $variant")

  class FakeSlice(
    override val variant: String,
    override val workspace: FakeWorkspace
  ) : Slice {
    val deps: Map<String, List<Edge>> by lazy {
      workspace.nodeIndex.map { (addr, node) ->
        addr to when (node) {
          is ModuleNode -> node.module.variants[variant]!!.deps.map { SimpleEdge(it.target, it.tags) }
          is TestNode -> node.config.deps.map { SimpleEdge(it.target, it.tags) }
          is ExternalNode -> TODO("External nodes not yet validated")
          else -> throw IllegalStateException("Unsupported node type ${node::class.qualifiedName}")
        }
      }.toMap()
    }

    val rdeps: Map<String, Set<Edge>> by lazy {
      val outer = linkedMapOf<String, MutableSet<Edge>>()
      workspace.nodeIndex.forEach { (addr, node) ->
        when (node) {
          is ModuleNode -> node.module.variants[variant]!!.deps.forEach {
            outer.getOrPut(it.target) { linkedSetOf() }.add(SimpleEdge(addr, it.tags))
          }
          is TestNode -> node.config.deps.forEach {
            outer.getOrPut(it.target) { linkedSetOf() }.add(SimpleEdge(addr, it.tags))
          }
          is ExternalNode -> TODO("External nodes not yet validated")
          else -> throw IllegalStateException("Unsupported node type ${node::class.qualifiedName}")
        }
      }
      outer.entries.map { (k, v) -> k to v.toSet() }.toMap()
    }

    override fun nodeAt(address: String) = workspace.nodeAt(address)

    override fun dependenciesOf(address: String) = deps[address] ?: listOf()

    override fun dependenciesOf(node: Node): List<Edge> = dependenciesOf(node.address)

    override fun dependenciesOn(address: String) = rdeps[address]?.toList() ?: listOf()

    override fun dependenciesOn(node: Node) = dependenciesOn(node.address)
  }
}
