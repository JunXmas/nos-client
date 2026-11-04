"""argv for Fabric 1.21.11 from Nostalgia's installed version JSONs (offline account), as JSON.

    python3 checks/fabric_argv.py <game dir> [extra game args...]
Env: NOSTALGIA (data dir), NOS_JVM (extra JVM flags, space separated).
"""
import json
import os
import sys

N = os.environ.get("NOSTALGIA", os.path.expanduser("~/.local/share/nostalgia"))
FABRIC = "fabric-loader-0.19.5-1.21.11"


def allowed(rules):
    ok = not rules
    for rule in rules or []:
        if "features" in rule:
            continue
        if rule.get("os", {}).get("name", "linux") == "linux":
            ok = rule["action"] == "allow"
    return ok


def maven_path(name):
    group, artifact, version = name.split(":")[:3]
    return f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.jar"


def classpath(fabric, vanilla):
    paths = [f"{N}/libraries/{maven_path(lib['name'])}" for lib in fabric["libraries"]]
    for lib in vanilla["libraries"]:
        artifact = lib.get("downloads", {}).get("artifact")
        if artifact and allowed(lib.get("rules")):
            paths.append(f"{N}/libraries/{artifact['path']}")
    paths.append(f"{N}/versions/1.21.11/1.21.11.jar")
    missing = [p for p in paths if not os.path.exists(p)]
    assert not missing, missing[:3]
    return ":".join(paths)


def main():
    game_dir = sys.argv[1]
    fabric = json.load(open(f"{N}/versions/{FABRIC}/{FABRIC}.json"))
    vanilla = json.load(open(f"{N}/versions/1.21.11/1.21.11.json"))
    jvm = [*fabric["arguments"]["jvm"], *os.environ.get("NOS_JVM", "").split()]
    print(json.dumps([
        f"{N}/runtime/java-runtime-delta/linux/bin/java", "-Xmx3G", *jvm,
        "-cp", classpath(fabric, vanilla), fabric["mainClass"],
        "--username", "Probe", "--version", "1.21.11", "--gameDir", game_dir,
        "--assetsDir", f"{N}/assets", "--assetIndex", vanilla["assetIndex"]["id"],
        "--uuid", "3f0ca0c89f163b8792fbb626b9a1bb99", "--accessToken", "0",
        "--userType", "legacy", "--width", "1280", "--height", "720", *sys.argv[2:],
    ]))


if __name__ == "__main__":
    main()
