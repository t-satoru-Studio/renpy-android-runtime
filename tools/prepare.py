#!/usr/bin/env python3
# Copyright (C) 2026 t-satoru
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Stage the upstream Ren'Py 8.6.0 Android runtime for the Gradle build.

Downloads the official RAPT (Android packaging) and SDK archives pinned in
upstream.lock.json, checks their SHA-256, and builds build/staging/:

    renpyandroid/       Gradle library module: RAPT Java (SDL3, Ren'Py, jnius),
                        file provider resources and the prebuilt librenpython.so
    assets/private.mp3  gzip tar unpacked to the app's files dir at first start:
                        main.py, renpy/*.pyc (without common) and lib/python3.12
    assets/x-renpy/x-common/...
                        renpy/common with every path segment prefixed "x-",
                        as RAPT's make_assets_tree does
    generated-res/      private_version string read by ResourceManager
    stamp.json          what was staged, from which archives

The layout reproduces RAPT's build.py (split_renpy, make_tar, make_assets_tree,
eliminate_pycache) for a build without a bundled game. The game is loaded from
external storage at run time instead (see runtime/main_prelude.py).

Usage: python3 tools/prepare.py [--cache DIR]
"""

import argparse
import concurrent.futures
import gzip
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tarfile
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
STAGING = ROOT / "build" / "staging"
LOCK = ROOT / "upstream.lock.json"

MAIN_PRELUDE = ROOT / "runtime" / "main_prelude.py"
COMMON_OVERLAY = ROOT / "runtime" / "common"

# Where the prelude goes in renpy.py: right after its module-level imports.
PRELUDE_ANCHOR = "import warnings\n"


def log(msg):
    print(f"[prepare] {msg}", flush=True)


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def fetch(entry, cache):
    url = entry["url"]
    dest = cache / url.rsplit("/", 1)[1]

    if dest.exists() and sha256_of(dest) == entry["sha256"]:
        return dest

    log(f"downloading {url}")
    tmp = dest.with_suffix(dest.suffix + ".part")
    with urllib.request.urlopen(url) as r, open(tmp, "wb") as w:
        shutil.copyfileobj(r, w, 1 << 20)

    digest = sha256_of(tmp)
    if digest != entry["sha256"]:
        tmp.unlink()
        sys.exit(f"SHA-256 mismatch for {url}: got {digest}, expected {entry['sha256']}")

    tmp.rename(dest)
    return dest


def extract_once(archive, target, opener):
    """Extract `archive` into `target` unless it was already extracted from it."""

    marker = target / ".source-sha256"
    digest = sha256_of(archive)

    if marker.exists() and marker.read_text().strip() == digest:
        return

    if target.exists():
        shutil.rmtree(target)
    target.mkdir(parents=True)

    log(f"extracting {archive.name}")
    with opener(archive) as a:
        if isinstance(a, tarfile.TarFile):
            a.extractall(target, filter="tar")
        else:
            a.extractall(target)

    marker.write_text(digest + "\n")


class PatternList:
    """RAPT's blocklist/keeplist matcher (rapt/buildlib/rapt/build.py)."""

    def __init__(self, path):
        self.patterns = []
        for line in path.read_text().splitlines():
            line = line.strip()
            if line and not line.startswith("#"):
                self.patterns.append(self.compile(line))

    @staticmethod
    def compile(pattern):
        regexp = ""
        while pattern:
            if pattern.startswith("**"):
                regexp += r".*"
                pattern = pattern[2:]
            elif pattern[0] == "*":
                regexp += r"[^/]*"
                pattern = pattern[1:]
            elif pattern[0] == "[":
                end = pattern.index("]")
                regexp += "[" + pattern[1:end] + "]"
                pattern = pattern[end + 1:]
            else:
                regexp += re.escape(pattern[0])
                pattern = pattern[1:]
        return re.compile(regexp + "$", re.I)

    def match(self, s):
        return any(p.match(s) or p.match("/" + s) for p in self.patterns)


class Filter:
    def __init__(self, rapt):
        self.block = PatternList(rapt / "blocklist.txt")
        self.keep = PatternList(rapt / "keeplist.txt")

    def include(self, relpath):
        return self.keep.match(relpath) or not self.block.match(relpath)


def eliminate_pycache(directory):
    """Move __pycache__/x.cpython-312.pyc to x.pyc, as RAPT does."""

    for p in list(directory.glob("**/__pycache__/*.pyc")):
        target = p.parent.parent / (p.stem.partition(".")[0] + ".pyc")
        if target.exists():
            target.unlink()
        p.rename(target)

    for p in sorted(directory.glob("**/__pycache__"), reverse=True):
        shutil.rmtree(p)


def compile_common(sdk, work):
    """
    Copy the SDK, add runtime/common/*.rpy to renpy/common and let the host
    SDK compile them, so the APK ships .rpyc files exactly as Ren'Py makes them.
    Returns the compiled renpy/common directory.
    """

    if work.exists():
        shutil.rmtree(work)
    shutil.copytree(sdk, work, symlinks=True)

    common = work / "renpy" / "common"
    overlay = sorted(COMMON_OVERLAY.glob("*.rpy"))
    for rpy in overlay:
        shutil.copy2(rpy, common / rpy.name)

    if overlay:
        log("compiling runtime/common with the host Ren'Py SDK")
        env = dict(os.environ, SDL_VIDEODRIVER="dummy", SDL_AUDIODRIVER="dummy")
        subprocess.run(
            [str(work / "renpy.sh"), str(work / "the_question"), "compile"],
            cwd=work, env=env, check=True,
            stdout=subprocess.DEVNULL,
        )
        for rpy in overlay:
            rpyc = common / (rpy.stem + ".rpyc")
            if not rpyc.exists() or rpyc.stat().st_mtime < (common / rpy.name).stat().st_mtime:
                sys.exit(f"Ren'Py did not compile {rpy.name}")

    return common


def make_main_py(sdk):
    source = (sdk / "renpy.py").read_text(encoding="utf-8")
    prelude = MAIN_PRELUDE.read_text(encoding="utf-8")

    if source.count(PRELUDE_ANCHOR) != 1:
        sys.exit("renpy.py changed: the prelude anchor is not unique")

    return source.replace(PRELUDE_ANCHOR, PRELUDE_ANCHOR + "\n" + prelude + "\n", 1)


def stage_private(sdk, filt, out_dir, tmp):
    """Build private.mp3 the way RAPT's split_renpy + make_tar do."""

    private = tmp / "private"
    if private.exists():
        shutil.rmtree(private)
    private.mkdir(parents=True)

    (private / "main.py").write_text(make_main_py(sdk), encoding="utf-8")

    # renpy/ (sourceless .pyc, as in official builds), without common.
    shutil.copytree(sdk / "renpy", private / "renpy",
                    ignore=shutil.ignore_patterns("common"))
    eliminate_pycache(private / "renpy")
    for py in (private / "renpy").glob("**/*.py"):
        py.unlink()

    # lib/python3.12: the pure-Python standard library and site-packages.
    shutil.copytree(sdk / "lib" / "python3.12", private / "lib" / "python3.12")

    mp3 = out_dir / "private.mp3"
    added = set()

    with tarfile.open(mp3, "w:gz", format=tarfile.GNU_FORMAT) as tf:

        def add(path, rel):
            chain = []
            while rel:
                chain.append((path, rel))
                path, rel = os.path.dirname(path), os.path.dirname(rel)
            for p, r in reversed(chain):
                if r not in added:
                    added.add(r)
                    tf.add(p, r, recursive=False)

        for dirpath, dirnames, filenames in os.walk(private):
            dirnames.sort()
            for name in sorted(dirnames) + sorted(filenames):
                full = os.path.join(dirpath, name)
                rel = os.path.relpath(full, private)
                if filt.include(rel):
                    add(full, rel)

    return hashlib.md5(mp3.read_bytes()).hexdigest()


def stage_common_assets(common, filt, assets):
    """renpy/common -> assets/x-renpy/x-common, as RAPT's make_assets_tree."""

    dst_root = assets / "x-renpy" / "x-common"
    dst_root.mkdir(parents=True)
    jobs = []

    for dirpath, dirnames, filenames in os.walk(common):
        rel_dir = os.path.relpath(dirpath, common)
        rel_dir = "" if rel_dir == "." else rel_dir
        new_dir = dst_root.joinpath(*["x-" + s for s in pathlib.PurePath(rel_dir).parts])

        keep_dirs = []
        for name in dirnames:
            rel = os.path.join("renpy/common", rel_dir, name)
            if filt.include(rel):
                keep_dirs.append(name)
                (new_dir / ("x-" + name)).mkdir()
        dirnames[:] = keep_dirs

        for name in filenames:
            rel = os.path.join("renpy/common", rel_dir, name)
            if filt.include(rel):
                jobs.append((pathlib.Path(dirpath) / name, new_dir / ("x-" + name)))

    def copy(job):
        old, new = job
        if old.suffix == ".gz":
            # AAPT gunzips .gz assets; RAPT double-gzips them to survive that.
            with open(old, "rb") as r, gzip.open(f"{new}.gz", "wb") as w:
                shutil.copyfileobj(r, w)
        else:
            shutil.copy2(old, new)

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as ex:
        list(ex.map(copy, jobs))

    return len(jobs)


def stage_renpyandroid(rapt, out):
    proto = rapt / "prototype" / "renpyandroid"
    templates = rapt / "templates"

    main = out / "src" / "main"
    main.mkdir(parents=True)

    shutil.copytree(proto / "src" / "main" / "java", main / "java",
                    ignore=shutil.ignore_patterns("*.class"))
    shutil.copytree(proto / "src" / "main" / "res", main / "res")

    # Only arm64-v8a: the other ABIs would add ~75 MB.
    jni = main / "jniLibs" / "arm64-v8a"
    jni.mkdir(parents=True)
    shutil.copy2(proto / "src" / "main" / "jniLibs" / "arm64-v8a" / "librenpython.so", jni)

    # RAPT renders these from templates; nothing in them is game-specific.
    shutil.copy2(templates / "renpyandroid-AndroidManifest.xml", main / "AndroidManifest.xml")
    (main / "res" / "values").mkdir(exist_ok=True)
    shutil.copy2(templates / "renpyandroid-strings.xml", main / "res" / "values" / "strings.xml")
    (main / "java" / "org" / "renpy" / "android" / "Constants.java").write_text(
        "package org.renpy.android;\n\n"
        "public class Constants {\n"
        "    // No in-app purchases.\n"
        "    public static String store = \"none\";\n\n"
        "    // No Play asset packs: games are read from external storage.\n"
        "    public static String assetPacks[ ] = { };\n"
        "}\n"
    )

    # The prototype's module build file, unchanged except for Kotlin, which
    # nothing in the module uses.
    gradle = (proto / "build.gradle").read_text()
    gradle = gradle.replace('    implementation "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.20"\n', "")
    (out / "build.gradle").write_text(gradle)


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--cache", type=pathlib.Path,
                        default=pathlib.Path(os.environ.get(
                            "RAR_CACHE", pathlib.Path.home() / ".cache" / "renpy-android-runtime")))
    args = parser.parse_args()

    lock = json.loads(LOCK.read_text())
    cache = args.cache.expanduser().resolve()
    cache.mkdir(parents=True, exist_ok=True)

    rapt_zip = fetch(lock["downloads"]["rapt"], cache)
    sdk_tar = fetch(lock["downloads"]["sdk"], cache)

    extract_once(rapt_zip, cache / "rapt", zipfile.ZipFile)
    extract_once(sdk_tar, cache / "sdk", lambda p: tarfile.open(p, "r:bz2"))

    rapt = cache / "rapt" / "rapt"
    sdk = cache / "sdk" / f"renpy-{lock['renpy_version']}-sdk"
    filt = Filter(rapt)

    if STAGING.exists():
        shutil.rmtree(STAGING)
    assets = STAGING / "assets"
    assets.mkdir(parents=True)

    log("staging renpyandroid module")
    stage_renpyandroid(rapt, STAGING / "renpyandroid")

    common = compile_common(sdk, cache / "sdk-work")

    log("building private.mp3")
    private_version = stage_private(sdk, filt, assets, cache / "tmp")

    log("staging renpy/common assets")
    count = stage_common_assets(common, filt, assets)

    values = STAGING / "generated-res" / "values"
    values.mkdir(parents=True)
    (values / "private_version.xml").write_text(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n"
        f"    <string name=\"private_version\" translatable=\"false\">{private_version}</string>\n"
        "</resources>\n"
    )

    stamp = {
        "renpy_build": lock["renpy_build"],
        "rapt_sha256": lock["downloads"]["rapt"]["sha256"],
        "sdk_sha256": lock["downloads"]["sdk"]["sha256"],
        "private_version": private_version,
        "common_assets": count,
    }
    (STAGING / "stamp.json").write_text(json.dumps(stamp, indent=2) + "\n")

    size = (assets / "private.mp3").stat().st_size
    log(f"done: private.mp3 {size // 1024} KiB (md5 {private_version}), {count} common assets")


if __name__ == "__main__":
    main()
