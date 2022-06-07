package com.squareup.spice.collections

import java.io.Serializable

/** MutableDeterministicMap defines a mutable map that retains iteration order. */
interface MutableDeterministicMap<K, V> : MutableMap<K, V>, DeterministicMap<K, V> {
  class ByHash<K, V>(private val backing: LinkedHashMap<K, V> = LinkedHashMap()) :
    Serializable,
    MutableDeterministicMap<K, V>,
    MutableMap<K, V> by backing {
    override fun toString(): String {
      return backing.toString()
    }

    override fun hashCode(): Int {
      return backing.hashCode()
    }

    override fun equals(other: Any?): Boolean = when (other) {
      is ByHash<*, *> -> backing == other.backing
      else -> backing == other
    }
  }
}

/** DeterministicMap defines a map that retains iteration order. */
interface DeterministicMap<K, V> : Map<K, V> {
  override fun equals(other: Any?): Boolean
  override fun toString(): String
  override fun hashCode(): Int
}

inline fun <reified K, reified V> emptyDeterministicMap(): DeterministicMap<K, V> {
  val instance by lazy<DeterministicMap<K, V>> { deterministicMapOf() }
  return instance
}

inline fun <reified K, reified V> mutableDeterministicMapOf(vararg e: Pair<K, V>) =
  sequenceOf(*e).toMap(MutableDeterministicMap.ByHash())

inline fun <reified K, reified V> deterministicMapOf(vararg e: Pair<K, V>) =
  sequenceOf(*e).toDeterministicMap()

inline fun <reified K, reified V> Map<K, V>.toDeterministicMap(): DeterministicMap<K, V> =
  when (this) {
    is DeterministicMap -> this
    is LinkedHashMap -> MutableDeterministicMap.ByHash(this)
    else -> MutableDeterministicMap.ByHash<K, V>().also { it.putAll(this) }
  }

inline fun <reified K, reified V> Iterable<Pair<K, V>>.toDeterministicMap(): DeterministicMap<K, V> =
  asSequence().toDeterministicMap()

inline fun <reified K, reified V> Sequence<Pair<K, V>>.toDeterministicMap(): DeterministicMap<K, V> =
  toMap(MutableDeterministicMap.ByHash())

inline fun <reified T, reified K, reified V> Iterable<T>.associateInOrder(
  transform: (T) -> Pair<K, V>
): DeterministicMap<K, V> =
  map(transform).toDeterministicMap()

fun <TYPE, KEY, VALUE> Sequence<TYPE>.foldToDeterministicMap(
  fold: MutableMap<KEY, VALUE>.(TYPE) -> Unit
): MutableDeterministicMap<KEY, VALUE> {
  return fold(MutableDeterministicMap.ByHash()) { acc, t ->
    acc.apply { fold(t) }
  }
}
