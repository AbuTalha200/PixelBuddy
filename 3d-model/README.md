# PixelBuddy reference scene – 3D model

Traced 3D reconstruction of `public/images/demo-game.jpg`.

- `build_model.py` – generator (Python + trimesh + shapely). Reference pixels map to
  world units at 100 px = 1 unit: `X = (px-441)/100`, `Y = (1568-py)/100`.
- `pixelbuddy_scene.glb` – the model (vertex-colored, ~15k triangles), opens in Blender,
  three.js, Windows 3D Viewer, etc.
- `preview.png` – orthographic front view for checking the trace against the reference.

Rebuild: `pip install trimesh shapely scipy numpy matplotlib && python build_model.py`
