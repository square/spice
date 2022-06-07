package com.squareup.spice.serialization

import com.google.common.truth.Truth.assertThat
import com.squareup.spice.collections.deterministicMapOf
import com.squareup.spice.model.Dependency
import com.squareup.spice.model.ExternalNodeConfiguration
import com.squareup.spice.model.ExternalWorkspaceDocument
import com.squareup.spice.model.ModuleDocument
import com.squareup.spice.model.ToolDefinition
import com.squareup.spice.model.VariantConfiguration
import com.squareup.spice.model.WorkspaceDocument
import org.junit.jupiter.api.Test

class SerializerTest {

  @Test fun roundTripFromWorkspaceObject() {
    val serializer = Serializer()
    val initial = WorkspaceDocument(
      name = "foo",
      tools = listOf(
        ToolDefinition("java"),
        ToolDefinition("kotlin"),
        ToolDefinition("anvil"),
        ToolDefinition("dagger"),
        ToolDefinition("anvil_library", not = listOf("dagger"))
      ),
      definitions = ModuleDocument(
        variants = deterministicMapOf(
          "main" to VariantConfiguration(
            deps = listOf(
              Dependency("foo/bar", listOf("a", "b")),
              Dependency("foo/baz")
            )
          )
        )
      ),
      external = listOf(
        ExternalWorkspaceDocument(
          "maven",
          "maven",
          deterministicMapOf(),
          deterministicMapOf(
            "com.google.guava:guava:25.0" to ExternalNodeConfiguration(
              exclude = listOf("blah:foo")
            ),
            "com.google.truth:truth:1.0" to null,
            "junit:junit:4.13" to null
          )
        )
      )
    )
    val yaml = serializer.marshall(initial)
    val actual = serializer.unmarshall(yaml, WorkspaceDocument::class.java)
    assertThat(actual).isEqualTo(initial)
  }

  @Test fun parseWorkspaceYaml() {
    // Yes this is brittle, yes it's testing parsing. But it's making sure the data objects are what
    // we want
    val serializer = Serializer()
    val initial = """
      ---
      name: "foo"
      
      definitions:
        namespace: "com.squareup.spice" 
        tools: ["kotlin"]
        variants:
            debug:
              srcs: ["src/main", "src/debug"]
              tests: 
                  "unit":
                      srcs: ["src/test"]
                      deps:
                        - "blah://com.google.truth"
                        - "blah://junit"
            release:
              srcs: ["src/main", "src/release"]
              tests:
                  ui: { srcs: ["src/androidTest"] }
            beta:
              srcs: ["src/main", "src/release", "src/beta"] # uses release sources AND beta sources
              tests:
                  ui: { srcs: ["src/androidTest"] }
        
      
      
      declared_tools:
        - name: "java"
        - name: "kotlin"
        - name: "anvil"
        - name: "dagger"
        - name: "anvil_library"
          not:
            - "dagger"  
      external:
        - name: "blah"
          type: "maven"
          properties:
            repositories:
              - central::http://repo1.maven.org
          artifacts:
              com.google.truth:truth:1.0: 
                  exclude: ["foo:bar"]
                  include: ["bar:foo"]
                  deps: ["blah:foo"] # mutually exclusive to exclude/include 
              junit:junit:4.13:
              "com.foo.bar:bar:1234":
    """.trimIndent()
    val actual = serializer.unmarshall(initial, WorkspaceDocument::class.java)
    assertThat(actual.name).isEqualTo("foo")
    assertThat(actual.tools).hasSize(5)
    assertThat(actual.tools.last().not).isEqualTo(listOf("dagger"))
    assertThat(actual.tools.last().not).isEqualTo(listOf("dagger"))
    assertThat(actual.external).hasSize(1)
    // TODO - allow for a find-by-type or other better apis than a cast.
    val mavenWorkspace = actual.external.first()
    assertThat(mavenWorkspace.name).isEqualTo("blah")
    assertThat(mavenWorkspace.type).isEqualTo("maven")
    with(mavenWorkspace.properties["repositories"]) {
      requireNotNull(this)
      assertThat(this).isEqualTo(listOf("central::http://repo1.maven.org"))
    }
    assertThat(mavenWorkspace.artifacts).hasSize(3)
    with(mavenWorkspace.artifacts["com.google.truth:truth:1.0"]) {
      requireNotNull(this)
      // Note: validation should prevent these three being all non-empty/null but happens elsewhere.
      assertThat(exclude).isEqualTo(listOf("foo:bar"))
      assertThat(include).isEqualTo(listOf("bar:foo"))
      assertThat(deps).isEqualTo(listOf("blah:foo"))
    }
  }

  @Test fun parseModuleYaml() {
    // Yes this is brittle, yes it's testing parsing. But it's making sure the data objects are what
    // we want
    val serializer = Serializer()
    val initial = """
      --- # Project Module
      name: "foo-bar"
      namespace: "com.squareup.spice" 
      tools: ["kotlin"]
      variants:
        debug:
          srcs: 
            - "src/main"
            - "src/debug"
          tests:
            unit:
              srcs: ["src/test"]
              deps:
                - /foo/blah: [foo]
                - maven://com.google.truth
                - maven://junit
        release:
          srcs: ["src/main", "src/release"]
          tests:
            ui: { srcs: ["src/androidTest"] }
    """.trimIndent()
    val actual = serializer.unmarshall(initial, ModuleDocument::class.java)
    with(actual) {
      assertThat(name).isEqualTo("foo-bar")
      assertThat(namespace).isEqualTo("com.squareup.spice")
      assertThat(tools).isEqualTo(setOf("kotlin"))
      assertThat(variants).hasSize(2)
      assertThat(variants.keys).isEqualTo(setOf("debug", "release"))
      with(variants["debug"]) {
        requireNotNull(this)
        assertThat(this.srcs).isEqualTo(listOf("src/main", "src/debug"))
        assertThat(this.tests).hasSize(1)
        with(tests["unit"]) {
          requireNotNull(this)
          assertThat(this.srcs).isEqualTo(listOf("src/test"))
          assertThat(this.deps).hasSize(3)
          assertThat(this.deps[0].tags).isEqualTo(listOf("foo"))
        }
      }
    }
  }
}
