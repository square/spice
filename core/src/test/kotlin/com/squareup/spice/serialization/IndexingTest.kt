package com.squareup.spice.serialization

import com.google.common.truth.Truth.assertThat
import com.squareup.spice.collections.deterministicMapOf
import com.squareup.spice.model.Dependency
import com.squareup.spice.model.FindResult.GeneralFindResult
import com.squareup.spice.model.FindResult.TestFindResult
import com.squareup.spice.model.FindResult.VariantFindResult
import com.squareup.spice.model.InvalidAddress
import com.squareup.spice.model.ModuleDocument
import com.squareup.spice.model.Node.ModuleNode
import com.squareup.spice.model.Node.TestNode
import com.squareup.spice.model.TestConfiguration
import com.squareup.spice.model.VariantConfiguration
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class IndexingTest {

  private val index = PathIndex()

  private val main = mapOf("src/main/java" to setOf("main"))
  private val testConfig = TestConfiguration(srcs = listOf("src/test/java"), tools = listOf("java"))

  @Test fun smokeTest() {
    index.addNode(ModuleNode("/foo/bar/baz/a", ModuleDocument(name = "a")), main, listOf())
    index.addNode(ModuleNode("/foo/bar/baz/b", ModuleDocument(name = "b")), main, listOf())
    index.addNode(ModuleNode("/foo/c", ModuleDocument(name = "c")), main, listOf())
    index.addNode(ModuleNode("/foo/bar/d", ModuleDocument(name = "d")), main, listOf())
    val testNode = TestNode("/e:main:unit", testConfig.copy(deps = listOf(Dependency("/foo/c"))))
    index.addNode(
      ModuleNode(
        "/e",
        ModuleDocument(
          name = "e",
          variants = deterministicMapOf(
            "main" to VariantConfiguration(
              srcs = listOf("src/main/java"),
              tests = deterministicMapOf("unit" to testNode.config)
            )
          )
        )
      ),
      main,
      tests = listOf(testNode)
    )
    assertThat(index.moduleForPath("/foo/c")?.module?.name).isEqualTo("c")
    assertThat(index.moduleForPath("/e")?.module?.name).isEqualTo("e")
    assertThat(index.moduleForPath("/foo/bar/baz/b")?.module?.name).isEqualTo("b")
    assertThat(index.moduleForPath("/foo/bar/d")?.module?.name).isEqualTo("d")
    assertThat(index.moduleForPath("/foo/bar/baz/a")?.module?.name).isEqualTo("a")
    index.nodeForPath("/e").only().let { actual ->
      actual as GeneralFindResult
      assertThat(actual.node.module.name).isEqualTo("e")
    }
    index.nodeForPath("/e/src/main/java/foo.bar").only().let { actual ->
      actual as VariantFindResult
      assertThat(actual.variant).isEqualTo("main")
      assertThat(actual.node.module.name).isEqualTo("e")
    }
    index.nodeForPath("/e/src/test/java/foo.bar").only().let { actual ->
      actual as TestFindResult
      assertThat(actual.node.address).isEqualTo("/e:main:unit")
    }
  }

  @Test fun notFound() {
    assertThat(index.moduleForPath("/b")).isNull()
    index.addNode(ModuleNode("/a", ModuleDocument(name = "a")), main, listOf())
    assertThat(index.moduleForPath("/b")).isNull()
    assertThat(index.nodeForPath("/b").toList()).isEmpty()
    index.addNode(ModuleNode("/b", ModuleDocument(name = "b")), main, listOf())
    assertThat(index.moduleForPath("/b")!!.module!!.name).isEqualTo("b")
    index.nodeForPath("/b").only().let { actual ->
      actual as GeneralFindResult
      assertThat(actual.node.module.name).isEqualTo("b")
    }
  }

  @Test fun findModuleForFile() {
    index.addNode(ModuleNode("/foo/bar/baz/a", ModuleDocument(name = "a")), main, listOf())
    index.addNode(ModuleNode("/foo/bar/baz/b", ModuleDocument(name = "b")), main, listOf())
    index.addNode(ModuleNode("/foo/c", ModuleDocument(name = "c")), main, listOf())
    index.addNode(ModuleNode("/foo/bar/d", ModuleDocument(name = "d")), main, listOf())
    index.addNode(ModuleNode("/e", ModuleDocument(name = "e")), main, listOf())
    val actual = index.moduleForPath("/foo/bar/d/src/main/java/com/squareup/Something.java")
    assertThat(actual?.module?.name).isEqualTo("d")
  }

  @Test fun overspecifiedPaths() {
    index.addNode(ModuleNode("/a", ModuleDocument(name = "a")), main, listOf())
    assertThat(index.moduleForPath("/a/")?.module?.name).isEqualTo("a")
    index.addNode(ModuleNode("/b/", ModuleDocument(name = "b")), main, listOf())
    assertThat(index.moduleForPath("/b")?.module?.name).isEqualTo("b")
  }

  @Test fun addNodeCollisionWithEqualNodes() {
    val node1 = ModuleNode("/foo/bar/baz/bash", ModuleDocument(name = "bar"))
    val node2 = ModuleNode("/foo/bar/baz/bash", ModuleDocument(name = "bar"))
    index.addNode(node1, main, listOf())
    index.addNode(node2, main, listOf())
    // should not error by the end.
  }

  @Test fun addNodeCollisionWithUnequalNodes() {
    val node1 = ModuleNode("/foo/bar/baz/bash", ModuleDocument(name = "bar"))
    val node2 = ModuleNode("/foo/bar/baz/bash", ModuleDocument(name = "foo"))
    assertThrows(IllegalArgumentException::class.java) {
      index.addNode(node1, main, listOf())
      index.addNode(node2, main, listOf())
    }
  }

  @Test fun addNodeCollisionWithSubpath() {
    val node1 = ModuleNode("/foo/bar/baz/bash", ModuleDocument(name = "bar"))
    val node2 = ModuleNode("/foo/bar", ModuleDocument(name = "foo"))
    assertThrows(InvalidAddress::class.java) {
      index.addNode(node1, main, listOf())
      index.addNode(node2, main, listOf())
    }
  }

  @Test fun addNodeCollisionWithSuperpath() {
    val node1 = ModuleNode("/foo/bar", ModuleDocument(name = "foo"))
    val node2 = ModuleNode("/foo/bar/baz/bash", ModuleDocument(name = "bar"))
    assertThrows(InvalidAddress::class.java) {
      index.addNode(node1, main, listOf())
      index.addNode(node2, main, listOf())
    }
  }

  private fun <T> Sequence<T>.only(): T {
    if (this.count() != 1) throw AssertionError("Expected iterable to have 1 element: $this")
    return first()
  }
}
