#!/usr/bin/env python3
"""Builds app/src/main/assets/cities.tsv.gz from GeoNames (CC BY 4.0, https://www.geonames.org/).

Inputs (download from https://download.geonames.org/export/dump/): cities5000.txt,
admin1CodesASCII.txt, countryInfo.txt.  Usage: build_cities.py <dir-with-inputs> <out.tsv.gz>

Output rows (tab separated, sorted by population desc):
name, asciiName, altNames(|-separated, ASCII-folded, deduped), countryCode, admin1, lat, lng, tzIndex, population
First line: '#tz' + tab-separated timezone table. Second line: '#cc' + 'CC=Country' pairs.
"""
import sys, gzip, unicodedata, re, os

src, out = sys.argv[1], sys.argv[2]
def fold(s):
    s = unicodedata.normalize('NFKD', s)
    s = ''.join(c for c in s if not unicodedata.combining(c))
    return s.lower().replace('\u2019', "'").strip()

admin1 = {}
for line in open(os.path.join(src, 'admin1CodesASCII.txt'), encoding='utf-8'):
    p = line.rstrip('\n').split('\t')
    if len(p) >= 3: admin1[p[0]] = p[2] or p[1]
countries = {}
for line in open(os.path.join(src, 'countryInfo.txt'), encoding='utf-8'):
    if line.startswith('#'): continue
    p = line.rstrip('\n').split('\t')
    if len(p) > 4: countries[p[0]] = p[4]

rows, tzs = [], {}
latin = re.compile(r"^[a-z0-9 '\-.()]+$")
for line in open(os.path.join(src, 'cities5000.txt'), encoding='utf-8'):
    p = line.rstrip('\n').split('\t')
    name, ascii_, alts, lat, lng, cc, a1, pop, tz = p[1], p[2], p[3], p[4], p[5], p[8], p[10], p[14], p[17]
    if not tz: continue
    base = {fold(name), fold(ascii_)}
    keep = []
    for a in alts.split(','):
        f = fold(a)
        if 3 < len(f) <= 40 and latin.match(f) and f not in base and f not in keep and not f.startswith('http'):
            keep.append(f)
    # GeoNames lists alternates alphabetically; keep the shortest ones (common exonyms like 'mecca')
    keep = sorted(keep, key=lambda a: (len(a), a))[:40]
    tzi = tzs.setdefault(tz, len(tzs))
    rows.append((int(pop or 0), name, ascii_, '|'.join(keep), cc, admin1.get(f'{cc}.{a1}', ''),
                 f'{float(lat):.4f}', f'{float(lng):.4f}', tzi))
rows.sort(key=lambda r: -r[0])
with gzip.open(out, 'wt', encoding='utf-8', compresslevel=9) as g:
    g.write('#tz\t' + '\t'.join(sorted(tzs, key=tzs.get)) + '\n')
    g.write('#cc\t' + '\t'.join(f'{k}={v}' for k, v in sorted(countries.items())) + '\n')
    for pop, name, ascii_, alts, cc, a1, lat, lng, tzi in rows:
        g.write('\t'.join([name.replace('\t', ' '), ascii_, alts, cc, a1, lat, lng, str(tzi), str(pop)]) + '\n')
print(len(rows), 'cities,', len(tzs), 'timezones ->', out, os.path.getsize(out), 'bytes')
