#!/usr/bin/env python3
"""Verify a Frostguard jpackage app image produced on macOS."""

from __future__ import annotations

import argparse
import os
import subprocess
from pathlib import Path


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("image", type=Path)
    parser.add_argument("--product-name", required=True)
    parser.add_argument("--expected-arch", choices=("arm64", "x86_64"), required=True)
    args = parser.parse_args()

    image = args.image.resolve()
    launcher = image / "Contents" / "MacOS" / args.product_name
    app_dir = image / "Contents" / "app"
    runtime_java = image / "Contents" / "runtime" / "Contents" / "Home" / "bin" / "java"
    adb = app_dir / "lib" / "adb" / "adb"
    launcher_config = app_dir / f"{args.product_name}.cfg"

    require(image.is_dir(), f"App image is missing: {image}")
    for executable in (launcher, runtime_java, adb):
        require(executable.is_file(), f"Required executable is missing: {executable}")
        require(os.access(executable, os.X_OK), f"File is not executable: {executable}")

    require(any(app_dir.glob("frostguard-desktop-*.jar")), "Desktop jar is missing")
    require((app_dir / "lib" / "tesseract" / "eng.traineddata").is_file(),
            "English OCR data is missing")
    require(launcher_config.is_file(), "jpackage launcher configuration is missing")
    config = launcher_config.read_text(encoding="utf-8")
    require("-Dfrostguard.update.pullRequestBuild=true" in config,
            "Test build must not participate in automatic updates")

    for executable in (launcher, runtime_java, adb):
        description = subprocess.check_output(("file", str(executable)), text=True)
        require(args.expected_arch in description,
                f"Wrong architecture for {executable}: {description.strip()}")

    print(f"Verified {image.name} ({args.expected_arch})")


if __name__ == "__main__":
    main()
