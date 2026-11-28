#!/usr/bin/env python3
"""Builds launcher/app/src/main/res/raw/ayat.json from Quran.com, so no verse text is typed by hand.

Arabic: text_imlaei. Translations: 39 (Malay, Abdullah Muhammad Basmeih), 20 (English, Sahih International).
Keeps the first COUNT verses from verses.txt whose Arabic and translations fit on Home (Arabic two lines, translation three).
Run: python3 tools/ayat/fetch.py
"""
import json, os, re, sys, time, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', '..', 'launcher', 'app', 'src', 'main', 'res', 'raw', 'ayat.json')
API = 'https://api.quran.com/api/v4'
COUNT, MAX_AR, MAX_TR = 60, 140, 300


def get(path):
    for attempt in range(4):
        try:
            req = urllib.request.Request(API + path, headers={'User-Agent': 'glass-tv-ayat/1.0', 'Accept': 'application/json'})
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.load(r)
        except Exception as e:
            if attempt == 3:
                raise
            time.sleep(2 * (attempt + 1))


def clean(text):
    text = re.sub(r'<sup[^>]*>.*?</sup>', '', text)
    text = re.sub(r'<[^>]+>', '', text)
    return re.sub(r'\s+', ' ', text).strip().rstrip(',;:')


def main():
    refs = [l.strip() for l in open(os.path.join(HERE, 'verses.txt'), encoding='utf-8')
            if l.strip() and not l.startswith('#')]
    names = {c['id']: c['name_simple'] for c in get('/chapters?language=en')['chapters']}
    out, skipped = [], []
    for ref in refs:
        v = get('/verses/by_key/%s?translations=39,20&fields=text_imlaei' % ref)['verse']
        tr = {t['resource_id']: clean(t['text']) for t in v['translations']}
        ar = v['text_imlaei'].strip()
        if not ar or 39 not in tr or 20 not in tr:
            sys.exit('missing text for ' + ref)
        if len(ar) > MAX_AR or len(tr[39]) > MAX_TR or len(tr[20]) > MAX_TR:
            skipped.append('%s (ar %d, ms %d, en %d)' % (ref, len(ar), len(tr[39]), len(tr[20])))
            continue
        surah = names[int(ref.split(':')[0])]
        out.append({'ref': ref, 'surah': surah, 'ar': ar, 'ms': tr[39], 'en': tr[20]})
        if len(out) == COUNT:
            break
        time.sleep(0.2)
    with open(OUT, 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, indent=0)
        f.write('\n')
    print('kept %d verses -> %s' % (len(out), os.path.relpath(OUT)))
    for s in skipped:
        print('skipped (too long):', s)


if __name__ == '__main__':
    main()
