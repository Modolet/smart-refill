"""@file package.py
@brief Package verified binaries, source, configuration, and evidence without game libraries.
@author XiangX (Modolet) <52348766+Modolet@users.noreply.github.com>
@date 2026-10-19
"""

import hashlib
import json
import re
import shutil
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DELIVERY = ROOT / "delivery"
STAMP = (2026, 10, 19, 0, 0, 0)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def zip_files(output, files):
    with zipfile.ZipFile(output, "w") as jar:
        for name, path in sorted(files):
            info = zipfile.ZipInfo(name, STAMP)
            info.compress_type = zipfile.ZIP_DEFLATED
            jar.writestr(info, path.read_bytes())


def main():
    binary = ROOT / "build/libs/smartrefill-neoforge-1.21.1-0.1.2.jar"
    expected = binary.with_suffix(".jar.sha256").read_text().split()[0]
    if digest(binary) != expected:
        raise RuntimeError("JAR checksum mismatch")
    full = ROOT / "build/isolated/client/smartrefill-tests.log"
    bare = ROOT / "build/isolated-bare/client/smartrefill-tests.log"
    match = re.search(r"RESULT passes=(\d+) failures=0", full.read_text())
    if match is None or "FAIL " in full.read_text():
        raise RuntimeError("Full-pack regression has not passed")
    accessory = [
        line
        for line in full.read_text().splitlines()
        if line.startswith("PASS accessory_terminal_")
    ]
    if len(accessory) != 28:
        raise RuntimeError(
            "Dedicated accessory-terminal regression has not passed all 28 checks"
        )
    if bare.read_text().strip() != "OPTIONAL_ABSENCE PASS":
        raise RuntimeError("Optional-dependency absence runtime test has not passed")
    tom_only = ROOT / "build/isolated-tom-only/client/smartrefill-tests.log"
    if tom_only.read_text().strip() != "OPTIONAL_ABSENCE PASS":
        raise RuntimeError("Tom-only runtime without Accessories has not passed")
    for profile in ["isolated", "isolated-bare", "isolated-tom-only"]:
        tested = ROOT / "build" / profile / "client/mods" / binary.name
        if digest(tested) != expected:
            raise RuntimeError("Tested and delivered JARs differ")
    with zipfile.ZipFile(binary) as jar:
        if any(
            "RuntimeTests" in name or "OptionalSmoke" in name for name in jar.namelist()
        ):
            raise RuntimeError("Production JAR contains test classes")
    revision = subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
    ).strip()
    DELIVERY.mkdir(exist_ok=True)
    shutil.copy2(binary, DELIVERY / binary.name)
    shutil.copy2(ROOT / "README.md", DELIVERY / "README.md")
    shutil.copy2(ROOT / "TEST-RESULTS.md", DELIVERY / "TEST-RESULTS.md")
    shutil.copy2(full, DELIVERY / "bmc5-full-pack.log")
    shutil.copy2(bare, DELIVERY / "optional-absence.log")
    shutil.copy2(tom_only, DELIVERY / "tom-only-without-accessories.log")
    (DELIVERY / "accessory-terminals.log").write_text(
        "\n".join(accessory) + "\nRESULT passes=28 failures=0\n"
    )
    shutil.copy2(ROOT / "build/build-inputs.json", DELIVERY / "build-inputs.json")
    shutil.copy2(
        ROOT / "build/isolated/client/config/smartrefill-common.toml",
        DELIVERY / "smartrefill-common.example.toml",
    )
    manifest = {
        "source_commit": revision,
        "jar": binary.name,
        "jar_sha256": expected,
        "full_pack_automatic_checks_passed": int(match.group(1)),
        "optional_absence_runtime": "PASS",
        "tom_without_accessories_runtime": "PASS",
        "accessory_terminal_automatic_checks_passed": len(accessory),
        "installer_fixture_tests": 3,
        "real_instance_replaced": False,
        "human_ui_acceptance": False,
        "dedicated_multiplayer_acceptance": False,
    }
    (DELIVERY / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"
    )
    rounds = []
    for index in range(1, 8):
        log = ROOT / f"build/round{index}/client/smartrefill-tests.log"
        if log.exists():
            content = log.read_text()
            result = re.search(r"RESULT passes=(\d+) failures=(\d+)", content)
            rounds.append(
                {
                    "round": index,
                    "result": result.group(0) if result else "incomplete",
                    "failures": [
                        line
                        for line in content.splitlines()
                        if line.startswith(("FAIL ", "EXCEPTION "))
                    ],
                }
            )
    (DELIVERY / "development-rounds.json").write_text(
        json.dumps(rounds, ensure_ascii=False, indent=2) + "\n"
    )
    sources = []
    for file in ROOT.rglob("*"):
        relative = file.relative_to(ROOT)
        if not file.is_file() or any(
            part
            in ["build", "delivery", "__pycache__", ".direnv", ".ruff_cache", ".git"]
            for part in relative.parts
        ):
            continue
        if file.name == ".envrc":
            continue
        sources.append(("smartrefill/" + relative.as_posix(), file))
    zip_files(DELIVERY / "smartrefill-0.1.2-sources.zip", sources)
    sums = "".join(
        f"{digest(file)}  {file.name}\n"
        for file in sorted(DELIVERY.iterdir())
        if file.is_file()
        and file.name not in ["SHA256SUMS", "smartrefill-0.1.2-delivery.zip"]
    )
    (DELIVERY / "SHA256SUMS").write_text(sums)
    bundle = DELIVERY / "smartrefill-0.1.2-delivery.zip"
    zip_files(
        bundle,
        [
            (file.name, file)
            for file in DELIVERY.iterdir()
            if file.is_file() and file != bundle
        ],
    )
    print(bundle)
    print("JAR SHA-256", expected)
    print("BUNDLE SHA-256", digest(bundle))


if __name__ == "__main__":
    main()
