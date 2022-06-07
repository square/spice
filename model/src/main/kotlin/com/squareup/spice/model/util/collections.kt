package com.squareup.spice.model.util

// TODO: Move to a new util project for common code.

/**
 * Not a true multimap structure, but the typical use-case of a SetMultimap, usable where
 * [toMap] might be used, but where duplicate keys result in a set of values.
 */
fun <K, V> List<Pair<K, V>>.toSetMultimap(): Map<K, Set<V>> =
  linkedMapOf<K, MutableSet<V>>().also { map ->
    this.forEach { (k, v) -> map.getOrPut(k) { linkedSetOf() }.add(v) }
  }

/**
 * Inverts a set-multimap so that the values are the keys and the keys are the values
 */
fun <K, V> Map<K, Set<V>>.invert(): Map<V, Set<K>> =
  entries.flatMap { (k, v) -> v.map { value -> value to k } }.toSetMultimap()

/**
 * Not a true multimap structure, but the typical use-case of a ListMultimap, usable where
 * [toMap] might be used, but where duplicate keys result in a list of values.
 */
fun <K, V> List<Pair<K, V>>.toListMultimap(): Map<K, List<V>> =
  linkedMapOf<K, MutableList<V>>().also { map ->
    this.forEach { (k, v) -> map.getOrPut(k) { mutableListOf() }.add(v) }
  }
