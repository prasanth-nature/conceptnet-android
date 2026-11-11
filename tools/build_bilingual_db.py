#!/usr/bin/env python3
"""Build a bilingual English/Tamil SQLite subset from ConceptNet 5.7.0.

Keeps every assertion whose start AND end concepts are /c/en/... or /c/ta/...
This includes en-en, ta-ta, en-ta and ta-en edges. Relation, weight and the
complete source metadata JSON are retained without rewriting.
"""
from __future__ import annotations
import argparse, gzip, json, os, re, sqlite3, sys, time
from pathlib import Path

SOURCE_URL = "https://s3.amazonaws.com/conceptnet/downloads/2019/edges/conceptnet-assertions-5.7.0.csv.gz"
LANG_RE = re.compile(r"^/c/(en|ta)/")
BATCH = 25_000

SCHEMA = """
PRAGMA journal_mode=WAL;
PRAGMA synchronous=NORMAL;
PRAGMA temp_store=FILE;
PRAGMA cache_size=-65536;
CREATE TABLE nodes (
  uri TEXT PRIMARY KEY,
  language TEXT NOT NULL CHECK(language IN ('en','ta')),
  label TEXT NOT NULL,
  normalized_label TEXT NOT NULL
) WITHOUT ROWID;
CREATE TABLE edges (
  id INTEGER PRIMARY KEY,
  edge_uri TEXT NOT NULL,
  relation_uri TEXT NOT NULL,
  start_uri TEXT NOT NULL,
  end_uri TEXT NOT NULL,
  weight REAL,
  metadata_json TEXT NOT NULL,
  start_language TEXT NOT NULL,
  end_language TEXT NOT NULL
);
CREATE INDEX edges_start_idx ON edges(start_uri);
CREATE INDEX edges_end_idx ON edges(end_uri);
CREATE INDEX edges_relation_idx ON edges(relation_uri);
CREATE INDEX edges_start_end_idx ON edges(start_language,end_language);
CREATE INDEX nodes_language_label_idx ON nodes(language, normalized_label);
"""

def lang(uri: str):
    m = LANG_RE.match(uri)
    return m.group(1) if m else None

def label(uri: str) -> str:
    # Keep the URI as the canonical identity; convert the concept path to
    # a readable label while retaining sense information only in the URI.
    parts = uri.split("/")
    return parts[3].replace("_", " ") if len(parts) > 3 else uri

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True, help="ConceptNet assertions .csv.gz")
    ap.add_argument("--output", default="conceptnet-en-ta-5.7.0.sqlite")
    args = ap.parse_args()
    source, output = Path(args.input), Path(args.output)
    if not source.is_file():
        ap.error(f"Input file not found: {source}")
    if output.exists():
        output.unlink()
    db = sqlite3.connect(output)
    db.executescript(SCHEMA)
    edge_batch, node_batch = [], {}
    counts = {"input": 0, "kept": 0, "en_en": 0, "ta_ta": 0, "en_ta": 0, "ta_en": 0, "malformed": 0}
    started = time.time()

    def flush():
        if node_batch:
            db.executemany(
                "INSERT OR IGNORE INTO nodes(uri,language,label,normalized_label) VALUES(?,?,?,?)",
                [(u, l, label(u), label(u).casefold()) for u, l in node_batch.items()]
            )
            node_batch.clear()
        if edge_batch:
            db.executemany(
                """INSERT INTO edges(edge_uri,relation_uri,start_uri,end_uri,weight,metadata_json,start_language,end_language)
                   VALUES(?,?,?,?,?,?,?,?)""", edge_batch
            )
            edge_batch.clear()
            db.commit()

    with gzip.open(source, "rt", encoding="utf-8", errors="replace", newline="") as stream:
        for line in stream:
            counts["input"] += 1
            fields = line.rstrip("\n").split("\t", 4)
            if len(fields) != 5:
                counts["malformed"] += 1
                continue
            edge_uri, relation_uri, start_uri, end_uri, metadata = fields
            sl, el = lang(start_uri), lang(end_uri)
            if sl not in ("en", "ta") or el not in ("en", "ta"):
                continue
            try:
                meta = json.loads(metadata)
                weight = float(meta.get("weight", 1.0))
            except (ValueError, TypeError, json.JSONDecodeError):
                meta, weight = {}, 1.0
            edge_batch.append((edge_uri, relation_uri, start_uri, end_uri, weight, metadata, sl, el))
            node_batch[start_uri] = sl
            node_batch[end_uri] = el
            counts["kept"] += 1
            counts[sl + "_" + el] += 1
            if len(edge_batch) >= BATCH:
                flush()
                if counts["input"] % 1_000_000 < BATCH:
                    print(f"Processed {counts['input']:,}; retained {counts['kept']:,}", flush=True)
    flush()
    db.executescript("""
      CREATE VIRTUAL TABLE nodes_fts USING fts5(uri UNINDEXED, label, language UNINDEXED, tokenize='unicode61 remove_diacritics 0');
      INSERT INTO nodes_fts(uri,label,language) SELECT uri,label,language FROM nodes;
      CREATE VIRTUAL TABLE edges_fts USING fts5(edge_uri UNINDEXED, metadata_json, tokenize='unicode61');
      INSERT INTO edges_fts(edge_uri,metadata_json) SELECT edge_uri,metadata_json FROM edges WHERE metadata_json LIKE '%surfaceText%';
      PRAGMA optimize;
    """)
    db.commit()
    integrity = db.execute("PRAGMA integrity_check").fetchone()[0]
    counts["nodes"] = db.execute("SELECT COUNT(*) FROM nodes").fetchone()[0]
    counts["database_bytes"] = output.stat().st_size
    counts["integrity_check"] = integrity
    counts["seconds"] = round(time.time() - started, 1)
    db.execute("CREATE TABLE build_info (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    info = {
      "dataset": "ConceptNet 5.7.0",
      "source_url": SOURCE_URL,
      "languages": "en,ta",
      "filter": "both endpoints must be /c/en/ or /c/ta/",
      "license": "CC BY-SA 4.0; individual assertions retain their original metadata/license",
      "counts": json.dumps(counts, ensure_ascii=False)
    }
    db.executemany("INSERT INTO build_info VALUES(?,?)", info.items())
    db.commit()
    db.close()
    print(json.dumps(counts, ensure_ascii=False, indent=2))
    if integrity != "ok":
        raise SystemExit("SQLite integrity check failed")

if __name__ == "__main__":
    main()
