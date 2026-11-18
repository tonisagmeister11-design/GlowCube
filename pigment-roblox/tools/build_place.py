"""Baut PigmentKrieg.rbxlx (Roblox-Place-Datei) aus den Lua-Quellen in src/.
Aufruf:  python3 tools/build_place.py
Die fertige Datei kann man in Roblox Studio per Doppelklick öffnen."""
import os, itertools
from xml.sax.saxutils import escape

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'src')
_ids = itertools.count(1)

def ref():
    return 'RBX%032X' % next(_ids)

def read(rel):
    with open(os.path.join(SRC, rel), encoding='utf-8') as f:
        return f.read()

def cdata(text):
    return '<![CDATA[' + text.replace(']]>', ']]]]><![CDATA[>') + ']]>'

def item(cls, name, children=(), source=None):
    props = '<string name="Name">%s</string>' % escape(name)
    if source is not None:
        props += '<ProtectedString name="Source">%s</ProtectedString>' % cdata(source)
    inner = ''.join(children)
    return '<Item class="%s" referent="%s"><Properties>%s</Properties>%s</Item>' % (cls, ref(), props, inner)

def module(name, rel):
    return item('ModuleScript', name, source=read(rel))

tree = [
    item('ReplicatedStorage', 'ReplicatedStorage', [
        item('Folder', 'PK', [module('Config', 'shared/Config.lua'), module('Pose', 'shared/Pose.lua'), module('Lang', 'shared/Lang.lua')]),
    ]),
    item('ServerScriptService', 'ServerScriptService', [
        item('Script', 'PigmentKriegServer', [
            module('BattleCore', 'server/BattleCore.lua'),
            module('Data', 'server/Data.lua'),
            module('Arena', 'server/Arena.lua'),
            module('Rooms', 'server/Rooms.lua'),
        ], source=read('server/Main.server.lua')),
    ]),
    item('StarterPlayer', 'StarterPlayer', [
        item('StarterPlayerScripts', 'StarterPlayerScripts', [
            item('LocalScript', 'PigmentKriegClient', [
                module('Render', 'client/Render.lua'),
                module('UI', 'client/UI.lua'),
            ], source=read('client/Main.client.lua')),
        ]),
    ]),
]

xml = ('<roblox xmlns:xmime="http://www.w3.org/2005/05/xmlmime" '
       'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
       'xsi:noNamespaceSchemaLocation="http://www.roblox.com/roblox.xsd" version="4">'
       '<External>null</External><External>nil</External>' + ''.join(tree) + '</roblox>')
out = os.path.join(ROOT, 'PigmentKrieg.rbxlx')
with open(out, 'w', encoding='utf-8') as f:
    f.write(xml)
print('geschrieben:', out, len(xml) // 1024, 'KB')
