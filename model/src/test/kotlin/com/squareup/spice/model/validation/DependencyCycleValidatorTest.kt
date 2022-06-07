package com.squareup.spice.model.validation

import com.google.common.truth.Truth.assertThat
import com.squareup.spice.builders.module
import com.squareup.spice.builders.workspaceDocument
import com.squareup.spice.model.CyclicReferenceError
import com.squareup.spice.model.Dependency
import com.squareup.spice.model.Node.ModuleNode
import com.squareup.spice.model.Workspace
import com.squareup.spice.test.FakeWorkspace
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DependencyCycleValidatorTest {
  @Test fun cycleSelf() {
    val e = assertThrows(CyclicReferenceError::class.java) {
      workspace().slice("main").validate(listOf(DependencyCycleValidator), "/c")
    }
    assertThat(e)
      .hasMessageThat().contains("Cycle detected in 'main' at \"/c\":")
  }

  @Test fun cyclePair() {
    val e = assertThrows(CyclicReferenceError::class.java) {
      workspace().slice("main").validate(listOf(DependencyCycleValidator), "/a")
    }
    assertThat(e)
      .hasMessageThat().contains("Cycle detected in 'main' at \"/a\":")
  }

  @Test fun cycleChain() {
    val e = assertThrows(CyclicReferenceError::class.java) {
      workspace().slice("main").validate(listOf(DependencyCycleValidator), "/i")
    }
    assertThat(e)
      .hasMessageThat().contains("Cycle detected in 'main' at \"/g\" from /i:")
  }

  fun workspace(): Workspace {
    return FakeWorkspace(
      workspaceDocument("fake") {
        definitions {
          namespace = ""
          variants { "main" {} }
        }
      },
      // Small cycle
      ModuleNode("/a", module { variants { "main" { deps { add(Dependency("/b")) } } } }),
      ModuleNode("/b", module { variants { "main" { deps { add(Dependency("/a")) } } } }),
      // Self cycle
      ModuleNode("/c", module { variants { "main" { deps { add(Dependency("/c")) } } } }),
      // Long cycle
      ModuleNode("/d", module { variants { "main" { deps { add(Dependency("/g")) } } } }),
      ModuleNode("/e", module { variants { "main" { deps { add(Dependency("/d")) } } } }),
      ModuleNode("/f", module { variants { "main" { deps { add(Dependency("/e")) } } } }),
      ModuleNode("/g", module { variants { "main" { deps { add(Dependency("/f")) } } } }),
      ModuleNode("/h", module { variants { "main" { deps { add(Dependency("/g")) } } } }),
      ModuleNode("/i", module { variants { "main" { deps { add(Dependency("/h")) } } } })
    )
  }
}
