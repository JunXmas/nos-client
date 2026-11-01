"""Every class named in a mixin string (`L…;` in target/method descriptors) must exist in the
named Minecraft jars or the JDK; a wrong one makes the injector miss, silently with require = 0.

    python3 checks/mixin_targets.py <src dir> <jar>...
Prints each unknown descriptor with its file; exits 1 if any.
"""
import re
import sys
import zipfile
from pathlib import Path

DESCRIPTOR = re.compile(r"L((?:net/minecraft|com/mojang)/[\w/$]+);")


def known_classes(jars):
    names = set()
    for jar in jars:
        with zipfile.ZipFile(jar) as archive:
            names.update(n[:-6] for n in archive.namelist() if n.endswith(".class"))
    return names


def unknown(src, classes):
    for path in sorted(Path(src).rglob("*.java")):
        for name in DESCRIPTOR.findall(path.read_text(encoding="utf-8")):
            if name not in classes:
                yield path, name


if __name__ == "__main__":
    classes = known_classes(sys.argv[2:])
    assert "net/minecraft/client/Camera" in classes, "jars are not the named Minecraft jars"
    missing = list(unknown(sys.argv[1], classes))
    for path, name in missing:
        print(f"{path}: L{name};")
    sys.exit(1 if missing else 0)
