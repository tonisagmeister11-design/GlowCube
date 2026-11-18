import os, math
from lupa import LuaRuntime
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
lua = LuaRuntime(unpack_returned_tuples=True)
Pose = lua.execute(open(os.path.join(root, 'src/shared/Pose.lua'), encoding='utf8').read())
check = lua.eval('''function(Pose)
  local bad, n = 0, 0
  for _, ty in ipairs({"sword","spear","archer","shield","ninja","mage","giant"}) do
    for combo = 0, 3 do
      for i = 0, 40 do
        local u = { type = ty, moving = (i % 2 == 0), phase = i * 0.4, seed = 3, attacking = (i % 3 ~= 0), atkT = i / 40,
                    combo = combo, hurt = (i % 5 == 0) and 0.1 or 0, blockT = (i % 7 == 0) and 0.2 or 0, cheer = (i % 11 == 0) and 0.4 or 0, victory = (i == 40) }
        for _, dir in ipairs({1, -1}) do
          local J = Pose.solve(Pose.compute(u, i * 0.1), 1.2, dir)
          for k, v in pairs(J) do
            n = n + 1
            if type(v) == "table" then
              if v[1] ~= v[1] or v[2] ~= v[2] or math.abs(v[1]) > 500 or math.abs(v[2]) > 500 then bad = bad + 1 end
            elseif v ~= v then bad = bad + 1 end
          end
          -- Füße am Boden (außer Salto/Sprung)
        end
      end
    end
  end
  return n, bad
end''')
n, bad = check(Pose)
print("Gelenke geprüft:", n, "fehlerhaft:", bad)
