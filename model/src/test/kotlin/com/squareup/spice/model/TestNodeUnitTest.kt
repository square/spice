package com.squareup.spice.model

import com.google.common.truth.Truth.assertThat
import com.squareup.spice.builders.module
import com.squareup.spice.builders.workspaceDocument
import com.squareup.spice.model.Node.ModuleNode
import com.squareup.spice.model.validation.STANDARD_VALIDATORS
import com.squareup.spice.test.FakeWorkspace
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TestNodeUnitTest {

  @Test fun simple() {
    val ws: Workspace = FakeWorkspace(
      workspaceDocument("fake") {
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
      },
      ModuleNode("/a", module {}),
      ModuleNode("/b", module { variants { "main" { deps { add("/a") } } } }),
      ModuleNode("/c", module { variants { "main" { deps { add("/b") } } } }),
      ModuleNode(
        "/d",
        module {
          variants {
            "main" {
              deps { add("/a") }
              tests { "unit" { deps { add("/c") } } }
            }
          }
        }
      ),
      ModuleNode("/e", module { variants { "main" { deps { add("/c", "/b") } } } })
    )
    val slice = ws.slice("main")
    assertThat(ws.nodeAt("/b").address).isEqualTo("/b")
    assertThat(slice.dependenciesOf("/c").map { it.target }).containsExactly("/b")
    assertThat(slice.dependenciesOn("/c").map { it.target }).containsExactly("/e", "/d:main:unit")
    ws.validate(STANDARD_VALIDATORS)
  }

  @Test fun dependantsOfTests() {
    val ws: Workspace = FakeWorkspace(
      workspaceDocument("fake") {
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
      },
      ModuleNode(
        "/a",
        module {
          variants {
            "main" {
              tests { "unit" { deps { add("/a") } } }
            }
          }
        }
      ),
      ModuleNode(
        "/b",
        module {
          variants {
            "main" {
              deps { add("/a") }
              tests { "unit" { deps { add("/a:main:unit") } } }
            }
          }
        }
      )
    )
    val e: InvalidGraph = assertThrows(InvalidGraph::class.java) { ws.validate(STANDARD_VALIDATORS) }
    assertThat(e).hasMessageThat().contains("/b:main:unit -> /a:main:unit")
  }
}
