package com.squareup.spice.model.validation

import com.squareup.spice.model.Node
import com.squareup.spice.model.Slice
import com.squareup.spice.model.Workspace

val STANDARD_VALIDATORS: List<Validator> = listOf(
  DependencyCycleValidator,
  GraphCompletenessValidator,
  TestLeafValidator
)

/**
 * A validator which can be applied to slices, validating nodes or graphs of nodes.
 */
interface Validator {
  fun validate(workspace: Workspace, slice: Slice, vararg roots: Node)
}
