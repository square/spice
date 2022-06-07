package com.squareup.spice.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents a spice workspace, the root of a set of modules, and the context in which all such
 * modules exist. It can define defaults for modules, as well as define tools applied to modules,
 * as well as external workspaces (non-local, imported parts of the graph, such as from maven
 * dependencies)
 */
// TODO: Possibly separate this with an interface.
data class WorkspaceDocument(
  /** A name for the overall workspace, largely for documentary and tool-reporting concerns. */
  val name: String,
  @JsonProperty("declared_tools")
  val tools: List<ToolDefinition> = listOf(),
  val external: List<ExternalWorkspaceDocument> = listOf(),
  val definitions: ModuleDocument
)
