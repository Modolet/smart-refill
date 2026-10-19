"""@file prepare_test.py
@brief Prepare a disposable full-pack client without any real saves or companion.
@author XiangX (Modolet) <52348766+Modolet@users.noreply.github.com>
@date 2026-10-19
"""

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
PROFILE = os.environ.get("SMARTREFILL_TEST_PROFILE", "full")
if PROFILE not in ["full", "bare"]:
    raise ValueError("SMARTREFILL_TEST_PROFILE must be full or bare")
DEST = ROOT / ("build/isolated-bare" if PROFILE == "bare" else "build/isolated")


def main():
    client = DEST / "client"
    if (client / "saves").exists():
        raise SystemExit(
            "Isolated world already exists; preserve it and use a fresh test directory"
        )
    (client / "mods").mkdir(parents=True, exist_ok=True)
    excluded = []
    for jar in (INSTANCE / "mods").glob("*.jar"):
        if PROFILE == "bare":
            excluded.append(jar.name)
            continue
        if any(
            s in jar.name.lower()
            for s in ["stackrefill-", "numen-", "debugbridge", "crashassistant-"]
        ):
            excluded.append(jar.name)
            continue
        shutil.copy2(jar, client / "mods" / jar.name)
    for name in [
        "config",
        "defaultconfigs",
        "kubejs",
        "global_packs",
        "paxi",
        "resourcepacks",
        "natives",
        "vaultpatcher",
    ]:
        if PROFILE == "bare" and name != "natives":
            continue
        if (INSTANCE / name).is_dir():
            shutil.copytree(INSTANCE / name, client / name, dirs_exist_ok=True)
    # Never copy accounts, launcher credentials, server lists, or saves.
    (client / "options.txt").write_text(
        "tutorialStep:none\njoinedFirstServer:true\nrenderDistance:2\nsimulationDistance:5\nmaxFps:30\nfullscreen:false\nenableVsync:false\n"
    )
    memory = client / "config/memorysettings.json"
    if memory.exists():
        config = json.loads(memory.read_text())
        config["disableWarnings"]["disableWarnings"] = True
        memory.write_text(json.dumps(config, indent=2))
    shutil.copy2(
        ROOT / "build/libs/smartrefill-neoforge-1.21.1-0.1.1.jar", client / "mods"
    )
    classes = ROOT / (
        "build/bare-test-classes" if PROFILE == "bare" else "build/test-classes"
    )
    classes.mkdir(exist_ok=True)
    cp = (ROOT / "build/classpath.txt").read_text() + ":" + str(ROOT / "build/classes")
    subprocess.run(
        [
            "javac",
            "--release",
            "21",
            "-encoding",
            "UTF-8",
            "-proc:none",
            "-cp",
            cp,
            "-d",
            str(classes),
            *map(str, (ROOT / "src/testcommon/java").rglob("*.java")),
            *map(
                str,
                (
                    ROOT
                    / ("src/baretest/java" if PROFILE == "bare" else "src/test/java")
                ).rglob("*.java"),
            ),
        ],
        check=True,
    )
    with zipfile.ZipFile(client / "mods/smartrefill-runtime-tests.jar", "w") as jar:
        for file in classes.rglob("*.class"):
            jar.write(file, file.relative_to(classes))
        jar.writestr(
            "META-INF/neoforge.mods.toml",
            'modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="smartrefill_runtime_tests"\nversion="1"\ndisplayName="Disposable SmartRefill Runtime Tests"\n',
        )
    # Resolve the installed launcher metadata; no credentials or previous /tmp launch files.
    version = json.loads((INSTANCE / (INSTANCE.name + ".json")).read_text())
    libraries = INSTANCE.parent.parent / "libraries"

    def allowed(entry):
        if "rules" not in entry:
            return True
        result = False
        for rule in entry["rules"]:
            if rule.get("features"):
                continue
            system = rule.get("os", {})
            if system.get("name", "linux") not in ["linux", "universal"] or system.get(
                "arch", "x86_64"
            ) not in ["x86_64", "amd64"]:
                continue
            result = rule["action"] == "allow"
        return result

    paths = []
    for lib in version["libraries"]:
        artifact = lib.get("downloads", {}).get("artifact", {}).get("path")
        if allowed(lib) and artifact and (libraries / artifact).is_file():
            paths.append(str(libraries / artifact))
    paths.append(str(INSTANCE / (INSTANCE.name + ".jar")))
    replacements = {
        "natives_directory": str(client / "natives"),
        "launcher_name": "smartrefill-isolated-tests",
        "launcher_version": "1",
        "classpath": os.pathsep.join(paths),
        "classpath_separator": os.pathsep,
        "version_name": INSTANCE.name,
        "primary_jar_name": INSTANCE.name + ".jar",
        "library_directory": str(libraries),
        "auth_player_name": "RefillTest",
        "game_directory": str(client),
        "assets_root": str(INSTANCE.parent.parent / "assets"),
        "assets_index_name": version["assetIndex"]["id"],
        "auth_uuid": "516a3ae1004a47d49d6ef1a67fb59954",
        "auth_access_token": "0",
        "clientid": "0",
        "auth_xuid": "0",
        "user_type": "legacy",
        "version_type": "release",
    }

    def resolve(entries):
        result = []
        for entry in entries:
            values = (
                [entry]
                if isinstance(entry, str)
                else entry.get("value", [])
                if allowed(entry)
                else []
            )
            if isinstance(values, str):
                values = [values]
            for value in values:
                for key, replacement in replacements.items():
                    value = value.replace("${" + key + "}", replacement)
                if "${" in value:
                    raise RuntimeError("Unresolved launcher variable: " + value)
                result.append(value)
        return result

    args = [
        shutil.which("java"),
        "-Xms1G",
        "-Xmx4G",
        "-Duser.home=" + str(DEST / "home"),
        "-Dorg.lwjgl.glfw.libname=/nix/store/jl7xfv2n23n8w6grzp64sr2k39y2xgh1-glfw-minecraft-3.4/lib/libglfw.so",
        *resolve(version["arguments"]["jvm"]),
        version["mainClass"],
        *resolve(version["arguments"]["game"]),
        "--width",
        "1024",
        "--height",
        "640",
    ]
    (DEST / "home").mkdir(exist_ok=True)
    (DEST / "launch-args.json").write_text(json.dumps(args, indent=2))
    (DEST / "exclusions.json").write_text(json.dumps(excluded, indent=2))
    print(
        "Isolated instance",
        client,
        "Excluded",
        excluded if PROFILE == "full" else f"all {len(excluded)} external mods",
    )


if __name__ == "__main__":
    main()
