#!/usr/bin/env python3
"""Generate V25 from the reviewed local bibliography; no network or dependencies.

Run from any directory:
    python db/catalog/build_domestic_catalog.py
    python db/catalog/build_domestic_catalog.py --check

The JSON is the reviewed source of truth. This script validates facts and emits
only insert-if-absent statements; it never fetches websites or executes SQL.
"""

from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import sys
import unicodedata
from urllib.parse import urlsplit


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_INPUT = ROOT / "db/catalog/domestic-textbooks-1000.json"
DEFAULT_OUTPUT = ROOT / "backend/src/main/resources/db/migration/V25__seed_domestic_textbook_catalog.sql"
EXPECTED_PUBLISHERS = {"高等教育出版社": 20, "机械工业出版社": 980}
SOURCE_HOSTS = {"hep": "www.hep.com.cn", "cmpedu": "www.cmpedu.com"}
ALLOWED_SUBJECT_ORIGINS = {"official_catalog", "editorial_title_keyword", "editorial_fallback"}


def normalized_text(value: str) -> str:
    return re.sub(r"\s+", "", unicodedata.normalize("NFKC", value)).casefold()


def valid_isbn(value: str) -> bool:
    return bool(re.fullmatch(r"97[89]\d{10}", value)) and sum(
        int(digit) * (1 if index % 2 == 0 else 3)
        for index, digit in enumerate(value)
    ) % 10 == 0


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def validate(data: dict) -> list[dict]:
    records = data.get("records", [])
    require(data.get("schemaVersion") == 1, "Unsupported schemaVersion")
    require(data.get("accessMode") == "CATALOG_ONLY", "Only catalogue metadata is supported")
    require(len(records) == 1000 and data.get("recordCount") == 1000, "Exactly 1000 reviewed records are required")
    require(dict(Counter(row.get("publisher") for row in records)) == EXPECTED_PUBLISHERS, "Publisher counts must be HEP 20 / CMP 980")
    seen_isbns, seen_urls, seen_titles = set(), set(), set()
    for number, row in enumerate(records, 1):
        prefix = f"Record {number}"
        for key, limit in {
            "title": 200, "author": 200, "publisher": 200, "subject": 100,
            "sourceId": 200, "sourceUrl": 1000, "coverUrl": 1000,
            "metadataUrl": 1000, "educationEvidence": 1000, "observedAt": 80,
        }.items():
            value = row.get(key)
            require(isinstance(value, str) and bool(value.strip()), f"{prefix}: missing {key}")
            require(len(value) <= limit, f"{prefix}: {key} exceeds its storage limit")
            require(not any(ord(char) < 32 or char in "\\\ufffd" for char in value), f"{prefix}: invalid characters in {key}")
        isbn = row.get("isbn", "")
        require(valid_isbn(isbn), f"{prefix}: invalid ISBN-13")
        url = urlsplit(row["sourceUrl"])
        require(url.scheme == "https" and url.hostname == SOURCE_HOSTS.get(row.get("source")), f"{prefix}: unofficial detail URL")
        require(not url.username and not url.password and not url.fragment, f"{prefix}: unexpected detail URL components")
        for key in ("coverUrl", "metadataUrl"):
            parsed = urlsplit(row[key])
            require(parsed.scheme == "https" and bool(parsed.hostname) and not parsed.username and not parsed.password, f"{prefix}: invalid {key}")
        require(row.get("educationLevel") == "本科" and "本科" in row["educationEvidence"], f"{prefix}: missing undergraduate evidence")
        require(row.get("subjectOrigin") in ALLOWED_SUBJECT_ORIGINS, f"{prefix}: unrecorded subject origin")
        require(row.get("languageCode") in ("zh", "en"), f"{prefix}: invalid catalogue language")
        year = row.get("publicationYear")
        require(year is None or type(year) is int and 1900 <= year <= 2100, f"{prefix}: invalid publication year")
        date = row.get("publicationDate")
        require(date is None or bool(re.fullmatch(r"\d{4}-\d{2}-\d{2}", date)), f"{prefix}: invalid publication date")
        require(date is None or year == int(date[:4]), f"{prefix}: publication year differs from source date")
        for forbidden in ("summary", "description", "contentText", "content_text", "downloadUrl", "fullTextUrl", "knowledgePoints"):
            require(forbidden not in row, f"{prefix}: bibliography must not contain {forbidden}")
        title_author = (normalized_text(row["title"]), normalized_text(row["author"]))
        require(isbn not in seen_isbns, f"{prefix}: duplicate ISBN")
        require(row["sourceUrl"] not in seen_urls, f"{prefix}: duplicate official URL")
        require(title_author not in seen_titles, f"{prefix}: duplicate title and author")
        seen_isbns.add(isbn)
        seen_urls.add(row["sourceUrl"])
        seen_titles.add(title_author)
    return records


def sql_literal(value: str | int | None) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, int):
        return str(value)
    # All inputs were checked for control characters and backslashes, keeping
    # identical literal semantics in MySQL and the H2 migration test.
    require("\\" not in value and "\x00" not in value, "Unsupported SQL literal")
    return "'" + value.replace("'", "''") + "'"


def render_sql(records: list[dict], digest: str) -> str:
    columns = (
        "title", "summary", "subject", "knowledge_points", "difficulty_level", "estimated_minutes",
        "resource_type", "resource_kind", "language_code", "coverage_note", "download_url", "cover_url",
        "source_name", "source_url", "author", "license_code", "license_url", "license_note", "content_text",
        "status", "review_note", "reviewed_by", "reviewed_at", "access_mode", "isbn", "publisher",
        "education_level", "publication_year",
    )
    lines = [
        "-- Generated by db/catalog/build_domestic_catalog.py; do not edit by hand.",
        "-- Input: db/catalog/domestic-textbooks-1000.json",
        f"-- Input SHA-256: {digest}",
        "-- 1000 official undergraduate bibliography entries: HEP 20 / CMP 980.",
        "-- CATALOG_ONLY: no textbook text, downloadable textbook file or content audit.",
        "-- Preserve existing rows with the same official URL or ISBN.",
        "",
    ]
    for row in records:
        summary = f"{row['publisher']} · 本科教材；ISBN {row['isbn']}。"
        coverage = "官方本科教材书目，仅提供版本与来源信息，不代表本站提供可用全文；阅读、购买或校园授权条件以原站为准。"
        licence = "仅记录事实书目和官方链接；出版社保留版权，未核实教材正文或封面的再分发许可，公开访问不等于授权转载。"
        values = (
            row["title"], summary, row["subject"], "", 2, 0,
            "EXTERNAL", "TEXTBOOK", row["languageCode"], coverage, None, row["coverUrl"],
            row["publisher"] + "官方教材目录", row["sourceUrl"], row["author"], "LINK_ONLY", None, licence, None,
            "PUBLISHED", "官方目录元数据核验；不是全文/内容审校", None, None,
            "CATALOG_ONLY", row["isbn"], row["publisher"], "本科", row["publicationYear"],
        )
        lines.extend([
            f"-- ISBN {row['isbn']}",
            "INSERT INTO study_resource (" + ", ".join(columns) + ")",
            "SELECT " + ", ".join(sql_literal(value) for value in values),
            "WHERE NOT EXISTS (",
            "    SELECT 1 FROM study_resource",
            "    WHERE source_url = " + sql_literal(row["sourceUrl"]) + " OR isbn = " + sql_literal(row["isbn"]),
            ");",
            "",
        ])
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--check", action="store_true", help="Validate JSON and require the checked-in SQL to match without writing")
    args = parser.parse_args()
    raw = args.input.read_bytes()
    records = validate(json.loads(raw.decode("utf-8")))
    sql = render_sql(records, hashlib.sha256(raw).hexdigest())
    if args.check:
        require(args.output.exists(), f"SQL file is missing: {args.output}")
        require(args.output.read_text(encoding="utf-8") == sql, "Generated SQL differs; run the generator before reviewing")
        result = "validated"
    else:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(sql, encoding="utf-8", newline="\n")
        result = "generated"
    print(json.dumps({"result": result, "records": len(records), "publishers": dict(Counter(r["publisher"] for r in records)), "subjects": dict(Counter(r["subject"] for r in records)), "output": str(args.output)}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    try:
        raise SystemExit(main())
    except (ValueError, OSError) as error:
        print(f"Catalogue generation failed: {error}", file=sys.stderr)
        raise SystemExit(1)
