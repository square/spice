package com.squareup.spice.model

import com.google.common.truth.Truth.assertThat
import com.squareup.spice.collections.deterministicMapOf
import com.squareup.spice.collections.deterministicSetOf
import org.junit.jupiter.api.Test

class MergeTest {

  @Test fun merge() {
    val definitions = ModuleDocument(
      namespace = "some.namespace",
      tools = deterministicSetOf("tool1"),
      variants = deterministicMapOf(
        "debug" to VariantConfiguration(
          srcs = listOf("src/main", "src/debug"),
          deps = listOf(Dependency("/bar")), // Don't do this for real. Default deps are weird.
          tests = deterministicMapOf(
            "unit" to TestConfiguration(
              listOf("src/test"),
              deps = listOf(
                Dependency("maven://junit"),
                Dependency("maven://com.google.truth")
              )
            )
          )
        )
      )
    )
    val module = ModuleDocument(
      name = "foo-module",
      tools = deterministicSetOf("tool2"),
      variants = deterministicMapOf(
        "debug" to VariantConfiguration(
          deps = listOf(Dependency("/baz")),
          tests = deterministicMapOf(
            "unit" to TestConfiguration(
              listOf("src/test", "src/test3"),
              deps = listOf(
                Dependency("/baz"),
                Dependency("/foo-module")
              )
            ),
            "unit2" to TestConfiguration(
              listOf("src/test2"),
              deps = listOf(
                Dependency("/baz"),
                Dependency("/foo-module")
              )
            )
          )
        )
      )
    )
    val merged = module.mergeDefaults(definitions)
    with(merged) {
      assertThat(name).isEqualTo("foo-module")
      assertThat(namespace).isEqualTo("some.namespace")
      assertThat(tools).isEqualTo(deterministicSetOf("tool1", "tool2"))
      assertThat(variants.keys).isEqualTo(setOf("debug"))
      val debug = requireNotNull(variants["debug"])
      assertThat(debug.deps.map { it.target }).containsExactly("/bar", "/baz")
      assertThat(debug.tests).hasSize(2)
      val unit = requireNotNull(debug.tests["unit"])
      assertThat(unit.srcs).containsExactly("src/test", "src/test3")
      assertThat(unit.deps.map { it.target }).containsExactly(
        "maven://junit",
        "maven://com.google.truth",
        "/baz",
        "/foo-module"
      )
    }
  }
}
