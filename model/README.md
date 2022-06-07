# Spice Model

The raw data model used by spice. This consists of data classes and very thin conveniences used
for manipulating those, or encoding/validating behavioral semantics of the spice model.

This should be free of any dependencies other than optional annotations, which are only needed
in apps/libraries which will process the annotations themselves (e.g. serialization formatting)
