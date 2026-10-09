"""
Builds a 3D model of the reference scene (public/images/demo-game.jpg):
a ruined gothic citadel on floating stone islands under a purple moon,
with a glowing red eye in a crescent-topped spire and a red-caped figure
standing on the foreground ledge.

Method (Blender-style "image trace" workflow, done in code):
  * Reference pixel coordinates (882 x 1568) are mapped to world units:
        X = (px - 441) / 100      Y = (1568 - py) / 100      Z = depth
  * Each silhouette is traced from the reference and extruded to its own
    depth, so the front view reproduces the image's outlines 1:1.
  * Stone surfaces get per-face brightness jitter for the pixel-art look.

Run:  python build_model.py          -> writes pixelbuddy_scene.glb (+ preview.png)
"""

import os
import numpy as np
import trimesh
from shapely.geometry import Polygon, Point
from shapely import affinity

HERE = os.path.dirname(os.path.abspath(__file__))
RNG = np.random.default_rng(7)
IMG_W, IMG_H = 882, 1568
SCALE = 100.0  # px per world unit


def W(px, py):
    """Reference pixel -> world (X, Y)."""
    return ((px - 441.0) / SCALE, (IMG_H - py) / SCALE)


def to_world(pts):
    return [W(x, y) for x, y in pts]


def jitter(rgb, amount=0.10):
    return np.clip(np.array(rgb) * (1 + RNG.uniform(-amount, amount)), 0, 1)


def paint(mesh, rgb, jit=0.10, alpha=1.0):
    n = len(mesh.faces)
    cols = np.zeros((n, 4))
    for i in range(n):
        c = jitter(rgb, jit) if jit else np.array(rgb)
        cols[i, :3] = c
    cols[:, 3] = alpha
    mesh.visual.face_colors = (cols * 255).astype(np.uint8)
    return mesh


def extrude_px(pts_px, z0, z1, rgb, holes_px=None, jit=0.10, alpha=1.0):
    """Trace a silhouette in pixels and extrude it from z0 to z1 (world)."""
    shell = Polygon(to_world(pts_px))
    if not shell.is_valid:
        shell = shell.buffer(0)
    holes = [to_world(h) for h in (holes_px or [])]
    poly = Polygon(shell.exterior.coords, holes) if holes else shell
    m = trimesh.creation.extrude_polygon(poly, height=z1 - z0)
    m.apply_translation([0, 0, z0])
    return paint(m, rgb, jit, alpha)


def shapely_extrude(poly, z0, z1, rgb, jit=0.0, alpha=1.0):
    m = trimesh.creation.extrude_polygon(poly, height=z1 - z0)
    m.apply_translation([0, 0, z0])
    return paint(m, rgb, jit, alpha)


def box(center, size, rgb, jit=0.10, alpha=1.0):
    m = trimesh.creation.box(extents=size)
    m.apply_translation(center)
    return paint(m, rgb, jit, alpha)


def ellipse_px(cx, cy, rx, ry, z0, z1, rgb, jit=0.0, alpha=1.0):
    c = W(cx, cy)
    p = affinity.scale(Point(c).buffer(1.0, quad_segs=24), rx / SCALE, ry / SCALE)
    return shapely_extrude(p, z0, z1, rgb, jit, alpha)


def blob_px(cx, cy, rx, ry, z, rgb, alpha=1.0):
    return ellipse_px(cx, cy, rx, ry, z, z + 0.05, rgb, jit=0.08, alpha=alpha)


# ---------------------------------------------------------------- palette
STONE_LIGHT = (0.42, 0.44, 0.58)
STONE_MID = (0.30, 0.32, 0.46)
STONE_DARK = (0.18, 0.19, 0.30)
STONE_SHADOW = (0.10, 0.10, 0.18)
SPIRE = (0.13, 0.13, 0.22)
SPIRE_EDGE = (0.22, 0.20, 0.34)
SKY_DARK = (0.12, 0.09, 0.36)
SKY_MID = (0.22, 0.16, 0.55)
CLOUD = (0.36, 0.26, 0.72)
CLOUD_LIGHT = (0.50, 0.40, 0.86)
MOON = (0.78, 0.66, 0.97)
MOON_CRATER = (0.60, 0.50, 0.85)
EYE = (1.00, 0.45, 0.12)
EYE_CORE = (1.00, 0.92, 0.55)
EYE_RING = (0.45, 0.05, 0.08)
CAPE = (0.70, 0.10, 0.14)
CAPE_DARK = (0.42, 0.05, 0.10)
SKIN = (0.90, 0.68, 0.52)
HAIR = (0.35, 0.18, 0.10)
ARMOR = (0.62, 0.64, 0.74)
BOOT = (0.12, 0.10, 0.14)
TORCH = (1.00, 0.55, 0.15)
WATER = (0.16, 0.20, 0.52)
WATER_LIGHT = (0.28, 0.32, 0.72)
WATER_FOG = (0.38, 0.40, 0.85)

parts = []

# ---------------------------------------------------------------- sky
# Backdrop wall far behind everything
parts.append(box((0, 8.0, -22.0), (24, 18, 0.2), SKY_DARK, jit=0.04))
# Clouds: stacked soft blobs (back layer = darker)
cloud_blobs = [
    (90, 110, 110, 60, CLOUD), (230, 70, 130, 55, CLOUD), (420, 40, 120, 45, CLOUD),
    (640, 60, 130, 55, CLOUD), (800, 110, 110, 60, CLOUD), (60, 260, 120, 60, CLOUD),
    (250, 240, 150, 50, CLOUD_LIGHT), (720, 250, 140, 50, CLOUD_LIGHT),
    (520, 300, 120, 40, CLOUD), (150, 420, 90, 40, CLOUD), (800, 420, 90, 40, CLOUD),
    (620, 210, 110, 40, CLOUD_LIGHT), (380, 170, 90, 30, CLOUD_LIGHT),
]
for i, (cx, cy, rx, ry, c) in enumerate(cloud_blobs):
    parts.append(blob_px(cx, cy, rx, ry, -16.0 + 0.02 * i, c))

# Moon (large disc with craters)
moon_c = W(593, 145)
moon_r = 102
parts.append(ellipse_px(593, 145, moon_r, moon_r, -15.0, -14.9, MOON))
for (cx, cy, r) in [(560, 120, 16), (620, 170, 22), (575, 180, 10), (640, 125, 9)]:
    parts.append(ellipse_px(cx, cy, r, r, -14.9, -14.85, MOON_CRATER))

# ---------------------------------------------------------------- central spire
spire_outline = [
    (340, 700), (335, 430), (352, 345), (392, 252), (428, 205), (446, 242),
    (472, 282), (500, 240), (526, 192), (552, 172), (578, 200), (600, 262),
    (616, 302), (642, 332), (672, 420), (692, 560), (702, 700),
]
parts.append(extrude_px(spire_outline, -6.2, -5.4, SPIRE, jit=0.08))
# Ribbed front buttresses (thin raised strips) for gothic detail
for x in (380, 440, 600, 660):
    top_y = 420 if x < 500 else 400
    parts.append(box((W(x, 0)[0], W(0, (top_y + 700) / 2)[1], -5.3),
                     (0.08, (700 - top_y) / SCALE, 0.12), SPIRE_EDGE, jit=0.05))
# Crescent horns (left and right) as curved swept arches
def arc_strip(cx, cy, r_out, r_in, a0, a1, z0, z1, rgb):
    outer = Point(cx, cy).buffer(r_out, quad_segs=32)
    inner = Point(cx, cy).buffer(r_in, quad_segs=32)
    ring = outer.difference(inner)
    arc = ring.intersection(Polygon(
        [(cx, cy)] + [(cx + 3 * r_out * np.cos(t), cy + 3 * r_out * np.sin(t))
                      for t in np.linspace(a0, a1, 40)]))
    geoms = list(arc.geoms) if hasattr(arc, "geoms") else [arc]
    meshes = [shapely_extrude(g, z0, z1, rgb, jit=0.05) for g in geoms if g.area > 1e-6]
    return trimesh.util.concatenate(meshes)

hx, hy = W(470, 430)
parts.append(arc_strip(hx, hy, 1.15, 0.85, np.radians(100), np.radians(170), -6.3, -5.5, SPIRE_EDGE))
parts.append(arc_strip(W(560, 350)[0], W(560, 350)[1], 1.3, 1.0, np.radians(-20), np.radians(55),
                       -6.3, -5.5, SPIRE_EDGE))

# Glowing eye: outer ring, eye body, slit pupil, bright core
ex, ey = W(540, 405)
eye_ring = trimesh.creation.annulus(r_min=0.26, r_max=0.36, height=0.12, sections=40)
eye_ring.apply_transform(trimesh.transformations.rotation_matrix(np.pi / 2, [1, 0, 0]))
eye_ring.apply_translation([ex, ey, -5.25])
parts.append(paint(eye_ring, EYE_RING, jit=0.03))
eye_body = trimesh.creation.icosphere(subdivisions=3, radius=0.26)
eye_body.apply_scale([0.82, 1.05, 0.35])
eye_body.apply_translation([ex, ey, -5.2])
parts.append(paint(eye_body, EYE, jit=0.04))
pupil = box((ex, ey, -4.92), (0.07, 0.42, 0.06), (0.10, 0.02, 0.03), jit=0.0)
parts.append(pupil)
core = trimesh.creation.icosphere(subdivisions=2, radius=0.09)
core.apply_scale([0.7, 1.0, 0.3])
core.apply_translation([ex, ey, -4.97])
parts.append(paint(core, EYE_CORE, jit=0.0))

# ---------------------------------------------------------------- back ruins & islands
# Mid-distance floating island under the spire (with dripping underside)
parts.append(extrude_px(
    [(440, 620), (560, 614), (620, 640), (600, 668), (520, 684), (440, 680)],
    -3.2, -2.6, STONE_MID, jit=0.08))
parts.append(extrude_px(
    [(340, 690), (440, 682), (440, 700), (412, 758), (386, 822), (368, 760), (340, 712)],
    -2.8, -1.6, STONE_DARK, jit=0.10))
# Waterfall streaks (translucent blue strips hanging from islands)
for x, y0, y1 in [(548, 548, 760), (600, 600, 700), (300, 720, 830), (430, 745, 860), (560, 800, 1160)]:
    m = box((W(x, 0)[0], W(0, (y0 + y1) / 2)[1], -3.0), (0.06, (y1 - y0) / SCALE, 0.04),
            WATER_LIGHT, jit=0.05, alpha=0.6)
    parts.append(m)

# Left-back ruin tower (arched windows)
left_tower = [(0, 700), (0, 340), (62, 330), (126, 345), (166, 350), (168, 500), (176, 700)]
left_holes = [[(70, 420), (96, 395), (122, 420), (122, 500), (70, 500)],
              [(18, 540), (40, 520), (56, 540), (56, 610), (18, 610)]]
parts.append(extrude_px(left_tower, -3.6, -2.9, STONE_DARK, holes_px=left_holes, jit=0.10))
# Left-back broken ruin (behind, shorter)
parts.append(extrude_px(
    [(168, 530), (182, 500), (240, 490), (280, 430), (298, 425), (300, 700), (176, 700)],
    -4.3, -3.8, STONE_DARK, jit=0.08))
# Floating arch bridge across back
parts.append(extrude_px(
    [(178, 545), (360, 548), (378, 560), (360, 690), (320, 690), (320, 580), (250, 575), (250, 690), (216, 690), (214, 580), (178, 575)],
    -3.4, -3.0, STONE_MID, jit=0.08))

# Right-back tall ruins
right_back = [(770, 650), (800, 600), (842, 520), (882, 500), (882, 1320), (830, 1310), (768, 1240), (760, 900)]
parts.append(extrude_px(right_back, -3.0, -2.2, STONE_MID, jit=0.10))
# Right spire top (small pinnacle, floating)
parts.append(extrude_px([(738, 260), (752, 225), (766, 260), (774, 310), (776, 360), (732, 360), (736, 300)],
                        -5.0, -4.6, STONE_MID, jit=0.08))
parts.append(extrude_px(
    [(676, 320), (720, 298), (800, 314), (790, 332), (730, 396), (690, 372)],
    -4.6, -4.1, STONE_DARK, jit=0.08))

# Small dark pillar silhouettes
parts.append(extrude_px([(250, 850), (330, 840), (332, 1000), (250, 1000)], -3.2, -2.9, STONE_DARK, jit=0.10))
parts.append(extrude_px([(420, 840), (500, 830), (560, 900), (580, 960), (530, 1000), (470, 940), (430, 900)],
                        -2.6, -2.0, STONE_MID, jit=0.10))
parts.append(extrude_px([(560, 790), (700, 800), (700, 970), (640, 1000), (572, 960)], -2.4, -1.8, STONE_MID, jit=0.08))

# Floating island in the lower-middle (dripping)
parts.append(extrude_px(
    [(470, 880), (580, 860), (590, 960), (540, 1000), (520, 1010), (490, 940), (470, 920)],
    -1.6, -1.2, STONE_MID, jit=0.10))
parts.append(extrude_px(
    [(600, 1000), (640, 990), (650, 1080), (605, 1070)], -2.0, -1.6, STONE_DARK, jit=0.10))

# ---------------------------------------------------------------- water / mist
parts.append(extrude_px([(430, 1120), (700, 1120), (882, 1200), (882, 1568), (430, 1568)],
                        -2.6, -2.5, WATER, jit=0.04, alpha=0.95))
parts.append(extrude_px([(470, 1150), (640, 1150), (640, 1190), (470, 1190)], -2.45, -2.42,
                        WATER_LIGHT, jit=0.04, alpha=0.7))
parts.append(extrude_px([(0, 1260), (90, 1300), (150, 1420), (0, 1568)], -2.2, -1.2, WATER, jit=0.04, alpha=0.8))

# Stone stairway on the right (stepped blocks)
for i in range(7):
    y = 1320 - i * 40
    x = 620 + i * 26
    parts.append(box((W(x + 60, 0)[0], W(0, y)[1], -1.2), (1.0 - i * 0.05, 0.38, 1.0), STONE_LIGHT, jit=0.08))

# ---------------------------------------------------------------- front ledge (main foreground platform)
ledge_outline = [(0, 1190), (120, 1160), (330, 1118), (550, 1124), (548, 1190), (522, 1262),
                 (478, 1380), (440, 1568), (0, 1568)]
ledge = extrude_px(ledge_outline, -0.4, 1.8, STONE_MID, jit=0.12)
parts.append(ledge)
# Ledge top surface (lighter, slab top)
parts.append(extrude_px([(0, 1190), (120, 1160), (330, 1118), (550, 1124), (546, 1140), (320, 1134), (120, 1176), (0, 1214)],
                        1.78, 1.86, STONE_LIGHT, jit=0.06))
# Brick courses on the front face of the ledge
for row, py in enumerate(range(1220, 1568, 46)):
    off = 0 if row % 2 == 0 else 40
    for px in range(-40 + off, 560, 80):
        if px > 540:
            continue
        cx, cy = W(px + 40, py + 20)
        parts.append(box((cx, cy, 1.85), (0.74, 0.40, 0.02), STONE_DARK, jit=0.12))
# Left-foreground pillar with arch
parts.append(extrude_px([(120, 1100), (180, 1060), (250, 1040), (300, 1060), (330, 1118), (240, 1126), (160, 1130)],
                        0.8, 1.2, STONE_DARK, jit=0.08))
parts.append(extrude_px(
    [(0, 1080), (0, 690), (72, 700), (124, 760), (140, 880), (160, 1040), (150, 1110), (0, 1140)],
    -3.2, -2.4, STONE_DARK, holes_px=[[(20, 900), (50, 840), (76, 900), (76, 1000), (20, 1000)]], jit=0.10))
parts.append(extrude_px(
    [(170, 780), (250, 700), (330, 712), (250, 745), (240, 1120), (190, 1120)],
    0.0, 0.8, STONE_MID, jit=0.10))

# Torches (warm emissive glows)
for (px, py) in [(50, 652), (135, 668), (620, 890), (682, 1000), (846, 492), (46, 720)]:
    x, y = W(px, py)
    parts.append(box((x, y, -3.0), (0.08, 0.2, 0.08), (0.35, 0.22, 0.15), jit=0.05))
    flame = trimesh.creation.icosphere(subdivisions=2, radius=0.09)
    flame.apply_translation([x, y + 0.14, -2.95])
    parts.append(paint(flame, TORCH, jit=0.0))

# ---------------------------------------------------------------- figure (red cape, on the ledge)
FEET_Y = W(0, 1126)[1]   # standing surface height
fx = W(402, 0)[0]
z_fig = 0.9
# Legs
parts.append(box((fx - 0.07, FEET_Y + 0.16, z_fig), (0.11, 0.32, 0.12), BOOT, jit=0.05))
parts.append(box((fx + 0.07, FEET_Y + 0.16, z_fig), (0.11, 0.32, 0.12), BOOT, jit=0.05))
# Torso + armor
parts.append(box((fx, FEET_Y + 0.52, z_fig), (0.30, 0.46, 0.20), ARMOR, jit=0.06))
parts.append(box((fx, FEET_Y + 0.60, z_fig + 0.11), (0.14, 0.26, 0.04), (0.30, 0.32, 0.46), jit=0.05))
# Arms
parts.append(box((fx - 0.22, FEET_Y + 0.50, z_fig), (0.09, 0.36, 0.12), ARMOR, jit=0.06))
parts.append(box((fx + 0.22, FEET_Y + 0.44, z_fig), (0.09, 0.36, 0.12), ARMOR, jit=0.06))
# Head + hair (sphere + top cap) and a horn-like tuft
head = trimesh.creation.icosphere(subdivisions=3, radius=0.14)
head.apply_translation([fx, FEET_Y + 0.92, z_fig])
parts.append(paint(head, SKIN, jit=0.03))
hair = trimesh.creation.icosphere(subdivisions=3, radius=0.15)
hair.apply_scale([1.0, 0.7, 1.0])
hair.apply_translation([fx, FEET_Y + 0.99, z_fig - 0.01])
parts.append(paint(hair, HAIR, jit=0.05))
# Cape: wide flowing sheet trailing to the left, behind the body
cape_px = [(395, 1000), (425, 1020), (432, 1080), (410, 1120), (370, 1118), (322, 1096),
           (286, 1074), (296, 1062), (338, 1058), (368, 1040), (384, 1010)]
cape_outline = Polygon(to_world(cape_px))
cape = shapely_extrude(cape_outline, z_fig - 0.13, z_fig - 0.10, CAPE, jit=0.10)
parts.append(cape)
# Cape inner fold (darker)
fold = Polygon(to_world([(380, 1030), (410, 1040), (412, 1100), (370, 1105), (330, 1088), (340, 1072)]))
parts.append(shapely_extrude(fold, z_fig - 0.10, z_fig - 0.085, CAPE_DARK, jit=0.05))
# Sword hilt hanging at the side
parts.append(box((fx + 0.27, FEET_Y + 0.25, z_fig + 0.1), (0.04, 0.45, 0.04), ARMOR, jit=0.05))

# ---------------------------------------------------------------- assemble + export
scene_mesh = trimesh.util.concatenate(parts)
scene_mesh.update_faces(scene_mesh.nondegenerate_faces())
scene_mesh.remove_unreferenced_vertices()
# Recenter so the ledge sits near origin for easy viewing
bounds = scene_mesh.bounds
print("bounds min", bounds[0].round(3), "max", bounds[1].round(3))
print("triangles", len(scene_mesh.faces))

out_glb = os.path.join(HERE, "pixelbuddy_scene.glb")
scene = trimesh.Scene(scene_mesh)
scene.export(out_glb)
print("wrote", out_glb, os.path.getsize(out_glb), "bytes")

# Front-view preview (orthographic, same framing as the reference) to check the trace
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.collections import PolyCollection

tris = scene_mesh.triangles
cols = scene_mesh.visual.face_colors[:, :3] / 255.0
depth = tris[:, :, 2].mean(axis=1)
order = np.argsort(depth)  # back to front
proj = tris[:, :, :2]
fig, ax = plt.subplots(figsize=(IMG_W / 150, IMG_H / 150), dpi=150)
ax.set_facecolor((0.05, 0.05, 0.12))
ax.add_collection(PolyCollection(proj[order], facecolors=cols[order], edgecolors="none"))
ax.set_xlim(-4.41, 4.41)
ax.set_ylim(0, IMG_H / SCALE)
ax.set_aspect("equal")
ax.axis("off")
plt.subplots_adjust(0, 0, 1, 1)
fig.savefig(os.path.join(HERE, "preview.png"), facecolor=fig.get_facecolor())
print("wrote preview.png")
