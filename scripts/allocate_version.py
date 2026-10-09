"""Allocate an app-specific version without touching MyParking release tags."""
import re
import subprocess
import sys
from pathlib import Path


def next_version(tags, base):
    pattern = re.compile(r"myexercise-v" + re.escape(base) + r"\.(\d+)")
    patches = [int(match.group(1)) for tag in tags if (match := pattern.fullmatch(tag))]
    patch = max(patches, default=0) + 1
    return f"{base}.{patch}", patch + 1


if __name__ == "__main__":
    properties = dict(line.split("=", 1) for line in Path("gradle.properties").read_text().splitlines()
                      if "=" in line and not line.startswith("#"))
    tags = subprocess.check_output(["git", "tag", "--list"], text=True).splitlines()
    name, code = next_version(tags, properties["releaseVersionBase"])
    tag = f"myexercise-v{name}"
    if "--tag" in sys.argv:
        print(tag)
    else:
        print(f"name={name}\ncode={code}\ntag={tag}")
