package com.squareup.spice.model

import com.squareup.spice.collections.DeterministicMap
import com.squareup.spice.collections.emptyDeterministicMap

/**
 * Defines a tool, properties, and its relationship to other tools - namely, which tools it requires (if it does)
 * and which tools it is incompatible with. These are used to validate tool application in modules.
 *
 * Tools themselves have no further definitions, but the semantics of how to apply the tools are
 * delegated to templates or client apps.
 */
data class ToolDefinition(
  val name: String,
  val any: List<String> = listOf(),
  val all: List<String> = listOf(),
  val not: List<String> = listOf(),
  val properties: DeterministicMap<String, String> = emptyDeterministicMap()
)
