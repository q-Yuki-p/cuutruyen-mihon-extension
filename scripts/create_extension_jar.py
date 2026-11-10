#!/usr/bin/env python3
"""Packages the extension classes and merged AndroidManifest.xml into a JAR file.

This JAR file allows desktop JVM runtimes like Suwayomi-Server to execute the extension
bytecode directly, bypassing buggy Android dex2jar decompiler issues with Java 8/17 default
interface methods (such as kotlinx.serialization GeneratedSerializer).
"""
import argparse
import os
import sys
import zipfile


def create_jar(app_dir: str, variant: str, output_jar: str) -> None:
    app_dir = os.path.abspath(app_dir)
    build_dir = os.path.join(app_dir, "build")

    # Find merged AndroidManifest.xml
    manifest_candidates = [
        os.path.join(build_dir, "intermediates", "merged_manifest", variant, f"process{variant.capitalize()}MainManifest", "AndroidManifest.xml"),
        os.path.join(build_dir, "intermediates", "merged_manifests", variant, "AndroidManifest.xml"),
    ]
    manifest_path = None
    for p in manifest_candidates:
        if os.path.isfile(p):
            manifest_path = p
            break

    if not manifest_path:
        # Search recursively in intermediates/merged_manifest*
        for root, _, files in os.walk(os.path.join(build_dir, "intermediates")):
            if "merged_manifest" in root and "AndroidManifest.xml" in files and variant in root:
                manifest_path = os.path.join(root, "AndroidManifest.xml")
                break

    if not manifest_path:
        raise FileNotFoundError(f"Could not find merged AndroidManifest.xml for variant '{variant}' in {build_dir}")

    # Find class directories
    class_dirs = []
    # AGP built-in kotlinc or kotlin-android
    for root, dirs, _ in os.walk(os.path.join(build_dir, "intermediates")):
        if f"compile{variant.capitalize()}Kotlin" in root and root.endswith("classes"):
            class_dirs.append(root)
        elif f"compile{variant.capitalize()}JavaWithJavac" in root and root.endswith("classes"):
            class_dirs.append(root)

    if not class_dirs:
        raise FileNotFoundError(f"Could not find compiled classes directories for variant '{variant}' in {build_dir}")

    os.makedirs(os.path.dirname(os.path.abspath(output_jar)), exist_ok=True)
    with zipfile.ZipFile(output_jar, "w", zipfile.ZIP_DEFLATED) as z:
        # Write plain-text AndroidManifest.xml at root of JAR
        z.write(manifest_path, "AndroidManifest.xml")

        # Write all class files
        added_entries = set(["AndroidManifest.xml"])
        for cdir in class_dirs:
            for root, _, files in os.walk(cdir):
                for f in files:
                    full_path = os.path.join(root, f)
                    rel_path = os.path.relpath(full_path, cdir)
                    if rel_path not in added_entries:
                        z.write(full_path, rel_path)
                        added_entries.add(rel_path)

    print(f"Created extension JAR successfully: {output_jar} ({os.path.getsize(output_jar)} bytes, {len(added_entries)} entries)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Create extension JAR for Suwayomi-Server")
    parser.add_argument("--app-dir", default="app", help="Path to app module directory")
    parser.add_argument("--variant", default="release", help="Build variant (release or debug)")
    parser.add_argument("--output", required=True, help="Output JAR path")
    args = parser.parse_args()

    create_jar(args.app_dir, args.variant, args.output)
