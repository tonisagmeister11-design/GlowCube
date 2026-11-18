"""Spielt Pigment Krieg (Roblox-Version) in einer Roblox-Attrappe durch (Server + Client + Spieler)."""
import os, json, sys, glob
import lupa
from lupa import LuaRuntime
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
lua = LuaRuntime(unpack_returned_tuples=True)
M = lua.execute(open(os.path.join(ROOT, 'tests/mock_roblox.lua'), encoding='utf8').read())

def to_py(v):
    if lupa.lua_type(v) == "table":
        keys = list(v.keys())
        if keys and all(isinstance(k, int) for k in keys) and sorted(keys) == list(range(1, len(keys) + 1)):
            return [to_py(v[k]) for k in range(1, len(keys) + 1)]
        return {str(k): to_py(v[k]) for k in keys}
    return v
def to_lua(v):
    if isinstance(v, dict): return lua.table_from({k: to_lua(x) for k, x in v.items()})
    if isinstance(v, list): return lua.table_from([to_lua(x) for x in v])
    return v
M.json.encode = lambda t: json.dumps(to_py(t))
M.json.decode = lambda s: to_lua(json.loads(s))

sources = {}
for p in glob.glob(os.path.join(ROOT, 'src', '**', '*.lua'), recursive=True):
    sources[os.path.relpath(p, os.path.join(ROOT, 'src')).replace(os.sep, '/')] = open(p, encoding='utf8').read()
scenario = lua.execute(open(os.path.join(ROOT, 'tests/run_game.lua'), encoding='utf8').read())
try:
    scenario(M, lua.table_from(sources), print, lambda s: to_lua(json.loads(s)))
    print('ALLES OK')
except Exception as e:
    print('ABBRUCH:', e)
    sys.exit(1)
