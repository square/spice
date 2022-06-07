package com.squareup.spice.collections

import java.io.Serializable

/** MutableDeterministicSet is a MutableSet that retains an consistent iteration order. */
interface MutableDeterministicSet<V> : MutableSet<V>, DeterministicSet<V> {
  class ByHash<V>(private val backing: LinkedHashSet<V> = linkedSetOf()) :
    MutableDeterministicSet<V>,
    Serializable,
    MutableSet<V> by backing {
    override fun toString(): String = backing.toString()
    override fun hashCode(): Int = backing.hashCode()
    override fun equals(other: Any?): Boolean = when (other) {
      is ByHash<*> -> backing == other.backing
      else -> backing == other
    }
  }
}

/** DeterministicSet is a Set that retains an consistent iteration order. */
interface DeterministicSet<V> : Set<V>

inline fun <reified T> emptyDeterministicSet(): DeterministicSet<T> {
  val instance by lazy {
    deterministicSetOf<T>()
  }
  return instance
}

inline fun <reified T> deterministicSetOf(vararg v: T): DeterministicSet<T> =
  mutableDeterministicSetOf(*v)

inline fun <reified T> mutableDeterministicSetOf(vararg v: T): MutableDeterministicSet<T> =
  MutableDeterministicSet.ByHash(linkedSetOf(*v))

inline fun <reified T> Iterable<T>?.toDeterministicSet(): DeterministicSet<T> = when (this) {
  null -> MutableDeterministicSet.ByHash()
  is DeterministicSet -> this
  is LinkedHashSet -> MutableDeterministicSet.ByHash(this)
  else -> MutableDeterministicSet.ByHash<T>().also { it.addAll(this) }
}

inline fun <reified T> Sequence<T>?.toDeterministicSet(): DeterministicSet<T> = when (this) {
  null -> MutableDeterministicSet.ByHash()
  else -> asIterable().toDeterministicSet()
}

inline operator fun <reified T> DeterministicSet<T>.plus(
  other: DeterministicSet<T>
): DeterministicSet<T> = (this as Set<T> + other).toDeterministicSet()
