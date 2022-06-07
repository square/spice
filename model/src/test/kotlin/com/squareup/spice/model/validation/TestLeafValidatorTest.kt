package com.squareup.spice.model.validation

import com.google.common.truth.Truth.assertThat
import com.squareup.spice.builders.module
import com.squareup.spice.builders.workspaceDocument
import com.squareup.spice.model.InvalidGraph
import com.squareup.spice.model.Node.ModuleNode
import com.squareup.spice.model.Node.TestNode
import com.squareup.spice.model.TestConfiguration
import com.squareup.spice.test.FakeWorkspace
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TestLeafValidatorTest {
  val workspaceDocument = workspaceDocument("fake") {
    definitions {
      namespace = ""
      variants { "main" {} }
    }
  }

  @Test fun invalidAddress() {
    val workspace = FakeWorkspace(
      workspaceDocument,
      ModuleNode("/a", module { variants { "main" { } } }),
      ModuleNode(
        "/b",
        module {
          variants {
            "main" {
              deps { add("/a") }
              tests { "foo/bar" { } }
            }
          }
        }
      ),
      ModuleNode(
        "/group1/c",
        module {
          variants {
            "main" {
              deps { add("/b") }
              tests { "unit" { } }
            }
          }
        }
      ),
      TestNode("/a:foo/bar:blah", TestConfiguration())
    )
    val e = assertThrows(InvalidGraph::class.java) { workspace.validate(STANDARD_VALIDATORS) }
    assertThat(e).hasMessageThat().contains("  - /a:foo/bar:blah")
    assertThat(e).hasMessageThat().doesNotContain("  - /a\n")
    assertThat(e).hasMessageThat().doesNotContain("  - /group1/c:main:unit")
  }
}
