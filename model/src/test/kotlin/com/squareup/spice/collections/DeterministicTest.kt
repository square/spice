package com.squareup.spice.collections

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DeterministicTest {
  @Test
  fun equals() {
    assertThat(
      deterministicMapOf(
        "a" to "b",
        "c" to "d"
      )
    ).isEqualTo(deterministicMapOf("c" to "d", "a" to "b"))

    assertThat(deterministicSetOf("b", "a")).isEqualTo(deterministicSetOf("b", "a"))
  }

  @Test fun hash() {
    assertThat(
      deterministicMapOf(
        "a" to "b",
        "c" to "d"
      ).hashCode()
    ).isEqualTo(deterministicMapOf("a" to "b", "c" to "d").hashCode())

    assertThat(deterministicSetOf("b", "a").hashCode())
      .isEqualTo(deterministicSetOf("b", "a").hashCode())
  }

  @Test fun emptyEquals() {
    assertThat(emptyDeterministicMap<Any, Any>()).isEqualTo(deterministicMapOf<Any, Any>())
    assertThat(emptyDeterministicSet<Any>()).isEqualTo(deterministicSetOf<Any>())
  }

  @Test fun deterministic() {
    assertThat(hashMapOf("z" to "y", "a" to "b", "m" to "n").toList())
      .isNotEqualTo(listOf("z" to "y", "a" to "b", "m" to "n"))

    assertThat(deterministicMapOf("z" to "y", "a" to "b", "m" to "n").toList())
      .isEqualTo(listOf("z" to "y", "a" to "b", "m" to "n"))

    assertThat(deterministicSetOf("z" to "y", "a" to "b", "m" to "n").toList())
      .isEqualTo(listOf("z" to "y", "a" to "b", "m" to "n"))
  }
}
