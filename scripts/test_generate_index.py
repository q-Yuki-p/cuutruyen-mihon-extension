#!/usr/bin/env python3
"""Tests for the modernized generator: index.pb only (no legacy index.min.json /
repo.json / index.json -- Mihon and Suwayomi both read the new schema directly).

Run: python3 scripts/test_generate_index.py
"""
import hashlib
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import generate_index as gi  # noqa: E402


# ---------------------------------------------------------------------------
# Independent protobuf DECODER (test-only, not shared code with the encoder).
# ---------------------------------------------------------------------------
def decode_varint(data: bytes, pos: int):
    result = 0
    shift = 0
    while True:
        b = data[pos]
        pos += 1
        result |= (b & 0x7F) << shift
        if not (b & 0x80):
            break
        shift += 7
    return result, pos


def decode_message(data: bytes):
    fields: dict = {}
    pos = 0
    while pos < len(data):
        tag, pos = decode_varint(data, pos)
        field_number = tag >> 3
        wire_type = tag & 0x7
        if wire_type == 0:
            value, pos = decode_varint(data, pos)
        elif wire_type == 2:
            length, pos = decode_varint(data, pos)
            value = data[pos:pos + length]
            pos += length
        else:
            raise ValueError(f"unsupported wire type {wire_type}")
        fields.setdefault(field_number, []).append(value)
    return fields


def s(b: bytes) -> str:
    return b.decode("utf-8")


class SourceIdTests(unittest.TestCase):
    def test_matches_hand_computed_md5(self):
        key = "cứu truyện/vi/1".encode("utf-8")
        digest = hashlib.md5(key).digest()
        expected = int.from_bytes(digest[:8], "big") & 0x7FFFFFFFFFFFFFFF
        self.assertEqual(gi.source_id("Cứu Truyện", "vi", 1), expected)

    def test_is_lowercased_before_hashing(self):
        self.assertEqual(
            gi.source_id("CỨU TRUYỆN", "vi", 1),
            gi.source_id("Cứu Truyện", "vi", 1),
        )

    def test_sign_bit_always_clear(self):
        for name in ["a", "b", "some name", "Cứu Truyện", "!!!"]:
            self.assertGreaterEqual(gi.source_id(name, "vi", 1), 0)
            self.assertLess(gi.source_id(name, "vi", 1), 2**63)


class NewSchemaDictTests(unittest.TestCase):
    def setUp(self):
        self.ext = gi.ExtensionInfo(
            name="Cứu Truyện", pkg="eu.kanade.tachiyomi.extension.vi.cuutruyen",
            lang="vi", nsfw=True, version_code=3, version_name="1.6.3",
            lib_version="1.6", version_id=1, base_url="https://cuutruyen.net",
            apk_filename="eu.kanade.tachiyomi.extension.vi.cuutruyen.apk",
        )
        self.store = gi.build_new_schema_dict(
            repo_name="Cứu Truyện", badge_label="CT", signing_key="ab" * 32,
            website="https://github.com/owner/repo", discord=None,
            extensions=[self.ext],
            apk_base_url="https://raw.githubusercontent.com/owner/repo/repo/apk",
            icon_base_url="https://raw.githubusercontent.com/owner/repo/repo/icon",
        )

    def test_no_legacy_fields_anywhere(self):
        # This is the whole point of the modernization: nothing named "pkg",
        # "apk", "code", or "baseUrl" (the old flat-array field names) should
        # appear anywhere in the new schema.
        import json
        raw = json.dumps(self.store)
        for legacy_field in ('"pkg"', '"apk"', '"code"', '"baseUrl"'):
            self.assertNotIn(legacy_field, raw)

    def test_extension_lib_is_1_6(self):
        ext = self.store["extensionList"]["extensions"][0]
        self.assertEqual(ext["extensionLib"], "1.6")

    def test_content_warning_nsfw(self):
        ext = self.store["extensionList"]["extensions"][0]
        self.assertEqual(ext["contentWarning"], "CONTENT_WARNING_NSFW")


class ProtobufRoundTripTests(unittest.TestCase):
    def setUp(self):
        self.ext = gi.ExtensionInfo(
            name="Cứu Truyện", pkg="eu.kanade.tachiyomi.extension.vi.cuutruyen",
            lang="vi", nsfw=True, version_code=3, version_name="1.6.3",
            lib_version="1.6", version_id=1, base_url="https://cuutruyen.net",
            apk_filename="eu.kanade.tachiyomi.extension.vi.cuutruyen.apk",
        )
        self.store = gi.build_new_schema_dict(
            repo_name="Cứu Truyện", badge_label="CT", signing_key="ab" * 32,
            website="https://github.com/owner/repo", discord=None,
            extensions=[self.ext],
            apk_base_url="https://raw.githubusercontent.com/owner/repo/repo/apk",
            icon_base_url="https://raw.githubusercontent.com/owner/repo/repo/icon",
        )
        self.pb = gi.encode_new_schema_protobuf(self.store)

    def test_top_level_scalar_fields_round_trip(self):
        fields = decode_message(self.pb)
        self.assertEqual(s(fields[gi.PB_STORE_NAME][0]), "Cứu Truyện")
        self.assertEqual(s(fields[gi.PB_STORE_BADGE][0]), "CT")
        self.assertEqual(s(fields[gi.PB_STORE_SIGNKEY][0]), "ab" * 32)

    def test_contact_submessage_round_trips(self):
        fields = decode_message(self.pb)
        contact = decode_message(fields[gi.PB_STORE_CONTACT][0])
        self.assertEqual(s(contact[gi.PB_CONTACT_WEBSITE][0]), "https://github.com/owner/repo")
        self.assertNotIn(gi.PB_CONTACT_DISCORD, contact)

    def test_extension_and_nested_source_round_trip(self):
        fields = decode_message(self.pb)
        extlist = decode_message(fields[gi.PB_STORE_EXT_LIST][0])
        [ext_bytes] = extlist[gi.PB_EXTLIST_EXTENSIONS]
        ext = decode_message(ext_bytes)

        self.assertEqual(s(ext[gi.PB_EXT_NAME][0]), "Cứu Truyện")
        self.assertEqual(s(ext[gi.PB_EXT_PKG][0]), "eu.kanade.tachiyomi.extension.vi.cuutruyen")
        self.assertEqual(s(ext[gi.PB_EXT_LIBVER][0]), "1.6")
        self.assertEqual(ext[gi.PB_EXT_VCODE][0], 3)
        self.assertEqual(s(ext[gi.PB_EXT_VNAME][0]), "1.6.3")
        self.assertEqual(ext[gi.PB_EXT_WARNING][0], gi.CONTENT_WARNING_NSFW)

        resources = decode_message(ext[gi.PB_EXT_RESOURCES][0])
        self.assertEqual(
            s(resources[gi.PB_RES_APKURL][0]),
            "https://raw.githubusercontent.com/owner/repo/repo/apk/eu.kanade.tachiyomi.extension.vi.cuutruyen.apk",
        )

        [src_bytes] = ext[gi.PB_EXT_SOURCES]
        src = decode_message(src_bytes)
        self.assertEqual(src[gi.PB_SRC_ID][0], gi.source_id("Cứu Truyện", "vi", 1))
        self.assertEqual(s(src[gi.PB_SRC_NAME][0]), "Cứu Truyện")
        self.assertEqual(s(src[gi.PB_SRC_LANG][0]), "vi")
        self.assertEqual(s(src[gi.PB_SRC_HOMEURL][0]), "https://cuutruyen.net")

    def test_safe_extension_encodes_content_warning_safe(self):
        safe_ext = gi.ExtensionInfo(
            name="X", pkg="x.y.z", lang="vi", nsfw=False, version_code=1,
            version_name="1.6.1", lib_version="1.6", version_id=1,
            base_url="https://x.test", apk_filename="x.apk",
        )
        store = gi.build_new_schema_dict(
            repo_name="R", badge_label="R", signing_key="00" * 32,
            website="https://x.test", discord=None, extensions=[safe_ext],
            apk_base_url="https://x.test/apk", icon_base_url="https://x.test/icon",
        )
        pb = gi.encode_new_schema_protobuf(store)
        fields = decode_message(pb)
        extlist = decode_message(fields[gi.PB_STORE_EXT_LIST][0])
        ext = decode_message(extlist[gi.PB_EXTLIST_EXTENSIONS][0])
        self.assertEqual(ext[gi.PB_EXT_WARNING][0], gi.CONTENT_WARNING_SAFE)


class VersionIdSyncTests(unittest.TestCase):
    """Guards against issue: generate_index.py's --version-id silently drifting out of
    sync with `override val versionId` in CuuTruyen.kt (see build.yml comment). If either
    side is refactored in a way that breaks the other, these should fail loudly instead of
    shipping an index.pb whose source id doesn't match what the installed app computes."""

    ROOT = Path(__file__).parent.parent
    SOURCE_KT = ROOT / "app/src/main/java/eu/kanade/tachiyomi/extension/vi/cuutruyen/CuuTruyen.kt"
    WORKFLOW = ROOT / ".github/workflows/build.yml"

    def test_kotlin_version_id_is_parseable(self):
        import re
        text = self.SOURCE_KT.read_text(encoding="utf-8")
        match = re.search(r"override val versionId\s*=\s*(\d+)", text)
        self.assertIsNotNone(match, "could not find 'override val versionId = <int>' in CuuTruyen.kt")
        # sanity: matches what source_id()'s default assumes callers will pass explicitly
        self.assertGreaterEqual(int(match.group(1)), 1)

    def test_workflow_passes_version_id_explicitly(self):
        text = self.WORKFLOW.read_text(encoding="utf-8")
        self.assertIn(
            "--version-id",
            text,
            "build.yml no longer passes --version-id to generate_index.py -- it would "
            "silently fall back to the hard-coded default and go out of sync with "
            "CuuTruyen.kt's real versionId the next time it's bumped",
        )
        self.assertIn(
            "override val versionId",
            text,
            "build.yml no longer greps versionId from CuuTruyen.kt",
        )


class CliEndToEndTests(unittest.TestCase):
    def test_full_run_produces_only_index_pb_and_index_json(self):
        with tempfile.TemporaryDirectory() as out_dir:
            subprocess.run(
                [
                    sys.executable, str(Path(__file__).parent / "generate_index.py"),
                    "--pkg", "eu.kanade.tachiyomi.extension.vi.cuutruyen",
                    "--name", "Cứu Truyện", "--lang", "vi", "--nsfw", "1",
                    "--code", "3", "--version", "1.6.3", "--lib-version", "1.6",
                    "--website", "https://github.com/owner/repo",
                    "--signing-fingerprint", "cd" * 32,
                    "--raw-base-url", "https://raw.githubusercontent.com/owner/repo/repo",
                    "--out-dir", out_dir,
                ],
                check=True, capture_output=True, text=True,
            )
            out = Path(out_dir)
            self.assertTrue((out / "index.pb").exists())
            self.assertTrue((out / "index.json").exists())
            # The whole point of the modernization: these legacy files must NOT
            # be produced anymore.
            self.assertFalse((out / "index.min.json").exists())
            self.assertFalse((out / "repo.json").exists())

            pb_bytes = (out / "index.pb").read_bytes()
            fields = decode_message(pb_bytes)
            self.assertEqual(s(fields[gi.PB_STORE_SIGNKEY][0]), "cd" * 32)


if __name__ == "__main__":
    unittest.main(verbosity=2)
