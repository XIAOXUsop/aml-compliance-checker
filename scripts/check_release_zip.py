"""Validate the exact ZIP that users download from a GitHub Release."""

import argparse
import io
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


def check_zip(path: Path, version: str, since_build: str) -> list[str]:
    problems = []
    if path.name != f"aml-compliance-checker-{version}.zip":
        problems.append(f"ZIP name does not match {version}: {path.name}")
    try:
        with zipfile.ZipFile(path) as archive:
            if archive.testzip() is not None:
                problems.append("ZIP contains a corrupt member")
            jars = [name for name in archive.namelist() if name.endswith(".jar")]
            if jars != [f"aml-compliance-checker/lib/aml-compliance-checker-{version}.jar"]:
                problems.append(f"unexpected plugin JAR entries: {jars}")
                return problems
            with zipfile.ZipFile(io.BytesIO(archive.read(jars[0]))) as jar:
                if jar.testzip() is not None:
                    problems.append("plugin JAR contains a corrupt member")
                plugin = ET.fromstring(jar.read("META-INF/plugin.xml"))
    except (OSError, KeyError, ValueError, ET.ParseError, zipfile.BadZipFile) as error:
        return problems + [f"cannot inspect plugin ZIP: {error}"]

    if plugin.findtext("version") != version:
        problems.append(f"plugin.xml version is {plugin.findtext('version')!r}, expected {version}")
    idea = plugin.find("idea-version")
    actual_since = idea.get("since-build") if idea is not None else None
    if actual_since != since_build:
        problems.append(f"IDE since-build is {actual_since!r}, expected {since_build}")
    return problems


def contains_commit(commit: str) -> bool:
    return subprocess.run(
        ["git", "-c", "safe.directory=*",
         "merge-base", "--is-ancestor", commit, "HEAD"],
        check=False,
        capture_output=True,
    ).returncode == 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--zip", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--since-build", default="252")
    parser.add_argument("--required-commit", default="ed85689")
    args = parser.parse_args()
    problems = check_zip(args.zip, args.version, args.since_build)
    if not contains_commit(args.required_commit):
        problems.append(f"release source does not contain required fix {args.required_commit}")
    if problems:
        print("\n".join(problems), file=sys.stderr)
        raise SystemExit(1)
    print(f"Release ZIP verified: {args.zip.name}; plugin {args.version}; IDE {args.since_build}+")
