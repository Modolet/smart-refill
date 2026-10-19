"""@file install.py
@brief Fail-closed replacement and rollback with backups outside mods.
@author XiangX (Modolet) <52348766+Modolet@users.noreply.github.com>
@date 2026-10-19
"""

import argparse
import hashlib
import json
import shutil
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[1]
DEFAULT = Path(Path.home() / ".minecraft/versions/Better MC [BMC5]")


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check_game_exited(instance):
    found = []
    for proc in Path("/proc").iterdir():
        if not proc.name.isdigit():
            continue
        try:
            cmd = (proc / "cmdline").read_bytes().split(b"\0")
            exe = Path(cmd[0].decode(errors="replace")).name if cmd else ""
            if "java" not in exe.lower():
                continue
            args = [c.decode(errors="replace") for c in cmd]
            try:
                cwd = (proc / "cwd").resolve(strict=True)
            except PermissionError as error:
                raise RuntimeError(
                    f"Cannot inspect working directory of Java PID {proc.name}; replacement refused"
                ) from error
            if (
                cwd == instance
                or str(instance) in args
                or any(str(instance) in arg for arg in args if arg.startswith("@"))
            ):
                found.append(proc.name)
        except (FileNotFoundError, ProcessLookupError):
            continue
        except PermissionError:
            # Other users' processes cannot own this user's game session.
            if proc.stat().st_uid == instance.stat().st_uid:
                raise RuntimeError(
                    f"Cannot inspect same-user PID {proc.name}; unable to confirm game exit"
                )
    if found:
        raise RuntimeError(
            "Game is still running; replacement refused, PID=" + ",".join(found)
        )


def install(instance, confirmed):
    jar = ROOT / "build/libs/smartrefill-neoforge-1.21.1-0.1.1.jar"
    expected = jar.with_suffix(".jar.sha256").read_text().split()[0]
    if sha(jar) != expected:
        raise RuntimeError("New JAR SHA-256 mismatch")
    check_game_exited(instance)
    old = sorted((instance / "mods").glob("stackrefill-*.jar"))
    existing = sorted((instance / "mods").glob("smartrefill-*.jar"))
    if not confirmed:
        print("DRY RUN: Back up and move outside mods:", *old, *existing)
        print("DRY RUN: Install:", jar, "SHA256", expected)
        print("Exit the game completely, then use --apply --game-exited")
        return
    backup = (
        instance
        / "smartrefill-backups"
        / datetime.now(ZoneInfo("Asia/Shanghai")).strftime("%Y%m%d-%H%M%S-%f")
    )
    backup.mkdir(parents=True, exist_ok=False)
    receipt = {
        "instance": str(instance),
        "new_jar": jar.name,
        "new_sha256": expected,
        "moved": [],
        "configs": [],
    }
    for source in (instance / "config").glob("*.toml"):
        if any(word in source.name.lower() for word in ["stackrefill", "smartrefill"]):
            target = backup / "config" / source.name
            target.parent.mkdir(exist_ok=True)
            shutil.copy2(source, target)
            receipt["configs"].append({"name": source.name, "sha256": sha(source)})
    for source in old + existing:
        receipt["moved"].append({"name": source.name, "sha256": sha(source)})
        shutil.move(str(source), backup / source.name)
    try:
        check_game_exited(instance)
        dest = instance / "mods" / jar.name
        staging = instance / (jar.name + ".staging")
        shutil.copy2(jar, staging)
        if sha(staging) != expected:
            raise RuntimeError("Copied JAR checksum mismatch")
        staging.replace(dest)
        (backup / "receipt.json").write_text(
            json.dumps(receipt, ensure_ascii=False, indent=2) + "\n"
        )
    except BaseException:
        for entry in receipt["moved"]:
            shutil.move(str(backup / entry["name"]), instance / "mods" / entry["name"])
        raise
    print("Installed; backup and receipt:", backup)


def rollback(backup):
    receipt = json.loads((backup / "receipt.json").read_text())
    instance = Path(receipt["instance"])
    check_game_exited(instance)
    new = instance / "mods" / receipt["new_jar"]
    if new.exists() and sha(new) != receipt["new_sha256"]:
        raise RuntimeError(
            "Installed JAR changed; refusing overwrite, inspect manually"
        )
    for entry in receipt["moved"]:
        if sha(backup / entry["name"]) != entry["sha256"]:
            raise RuntimeError("Backup checksum mismatch")
    if new.exists():
        shutil.move(str(new), backup / ("disabled-" + new.name))
    for entry in receipt["moved"]:
        shutil.copy2(backup / entry["name"], instance / "mods" / entry["name"])
    for entry in receipt["configs"]:
        current = instance / "config" / entry["name"]
        if current.exists():
            shutil.copy2(current, backup / ("before-rollback-" + entry["name"]))
        shutil.copy2(backup / "config" / entry["name"], current)
    print("Rolled back; backup retained:", backup)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--instance", type=Path, default=DEFAULT)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--game-exited", action="store_true")
    parser.add_argument("--rollback", type=Path)
    args = parser.parse_args()
    if args.rollback:
        if not args.apply or not args.game_exited:
            parser.error("Rollback requires --apply --game-exited")
        rollback(args.rollback.resolve())
    else:
        if args.apply and not args.game_exited:
            parser.error("Confirm game exit before replacement with --game-exited")
        install(args.instance.resolve(), args.apply)


if __name__ == "__main__":
    main()
