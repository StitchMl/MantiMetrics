#!/usr/bin/env python3
"""
Patch the NSmells family of columns in the generated dataset CSVs using per-release SonarCloud
smell snapshots exported by com.mantimetrics.orchestrator.SmellSnapshotExporter.

For every row it sets:
  - NSmells         (col 8)  = smell count for that (release, class) from output/smells/<tag>.tsv
  - NSmellsDensity  (col 9)  = NSmells / max(LOC, 1), formatted "%.4f" (identical to the pipeline)
  - maxNSmells      (col 23) = persistent running max of NSmells per class, in processing order
  - prevNSmells     (col 33) = NSmells of the same class in the immediately preceding SELECTED release

The max/prev recomputation reproduces the pipeline exactly (validated byte-for-byte on the current
CSVs). Releases without a TSV keep their existing NSmells, so partial exports are safe. Every other
column is preserved verbatim.

Usage:
  python3 tools/patch_nsmells.py                       # in place: output/batch/*.csv
  python3 tools/patch_nsmells.py --out-dir /tmp/check  # write elsewhere (dry check)
"""
import argparse
import glob
import os
from decimal import Decimal, ROUND_HALF_UP


def fmt4(x):
    """Format like Java String.format(Locale.ROOT, "%.4f", x): 4 decimals, HALF_UP rounding."""
    return str(Decimal(x).quantize(Decimal("0.0001"), rounding=ROUND_HALF_UP))

# 0-based column indices in the 35-column class-level schema.
COL_PATH = 1
COL_CLASS = 2
COL_RELEASE = 3
COL_LOC = 4
COL_NSMELLS = 7
COL_DENSITY = 8
COL_MAXNSMELLS = 22
COL_PREVNSMELLS = 32
EXPECTED_COLS = 35


def strip_slashes(p):
    """Match the pipeline's PathUtility.normalizeDatasetPath (backslash->slash, no leading/trailing /)."""
    p = p.replace("\\", "/")
    return p.strip("/")


def load_smells(smells_dir):
    """tag -> {normalized_path -> smell_count} from output/smells/<tag>.tsv."""
    out = {}
    for tsv in glob.glob(os.path.join(smells_dir, "*.tsv")):
        tag = os.path.splitext(os.path.basename(tsv))[0]
        m = {}
        with open(tsv, encoding="utf-8") as f:
            for line in f:
                line = line.rstrip("\n")
                if not line:
                    continue
                path, _, count = line.rpartition("\t")
                if path:
                    m[strip_slashes(path)] = int(count)
        out[tag] = m
    return out


def read_rows(path):
    with open(path, "rb") as f:
        raw = f.read()
    term = "\r\n" if b"\r\n" in raw else "\n"
    text = raw.decode("utf-8")
    lines = text.split(term)
    if lines and lines[-1] == "":
        lines = lines[:-1]  # drop the empty element after the trailing terminator
    header = lines[0]
    rows = [ln.split(",") for ln in lines[1:] if ln]
    return header, rows, term


def release_order(csv_path):
    """First-appearance order of ReleaseId = the pipeline's processing order for that file."""
    _, rows, _ = read_rows(csv_path)
    order, seen = [], set()
    for r in rows:
        rel = r[COL_RELEASE]
        if rel not in seen:
            seen.add(rel)
            order.append(rel)
    return order


def patch_file(csv_path, smells, selected_order, out_path):
    header, rows, term = read_rows(csv_path)
    for r in rows:
        assert len(r) == EXPECTED_COLS, f"{csv_path}: expected {EXPECTED_COLS} cols, got {len(r)}"

    # 1) NSmells + density from the TSV snapshot (keep existing when the tag has no snapshot).
    for r in rows:
        tag = r[COL_RELEASE]
        if tag in smells:
            ns = smells[tag].get(strip_slashes(r[COL_PATH]), 0)
            r[COL_NSMELLS] = str(ns)
        loc = int(r[COL_LOC])
        ns = int(r[COL_NSMELLS])
        r[COL_DENSITY] = fmt4(ns / float(max(loc, 1)))

    by_rel = {}
    for r in rows:
        by_rel.setdefault(r[COL_RELEASE], []).append(r)

    # 2) maxNSmells: persistent running max per class over the file's processing order.
    file_order = []
    seen = set()
    for r in rows:
        rel = r[COL_RELEASE]
        if rel not in seen:
            seen.add(rel)
            file_order.append(rel)
    running_max = {}
    for rel in file_order:
        for r in by_rel[rel]:
            key = (r[COL_PATH], r[COL_CLASS])
            ns = int(r[COL_NSMELLS])
            m = max(running_max.get(key, 0), ns)
            running_max[key] = m
            r[COL_MAXNSMELLS] = str(m)

    # 3) prevNSmells: previous SELECTED release's value; reset each selected release (empty included).
    prev_map = {}
    for rel in selected_order:
        cur = {}
        for r in by_rel.get(rel, []):
            key = (r[COL_PATH], r[COL_CLASS])
            r[COL_PREVNSMELLS] = str(prev_map.get(key, 0))
            cur[key] = int(r[COL_NSMELLS])
        prev_map = cur

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    out = [header] + [",".join(r) for r in rows]
    data = term.join(out) + term  # trailing terminator, matching the pipeline output
    with open(out_path, "wb") as f:
        f.write(data.encode("utf-8"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--batch-dir", default="output/batch")
    ap.add_argument("--smells-dir", default="output/smells")
    ap.add_argument("--out-dir", default=None, help="default: patch in place")
    args = ap.parse_args()

    smells = load_smells(args.smells_dir)
    print("smell snapshots loaded for tags:", sorted(smells.keys()) or "(none)")

    files = sorted(f for f in glob.glob(os.path.join(args.batch_dir, "*.csv")))
    # Canonical selected-release order per percentage (from the churn0/total/gh0 variant).
    canon = {}
    for pct in ("pct20", "pct34"):
        ref = os.path.join(args.batch_dir, f"avro_{pct}_total_gh0_churn0.csv")
        if os.path.exists(ref):
            canon[pct] = release_order(ref)

    for f in files:
        base = os.path.basename(f)
        pct = "pct20" if "pct20" in base else "pct34"
        selected_order = canon.get(pct, release_order(f))
        out_path = f if args.out_dir is None else os.path.join(args.out_dir, base)
        patch_file(f, smells, selected_order, out_path)
        print("patched", base)


if __name__ == "__main__":
    main()
