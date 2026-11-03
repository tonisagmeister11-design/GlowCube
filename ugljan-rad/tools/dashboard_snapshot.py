#!/usr/bin/env python3
"""Baut das Dashboard-Artefakt: holt die anonymen Summen vom Server und setzt sie in die Vorlage ein.
Aufruf: python3 tools/dashboard_snapshot.py <ausgabe.html>   (Schluessel in ~/.velora-stats-key)"""
import json, os, sys, urllib.request
key = open(os.path.expanduser('~/.velora-stats-key')).read().strip()
req = urllib.request.Request('https://velora-ki.toni-sagmeister11.workers.dev/admin/api/summary', headers={'X-Stats-Key': key, 'User-Agent': 'BikeOra-Dashboard'})
data = json.load(urllib.request.urlopen(req, timeout=30))
tpl = open(os.path.join(os.path.dirname(__file__), 'dashboard_template.html'), encoding='utf-8').read()
out = tpl.replace('/*DATA*/{}', json.dumps(data, ensure_ascii=False).replace('</', '<\\/'))
open(sys.argv[1], 'w', encoding='utf-8').write(out)
print(sys.argv[1], data['nutzer'], 'Konten')
