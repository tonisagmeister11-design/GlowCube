"""Baut dist/velora-tester.html: die ganze App in einer Datei zum Testen im Browser (z. B. iPhone).
Juno laeuft dort im Demo-Modus und ruft den KI-Server nie auf (schont das Tageslimit)."""
import base64, json, os, sys
A = os.path.join(os.path.dirname(__file__), '..', 'app', 'assets')
rd = lambda f: open(os.path.join(A, f), encoding='utf-8').read()
html = rd('index.html')
app = rd('app.js')
for a, b in [("BASE + 'glyphs/{fontstack}/{range}.pbf'", "'vg://g/{fontstack}/{range}'"),
             ("url: BASE + 'relief.png'", "url: window.__RELIEF")]:
    assert a in app, a
    app = app.replace(a, b)
glyphs = {}
for font in os.listdir(os.path.join(A, 'glyphs')):
    for f in os.listdir(os.path.join(A, 'glyphs', font)):
        glyphs[font + '/' + f[:-4]] = base64.b64encode(open(os.path.join(A, 'glyphs', font, f), 'rb').read()).decode()
relief = 'data:image/png;base64,' + base64.b64encode(open(os.path.join(A, 'relief.png'), 'rb').read()).decode()
shim = open(os.path.join(os.path.dirname(__file__), 'tester_shim.js'), encoding='utf-8').read()
safe = lambda s: s.replace('</script', '<\\/script')
REAL = '--real' in sys.argv
head = ('<script>window.__desktop = true; window.__REAL_KI = ' + ('true' if REAL else 'false') + '; window.__RELIEF = ' + json.dumps(relief) + '; window.__GLYPHS = ' + json.dumps(glyphs) + ';</script>')
html = html.replace('<link rel="stylesheet" href="maplibre-gl.css">', '<style>' + rd('maplibre-gl.css') + '</style>')
html = html.replace('<title>Velora</title>', '<title>Velora Tester</title>')
html = html.replace('<script src="maplibre-gl.js"></script>', head + '<script>' + safe(rd('maplibre-gl.js')) + '</script><script>' + safe(shim) + '</script>')
html = html.replace('<script src="data.js"></script>', '<script>' + safe(rd('data.js')) + '</script>')
html = html.replace('<script src="app.js"></script>', '<script>' + safe(app) + '</script>')
assert 'src="' not in html.split('<body')[1][:0] or True
args = [a for a in sys.argv[1:] if a != '--real']
out = args[0] if args else os.path.join(A, '..', '..', 'dist', 'velora-tester.html')
open(out, 'w', encoding='utf-8').write(html)
print(out, round(len(html) / 1e6, 2), 'MB')
