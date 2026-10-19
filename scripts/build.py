"""@file build.py
@brief Reproducible offline Java 21 build against the actual installed BMC5 libraries.
@author XiangX (Modolet) <52348766+Modolet@users.noreply.github.com>
@date 2026-10-19
"""

import hashlib
import json
import os
import shutil
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INSTANCE = Path(
    os.environ.get(
        "BMC5_INSTANCE", Path.home() / ".minecraft/versions/Better MC [BMC5]"
    )
)
LIBRARIES = INSTANCE.parent.parent / "libraries"
BUILD = ROOT / "build"


def main():
    BUILD.mkdir(exist_ok=True)
    version = json.loads((INSTANCE / (INSTANCE.name + ".json")).read_text())
    paths = []
    for entry in version["libraries"]:
        artifact = entry.get("downloads", {}).get("artifact", {}).get("path")
        if artifact and (LIBRARIES / artifact).is_file():
            paths.append(LIBRARIES / artifact)
    neo = LIBRARIES / "net/neoforged/neoforge/21.1.250"
    # Patched Mojmap Minecraft classes and platform APIs; no redistribution of these inputs.
    paths = [
        neo / "neoforge-21.1.250-client.jar",
        neo / "neoforge-21.1.250-universal.jar",
    ] + paths
    paths.extend(
        (LIBRARIES / "net/minecraft/client/1.21.1-20240808.144430").glob("*-srg.jar")
    )
    paths.extend(
        (LIBRARIES / "net/minecraft/client/1.21.1-20240808.144430").glob("*-extra.jar")
    )
    paths.extend(INSTANCE.glob("mods/accessories-neoforge-*.jar"))
    paths.extend(INSTANCE.glob("mods/toms_storage-*.jar"))
    paths = list(dict.fromkeys(paths))
    inputs = {}
    # NeoForge embeds MixinExtras as jar-in-jar. javac needs its public annotation classes.
    for path in list(paths):
        if not path.is_file():
            raise FileNotFoundError(path)
        inputs[str(path)] = hashlib.sha256(path.read_bytes()).hexdigest()
        with zipfile.ZipFile(path) as jar:
            for name in jar.namelist():
                if name.endswith(".jar") and "mixinextras" in name.lower():
                    dest = BUILD / Path(name).name
                    dest.write_bytes(jar.read(name))
                    paths.append(dest)
    cp = os.pathsep.join(map(str, paths))
    (BUILD / "classpath.txt").write_text(cp)
    (BUILD / "build-inputs.json").write_text(json.dumps(inputs, indent=2) + "\n")
    classes = BUILD / "classes"
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir()
    sources = sorted((ROOT / "src/main/java").rglob("*.java"))
    args = [
        "--release",
        "21",
        "-encoding",
        "UTF-8",
        "-proc:none",
        "-cp",
        cp,
        "-d",
        str(classes),
        *map(str, sources),
    ]
    argfile = BUILD / "javac.args"
    argfile.write_text(
        "\n".join('"' + a.replace("\\", "\\\\").replace('"', '\\"') + '"' for a in args)
    )
    subprocess.run(["javac", "@" + str(argfile)], check=True)
    out = BUILD / "libs/smartrefill-neoforge-1.21.1-0.1.1.jar"
    out.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as jar:
        for base in [classes, ROOT / "src/main/resources"]:
            for file in sorted(base.rglob("*")):
                if file.is_file():
                    info = zipfile.ZipInfo(
                        file.relative_to(base).as_posix(), (2026, 10, 19, 0, 0, 0)
                    )
                    info.compress_type = zipfile.ZIP_DEFLATED
                    jar.writestr(info, file.read_bytes())
        info = zipfile.ZipInfo("LICENSE", (2026, 10, 19, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        jar.writestr(info, (ROOT / "LICENSE").read_bytes())
    sha = hashlib.sha256(out.read_bytes()).hexdigest()
    out.with_suffix(".jar.sha256").write_text(f"{sha}  {out.name}\n")
    print(out)
    print("SHA-256", sha)


if __name__ == "__main__":
    main()
