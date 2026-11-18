-- Pigment Krieg: Kampflogik (reines Lua, ohne Roblox-APIs -> testbar)
-- Seite 1 = links (läuft nach rechts), Seite 2 = rechts (läuft nach links).
-- Koordinaten in Spiel-Einheiten: x entlang des Schlachtfelds, y nach unten (negativ = Luft), z = Tiefe 0..34.

local Core = {}
Core.__index = Core

local function clamp(v, a, b)
	if v < a then return a elseif v > b then return b end
	return v
end
local function rand(a, b) return a + math.random() * (b - a) end
local function sign(x)
	if x < 0 then return -1 elseif x > 0 then return 1 end
	return 0
end

-- mode: "classic" | "waves" (Überleben) | "boss" (Boss-Ansturm) | "turbo"
function Core.new(Config, arenaIdx, fx, mode)
	local self = setmetatable({}, Core)
	self.mode = mode or "classic"
	self.wave = 0
	self.waveT = 8
	self.waveQueue = {}
	self.waveSpawnT = 0
	self.bossT = { 50, 50 }
	self.hill = { 0, 0 }
	self.hillOwner = 0
	self.HILL_X = Config.WORLD_W / 2
	self.HILL_R = 170
	self.C = Config
	self.W = Config.WORLD_W
	self.arenaIdx = arenaIdx
	self.arena = Config.ARENAS[arenaIdx]
	self.fx = fx or function() end
	self.units = {}
	self.projs = {}
	self.nextId = 0
	self.time = 0
	self.over = false
	self.result = nil
	self.gold = { 120, 120 }
	self.income = { 11, 11 }
	self.cmd = { "attack", "attack" }
	self.spawnCd = { {}, {} }
	self.abCd = { { meteor = 8, rage = 12, heal = 15 }, { meteor = 25, rage = 25, heal = 35 } }
	self.abLv = { { meteor = 1, rage = 1, heal = 1 }, { meteor = 1, rage = 1, heal = 1 } }
	self.rage = { 0, 0 }
	self.ai = { { on = true, save = 60, next = 2 }, { on = true, save = 80, next = 3 } }
	self.aiMul = { 1, 1 }
	self.bossDone = { false, false }
	self.boss = nil
	self.kills = {}
	self.sideKills = { 0, 0 }
	self.bases = {
		{ side = 1, x = 130, hp = 2400, maxHp = 2400, shoot = 0, tower = 0 },
		{ side = 2, x = self.W - 130, hp = 2400, maxHp = 2400, shoot = 0, tower = 0 },
	}
	self:configureSide(1, { ai = true })
	self:configureSide(2, { ai = true })
	return self
end

-- opts: ai (bool), tower (0..5), mine (0..5)
function Core:configureSide(side, opts)
	local d = self.arena.diff
	local b = self.bases[side]
	self.ai[side].on = opts.ai and true or false
	if opts.ai then
		b.maxHp = 2200 * (0.8 + d * 0.25)
		b.tower = math.max(0, math.floor(d * 1.5 + 0.5) - 1)
		self.income[side] = 10 * (0.85 + d * 0.25)
		self.aiMul[side] = 0.85 + d * 0.2
		self.abLv[side] = { meteor = d > 1.7 and 2 or 1, rage = 1, heal = 1 }
	else
		b.tower = opts.tower or 0
		b.maxHp = 2400 * (1 + b.tower * 0.15)
		self.income[side] = 11 + (opts.mine or 0) * 3
		self.aiMul[side] = 1
		if opts.ab then self.abLv[side] = opts.ab end
	end
	if self.mode == "turbo" then self.income[side] = self.income[side] * 2.2 end
	if self.mode == "boss" and opts.ai then self.income[side] = self.income[side] * 0.6 end
	if self.mode == "sudden" then
		b.maxHp = b.maxHp * 0.35
		self.gold[side] = 350
	end
	if self.mode == "waves" and side == 2 then
		-- Wellen statt normaler KI, Burg unzerstörbar
		self.ai[2].on = false
		self.income[2] = 0
		b.maxHp = 999999
	end
	b.hp = b.maxHp
end

-- Steuerung mitten im Kampf wechseln (Spieler kommt/geht), ohne Burgleben zurückzusetzen
function Core:setControl(side, ai)
	self.ai[side].on = ai and true or false
end

function Core:other(side) return 3 - side end

function Core:aliveCount(side)
	local n = 0
	for _, u in ipairs(self.units) do
		if u.side == side and not u.dead then n = n + 1 end
	end
	return n
end

------------------------------------------------------------------ Spawnen
-- p: { tier = 1..5, helmet = "none", hpMul = 1, owner = userId }
function Core:canSpawn(side, type)
	local def = self.C.UNITS[type]
	if not def or self.over then return false, "over" end
	if self.gold[side] < def.cost then return false, "gold" end
	if (self.spawnCd[side][type] or 0) > 0 then return false, "cooldown" end
	if self:aliveCount(side) >= self.C.MAX_UNITS then return false, "full" end
	return true
end

function Core:spawn(side, type, p)
	p = p or {}
	local def = self.C.UNITS[type]
	if p.free then
		if self.over or not def or self:aliveCount(side) >= self.C.MAX_UNITS then return nil, "full" end
	else
		local ok, why = self:canSpawn(side, type)
		if not ok then return nil, why end
		self.gold[side] = self.gold[side] - def.cost
		self.spawnCd[side][type] = def.spawnCd * (self.mode == "turbo" and 0.5 or 1)
	end
	local tier, helmet, hpMul
	if self.ai[side].on and not p.owner and not p.tier then
		tier = clamp(self.arenaIdx, 1, 5)
		helmet = self.arenaIdx >= 4 and "knight" or "none"
		hpMul = self.aiMul[side]
	else
		tier = p.tier or 1
		helmet = p.helmet or "none"
		hpMul = p.hpMul or 1
	end
	local b = self.bases[side]
	local dir = self.C.SIDES[side].dir
	self.nextId = self.nextId + 1
	local u = {
		id = self.nextId, side = side, type = type, def = def, dir = dir,
		x = b.x + dir * 50, z = rand(0, 34), hp = def.hp * hpMul, maxHp = def.hp * hpMul,
		dmg = def.dmg * (1 + (tier - 1) * 0.28), tier = tier, helmet = helmet, owner = p.owner,
		state = "walk", atkT = 0, cdLeft = 0.3, kbx = 0, spawnT = 0.5, blockT = 0, cheer = 0,
		scale = def.scale, moving = false, combo = 0, attackN = 0, seed = math.random() * 100, skin = p.skin,
	}
	table.insert(self.units, u)
	self.fx("spawn", u)
	return u
end

function Core:spawnBoss(side)
	local g = self.C.UNITS.giant
	local d = self.arena.diff
	local def = {}
	for k, v in pairs(g) do def[k] = v end
	def.hp = g.hp * 4.5 * (0.8 + d * 0.2)
	def.speed = 34
	def.aoe = 110
	local b = self.bases[side]
	local dir = self.C.SIDES[side].dir
	self.nextId = self.nextId + 1
	local u = {
		id = self.nextId, side = side, type = "giant", def = def, dir = dir,
		x = b.x + dir * 60, z = 18, hp = def.hp, maxHp = def.hp, dmg = g.dmg * 1.5 * (1 + (self.arenaIdx - 1) * 0.2),
		tier = clamp(self.arenaIdx + 1, 2, 5), helmet = "crown", state = "walk", atkT = 0, cdLeft = 1, kbx = 0,
		spawnT = 0.5, blockT = 0, cheer = 1.2, scale = 2.35, moving = false, combo = 0, attackN = 0, seed = 7, boss = true,
	}
	table.insert(self.units, u)
	self.boss = u
	self.bossDone[side] = true
	self.fx("spawn", u)
	self.fx("announce", { text = "BOSS ERSCHEINT!", cls = "boss" })
	self.fx("shake", { amount = 1.2 })
end

------------------------------------------------------------------ Fähigkeiten
function Core:abilityCd(side, k)
	local lv = self.abLv[side][k] or 1
	return self.C.ABILITIES[k].cd * (1 - (lv - 1) * 0.08) * (self.mode == "turbo" and 0.5 or 1)
end

function Core:canUse(side, k)
	return (not self.over) and self.countdownLeft == nil and (self.abCd[side][k] or 0) <= 0
end

function Core:useAbility(side, k, x, caster)
	if not self:canUse(side, k) then return false end
	local lv = self.abLv[side][k] or 1
	self.abCd[side][k] = self:abilityCd(side, k)
	if k == "meteor" then
		x = clamp(tonumber(x) or self.W / 2, 80, self.W - 80)
		local dir = self.C.SIDES[side].dir
		local fromX = x - dir * 520
		local p = { meteor = true, x = fromX, y = -1100, z = 18, tz = 18, vx = (x - fromX) / 0.85, vy = 1100 / 0.85, g = 0,
			side = side, dmg = 150 * (1 + (lv - 1) * 0.35), tier = 5, life = 3, aoe = 135 + lv * 10, caster = caster }
		self:addProj(p)
	elseif k == "rage" then
		self.rage[side] = 6 + lv * 2
		for _, u in ipairs(self.units) do
			if u.side == side and not u.dead then u.cheer = 0.8 end
		end
		self.fx("ability", { side = side, k = "rage" })
	elseif k == "heal" then
		local pct = 0.25 + lv * 0.15
		local ids = {}
		for _, u in ipairs(self.units) do
			if u.side == side and not u.dead then
				u.hp = math.min(u.maxHp, u.hp + u.maxHp * pct)
				table.insert(ids, u.id)
			end
		end
		local b = self.bases[side]
		b.hp = math.min(b.maxHp, b.hp + b.maxHp * 0.06 * lv)
		self.fx("ability", { side = side, k = "heal", ids = ids })
	end
	return true
end

------------------------------------------------------------------ Hauptschleife
function Core:startCountdown(t) self.countdownLeft = t end

function Core:update(dt)
	if self.countdownLeft then
		self.countdownLeft = self.countdownLeft - dt
		if self.countdownLeft <= 0 then self.countdownLeft = nil end
		return
	end
	self.time = self.time + dt
	for s = 1, 2 do
		if not self.over then self.gold[s] = self.gold[s] + self.income[s] * dt end
		for k, v in pairs(self.spawnCd[s]) do self.spawnCd[s][k] = v - dt end
		for k, v in pairs(self.abCd[s]) do self.abCd[s][k] = v - dt end
		self.rage[s] = math.max(0, self.rage[s] - dt)
	end
	if not self.over then
		for s = 1, 2 do
			if self.ai[s].on then self:runAI(s, dt) end
			local b = self.bases[s]
			if self.mode ~= "boss" and self.ai[s].on and not self.bossDone[s] and not self.ai[3 - s].on and b.hp < b.maxHp * 0.55 then
				self:spawnBoss(s)
			end
			if self.mode == "boss" and self.ai[s].on then
				self.bossT[s] = self.bossT[s] - dt
				if self.bossT[s] <= 0 and not self.boss then
					self.bossT[s] = 45
					self:spawnBoss(s)
				end
			end
		end
		if self.mode == "waves" then self:updateWaves(dt) end
		if self.mode == "hill" then self:updateHill(dt) end
	end
	for _, u in ipairs(self.units) do
		if not u.dead then self:updateUnit(u, dt) end
	end
	local keep = {}
	for _, u in ipairs(self.units) do
		if not u.dead then table.insert(keep, u) end
	end
	self.units = keep
	for _, b in ipairs(self.bases) do self:updateBase(b, dt) end
	self:updateProjs(dt)
	if not self.over then
		if self.bases[2].hp <= 0 then self:finish(1)
		elseif self.bases[1].hp <= 0 then self:finish(2) end
	end
end

------------------------------------------------------------------ Überleben (Wellen)
local WAVE_TYPES = { "sword", "sword", "spear", "archer", "shield", "ninja", "mage", "giant" }
function Core:updateWaves(dt)
	self.waveT = self.waveT - dt
	if self.waveT <= 0 then
		self.wave = self.wave + 1
		local w = self.wave
		self.waveT = 24
		local n = 3 + w * 2
		local maxIdx = math.min(#WAVE_TYPES, 3 + w)
		for i = 1, n do
			table.insert(self.waveQueue, WAVE_TYPES[math.random(1, maxIdx)])
		end
		if w % 5 == 0 then self.waveBoss = true end
		self.fx("announce", { text = "WELLE " .. w, cls = w % 5 == 0 and "boss" or "warn" })
		self.fx("wave", { wave = w })
	end
	self.waveSpawnT = self.waveSpawnT - dt
	if #self.waveQueue > 0 and self.waveSpawnT <= 0 then
		self.waveSpawnT = 0.45
		local t = table.remove(self.waveQueue, 1)
		local w = self.wave
		self:spawn(2, t, { free = true, tier = clamp(1 + math.floor(w / 3), 1, 5), helmet = w >= 6 and "knight" or "none", hpMul = 0.75 + w * 0.09 })
	elseif #self.waveQueue == 0 and self.waveBoss then
		self.waveBoss = false
		self:spawnBoss(2)
	end
end

------------------------------------------------------------------ Hügel-König
function Core:updateHill(dt)
	local c = { 0, 0 }
	for _, u in ipairs(self.units) do
		if not u.dead and math.abs(u.x - self.HILL_X) < self.HILL_R then c[u.side] = c[u.side] + 1 end
	end
	local owner = 0
	if c[1] > 0 and c[2] == 0 then owner = 1 elseif c[2] > 0 and c[1] == 0 then owner = 2 end
	if owner ~= 0 then
		self.hill[owner] = self.hill[owner] + dt * (1.2 + 0.12 * math.min(c[owner], 10))
		if self.hill[owner] >= 100 then self.hill[owner] = 100; self:finish(owner); return end
	end
	if owner ~= self.hillOwner then
		self.hillOwner = owner
		if owner ~= 0 then self.fx("announce", { text = "HÜGEL EROBERT!", cls = "warn", side = owner }) end
	end
end

function Core:finish(winner)
	self.over = true
	self.result = winner
	self.fx("over", { winner = winner, x = self.bases[3 - winner].x })
	for _, u in ipairs(self.units) do
		if not u.dead then
			if u.side == winner then u.victory = true
			else self:kill(u, self.C.SIDES[winner].dir, true, nil) end
		end
	end
end

------------------------------------------------------------------ KI
function Core:runAI(side, dt)
	local ai = self.ai[side]
	ai.next = ai.next - dt
	if ai.next > 0 then return end
	ai.next = rand(0.4, 1.0)
	local g = self.gold[side]
	local foes, mine = {}, {}
	for _, u in ipairs(self.units) do
		if not u.dead then
			if u.side == side then table.insert(mine, u) else table.insert(foes, u) end
		end
	end
	local home = self.bases[side].x
	local threat = 0
	for _, f in ipairs(foes) do if math.abs(f.x - home) < 700 then threat = threat + 1 end end
	-- Fähigkeiten
	if self.arena.diff >= 1.3 and #foes >= 4 and self:canUse(side, "meteor") then
		local best, bc = nil, 0
		for _, f in ipairs(foes) do
			local c = 0
			for _, o in ipairs(foes) do if math.abs(o.x - f.x) < 110 then c = c + 1 end end
			if c > bc then bc = c; best = f end
		end
		local friendly = false
		if best then for _, m in ipairs(mine) do if math.abs(m.x - best.x) < 90 then friendly = true end end end
		if best and bc >= 4 and not friendly then self:useAbility(side, "meteor", best.x) end
	end
	if #mine >= 7 and self:canUse(side, "rage") and math.random() < 0.3 then self:useAbility(side, "rage") end
	if self:canUse(side, "heal") then
		local hurt = 0
		for _, m in ipairs(mine) do if m.hp < m.maxHp * 0.45 then hurt = hurt + 1 end end
		if hurt >= 4 then self:useAbility(side, "heal") end
	end
	if g < ai.save and threat < 3 then return end
	local cnt = {}
	for _, f in ipairs(foes) do cnt[f.type] = (cnt[f.type] or 0) + 1 end
	local function c(t) return cnt[t] or 0 end
	local archers = 0
	for _, m in ipairs(mine) do if m.type == "archer" then archers = archers + 1 end end
	local w = {
		sword = 4, spear = 3 + c("giant") * 3, archer = archers > 6 and 0.5 or (3 + c("sword") * 0.5),
		shield = 1.5 + c("archer") * 1.2, ninja = 2 + c("archer") * 1.5 + c("mage") * 1.5,
		mage = 1.5 + c("shield") * 1.5 + c("sword") * 0.4, giant = (g > 220 and 3 or 0.5) + c("sword") * 0.6,
	}
	local tot = 0
	for _, t in ipairs(self.C.UNIT_ORDER) do tot = tot + w[t] end
	local r = math.random() * tot
	local choice = "sword"
	for _, t in ipairs(self.C.UNIT_ORDER) do
		r = r - w[t]
		if r <= 0 then choice = t; break end
	end
	if not self:spawn(side, choice) then
		if g > 40 then self:spawn(side, "sword") end
	elseif self.gold[side] < 30 then
		ai.save = rand(60, 260)
	end
end

------------------------------------------------------------------ Einheiten
function Core:updateUnit(u, dt)
	local rm = self.rage[u.side] > 0 and 1.45 or 1
	u.rage = rm > 1
	u.cdLeft = u.cdLeft - dt * rm
	u.spawnT = math.max(0, u.spawnT - dt)
	u.blockT = math.max(0, u.blockT - dt)
	u.cheer = math.max(0, u.cheer - dt)
	if u.spawnT > 0 then u.moving = false; return end
	if u.cheer > 0 and u.state ~= "attack" then u.moving = false; return end
	if math.abs(u.kbx) > 2 then
		u.x = u.x + u.kbx * dt
		u.kbx = u.kbx * (0.004 ^ dt)
	end
	u.x = clamp(u.x, 40, self.W - 40)
	if u.victory then u.moving = false; return end

	if u.state == "attack" then
		u.atkT = u.atkT + dt * rm / u.atkDur
		if not u.hitDone and u.atkT >= u.hitAt then
			u.hitDone = true
			self:doHit(u)
		end
		if u.atkT >= 1 then
			u.state = "idle"
			u.combo = u.combo + 1
		end
		u.moving = false
		return
	end

	local def = u.def
	local target = self:findTarget(u)
	local eb = self.bases[3 - u.side]
	local goal = nil
	if target then
		local dist = math.abs(target.x - u.x) - (target.isBase and 46 or 0)
		if dist <= def.range then
			u.moving = false
			u.dir = target.x >= u.x and 1 or -1
			if u.cdLeft <= 0 then self:startAttack(u, target) end
			return
		end
		goal = target.x
	end
	if goal == nil then
		if self.cmd[u.side] == "defend" then
			local home = self.C.SIDES[u.side].dir
			local rally = self.bases[u.side].x + home * (260 + (u.seed % 1) * 60 + (def.ranged and -60 or 0))
			if math.abs(rally - u.x) > 12 then goal = rally end
		else
			goal = eb.x
		end
	end
	if goal == nil then
		u.moving = false
		u.dir = self.C.SIDES[u.side].dir
		return
	end
	local d = goal > u.x and 1 or -1
	u.dir = d
	u.moving = true
	u.x = u.x + d * def.speed * rm * dt
end

function Core:findTarget(u)
	local def = u.def
	local aggro = def.ranged and (def.range + 30) or 260
	local best, bd = nil, 1e9
	local defending = self.cmd[u.side] == "defend"
	local home = self.bases[u.side].x
	local fwd = self.C.SIDES[u.side].dir
	for _, e in ipairs(self.units) do
		if e.side ~= u.side and not e.dead then
			local d = math.abs(e.x - u.x)
			if d <= aggro and not (defending and math.abs(e.x - home) > 560) then
				local score = d + (sign(e.x - u.x) ~= fwd and 40 or 0)
				if score < bd then bd = score; best = e end
			end
		end
	end
	if best then return best end
	local eb = self.bases[3 - u.side]
	if math.abs(eb.x - u.x) < def.range + 50 + (def.ranged and 0 or 10) then
		return { isBase = true, x = eb.x, base = eb }
	end
	return nil
end

function Core:startAttack(u, t)
	u.state = "attack"
	u.atkT = 0
	u.hitDone = false
	u.target = t
	u.atkDur = u.def.atk * (1 - (u.tier - 1) * 0.04)
	u.hitAt = self.C.HIT_AT[u.type]
	u.cdLeft = u.def.cd * (1 - (u.tier - 1) * 0.05) + rand(0, 0.15)
	u.attackN = u.attackN + 1
	self.fx("attack", u)
end

-- ungefähre Position der Waffenhand (für Projektile)
local function handPos(u)
	return u.x + u.dir * 14 * u.scale, -50 * u.scale
end

function Core:doHit(u)
	local t = u.target
	if not t then return end
	if u.type == "mage" or u.type == "archer" then
		if not t.isBase and t.dead then return end
		local sx, sy = handPos(u)
		if u.type == "mage" then sy = -78 * u.scale end
		local tx
		if t.isBase then tx = t.x else tx = t.x + (t.moving and t.dir * t.def.speed * (u.type == "mage" and 0.5 or 0.6) or 0) end
		tx = tx + rand(-10, 10)
		local mage = u.type == "mage"
		local T = clamp(math.abs(tx - sx) / (mage and 380 or 520), mage and 0.4 or 0.35, mage and 1.0 or 1.1)
		local gr = mage and 500 or 900
		local ty = t.isBase and -70 or (mage and -30 or -40 * (t.scale or 1))
		self:addProj({
			orb = mage, x = sx, y = sy, z = u.z, tz = t.isBase and u.z or t.z,
			vx = (tx - sx) / T, vy = (ty - sy - 0.5 * gr * T * T) / T, g = gr,
			side = u.side, dmg = u.dmg, tier = u.tier, life = 4, owner = u, aoe = mage and u.def.aoe or nil,
		})
		return
	end
	local reach = u.def.range + 22
	if u.type == "giant" then
		local hx = u.x + u.dir * 44 * u.scale
		self.fx("slam", { x = hx, z = u.z, r = u.def.aoe, boss = u.boss })
		local victims = {}
		for _, e in ipairs(self.units) do
			if e.side ~= u.side and not e.dead and math.abs(e.x - hx) < u.def.aoe then table.insert(victims, e) end
		end
		for _, e in ipairs(victims) do self:damage(e, u.dmg, u, true, false) end
		local eb = self.bases[3 - u.side]
		if math.abs(eb.x - hx) < u.def.aoe + 40 then self:damageBase(eb, u.dmg * 1.2, u) end
		return
	end
	if t.isBase then self:damageBase(t.base, u.dmg, u); return end
	if t.dead or math.abs(t.x - u.x) > reach then return end
	-- Parade
	if t.state == "attack" and t.type ~= "archer" and math.random() < 0.18 then
		self.fx("clash", { x = (u.x + t.x) / 2, y = -40 * u.scale, z = u.z })
		self:damage(t, u.dmg * 0.3, u, false, false)
		return
	end
	local heavy = u.type == "spear" or (u.type == "ninja" and u.combo % 4 == 3)
	self:damage(t, u.dmg, u, heavy, false)
end

-- src: Einheit oder { x, side, tier, aoe, owner }
function Core:damage(e, amount, src, heavy, proj)
	if e.dead then return end
	amount = amount * rand(0.88, 1.12)
	if self.mode == "sudden" then amount = amount * 1.5 end
	if src and src.side and self.rage[src.side] > 0 then amount = amount * 1.4 end
	local dir = -e.dir
	if src and src.x then dir = (e.x >= src.x) and 1 or -1 end
	if e.type == "shield" and not (src and src.aoe) and e.dir == -dir and e.state ~= "attack" then
		amount = amount * (proj and 0.25 or 0.45)
		e.blockT = 0.3
		e.kbx = e.kbx + dir * 40
		e.hp = e.hp - amount
		self.fx("hit", { id = e.id, x = e.x, y = -40 * e.scale, z = e.z, side = e.side, amount = amount, dir = dir, blocked = true, src = src and src.side })
		if e.hp <= 0 then self:kill(e, dir, false, src) end
		return
	end
	local crit = math.random() < 0.1
	if crit then amount = amount * 1.8 end
	e.hp = e.hp - amount
	e.kbx = e.kbx + dir * (heavy and 260 or 90) / e.scale
	if e.state == "attack" and heavy then e.state = "idle" end
	self.fx("hit", { id = e.id, x = e.x, y = -38 * e.scale, z = e.z, side = e.side, amount = amount, dir = dir, heavy = heavy, crit = crit, tier = src and src.tier, src = src and src.side })
	if e.hp <= 0 then self:kill(e, dir, heavy or crit, src) end
end

function Core:kill(e, dir, heavy, src)
	if e.dead then return end
	e.dead = true
	e.state = "dead"
	local power = heavy and rand(1.0, 1.5) or rand(0.4, 0.7)
	local cut = heavy and math.random() < 0.3
	if e.boss then
		e.boss = false
		self.boss = nil
		if not self.over then
			self.fx("announce", { text = "BOSS BESIEGT!", cls = "ok" })
			self.fx("shake", { amount = 1.5 })
		end
	end
	local owner = src and src.owner
	if type(owner) == "table" then owner = owner.owner end
	if owner and not self.over then self.kills[owner] = (self.kills[owner] or 0) + 1 end
	if not self.over and src and src.side then self.sideKills[src.side] = self.sideKills[src.side] + 1 end
	self.fx("death", { id = e.id, unit = e, dir = dir, power = power, cut = cut, owner = owner })
end

------------------------------------------------------------------ Explosion / Basis
function Core:explode(x, z, radius, dmg, side, big, owner)
	self.fx("explode", { x = x, z = z, r = radius, big = big, side = side })
	local victims = {}
	for _, e in ipairs(self.units) do
		if e.side ~= side and not e.dead and math.abs(e.x - x) < radius then table.insert(victims, e) end
	end
	for _, e in ipairs(victims) do
		local d = math.abs(e.x - x)
		self:damage(e, dmg * (1 - d / radius * 0.5), { x = x, side = side, tier = 5, aoe = true, owner = owner }, true, false)
	end
	local eb = self.bases[3 - side]
	if math.abs(eb.x - x) < radius + 40 then self:damageBase(eb, dmg * (big and 0.8 or 0.5), nil) end
	return #victims
end

function Core:damageBase(b, amount, src)
	if self.over then return end
	if self.mode == "waves" and b.side == 2 then return end
	b.hp = b.hp - amount
	self.fx("base", { side = b.side, amount = amount, x = b.x })
end

function Core:updateBase(b, dt)
	if self.over or b.hp <= 0 then return end
	b.shoot = b.shoot - dt
	if b.shoot > 0 then return end
	local range = 430 + b.tower * 40
	local best, bd = nil, 1e9
	for _, e in ipairs(self.units) do
		if e.side ~= b.side and not e.dead then
			local d = math.abs(e.x - b.x)
			if d < range and d < bd then bd = d; best = e end
		end
	end
	if not best then b.shoot = 0.3; return end
	b.shoot = math.max(0.7, 2.2 - b.tower * 0.3)
	local sx = b.x + (b.side == 1 and 20 or -20)
	local sy = -190
	local tx = best.x + (best.moving and best.dir * best.def.speed * 0.5 or 0)
	local T, g = 0.8, 900
	self:addProj({ x = sx, y = sy, z = best.z, tz = best.z, vx = (tx - sx) / T, vy = (-40 - sy - 0.5 * g * T * T) / T, g = g,
		side = b.side, dmg = 22 + b.tower * 10, tier = clamp(b.tower + 1, 1, 5), life = 4, bolt = true })
end

------------------------------------------------------------------ Projektile
function Core:addProj(p)
	self.nextId = self.nextId + 1
	p.id = self.nextId
	table.insert(self.projs, p)
	self.fx("proj", p)
end

function Core:updateProjs(dt)
	local keep = {}
	for _, p in ipairs(self.projs) do
		p.vy = p.vy + p.g * dt
		p.x = p.x + p.vx * dt
		p.y = p.y + p.vy * dt
		p.z = p.z + (p.tz - p.z) * math.min(1, dt * 3)
		p.life = p.life - dt
		local done = false
		if p.meteor then
			if p.y >= 0 then
				self:explode(p.x, p.z, p.aoe, p.dmg, p.side, true, p.caster)
				self.fx("shake", { amount = 2 })
				done = true
			end
		elseif p.orb then
			local hit = p.y >= 0
			for _, e in ipairs(self.units) do
				if not hit and e.side ~= p.side and not e.dead and math.abs(e.x - p.x) < 12 * e.scale and p.y > -70 * e.scale then hit = true end
			end
			local eb = self.bases[3 - p.side]
			if math.abs(eb.x - p.x) < 44 and p.y > -230 then hit = true end
			if hit then
				self:explode(p.x, p.z, p.aoe, p.dmg, p.side, false, p.owner and p.owner.owner)
				done = true
			end
		else
			for _, e in ipairs(self.units) do
				if not done and e.side ~= p.side and not e.dead then
					local h = 70 * e.scale
					if math.abs(e.x - p.x) < 10 * e.scale + 4 and p.y > -h and p.y < 2 then
						local src = p.owner or { x = p.x - sign(p.vx), side = p.side, tier = p.tier }
						self:damage(e, p.dmg, src, false, true)
						done = true
					end
				end
			end
			if not done then
				local eb = self.bases[3 - p.side]
				if math.abs(eb.x - p.x) < 44 and p.y > -230 then
					self:damageBase(eb, p.dmg * 0.8, p.owner)
					done = true
				elseif p.y >= 0 then
					done = true
				end
			end
		end
		if p.life <= 0 then done = true end
		if done then
			self.fx("projEnd", { id = p.id, x = p.x, y = math.min(p.y, 0), z = p.z })
		else
			table.insert(keep, p)
		end
	end
	self.projs = keep
end

return Core
