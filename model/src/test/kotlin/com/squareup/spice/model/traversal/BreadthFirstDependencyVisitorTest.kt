package com.squareup.spice.model.traversal

import com.google.common.truth.Truth.assertThat
import com.squareup.spice.builders.module
import com.squareup.spice.builders.workspaceDocument
import com.squareup.spice.model.Node.ModuleNode
import com.squareup.spice.model.ToolDefinition
import com.squareup.spice.model.Workspace
import com.squareup.spice.test.FakeWorkspace
import org.junit.jupiter.api.Test

class BreadthFirstDependencyVisitorTest {

  val ws: Workspace = FakeWorkspace(
    workspaceDocument("fake") {
      definitions {
        namespace = ""
        tools { ToolDefinition("kotlin") }
        variants {
          "main" {
            srcs = listOf("src/test/java")
            tools { uses("java") }
            tests { "unit" { srcs = listOf("src/test/java") } }
          }
        }
      }
    },
    ModuleNode("/a", module {}),
    ModuleNode("/b", module { variants { "main" { deps { add("/a") } } } }),
    ModuleNode("/c", module { variants { "main" { deps { add("/a") } } } }),
    ModuleNode("/d", module { variants { "main" { deps { add("/b") } } } }),
    ModuleNode("/e", module { variants { "main" { deps { add("/c") } } } }),
    ModuleNode("/f", module { variants { "main" { deps { add("/e", "/b") } } } }),
    ModuleNode("/g", module { variants { "main" { deps { add("/d") } } } }),
    ModuleNode("/h", module { variants { "main" { deps { add("/g", "/f") } } } })
  )
  val slice = ws.slice("main")

  @Test fun foo() {
    // Force lazy loading for debug purposes.
    slice.dependenciesOn(slice.dependenciesOf(ws.nodeAt("/h")).first().target)

    val queue = arrayListOf<String>()
    BreadthFirstDependencyVisitor { module -> queue.add(module.address) }.visit(slice, ws.nodeAt("/h"))
    // Breadth-first, sorted at each target->set<edge>.
    // (h) -> (h: f, g) -> (f: b, e) -> (g: d) -> (b: a) -> (e: c) (duplicate occurrences ignored)
    // h, f, g, b, e, d, a, c
    assertThat(queue.toList()).isEqualTo(listOf("/h", "/f", "/g", "/b", "/e", "/d", "/a", "/c"))
  }
}
