# Simuliert komplette Kämpfe der Kampflogik ohne Roblox (Lua 5.4 über lupa).
import sys, os
from lupa import LuaRuntime
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
lua = LuaRuntime(unpack_returned_tuples=True)
cfg = lua.execute(open(os.path.join(root, 'src/shared/Config.lua'), encoding='utf8').read())
Core = lua.execute(open(os.path.join(root, 'src/server/BattleCore.lua'), encoding='utf8').read())
run = lua.eval('''function(Core, cfg, arena, human, seed)
  math.randomseed(seed)
  local counts = {}
  local fx = function(kind, d) counts[kind] = (counts[kind] or 0) + 1 end
  local b = Core.new(cfg, arena, fx)
  if human then
    b:configureSide(1, { ai = false, tower = 2, mine = 2, ab = { meteor = 2, rage = 2, heal = 2 } })
  end
  b:startCountdown(3)
  local t = 0
  local order = cfg.UNIT_ORDER
  while not b.over and t < 900 do
    b:update(1/30); t = t + 1/30
    if human then
      local k = order[math.random(1, #order)]
      b:spawn(1, k, { tier = 3, helmet = "face", owner = 42 })
      if b:canUse(1, "meteor") then b:useAbility(1, "meteor", b.bases[2].x - 300, 42) end
      if b:canUse(1, "rage") and math.random() < 0.01 then b:useAbility(1, "rage") end
      if b:canUse(1, "heal") and math.random() < 0.01 then b:useAbility(1, "heal") end
      if math.random() < 0.002 then b.cmd[1] = (b.cmd[1] == "attack") and "defend" or "attack" end
    end
  end
  for i = 1, 90 do b:update(1/30) end
  local s = ""
  for k, v in pairs(counts) do s = s .. k .. "=" .. v .. " " end
  return b.result, t, b.bases[1].hp, b.bases[2].hp, b.kills[42] or 0, s
end''')
fails = 0
for arena in range(1, 6):
    for human in (False, True):
        for seed in (1, 2):
            r = run(Core, cfg, arena, human, seed)
            print(f"arena {arena} human={human} seed={seed}: winner={r[0]} time={r[1]:.0f}s base1={r[2]:.0f} base2={r[3]:.0f} kills42={r[4]}")
            if r[0] is None: fails += 1
print("FX:", r[5])
print("unentschieden/hängt:", fails)
