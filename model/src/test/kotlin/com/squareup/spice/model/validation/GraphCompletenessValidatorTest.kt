package com.squareup.spice.model.validation

import com.google.common.truth.Truth.assertThat
import com.squareup.spice.builders.module
import com.squareup.spice.builders.workspaceDocument
import com.squareup.spice.model.IncompleteGraph
import com.squareup.spice.model.Node.ModuleNode
import com.squareup.spice.test.FakeWorkspace
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GraphCompletenessValidatorTest {

  @Test fun loadAllWithDanglingReferences() {
    val ws = workspace()
    assertThat(ws.nodes.count()).isEqualTo(3)
    val e = assertThrows(IncompleteGraph::class.java) {
      ws.validate(listOf(GraphCompletenessValidator))
    }
    assertThat(e).hasMessageThat().contains("Incomplete graph")
    assertThat(e).hasMessageThat().contains("- /r (referenced by /b)")
    assertThat(e).hasMessageThat().contains("- /q (referenced by /a, /b)")
  }

  fun workspace(): FakeWorkspace {
    return FakeWorkspace(
      workspaceDocument("fake") {
        definitions {
          namespace = ""
          variants { "main" {} }
        }
      },
      ModuleNode("/a", module { variants { "main" { deps { add("/q") } } } }),
      ModuleNode("/b", module { variants { "main" { deps { add("/a", "/q", "/r") } } } }),
      ModuleNode("/c", module { variants { "main" { deps { add("/c") } } } })
    )
  }
}
