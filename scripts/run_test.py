"""@file run_test.py
@brief Run only this task's disposable BMC5 client in a private X server.
@author XiangX (Modolet) <52348766+Modolet@users.noreply.github.com>
@date 2026-10-19
"""

import json
import os
import subprocess
import time
from pathlib import Path

PROFILE = os.environ.get("SMARTREFILL_TEST_PROFILE", "full")
if PROFILE not in ["full", "bare", "tom-only"]:
    raise ValueError("SMARTREFILL_TEST_PROFILE must be full, bare, or tom-only")
ROOT = Path(__file__).resolve().parents[1] / (
    "build/isolated" if PROFILE == "full" else "build/isolated-" + PROFILE
)


def main():
    display = 142
    if Path(f"/tmp/.X11-unix/X{display}").exists():
        raise SystemExit(
            "Private display is occupied; leaving existing processes alone"
        )
    env = os.environ.copy()
    env.update(
        DISPLAY=f":{display}",
        LIBGL_ALWAYS_SOFTWARE="1",
        ALSOFT_DRIVERS="null",
        __GLX_VENDOR_LIBRARY_NAME="mesa",
    )
    env.pop("WAYLAND_DISPLAY", None)
    env["LD_LIBRARY_PATH"] = (
        "/nix/store/mj9klbkhzal0vc1aazsajrydkimhc0h2-libglvnd-1.7.0/lib:/nix/store/z5wmfi7agarwjlqb456f2z2r4vsmjad4-mesa-26.2.4/lib:/run/opengl-driver/lib:"
        + env.get("LD_LIBRARY_PATH", "")
    )
    env["LIBGL_DRIVERS_PATH"] = (
        "/nix/store/z5wmfi7agarwjlqb456f2z2r4vsmjad4-mesa-26.2.4/lib/dri"
    )
    with (ROOT / "xvfb.log").open("w") as xlog, (ROOT / "console.log").open("w") as log:
        display_process = subprocess.Popen(
            [
                "Xvfb",
                f":{display}",
                "-screen",
                "0",
                "1024x640x24",
                "-nolisten",
                "tcp",
                "-ac",
            ],
            stdout=xlog,
            stderr=subprocess.STDOUT,
        )
        try:
            for _ in range(50):
                if Path(f"/tmp/.X11-unix/X{display}").exists():
                    break
                time.sleep(0.1)
            process = subprocess.Popen(
                json.loads((ROOT / "launch-args.json").read_text()),
                cwd=ROOT / "client",
                env=env,
                stdout=log,
                stderr=subprocess.STDOUT,
            )
            print("Isolated client PID", process.pid, flush=True)
            print("Exit code", process.wait(), flush=True)
        finally:
            display_process.terminate()
            display_process.wait()


if __name__ == "__main__":
    main()
