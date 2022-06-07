package com.squareup.spice.model

import com.squareup.spice.model.Node.ModuleNode
import com.squareup.spice.model.Node.TestNode
import com.squareup.spice.model.validation.Validator

/**
 * The supergraph of all possible graphs defined in a spice system, this is a key entrypoint, from
 * which address lookup can be done, nodes can be fetched, variant [slices][Slice] can be obtained,
 * as well as the list of available variants.
 *
 * Generally graph operations will be done on [Slice] instances, but the whole system, and some
 * holistic operations are located on the Workspace.
 */
interface Workspace {

  /** The list of variants registered in this workspace */
  val variants: List<String>

  /** The underlying configuration data structure for this workspace */
  val document: WorkspaceDocument

  /**
   * A sequence of all nodes present in this workspace. This can be expensive if the underlying
   * implementation needs to load data in order ot fulfill this.
   */
  @WholeGraph
  val nodes: Sequence<Node>

  /**
   * For all slices, applies the given validators, in the order given.
   *
   * Completeness simply means that there are no dangling references (no node could be found for
   * the target of an edge).  Validations of subgraphs should be done via [Slice.validate] methods.
   *
   * This can be expensive, if the implementation needs to load a lot of information to have the
   * full graph available.
   */
  @WholeGraph
  fun validate(validators: List<Validator>) {
    variants.forEach { slice(it).validate(validators, *nodes.toList().toTypedArray()) }
  }

  /**
   * Obtain the node for a given address (load semantics are an implementation responsibility)
   *
   * This node is not constrained by any variant slice, but can freely be given to [Slice] instances
   * from this workspace for graph operations.
   *
   * Any supported node can be queried, with a few constraints - if a node nested with in a Module
   * is queried, the appropriate node may be returned - that node must have a "parent" node in the
   * system, such that it could be queried.  e.g., if "/foo/bar:main:unit" exists, "/foo/bar" must
   * exist as a [ModuleNode].
   */
  fun nodeAt(address: String): Node

  /**
   * Returns one or more nodes (if any is loaded) in which the supplied path may be found. Those nodes
   * should be contained within one module, and a [FindResult.GeneralFindResult] should only be
   * returned if the path is found within a given module, but not within any source sets that would
   * attribute it more narrowly.
   */
  fun findNode(path: String): Sequence<FindResult<*>>

  /**
   * Returns a [ModuleNode] in which the supplied path may be found (that is, it's a member of that
   * module).
   */
  fun findModule(path: String): ModuleNode?

  /**
   * Obtains a slice of the graph, which is a variant-filtered view. Other than basic module loading
   * and managing external workspace interactions, all graph operations occur on Slice instances.
   */
  fun slice(variant: String): Slice
}

/**
 * A result for the [Workspace.findModule] function, returning one of several known types of responses
 * depending on type.
 *
 * It is not strictly impossible for a file to exist in a variant node and a test node, or more than
 * one variant node or test node, if the source sets are so-specified. It should never be the case
 * that a file is returned in a [GeneralFindResult] if it is found in the more narrow
 * [VariantFindResult] or [TestFindResult].
 *
 * A file should never be found in more than one module.
 */
sealed class FindResult<T : Node> {
  abstract val node: T
  /**
   * A module node result that is constrained to a variant. Files will be in this result if they
   * are found within the variant's source-set. A file may be found in multiple []VariantFindResult]
   * objects if the same source-set is listed in multiple variants.
   */
  data class VariantFindResult(
    override val node: ModuleNode,
    val variant: String
  ) : FindResult<ModuleNode>()

  /**
   * References a [ModuleNode] where a file is not a part of any declared source-set, but exists within the
   * overall file structure of the module. Useful for finding common files, build files, and
   * identifying the node, where there isn't a variant the file is associated with.
   */
  data class GeneralFindResult(override val node: ModuleNode) : FindResult<ModuleNode>()

  /**
   * References a [TestNode] where the file was found in the source set of a test node.
   */
  data class TestFindResult(override val node: TestNode) : FindResult<TestNode>()
}

// TODO: supply an ExternalWorkspace API similar to Slice, for graph operations.
//       This can encapsulate any resolution logic needed to let external workspaces participate
//       in normal graph operations, either in a shallow (no deps) or deep (resolved deps) mode.
//       For now this doc just provides the raw metadata.

/**
 * A variant-constrained view of the workspace, and the locus of all graph operations.
 *
 * Spice workspaces contain many subgraphs, both local variants and external, graph operations must
 * happen in the context of a specific slice.
 *
 * > Note: this contract assumes enough graph state is loaded by any implementation such that its
 * >       graph walk operations (dependenciesOf/dependenciesOn) will function. Implementations may
 * >       choose to load up-front, load dynamically, or fail with an exception if the graph is not
 * >       sufficiently complete. No slice should ever return a partial or incomplete answer.
 */
interface Slice {
  /** The named variant of the whole graph this slice represents */
  val variant: String

  /** The [Workspace] of which this [Slice] is a member. */
  val workspace: Workspace

  /**
   * Applies the supplied validators to the supplied nodes, in the order given.
   *
   * Implementations may choose to load graph state incrementally, or throw an exception on a
   * partially loaded workspace
   */
  fun validate(validators: List<Validator>, vararg roots: String): Slice {
    return validate(validators, *roots.map { nodeAt(it) }.toTypedArray())
  }

  /**
   * Applies the supplied validators to the supplied nodes, in the order given.
   *
   * Implementations may choose to load graph state incrementally, or throw an exception on a
   * partially loaded workspace
   */
  fun validate(validators: List<Validator>, vararg roots: Node): Slice {
    validators.forEach { it.validate(workspace, this, *roots) }
    return this
  }

  /** The same operation as [Workspace.nodeAt], but located on [Slice] for convenience. */
  fun nodeAt(address: String): Node

  /**
   * Returns the "edges" of dependency relationships (and their configuration [tags]) having the
   * node at the given address as the source.
   *
   * > Note: Assumes sufficient workspace state is loaded to provide a correct answer.
   */
  fun dependenciesOf(address: String): List<Edge>

  /**
   * Returns the "edges" of dependency relationships (and their configuration [tags]) having the
   * given node as the source.
   *
   * > Note: Assumes sufficient workspace state is loaded to provide a correct answer.
   */
  fun dependenciesOf(node: Node): List<Edge>

  /**
   * Returns the "edges" of dependency relationships (and their configuration [tags]) having the
   * node at the given address as their destination.  This is the inverse of dependenciesOf, sometimes
   * called reverse-dependencies.
   *
   * > Note: Assumes sufficient workspace state is loaded to provide a correct answer.
   */
  fun dependenciesOn(address: String): List<Edge>

  /**
   * Returns the "edges" of dependency relationships (and their configuration [tags]) having the
   * given node as their destination.  This is the inverse of dependenciesOf, sometimes called
   * reverse-dependencies.
   *
   * > Note: Assumes sufficient workspace state is loaded to provide a correct answer.
   */
  fun dependenciesOn(node: Node): List<Edge>
}
