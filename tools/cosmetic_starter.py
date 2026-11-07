"""Bộ cosmetic khởi đầu: sinh file Blockbench nguồn rồi chuyển sang rig Nos.

    python3 tools/cosmetic_starter.py

Ghi `art/cosmetics/starter/<món>.bbmodel` (mở bằng Blockbench để sửa tiếp), rồi
`assets/nosclient/cosmetics/starter/<món>.json` + `textures/cosmetics/starter/<món>.png`.
Toạ độ pixel, gốc ở chân, Y lên, mặt nhân vật nhìn về -Z. Không có áo choàng (Mojang cấm bán).
"""

from __future__ import annotations

import base64
import io
import json
import random
import sys
import zlib
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
from bbmodel_to_nos import convert  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "art/cosmetics/starter"
ASSETS = ROOT / "src/main/resources/assets/nosclient"
CELL = 16  # mỗi vật liệu một ô 16x16 trong texture 64x64
SIDE = 64


def shade(rgb: tuple[int, int, int], k: float) -> tuple[int, int, int, int]:
    return (*(max(0, min(255, round(c * k))) for c in rgb), 255)


def paint(pattern: str, base: tuple[int, int, int], accent: tuple[int, int, int], seed: int) -> Image.Image:
    rng = random.Random(seed)
    cell = Image.new("RGBA", (CELL, CELL))
    for y in range(CELL):
        for x in range(CELL):
            k = 1 + rng.uniform(-0.07, 0.07)
            color = base
            if pattern == "stripes" and y % 6 in (4, 5):
                color = accent
            elif pattern == "lens":
                k = 1.25 - y / CELL * 0.6
                if (x, y) in ((2, 2), (3, 2), (2, 3)):
                    color, k = accent, 1
            elif pattern == "edge" and (y < 2 or x < 1 or x > CELL - 2):
                color = accent
            elif pattern == "gem":
                k = 1.3 if x + y < 8 else 0.85
            cell.putpixel((x, y), shade(color, k))
    return cell


class Item:
    def __init__(self, key: str, name: str, slot: str, materials: dict[str, tuple]) -> None:
        self.key, self.name, self.slot = key, name, slot
        self.cells = {m: i for i, m in enumerate(materials)}
        self.texture = Image.new("RGBA", (SIDE, SIDE))
        for i, (pattern, base, accent) in enumerate(materials.values()):
            self.texture.paste(paint(pattern, base, accent, zlib.crc32(f"{key}{i}".encode())), (i % 4 * CELL, i // 4 * CELL))
        self.elements: list[dict] = []
        self.groups: list[dict] = []
        self.animators: dict[str, dict] = {}
        self.length = 0.0

    def box(self, material: str, lo: list[float], hi: list[float], group: dict | None = None, skip: tuple[str, ...] = ()) -> None:
        cell = self.cells[material]
        u0, v0 = cell % 4 * CELL, cell // 4 * CELL
        size = [b - a for a, b in zip(lo, hi)]
        dims = {"north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2)}
        faces = {}
        for face, (a, b) in dims.items():
            w, h = min(CELL, max(1, round(size[a]))), min(CELL, max(1, round(size[b])))
            faces[face] = {"uv": [u0, v0, u0 + w, v0 + h], "texture": None if face in skip else 0}
        uuid = f"{self.key}-c{len(self.elements)}"
        self.elements.append({"uuid": uuid, "type": "cube", "name": material, "from": lo, "to": hi, "origin": [0, 0, 0], "faces": faces})
        (group["children"] if group else self.groups).append(uuid)

    def group(self, name: str, origin: list[float], rotation: list[float] | None = None) -> dict:
        group = {"uuid": f"{self.key}-g{len(self.animators) + len(self.groups)}", "name": name, "origin": origin, "rotation": rotation or [0, 0, 0], "children": []}
        self.groups.append(group)
        return group

    def spin(self, group: dict, length: float, frames: list[tuple[float, list[float]]]) -> None:
        self.length = length
        self.animators[group["uuid"]] = {"keyframes": [{"channel": "rotation", "time": t, "data_points": [dict(zip("xyz", r))]} for t, r in frames]}

    def bbmodel(self) -> dict:
        buffer = io.BytesIO()
        self.texture.save(buffer, "PNG")
        model = {
            "meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
            "name": self.key,
            "resolution": {"width": SIDE, "height": SIDE},
            "elements": self.elements,
            "outliner": self.groups,
            "textures": [{"name": f"{self.key}.png", "source": "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode()}],
        }
        if self.animators:
            model["animations"] = [{"name": "idle", "loop": "loop", "length": self.length, "animators": self.animators}]
        return model


RED, CREAM, BLACK, GOLD = (176, 46, 52), (236, 228, 210), (30, 30, 36), (232, 182, 58)


def starter() -> list[Item]:
    items = []

    cap = Item("cap", "Mũ lưỡi trai", "head", {"crown": ("noise", RED, RED), "brim": ("noise", (140, 34, 40), RED), "logo": ("noise", CREAM, CREAM)})
    cap.box("crown", [-4.6, 28.6, -4.6], [4.6, 32.9, 4.6])
    cap.box("brim", [-4.6, 28.6, -8.4], [4.6, 29.3, -4.6])
    cap.box("logo", [-1.6, 29.8, -4.75], [1.6, 31.6, -4.6])
    cap.box("logo", [-0.6, 32.9, -0.6], [0.6, 33.5, 0.6])
    items.append(cap)

    shades = Item("shades", "Kính râm pixel", "face", {"frame": ("noise", BLACK, BLACK), "lens": ("lens", (70, 60, 140), (230, 240, 255))})
    for side in (-1, 1):
        x0, x1 = sorted((side * 0.6, side * 3.9))
        shades.box("lens", [x0, 26.6, -4.85], [x1, 28.9, -4.55])
        shades.box("frame", [x0 - 0.15, 28.9, -4.9], [x1 + 0.15, 29.3, -4.5])
        tx0, tx1 = sorted((side * 4.4, side * 4.65))
        shades.box("frame", [tx0, 28.5, -4.6], [tx1, 29.1, 0])
    shades.box("frame", [-0.6, 28.2, -4.9], [0.6, 28.7, -4.5])
    items.append(shades)

    ears = Item("fox_ears", "Tai cáo", "head", {"fur": ("noise", (222, 120, 46), (222, 120, 46)), "inner": ("noise", CREAM, CREAM), "tip": ("noise", (60, 44, 40), (60, 44, 40))})
    for side in (-1, 1):
        ear = ears.group("ear_right" if side < 0 else "ear_left", [side * 2.6, 32, 0], [0, 0, side * -14])
        for (a, b), (y0, y1), material in (((0.9, 4.3), (32, 34.2), "fur"), ((1.4, 3.8), (34.2, 35.8), "fur"), ((1.9, 3.3), (35.8, 37.2), "tip")):
            x0, x1 = sorted((side * a, side * b))
            ears.box(material, [x0, y0, -0.9], [x1, y1, 0.9], ear)
        x0, x1 = sorted((side * 1.6, side * 3.6))
        ears.box("inner", [x0, 32.4, -1.05], [x1, 35.2, -0.9], ear)
        ears.spin(ear, 3.0, [(0, [0, 0, 0]), (2.6, [0, 0, 0]), (2.75, [0, 0, side * 14]), (3.0, [0, 0, 0])])
    items.append(ears)

    crown = Item("crown", "Vương miện", "head", {"gold": ("noise", GOLD, GOLD), "gem": ("gem", (200, 30, 60), (200, 30, 60)), "blue": ("gem", (40, 110, 220), (40, 110, 220))})
    w = 4.75
    crown.box("gold", [-w, 32, -w], [w, 33.6, -w + 0.5], skip=("up", "down"))
    crown.box("gold", [-w, 32, w - 0.5], [w, 33.6, w], skip=("up", "down"))
    crown.box("gold", [-w, 32, -w + 0.5], [-w + 0.5, 33.6, w - 0.5], skip=("up", "down"))
    crown.box("gold", [w - 0.5, 32, -w + 0.5], [w, 33.6, w - 0.5], skip=("up", "down"))
    for x in (-w, -0.75, w - 1.5):
        for z0, z1 in ((-w, -w + 0.5), (w - 0.5, w)):
            crown.box("gold", [x, 33.6, z0], [x + 1.5, 35.2, z1])
    for z in (-w, -0.75, w - 1.5):
        for x0, x1 in ((-w, -w + 0.5), (w - 0.5, w)):
            crown.box("gold", [x0, 33.6, z], [x1, 35.2, z + 1.5])
    crown.box("gem", [-0.7, 32.3, -w - 0.3], [0.7, 33.3, -w])
    crown.box("blue", [-3.4, 32.5, -w - 0.2], [-2.4, 33.1, -w])
    crown.box("blue", [2.4, 32.5, -w - 0.2], [3.4, 33.1, -w])
    items.append(crown)

    halo = Item("halo", "Hào quang", "head", {"glow": ("noise", (255, 236, 150), (255, 236, 150))})
    ring = halo.group("ring", [0, 35.6, 0], [6, 0, 0])
    for lo, hi in (([-3.5, 35.2, -3.5], [3.5, 35.9, -2.7]), ([-3.5, 35.2, 2.7], [3.5, 35.9, 3.5]), ([-3.5, 35.2, -2.7], [-2.7, 35.9, 2.7]), ([2.7, 35.2, -2.7], [3.5, 35.9, 2.7])):
        halo.box("glow", lo, hi, ring)
    halo.spin(ring, 4.0, [(0, [0, 0, 0]), (4.0, [0, 360, 0])])
    items.append(halo)

    scarf = Item("scarf", "Khăn len", "neck", {"wool": ("stripes", (196, 52, 52), CREAM)})
    scarf.box("wool", [-4.45, 21.4, -2.45], [4.45, 24.4, 2.45])
    scarf.box("wool", [-3.2, 14.6, -2.95], [-0.9, 21.4, -2.45])
    scarf.box("wool", [-0.9, 16.4, -2.85], [0.9, 21.4, -2.45])
    items.append(scarf)

    pack = Item("backpack", "Balo phiêu lưu", "back", {"canvas": ("edge", (58, 120, 128), (40, 86, 92)), "pocket": ("noise", (40, 92, 98), (40, 92, 98)), "strap": ("noise", (110, 74, 46), (110, 74, 46)), "buckle": ("gem", GOLD, GOLD)})
    pack.box("canvas", [-3.2, 13.4, 2.0], [3.2, 22.0, 5.0])
    pack.box("canvas", [-3.4, 19.4, 1.9], [3.4, 22.3, 5.3])
    pack.box("pocket", [-2.4, 14.2, 5.0], [2.4, 17.6, 5.8])
    pack.box("buckle", [-0.5, 18.6, 5.15], [0.5, 19.6, 5.45])
    for x0 in (-3.0, 1.8):
        pack.box("strap", [x0, 15.0, -2.35], [x0 + 1.2, 24.2, -2.05])
        pack.box("strap", [x0, 24.0, -2.35], [x0 + 1.2, 24.35, 2.0])
    items.append(pack)

    wings = Item("pixel_wings", "Cánh pixel", "back", {"feather": ("edge", (236, 242, 250), (150, 190, 236))})
    for side in (-1, 1):
        wing = wings.group("wing_right" if side < 0 else "wing_left", [side * 1.2, 20.5, 2.3])
        # Ba tầng lông, ngắn dần xuống dưới, đầu cánh vểnh lên.
        for (a, b), (y0, y1) in (((1.0, 11.0), (21.0, 24.0)), ((1.0, 13.5), (23.0, 26.5)), ((1.0, 9.0), (18.0, 21.0)), ((1.0, 6.0), (15.5, 18.0))):
            x0, x1 = sorted((side * a, side * b))
            wings.box("feather", [x0, y0, 2.3], [x1, y1, 2.8], wing)
        wings.spin(wing, 1.6, [(0, [0, side * -10, 0]), (0.8, [0, side * 28, 0]), (1.6, [0, side * -10, 0])])
    items.append(wings)
    return items


def main() -> int:
    ART.mkdir(parents=True, exist_ok=True)
    (ASSETS / "cosmetics/starter").mkdir(parents=True, exist_ok=True)
    (ASSETS / "textures/cosmetics/starter").mkdir(parents=True, exist_ok=True)
    catalog = []
    for item in starter():
        model = item.bbmodel()
        (ART / f"{item.key}.bbmodel").write_text(json.dumps(model, ensure_ascii=False, indent=1), encoding="utf-8")
        rig = convert(model, item.slot, f"nosclient:textures/cosmetics/starter/{item.key}.png")
        (ASSETS / f"cosmetics/starter/{item.key}.json").write_text(json.dumps(rig, separators=(",", ":")), encoding="utf-8")
        item.texture.save(ASSETS / f"textures/cosmetics/starter/{item.key}.png")
        catalog.append({"key": item.key, "name": item.name, "slot": item.slot})
    (ART / "catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=1), encoding="utf-8")
    print("\n".join(f"{c['key']:12} {c['slot']:5} {c['name']}" for c in catalog))
    return 0


if __name__ == "__main__":
    sys.exit(main())
