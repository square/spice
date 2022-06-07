package com.squareup.spice.builders

import com.squareup.spice.collections.MutableDeterministicSet
import com.squareup.spice.collections.mutableDeterministicSetOf
import com.squareup.spice.model.ExternalWorkspaceDocument
import com.squareup.spice.model.ModuleDocument
import com.squareup.spice.model.WorkspaceDocument

/** WorkspaceDocumentBuilder defines a simplified api for assembling WorkspaceDocument instances.
 *
 * Usage: see [ToolDefinitionSet], [ExternalWorkspaceDocumentBuilder], [ModuleDocumentBuilder].
 */
@SpiceBuilderScope
class WorkspaceDocumentBuilder(val name: String) {
  val tools = ToolDefinitionSet()

  val external: MutableDeterministicSet<ExternalWorkspaceDocument> = mutableDeterministicSetOf()

  fun external(config: ExternalWorkspaceDocumentBuilder.Scope.() -> Unit) {
    ExternalWorkspaceDocumentBuilder.Scope { external.add(it) }.apply(config)
  }

  var definitions: ModuleDocument = ModuleDocument()
  fun definitions(config: ModuleDocumentBuilder.() -> Unit) {
    definitions = ModuleDocumentBuilder().apply(config).build()
  }

  fun build(config: WorkspaceDocumentBuilder.() -> Unit) = apply(config).build()

  fun build() = WorkspaceDocument(
    name, tools.sortedBy { it.name },
    external.sortedBy { it.name }.toList(),
    definitions
  )
}

fun workspaceDocument(
  name: String,
  invokable: WorkspaceDocumentBuilder.() -> Unit
): WorkspaceDocument {
  val builder = WorkspaceDocumentBuilder(name)
  invokable.invoke(builder)
  return builder.build()
}
