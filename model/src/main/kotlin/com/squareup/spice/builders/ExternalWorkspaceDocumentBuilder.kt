package com.squareup.spice.builders

import com.squareup.spice.collections.DeterministicMap
import com.squareup.spice.collections.emptyDeterministicMap
import com.squareup.spice.collections.toDeterministicMap
import com.squareup.spice.model.ExternalNodeConfiguration
import com.squareup.spice.model.ExternalWorkspaceDocument
import java.util.SortedMap
import java.util.TreeMap

/**
 * ExternalWorkspaceDocumentBuilder provides a fluent typesafe builder for external workspaces.
 *
 * Usage:
 *
 *  val documents = mutableListOf<ExternalWorkspaceDocument>()
 *  ExternalWorkspaceDocumentBuilder.Scope{ documents.add(it)}.apply {
 *     workspace("bobMerill").ofType("songwriter") {
 *       artifacts {
 *          "hey.mambo" {
 *            include = listOf("carla.boni")
 *            exclude = listOf("dean.martin")
 *          }
 *       }
 *     }
 *  }
 *
 *  assertThat(documents).containsExactly(
 *    ExternalWorkspaceDocument(
 *      name = "bobMerill",
 *      type = "songwriter",
 *      artifacts = mapOf(
 *        "hey.mambo" to ExternalNodeConfiguration(
 *          include = listOf("carla.boni"),
 *          exclude = listOf("dean.martin")
 *        )
 *    )
 *  )
 */
class ExternalWorkspaceDocumentBuilder(val name: String, private val type: String) {
  class Scope(val accept: (ExternalWorkspaceDocument) -> Unit) {

    fun workspace(name: String) = Named(name)

    inner class Named(val name: String) {
      fun ofType(type: String, config: ExternalWorkspaceDocumentBuilder.() -> Unit) {
        accept(ExternalWorkspaceDocumentBuilder(name, type).apply(config).build())
      }
    }
  }

  private val properties: SortedMap<String, List<String>> = TreeMap()

  private val artifacts: SortedMap<String, ExternalNodeConfiguration?> = TreeMap()

  fun artifacts(config: ExternalNodeConfigurationBuilder.Scope.() -> Unit) {
    ExternalNodeConfigurationBuilder.Scope { n, c ->
      artifacts[n] = c
    }.apply(config)
  }

  fun build() = ExternalWorkspaceDocument(
    name,
    type,
    properties.toDeterministicMap(),
    artifacts.toDeterministicMap()
  )

  class ExternalNodeConfigurationBuilder {
    class Scope(val accept: (String, ExternalNodeConfiguration?) -> Unit) {
      operator fun String.invoke(config: ExternalNodeConfigurationBuilder.() -> Unit) {
        accept(this, ExternalNodeConfigurationBuilder().apply(config).build())
      }
    }

    var exclude: List<String> = listOf()
    var include: List<String> = listOf()
    var deps: List<String> = listOf()
    private var hashes: DeterministicMap<String, String>? = emptyDeterministicMap()

    fun build() = when {
      exclude.isNotEmpty() ||
        include.isNotEmpty() ||
        deps.isNotEmpty() ||
        (hashes?.isNotEmpty() ?: false)
      -> ExternalNodeConfiguration(exclude, include, deps, hashes)
      else -> null
    }
  }
}
