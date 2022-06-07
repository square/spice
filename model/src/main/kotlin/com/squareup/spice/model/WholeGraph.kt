package com.squareup.spice.model

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY

/**
 * Tags an API as a whole-graph operation (or containing a whole-graph operation implicitly). This
 * means that implementations of that API may have to perform expensive preparation in order to be
 * ready to return their results, and so be used advisedly in situations of performance bottlenecks.
 *
 * There is no guarantee that the operation MUST be expensive, merely that implementations may likely
 * need to perform expensive operations. The lack of this annotation also does not provide contractual
 * guarantees of cheapness. However operations without this in the spice model should be able to
 * be implemented to only do incrementally required preparatory work.
 *
 * > Note: This is a purely documentary annotation, though it could be used for IDE signalling, etc.
 */
@Target(FUNCTION, PROPERTY)
@Retention(BINARY)
annotation class WholeGraph
