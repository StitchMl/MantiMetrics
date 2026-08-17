#!/usr/bin/env python3
"""
Convert the class-level dataset CSVs into Weka ARFF files.

Attribute typing (per course guidance):
  - Project, Path, Class, ReleaseId  -> string   (traceability metadata; ignore/Remove before training)
  - prevBuggy, Buggy                 -> nominal {yes,no}   (Buggy is the last attribute / class)
  - everything else                  -> numeric

Reads output/batch/*.csv and writes output/arff/<stem>.arff.
"""
import argparse
import csv
import glob
import os

STRING_COLS = {"Project", "Path", "Class", "ReleaseId"}
NOMINAL_COLS = {"prevBuggy", "Buggy"}  # values are yes/no


def arff_type(col):
    if col in STRING_COLS:
        return "string"
    if col in NOMINAL_COLS:
        return "{yes,no}"
    return "numeric"


def quote(value):
    """ARFF-quote a string value (single quotes, escape internal quotes/backslashes)."""
    v = value.replace("\\", "\\\\").replace("'", "\\'")
    return "'" + v + "'"


def convert(csv_path, out_path):
    with open(csv_path, newline="", encoding="utf-8") as f:
        reader = csv.reader(f)
        header = next(reader)
        rows = list(reader)

    relation = os.path.splitext(os.path.basename(csv_path))[0]
    lines = ["@relation " + quote(relation), ""]
    for col in header:
        lines.append(f"@attribute {quote(col)} {arff_type(col)}")
    lines.append("")
    lines.append("@data")

    for r in rows:
        cells = []
        for col, val in zip(header, r):
            val = val.strip()
            if val == "":
                cells.append("?")
            elif col in STRING_COLS:
                cells.append(quote(val))
            else:
                cells.append(val)  # numeric or nominal yes/no
        lines.append(",".join(cells))

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    with open(out_path, "w", encoding="utf-8", newline="") as f:
        f.write("\n".join(lines) + "\n")
    return relation, len(header), len(rows)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--batch-dir", default="output/batch")
    ap.add_argument("--out-dir", default="output/arff")
    args = ap.parse_args()

    for csv_path in sorted(glob.glob(os.path.join(args.batch_dir, "*.csv"))):
        stem = os.path.splitext(os.path.basename(csv_path))[0]
        out_path = os.path.join(args.out_dir, stem + ".arff")
        rel, ncol, nrow = convert(csv_path, out_path)
        print(f"{rel}: {ncol} attrs, {nrow} instances -> {out_path}")


if __name__ == "__main__":
    main()
