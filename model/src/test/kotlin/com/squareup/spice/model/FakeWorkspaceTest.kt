package com.squareup.spice.model

import com.google.common.truth.Truth.assertThat
import com.squareup.spice.builders.module
import com.squareup.spice.builders.workspaceDocument
import com.squareup.spice.model.FindResult.GeneralFindResult
import com.squareup.spice.model.FindResult.TestFindResult
import com.squareup.spice.model.FindResult.VariantFindResult
import com.squareup.spice.model.Node.ModuleNode
import com.squareup.spice.model.validation.DependencyCycleValidator
import com.squareup.spice.model.validation.GraphCompletenessValidator
import com.squareup.spice.test.FakeWorkspace
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FakeWorkspaceTest {
  val wsDoc = workspaceDocument("fake") {
    definitions {
      namespace = ""
      tools { ToolDefinition("kotlin") }
      variants {
        "main" {
          srcs = listOf("src/main/java")
          tools { uses("java") }
          tests { "unit" { srcs = listOf("src/test/java") } }
        }
      }
    }
  }

  @Test fun simple() {
    val ws: Workspace = FakeWorkspace(
      wsDoc,
      ModuleNode("/a", module {}),
      ModuleNode("/b", module { variants { "main" { deps { add("/a") } } } }),
      ModuleNode("/c", module { variants { "main" { deps { add("/b") } } } }),
      ModuleNode("/d", module { variants { "main" { deps { add("/a") } } } }),
      ModuleNode("/e", module { variants { "main" { deps { add("/c", "/b") } } } })
    )

    val slice = ws.slice("main")
    assertThat(ws.nodeAt("/b").address).isEqualTo("/b")
    assertThat(slice.dependenciesOf("/c").map { it.target }).isEqualTo(listOf("/b"))
    assertThat(slice.dependenciesOn("/c").map { it.target }).isEqualTo(listOf("/e"))
    ws.validate(listOf(GraphCompletenessValidator, DependencyCycleValidator))
  }

  @Test fun duplicateAddresses() {
    val t = assertThrows(IllegalArgumentException::class.java) {
      FakeWorkspace(
        wsDoc,
        ModuleNode("/a", module {}),
        ModuleNode("/a", module { variants { "main" { deps { add("/a") } } } })
      )
    }
    assertThat(t).hasMessageThat().isEqualTo("Duplicate addresses added to FakeWorkspace: [/a]")
  }

  @Test fun lookupModules() {
    val ws: Workspace = FakeWorkspace(
      wsDoc,
      ModuleNode("/a", module {}),
      ModuleNode(
        "/d",
        module {
          variants {
            "main" {
              deps { add("/a") }
              tests { "unit" { deps { add("/d") } } }
            }
          }
        }
      )
    )

    ws.validate(listOf(GraphCompletenessValidator, DependencyCycleValidator))
    assertThat(ws.findModule("/q/foo/bar")).isNull()
    assertThat(ws.findModule("/a/foo/bar")!!.address).isEqualTo("/a")
    assertThat(ws.findModule("/d/foo/bar")!!.address).isEqualTo("/d")
    assertThat(ws.findModule("/d/src/main/java/foo/bar")!!.address).isEqualTo("/d")
    assertThat(ws.findModule("/d/src/test/java/foo/bar")!!.address).isEqualTo("/d")
  }

  @Test fun lookupNodes() {
    val ws: Workspace = FakeWorkspace(
      wsDoc,
      ModuleNode("/a", module {}),
      ModuleNode(
        "/d",
        module {
          variants {
            "main" {
              deps { add("/a") }
              tests { "unit" { deps { add("/d") } } }
            }
          }
        }
      )
    )

    ws.validate(listOf(GraphCompletenessValidator, DependencyCycleValidator))
    assertThat(ws.findNode("/q/blah.txt").toList()).isEmpty()
    ws.findNode("/d/blah.txt").toList().only().let { actual ->
      assertThat(actual).isInstanceOf(GeneralFindResult::class.java)
      assertThat(actual.node.address).isEqualTo("/d")
    }
    ws.findNode("/d/src/main/java/some/file.txt").toList().only().let { actual ->
      assertThat(actual).isInstanceOf(VariantFindResult::class.java)
      actual as VariantFindResult
      assertThat(actual.variant)
      assertThat(actual.node.address).isEqualTo("/d")
    }
    ws.findNode("/d/src/test/java/some/file.txt").toList().only().let { actual ->
      assertThat(actual).isInstanceOf(TestFindResult::class.java)
      assertThat(actual.node.address).isEqualTo("/d:main:unit")
    }
  }

  private fun <T> List<T>.only(): T {
    if (size != 1) throw AssertionError("List should have had one element: $this")
    return first()
  }
}
