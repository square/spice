package com.squareup.spice.model

/**
 * A node in the graph representing a unit of action, which may have dependencies on other nodes.
 * It could be a local module, or an external workspace address, or a test-set. It can in some ways
 * be looked at as a source for dependencies, though different kinds of nodes may have their own
 * metadata.
 *
 * All nodes are addressable, and are the chief handle the calling code has on a given part of the
 * graph.
 *
 * Node's dependencies will always be in the context of a particular variant slice of the graph.
 */
interface Node {
  /**
   * The address of this node in the workspace's address-space (e.g. /foo or maven://foo.bar:blah)
   *
   * Different types of nodes will have different addressing schemes. Notably, external nodes will
   * have a uri protocol (e.g. `maven://foo.bar:blah`) which will be a signal to any tooling on
   * how the address should be interpreted.
   *
   * Internal nodes will start with `/` and have the form `/path/to/node:variant:test_target`.
   * e.g. `/foo/bar/baz:debug:unit-tests`. A module will have a simple ath such as `/foo/bar`.
   * It is generally not referenced with a variant unless in a context where variant-filtering
   * is needed, such as a query.
   */
  val address: String

  /**
   * Represents a local module, addressed from the workspace root. Local modules "contain"
   * variants and tests, though the [ModuleNode] does not maintain direct references to its
   * variants or tests, these being
   */
  // TODO: Express this more as a front-end API for a ModelDocument, such that the ModuleDocument
  //       can be an implementation detail of ModuleNode.
  data class ModuleNode(
    override val address: String,
    val module: ModuleDocument
  ) : Node {
    val path: List<String> by lazy { address.trim('/').split("/") }
  }

  /**
   * Represents a test, which is associated with a module (in the context of a variant).
   *
   * TestNode addresses must be of the form `/path/to/module:variant:test-set-name`. If
   * a test node exists within a workspace, then its "parent" module must also exist. i.e.
   * `/path/to/module:main:unit` implies the existence of an obtainable `/path/to/module`
   * node.
   *
   * Tests may (and will) be the target of dependency edges. While tests are typically
   * dependent on their module code, that is not assumed, but should be represented in
   * dependency edges.
   *
   * Test nodes are also special in that they may only exist within the context of a
   * variant, and it is an error to attempt to resolve a test node against a variant
   * in which it does not sit. That is to say, a [Slice] may not resolve a test node
   * which is not within its own variant.
   */
  data class TestNode(
    override val address: String,
    val config: TestConfiguration
  ) : Node {
    /**
     * Preferred way to manually construct, to ensure that tests derived from module document
     * information are properly addressed.
     */
    constructor(
      moduleAddress: String,
      variantName: String,
      testName: String,
      config: TestConfiguration
    ) : this("$moduleAddress:$variantName:$testName", config)

    val module: String = address.split(":")[0]
    val variant: String = address.split(":")[1]
    val name: String = address.split(":")[2]

    companion object {
      val VALID_ADDRESS_PATTERN = "/[a-zA-Z1-9_/-]*:[a-zA-Z1-9_-]*:[a-zA-Z1-9_-]*".toRegex()
    }
  }

  /**
   * Represents an external node (e.g. a maven artifact). The address should be reified against an
   * external workspace, and any metadata for it in that context should be obtained from that
   * workspace.
   */
  data class ExternalNode(
    override val address: String
  ) : Node
}
