#!/usr/bin/env python3
"""Generates the extension-repo catalog for a single-extension Mihon/Suwayomi repo.

Both Mihon (mihonapp/mihon@main) and Suwayomi-Server (Suwayomi/Suwayomi-Server
@master) parse the modern NetworkExtensionStore schema directly -- confirmed by
reading both apps' real ExtensionStoreService.kt, which is identical between the
two. No legacy index.min.json/repo.json bridge is needed for these two targets.

Produces two files:
  index.pb    Protobuf-encoded NetworkExtensionStore -- the file to actually add
              as the repo URL in Mihon/Suwayomi.
  index.json  Same schema, human-readable JSON, for debugging/inspection only.

Field numbers for the protobuf encoder are copied 1:1 from the real Kotlin
model (data/src/main/java/mihon/data/extension/model/NetworkExtensionStore.kt)
-- see the PB_* constants below.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from dataclasses import dataclass


# ---------------------------------------------------------------------------
# Source ID -- must match HttpSource.generateId() exactly (source-api/src/main
# /kotlin/eu/kanade/tachiyomi/source/online/HttpSource.kt:98-102 in mihonapp/
# mihon@main), or the catalog-advertised id won't match what the installed
# extension reports at runtime:
#
#   val key = "${name.lowercase()}/$lang/$versionId"
#   val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
#   return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
#       .reduce(Long::or) and Long.MAX_VALUE
# ---------------------------------------------------------------------------
def source_id(name: str, lang: str, version_id: int) -> int:
    key = f"{name.lower()}/{lang}/{version_id}".encode("utf-8")
    digest = hashlib.md5(key).digest()
    value = int.from_bytes(digest[:8], byteorder="big", signed=False)
    return value & 0x7FFFFFFFFFFFFFFF  # clears the sign bit, same as `and Long.MAX_VALUE`


# ---------------------------------------------------------------------------
# Minimal protobuf wire-format encoder (no external deps -- protobuf's wire
# format is simple enough to hand-write for this flat, message-only schema:
# varints + length-delimited strings/submessages, nothing packed/repeated-
# scalar that would need special-casing).
# ---------------------------------------------------------------------------
def _varint(value: int) -> bytes:
    if value < 0:
        raise ValueError("varint encoder only supports non-negative values here")
    out = bytearray()
    v = value
    while True:
        b = v & 0x7F
        v >>= 7
        out.append(b | 0x80 if v else b)
        if not v:
            break
    return bytes(out)


def _tag(field_number: int, wire_type: int) -> bytes:
    return _varint((field_number << 3) | wire_type)


def pb_string(field_number: int, value: str) -> bytes:
    data = value.encode("utf-8")
    return _tag(field_number, 2) + _varint(len(data)) + data


def pb_varint(field_number: int, value: int) -> bytes:
    return _tag(field_number, 0) + _varint(value)


def pb_message(field_number: int, payload: bytes) -> bytes:
    return _tag(field_number, 2) + _varint(len(payload)) + payload


def pb_repeated_messages(field_number: int, payloads: list[bytes]) -> bytes:
    return b"".join(pb_message(field_number, p) for p in payloads)


# ---------------------------------------------------------------------------
# Data + schema
# ---------------------------------------------------------------------------
CONTENT_WARNING_NSFW = 3  # NetworkExtensionStore.ContentWarning: UNSPECIFIED=0 SAFE=1 MIXED=2 NSFW=3
CONTENT_WARNING_SAFE = 1


@dataclass
class ExtensionInfo:
    name: str
    pkg: str
    lang: str
    nsfw: bool
    version_code: int
    version_name: str
    lib_version: str
    version_id: int
    base_url: str
    apk_filename: str


def build_new_schema_dict(repo_name: str, badge_label: str, signing_key: str,
                           website: str, discord, extensions, apk_base_url: str,
                           icon_base_url: str) -> dict:
    """Mirrors NetworkExtensionStore.kt exactly (used for both index.json and
    the source-of-truth dict that gets protobuf-encoded into index.pb)."""
    return {
        "name": repo_name,
        "badgeLabel": badge_label,
        "signingKey": signing_key,
        "contact": {"website": website, "discord": discord},
        "extensionList": {
            "extensions": [
                {
                    "name": ext.name,
                    "packageName": ext.pkg,
                    "resources": {
                        "apkUrl": f"{apk_base_url}/{ext.apk_filename}",
                        "iconUrl": f"{icon_base_url}/{ext.pkg}.png",
                    },
                    "extensionLib": ext.lib_version,
                    "versionCode": ext.version_code,
                    "versionName": ext.version_name,
                    "contentWarning": "CONTENT_WARNING_NSFW" if ext.nsfw else "CONTENT_WARNING_SAFE",
                    "sources": [
                        {
                            "id": source_id(ext.name, ext.lang, ext.version_id),
                            "name": ext.name,
                            "language": ext.lang,
                            "homeUrl": ext.base_url,
                        }
                    ],
                }
                for ext in extensions
            ]
        },
    }


# Proto field numbers, copied 1:1 from NetworkExtensionStore.kt (@ProtoNumber).
PB_STORE_NAME, PB_STORE_BADGE, PB_STORE_SIGNKEY, PB_STORE_CONTACT = 1, 2, 3, 4
PB_STORE_EXT_LIST = 101
PB_CONTACT_WEBSITE, PB_CONTACT_DISCORD = 1, 2
PB_EXTLIST_EXTENSIONS = 1
PB_EXT_NAME, PB_EXT_PKG, PB_EXT_RESOURCES = 1, 2, 3
PB_EXT_LIBVER, PB_EXT_VCODE, PB_EXT_VNAME, PB_EXT_WARNING, PB_EXT_SOURCES = 4, 5, 6, 7, 8
PB_RES_APKURL, PB_RES_ICONURL = 1, 2
PB_SRC_ID, PB_SRC_NAME, PB_SRC_LANG, PB_SRC_HOMEURL = 1, 2, 3, 4


def encode_new_schema_protobuf(store: dict) -> bytes:
    contact = store["contact"]
    contact_bytes = pb_string(PB_CONTACT_WEBSITE, contact["website"])
    if contact.get("discord"):
        contact_bytes += pb_string(PB_CONTACT_DISCORD, contact["discord"])

    ext_payloads = []
    for ext in store["extensionList"]["extensions"]:
        res_bytes = (
            pb_string(PB_RES_APKURL, ext["resources"]["apkUrl"])
            + pb_string(PB_RES_ICONURL, ext["resources"]["iconUrl"])
        )
        src_payloads = []
        for src in ext["sources"]:
            src_bytes = (
                pb_varint(PB_SRC_ID, src["id"])
                + pb_string(PB_SRC_NAME, src["name"])
                + pb_string(PB_SRC_LANG, src["language"])
            )
            if src.get("homeUrl"):
                src_bytes += pb_string(PB_SRC_HOMEURL, src["homeUrl"])
            src_payloads.append(src_bytes)

        warning_value = CONTENT_WARNING_NSFW if ext["contentWarning"] == "CONTENT_WARNING_NSFW" else CONTENT_WARNING_SAFE
        ext_bytes = (
            pb_string(PB_EXT_NAME, ext["name"])
            + pb_string(PB_EXT_PKG, ext["packageName"])
            + pb_message(PB_EXT_RESOURCES, res_bytes)
            + pb_string(PB_EXT_LIBVER, ext["extensionLib"])
            + pb_varint(PB_EXT_VCODE, ext["versionCode"])
            + pb_string(PB_EXT_VNAME, ext["versionName"])
            + pb_varint(PB_EXT_WARNING, warning_value)
            + pb_repeated_messages(PB_EXT_SOURCES, src_payloads)
        )
        ext_payloads.append(ext_bytes)

    extlist_bytes = pb_repeated_messages(PB_EXTLIST_EXTENSIONS, ext_payloads)

    out = (
        pb_string(PB_STORE_NAME, store["name"])
        + pb_string(PB_STORE_BADGE, store["badgeLabel"])
        + pb_string(PB_STORE_SIGNKEY, store["signingKey"])
        + pb_message(PB_STORE_CONTACT, contact_bytes)
        + pb_message(PB_STORE_EXT_LIST, extlist_bytes)
    )
    return out


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------
def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pkg", required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--lang", required=True)
    parser.add_argument("--nsfw", type=int, default=0)
    parser.add_argument("--code", type=int, required=True)
    parser.add_argument("--version", required=True, help="versionName, e.g. 1.6.1")
    parser.add_argument("--lib-version", required=True, help="e.g. 1.6 (tachiyomix lib version)")
    parser.add_argument("--version-id", type=int, default=1)
    parser.add_argument("--base-url", default="https://cuutruyen.net", help="the manga site's own base URL")
    parser.add_argument("--repo-name", default="Cứu Truyện")
    parser.add_argument("--badge-label", default="CT")
    parser.add_argument("--website", required=True, help="e.g. https://github.com/<owner>/<repo>")
    parser.add_argument("--discord", default=None)
    parser.add_argument("--signing-fingerprint", required=True,
                         help="lowercase hex SHA-256 of the release cert, see scripts/print_signing_fingerprint.sh")
    parser.add_argument("--raw-base-url", required=True,
                         help="raw.githubusercontent.com/<owner>/<repo>/repo -- base for apk/icon links")
    parser.add_argument("--out-dir", required=True)
    args = parser.parse_args()

    ext = ExtensionInfo(
        name=args.name,
        pkg=args.pkg,
        lang=args.lang,
        nsfw=bool(args.nsfw),
        version_code=args.code,
        version_name=args.version,
        lib_version=args.lib_version,
        version_id=args.version_id,
        base_url=args.base_url,
        apk_filename=f"{args.pkg}.apk",
    )

    os.makedirs(args.out_dir, exist_ok=True)

    new_schema = build_new_schema_dict(
        repo_name=args.repo_name,
        badge_label=args.badge_label,
        signing_key=args.signing_fingerprint,
        website=args.website,
        discord=args.discord,
        extensions=[ext],
        apk_base_url=f"{args.raw_base_url}/apk",
        icon_base_url=f"{args.raw_base_url}/icon",
    )
    with open(os.path.join(args.out_dir, "index.json"), "w", encoding="utf-8") as f:
        json.dump(new_schema, f, ensure_ascii=False, indent=2)
        f.write("\n")

    pb_bytes = encode_new_schema_protobuf(new_schema)
    with open(os.path.join(args.out_dir, "index.pb"), "wb") as f:
        f.write(pb_bytes)

    print(f"Wrote index.json, index.pb ({len(pb_bytes)} bytes) to {args.out_dir}")


if __name__ == "__main__":
    main()
