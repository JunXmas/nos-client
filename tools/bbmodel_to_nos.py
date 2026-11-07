"""Blockbench `.bbmodel` (Generic Model) → JSON rig Nos dùng (`CosmeticMesh`, format 1).

    python3 tools/bbmodel_to_nos.py <file.bbmodel> <slot> <namespace:textures/...png> <out.json> [out.png]

Quy ước khi vẽ trong Blockbench: đơn vị pixel (16 = một block), gốc toạ độ ở chân nhân vật, Y lên,
mặt nhân vật nhìn về -Z (như skin Minecraft). Mỗi group là một xương; khối nằm trong group xoay
theo xương đó. Chỉ một texture. Hoạt ảnh xoay của group (animation đầu tiên, nếu có) được lấy mẫu
60 hình/giây; vị trí/scale keyframe bị bỏ qua.
Ghi kèm `out.png` (texture nhúng trong file) nếu có đường dẫn.
"""

from __future__ import annotations

import base64
import json
import math
import sys
from pathlib import Path

FPS = 60
MAX_NODES = 128
FACES = {  # mặt → 4 góc (chỉ số trong 8 đỉnh hộp) theo thứ tự UV: trái-trên, phải-trên, phải-dưới, trái-dưới
    "north": ((1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)),
    "south": ((0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)),
    "east": ((1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)),
    "west": ((0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)),
    "up": ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)),
    "down": ((0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)),
}
NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def quat_euler(degrees: list[float]) -> tuple[float, float, float, float]:
    """Euler Blockbench (độ, thứ tự ZYX như Blockbench áp) → quaternion (x, y, z, w)."""
    x, y, z = (math.radians(d) / 2 for d in degrees)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    return (
        sx * cy * cz - cx * sy * sz,
        cx * sy * cz + sx * cy * sz,
        cx * cy * sz - sx * sy * cz,
        cx * cy * cz + sx * sy * sz,
    )


def rotate(q: tuple[float, float, float, float], v: tuple[float, float, float]) -> tuple[float, float, float]:
    qx, qy, qz, qw = q
    vx, vy, vz = v
    tx, ty, tz = 2 * (qy * vz - qz * vy), 2 * (qz * vx - qx * vz), 2 * (qx * vy - qy * vx)
    return (
        vx + qw * tx + (qy * tz - qz * ty),
        vy + qw * ty + (qz * tx - qx * tz),
        vz + qw * tz + (qx * ty - qy * tx),
    )


def box_vertices(cube: dict, pivot: list[float], width: int, height: int) -> list[float]:
    """Hai tam giác mỗi mặt, mỗi tam giác lặp đỉnh cuối thành tứ giác suy biến như rig GLB cũ."""
    lo, hi = cube["from"], cube["to"]
    inflate = cube.get("inflate", 0)
    lo = [a - inflate for a in lo]
    hi = [b + inflate for b in hi]
    origin = cube.get("origin", [0, 0, 0])
    spin = quat_euler(cube.get("rotation", [0, 0, 0]))
    out: list[float] = []
    for face, corners in FACES.items():
        data = cube.get("faces", {}).get(face)
        if not data or data.get("texture") is None:
            continue
        u0, v0, u1, v1 = data["uv"]
        uv = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
        turns = int(data.get("rotation", 0)) // 90 % 4
        uv = uv[-turns:] + uv[:-turns] if turns else uv
        points = []
        for (ix, iy, iz), (u, v) in zip(corners, uv):
            p = (hi[0] if ix else lo[0], hi[1] if iy else lo[1], hi[2] if iz else lo[2])
            p = rotate(spin, tuple(a - o for a, o in zip(p, origin)))
            p = tuple((a + o - c) / 16 for a, o, c in zip(p, origin, pivot))
            points.append((p, (u / width, v / height)))
        normal = rotate(spin, NORMALS[face])
        for a, b, c in ((0, 1, 2), (0, 2, 3)):
            for p, t in (points[a], points[b], points[c], points[c]):
                out += [*p, *t, *normal]
    return out


def sample(keyframes: list[dict], time: float) -> list[float]:
    frames = sorted(keyframes, key=lambda k: k["time"])
    values = [[float(frame["data_points"][0][axis]) for axis in "xyz"] for frame in frames]
    if time <= frames[0]["time"]:
        return values[0]
    for i in range(1, len(frames)):
        if time <= frames[i]["time"]:
            span = frames[i]["time"] - frames[i - 1]["time"]
            k = (time - frames[i - 1]["time"]) / span if span else 1
            return [a + (b - a) * k for a, b in zip(values[i - 1], values[i])]
    return values[-1]


def convert(model: dict, slot: str, texture: str) -> dict:
    width = model.get("resolution", {}).get("width", 16)
    height = model.get("resolution", {}).get("height", 16)
    cubes = {e["uuid"]: e for e in model["elements"] if e.get("type", "cube") == "cube"}
    animation = (model.get("animations") or [None])[0]
    duration = float(animation["length"]) if animation and animation.get("length") else 1.0
    nodes: list[dict] = []

    def visit(entry: dict | str, parent: int, parent_pivot: list[float]) -> None:
        if isinstance(entry, str):
            return
        pivot = entry.get("origin", [0, 0, 0])
        rest = entry.get("rotation", [0, 0, 0])
        vertices: list[float] = []
        for child in entry.get("children", []):
            if isinstance(child, str) and child in cubes and cubes[child].get("export", True):
                vertices += box_vertices(cubes[child], pivot, width, height)
        node = {
            "name": entry.get("name", "bone"),
            "parent": parent,
            "translation": [(p - q) / 16 for p, q in zip(pivot, parent_pivot)],
            "rotation": list(quat_euler(rest)),
            "vertices": [round(v, 7) for v in vertices],
        }
        animator = (animation or {}).get("animators", {}).get(entry.get("uuid", ""))
        rotations = [k for k in (animator or {}).get("keyframes", []) if k.get("channel") == "rotation"]
        if rotations:
            keys: list[float] = []
            for frame in range(round(duration * FPS) + 1):
                angles = [r + a for r, a in zip(rest, sample(rotations, frame / FPS))]
                keys += quat_euler(angles)
            node["keys"] = [round(k, 7) for k in keys]
        nodes.append(node)
        index = len(nodes) - 1
        for child in entry.get("children", []):
            visit(child, index, pivot)

    # Khối nằm thẳng ở gốc outliner (không trong group) thuộc xương gốc.
    visit({"name": slot, "origin": [0, 0, 0], "children": model["outliner"]}, -1, [0, 0, 0])
    if len(nodes) > MAX_NODES:
        raise SystemExit(f"quá {MAX_NODES} xương")
    if not any(n["vertices"] for n in nodes):
        raise SystemExit("model không có khối nào được xuất")
    return {"format": 1, "fps": FPS, "duration": duration, "slot": slot, "texture": texture, "nodes": nodes}


def main(argv: list[str]) -> int:
    if len(argv) not in (5, 6):
        print(__doc__)
        return 2
    model = json.loads(Path(argv[1]).read_text(encoding="utf-8"))
    Path(argv[4]).write_text(json.dumps(convert(model, argv[2], argv[3]), separators=(",", ":")), encoding="utf-8")
    if len(argv) == 6:
        source = model["textures"][0]["source"]
        if not source.startswith("data:image/png;base64,"):
            raise SystemExit("texture phải nhúng PNG trong file .bbmodel")
        Path(argv[5]).write_bytes(base64.b64decode(source.split(",", 1)[1]))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
