"""Port Aurelia city generation library.

Pure Python (+ numpy) so it runs inside Blender's bundled interpreter as well as
in a normal Python environment with the `bpy` module.

Coordinate convention (identical to Godot):
    X = east, Y = up, Z = south.   2D plan points are (x, z) tuples.
Blender is Z-up; use `geom.g2b()` to convert when building meshes.
"""
