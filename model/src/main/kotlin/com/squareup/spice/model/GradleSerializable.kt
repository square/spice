package com.squareup.spice.model

import java.io.IOException
import java.io.ObjectOutputStream
import java.io.Serializable

/**
 * GradleSerializable is a marker interface to satisfy the requirements of @Input annotations.
 *
 * While Spice does not have any planned uses of serialization, Gradle appears to. Implementors of
 * this interface should follow the guidance in the Serializable and implement a
 * @JvmStatic serialVersionUID long.
 */
interface GradleSerializable : Serializable {
  @Throws(IOException::class)
  fun writeObject(stream: ObjectOutputStream) {
    stream.defaultWriteObject()
  }
}
