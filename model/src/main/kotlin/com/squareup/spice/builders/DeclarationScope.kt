package com.squareup.spice.builders

/** DeclarationScope for setting and properties in fluent builders. */
class DeclarationScope<K, V>(private val setProperty: (Pair<K, V?>) -> Unit) {
  fun set(vararg kv: Pair<K, V?>) {
    kv.forEach(setProperty)
  }

  fun define(k: K) {
    set(k to null)
  }
}
