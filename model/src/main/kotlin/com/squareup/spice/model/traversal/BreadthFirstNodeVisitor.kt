package com.squareup.spice.model.traversal

import com.squareup.spice.model.Edge
import com.squareup.spice.model.Node
import com.squareup.spice.model.Slice
import java.util.Collections
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap

/**
 * A visiter helper supertype which implements a breadth-first traversal of whatever nodes are
 * returned from the [more] lambda. This is not a parallel or recursive traversal, instead using a
 * simple to-process-queue/seen-set to loop through the nodes.
 */
open class BreadthFirstNodeVisitor(
  protected val more: (Slice, Node) -> Sequence<Edge>,
  protected val moreError: (slice: Slice, address: String, error: Throwable) -> Sequence<String> =
    { _, _, t -> throw t },
  protected val nodeError: (slice: Slice, address: String, error: Throwable) -> Node? =
    { _, _, t -> throw t },
  protected val on: (Node) -> Unit
) : NodeVisitor {
  override fun visit(slice: Slice, vararg roots: Node) {
    val rootSet = roots.map { it.address }.toSet()
    val seen = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    val queue = LinkedList<String>().apply { addAll(rootSet) }
    while (queue.isNotEmpty()) {
      val current = queue.pollFirst()
      if (current in seen) continue
      else seen.add(current)
      val currentNode = try {
        slice.nodeAt(current) // may perform a load
      } catch (t: Throwable) {
        nodeError(slice, current, t)
      } ?: continue
      on.invoke(currentNode)
      val deps = try {
        more.invoke(slice, currentNode).map { it.target }
      } catch (t: Throwable) {
        moreError.invoke(slice, currentNode.address, t)
      }
      deps.forEach { queue.addLast(it) }
    }
  }
}
