"""Ảnh duyệt bộ cosmetic: Steve + từng món, chiếu chéo, có texture và che khuất theo độ sâu.

    NOS_COSMETIC_DUMP=<dir> ./gradlew test --tests '*StarterSetTest' --rerun
    python3 tools/render_cosmetics.py <dir>/baked.json <skin.png> <out.png>

Toạ độ đã "bake" bằng chính CosmeticMesh của Nos (block, gốc ở chân, Y lên, mặt về -Z).
Đây là ảnh kiểm hình dáng/màu, không thay cho ảnh chụp trong game.
"""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / "src/main/resources/assets/nosclient/textures/cosmetics/starter"
NAMES = json.loads((ROOT / "art/cosmetics/starter/catalog.json").read_text(encoding="utf-8"))
TILE, SCALE = 300, 125
LIGHT = (0.35, 0.8, -0.5)

# Steve: (from px, to px, uv offset trong skin 64x64) — skin chuẩn, mặt nhìn -Z.
BODY = [
    ((-4, 24, -4), (4, 32, 4), (0, 0)),  # đầu
    ((-4, 12, -2), (4, 24, 2), (16, 16)),  # thân
    ((-8, 12, -2), (-4, 24, 2), (40, 16)),  # tay phải
    ((4, 12, -2), (8, 24, 2), (32, 48)),  # tay trái
    ((-4, 0, -2), (0, 12, 2), (0, 16)),  # chân phải
    ((0, 0, -2), (4, 12, 2), (16, 48)),  # chân trái
]


def view(yaw: float):
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(-24)), math.sin(math.radians(-24))

    def project(p):
        x, y, z = p
        x, z = x * cy + z * sy, -x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return x, y, z

    return project


def body_triangles(skin: Image.Image):
    """Mặt Steve: mỗi mặt chia ô pixel để giữ được texture khi vẽ tam giác phẳng màu."""
    quads = []
    for lo, hi, (u, v) in BODY:
        w, h, d = (b - a for a, b in zip(lo, hi))
        faces = {
            "north": ((u + d, v + d), w, h, lambda i, j: ((lo[0] + w - i - 1, hi[1] - j - 1, lo[2]), (1, 0, 0), (0, 1, 0)), (0, 0, -1)),
            "south": ((u + d + w + d, v + d), w, h, lambda i, j: ((lo[0] + i, hi[1] - j - 1, hi[2]), (1, 0, 0), (0, 1, 0)), (0, 0, 1)),
            "west": ((u, v + d), d, h, lambda i, j: ((lo[0], hi[1] - j - 1, lo[2] + i), (0, 0, 1), (0, 1, 0)), (-1, 0, 0)),
            "east": ((u + d + w, v + d), d, h, lambda i, j: ((hi[0], hi[1] - j - 1, hi[2] - i - 1), (0, 0, 1), (0, 1, 0)), (1, 0, 0)),
            "up": ((u + d, v), w, d, lambda i, j: ((lo[0] + i, hi[1], lo[2] + j), (1, 0, 0), (0, 0, 1)), (0, 1, 0)),
        }
        for (fu, fv), cols, rows, cell, normal in faces.values():
            for i in range(cols):
                for j in range(rows):
                    color = skin.getpixel((fu + i, fv + j))
                    if color[3] == 0:
                        continue
                    (ox, oy, oz), a, b = cell(i, j)
                    corners = [(ox + a[0] * s + b[0] * t, oy + a[1] * s + b[1] * t, oz + a[2] * s + b[2] * t) for s, t in ((0, 0), (1, 0), (1, 1), (0, 1))]
                    corners = [tuple(c / 16 for c in p) for p in corners]
                    if normal == (0, 0, -1):
                        pass
                    quads.append((corners, color[:3], normal))
    return quads


def cosmetic_triangles(vertices: list[float], texture: Image.Image):
    tris = []
    for p in range(0, len(vertices), 32):
        pts = [vertices[p + k * 8 : p + k * 8 + 3] for k in range(3)]
        uv = [vertices[p + k * 8 + 3 : p + k * 8 + 5] for k in range(3)]
        normal = vertices[p + 5 : p + 8]
        tris.append((pts, (texture, uv), normal))
    return tris


def draw(scene, yaw: float, tile: Image.Image, origin_y: float) -> None:
    """Rasterize with a per-pixel depth buffer (camera looks along +Z after projection)."""
    project = view(yaw)
    width, height = tile.size
    depth = [math.inf] * (width * height)
    pixels = tile.load()
    for points, color, normal in scene:
        flat = [project(p) for p in points]
        nx, ny, nz = project(normal)
        if nz > 0.02:
            continue
        k = 0.55 + 0.45 * max(0, -(nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]))
        textured = not isinstance(color[0], int)
        if textured:
            texture, uvs = color
            source = texture.load()
        else:
            shaded = tuple(min(255, int(c * k)) for c in color)
            uvs = [(0, 0)] * len(flat)
        screen = [(width / 2 + q[0] * SCALE, origin_y - q[1] * SCALE, q[2], uv) for q, uv in zip(flat, uvs)]
        for tri in ((screen[0], screen[1], screen[2]), (screen[0], screen[2], screen[3])) if len(screen) == 4 else ((screen[0], screen[1], screen[2]),):
            (x0, y0, z0, t0), (x1, y1, z1, t1), (x2, y2, z2, t2) = tri
            area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if abs(area) < 1e-9:
                continue
            for y in range(max(0, int(min(y0, y1, y2))), min(height, int(max(y0, y1, y2)) + 1)):
                for x in range(max(0, int(min(x0, x1, x2))), min(width, int(max(x0, x1, x2)) + 1)):
                    px, py = x + 0.5, y + 0.5
                    a = ((x1 - px) * (y2 - py) - (x2 - px) * (y1 - py)) / area
                    b = ((x2 - px) * (y0 - py) - (x0 - px) * (y2 - py)) / area
                    c = 1 - a - b
                    if min(a, b, c) < -1e-6:
                        continue
                    z = a * z0 + b * z1 + c * z2
                    if z < depth[y * width + x]:
                        if textured:
                            u = min(texture.width - 1, int((a * t0[0] + b * t1[0] + c * t2[0]) * texture.width))
                            v = min(texture.height - 1, int((a * t0[1] + b * t1[1] + c * t2[1]) * texture.height))
                            texel = source[u, v]
                            if texel[3] == 0:
                                continue
                            pixels[x, y] = tuple(min(255, int(ch * k)) for ch in texel[:3])
                        else:
                            pixels[x, y] = shaded
                        depth[y * width + x] = z


def main(argv: list[str]) -> int:
    baked = json.loads(Path(argv[1]).read_text())
    skin = Image.open(argv[2]).convert("RGBA")
    body = body_triangles(skin)
    keys = [n["key"] for n in NAMES]
    columns = 4
    sheet = Image.new("RGB", (TILE * columns, (TILE + 34) * math.ceil(len(keys) / columns)), (24, 28, 32))
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 17)
    for index, key in enumerate(keys):
        texture = Image.open(TEXTURES / f"{key}.png").convert("RGBA")
        frame = baked[key]["frames"][1]
        yaw = 215 if baked[key]["slot"] == "back" else -35
        tile = Image.new("RGB", (TILE, TILE + 34), (32, 38, 44))
        scene = body + cosmetic_triangles(frame, texture)
        draw(scene, yaw, tile, TILE * 0.97)
        name = next(n["name"] for n in NAMES if n["key"] == key)
        ImageDraw.Draw(tile).text((12, TILE + 6), name, font=font, fill=(230, 236, 240))
        sheet.paste(tile, (index % columns * TILE, index // columns * (TILE + 34)))
    sheet.save(argv[3])
    print(argv[3], sheet.size)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
