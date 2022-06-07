package com.squareup.spice.model

import com.squareup.spice.collections.DeterministicMap
import com.squareup.spice.collections.emptyDeterministicMap

/**
 * Defines the core contract of an external workspace. External workspaces provide non-local
 * nodes in the graph using a URI addressing scheme <name>://<address>. The specific <address>
 * is implementation dependant. Calling code that obtains external workspaces should cast to the
 * appropriate type if it needs to do something fancy.
 */
data class ExternalWorkspaceDocument(
  /**
   * The name by which this workspace will be referenced in the global address space.
   * addresses of the form <name>://<groupId>:<artifactId> will reference artifacts specified
   * in this workspace.
   */
  val name: String,

  /**
   * A string identifier to help consuming software understand how to interpret this external
   * workspace. (e.g. "maven", would signal the software to interpret this workspace as a maven
   * artifact "universe" and handle any resolution that might be necessary to provide a fully
   * articulated graph.
   */
  val type: String,

  /**
   * Metadata about this workspace that can be consumed by tools that understand it's [type].
   *
   * For instance, maven external workspaces may interpret a "repositories" property which contains
   * the information needed to set up resolution from maven repos.
   */
  // TODO: See if we need a more complex Property object that can contain scalar/array/map data.
  val properties: DeterministicMap<String, List<String>> = emptyDeterministicMap(),

  /**
   * A map of artifacts to configuration. These artifact labels are interpreted by software that
   * knows about the external workspace [type]. For instance, a maven based external workspace would
   * interpret these artifacts as group_id:artifact_id:version tuples.
   */
  val artifacts: DeterministicMap<String, ExternalNodeConfiguration?> = emptyDeterministicMap()
)

/**
 * Configuration elements for artifacts in external workspaces. These are limited to deps-surgery
 * primitives and artifact hashes (if used).
 *
 * [include] and [exclude] can be used together, but are incompatible with [deps].
 */
data class ExternalNodeConfiguration(
  val exclude: List<String> = listOf(),
  val include: List<String> = listOf(),
  val deps: List<String> = listOf(),
  val hashes: DeterministicMap<String, String>? = emptyDeterministicMap()
)
