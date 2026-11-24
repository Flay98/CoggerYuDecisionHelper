#!/usr/bin/env python3
"""Extract one generated case for inspection; standard library only, no matrix computation."""
import argparse
import csv
import gzip
import json
from pathlib import Path


def find(path, case_id, output=False):
    with gzip.open(path, 'rt', encoding='utf-8') as handle:
        for line in handle:
            value = json.loads(line)
            if (value['run']['id'] if output else value['id']) == case_id:
                return value
    raise ValueError(f'Case {case_id} not found in {path}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--id', required=True)
    parser.add_argument('--profile', choices=['SMOKE', 'REPORT'], default='REPORT')
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    original = find(args.root / 'inputs' / args.profile / 'matrices.jsonl.gz', args.id)
    corrected = find(args.root / 'outputs' / args.profile / 'results.jsonl.gz', args.id, True)
    if original['sha256'] != corrected['run']['inputSha256']:
        raise ValueError('Input/output hash references disagree')
    args.output_dir.mkdir(parents=True, exist_ok=True)
    for name, record in [('original_matrix.csv', original), ('corrected_matrix.csv', corrected)]:
        with (args.output_dir / name).open('w', encoding='utf-8', newline='') as handle:
            csv.writer(handle, lineterminator='\n').writerows(record['matrix'])
    for name, record in [('input.json', original), ('result.json', corrected)]:
        (args.output_dir / name).write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f"Extracted {args.id}; reconstructed CSV doubles, preserved original archive metadata/histories")


if __name__ == '__main__':
    main()
