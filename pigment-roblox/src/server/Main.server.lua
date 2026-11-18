-- Pigment Krieg: Server. Steuert Runden, KI, Kauf-Anfragen und schickt den Zustand an alle Clients.
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")
local MarketplaceService = game:GetService("MarketplaceService")
local DataStoreService = game:GetService("DataStoreService")
local HttpService = game:GetService("HttpService")

Players.CharacterAutoLoads = false

local PK = ReplicatedStorage:WaitForChild("PK")
local Config = require(PK:WaitForChild("Config"))
local Core = require(script:WaitForChild("BattleCore"))
local Data = require(script:WaitForChild("Data"))
local Arena = require(script:WaitForChild("Arena"))

-- Netzwerk -------------------------------------------------------------
local remotes = Instance.new("Folder")
remotes.Name = "Remotes"
local FxEvent = Instance.new("RemoteEvent"); FxEvent.Name = "Fx"; FxEvent.Parent = remotes
local Action = Instance.new("RemoteEvent"); Action.Name = "Action"; Action.Parent = remotes
remotes.Parent = PK

local State = Instance.new("Folder")
State.Name = "State"
local UnitsFolder = Instance.new("Folder"); UnitsFolder.Name = "Units"; UnitsFolder.Parent = State
State.Parent = PK

local battle = nil
local phase = "lobby"
local phaseEnd = 0
local arenaIdx = 1
local mode = "classic"
local searchEnd = nil
local unitInst = {}   -- id -> Configuration
local fxQueue = {}
local lastSent = {}   -- id -> {x, hp, moving, dir}

local function setPhase(p, duration)
	phase = p
	phaseEnd = duration and (workspace:GetServerTimeNow() + duration) or 0
	State:SetAttribute("Phase", p)
	State:SetAttribute("PhaseEnd", phaseEnd)
end

local function sideOf(player) return player:GetAttribute("Side") or 0 end

local function playersOn(side)
	local list = {}
	for _, p in ipairs(Players:GetPlayers()) do
		if sideOf(p) == side then table.insert(list, p) end
	end
	return list
end

-- Effekte bündeln: ein Remote-Aufruf pro Frame --------------------------
local function round1(x) return math.floor(x * 10 + 0.5) / 10 end

local function onFx(kind, d)
	if kind == "spawn" then
		local u = d
		local c = Instance.new("Configuration")
		c.Name = tostring(u.id)
		c:SetAttribute("Type", u.type)
		c:SetAttribute("Side", u.side)
		c:SetAttribute("X", round1(u.x))
		c:SetAttribute("Z", round1(u.z))
		c:SetAttribute("HP", math.ceil(u.hp))
		c:SetAttribute("MaxHP", math.ceil(u.maxHp))
		c:SetAttribute("Tier", u.tier)
		c:SetAttribute("Helmet", u.helmet)
		c:SetAttribute("Scale", u.scale)
		c:SetAttribute("Boss", u.boss == true)
		c:SetAttribute("Speed", u.def.speed)
		c:SetAttribute("Moving", false)
		c:SetAttribute("Dir", u.dir)
		c:SetAttribute("Cheer", u.cheer > 0)
		c:SetAttribute("Skin", u.skin or "")
		c.Parent = UnitsFolder
		unitInst[u.id] = c
		lastSent[u.id] = { x = u.x, hp = u.hp, moving = false, dir = u.dir }
		return
	elseif kind == "attack" then
		table.insert(fxQueue, { "attack", d.id, d.attackN, d.atkDur, d.combo })
		return
	elseif kind == "death" then
		local c = unitInst[d.id]
		if c then
			c:SetAttribute("HP", 0)
			c:SetAttribute("X", round1(d.unit.x))
			unitInst[d.id] = nil
			lastSent[d.id] = nil
			task.delay(3, function() c:Destroy() end)
		end
		table.insert(fxQueue, { "death", d.id, d.dir, round1(d.power), d.cut, d.owner or 0 })
		return
	elseif kind == "proj" then
		local k = d.meteor and "meteor" or (d.orb and "orb" or (d.bolt and "bolt" or "arrow"))
		table.insert(fxQueue, { "proj", d.id, k, round1(d.x), round1(d.y), round1(d.z), round1(d.tz), round1(d.vx), round1(d.vy), d.g, d.side, d.tier })
		return
	elseif kind == "wave" then
		State:SetAttribute("Wave", d.wave)
		return
	elseif kind == "over" then
		table.insert(fxQueue, { "over", d.winner })
		Arena.collapse(3 - d.winner)
		return
	end
	-- alles andere als Tabelle ohne Einheiten-Verweise weiterreichen
	local copy = { kind }
	local t = {}
	for k, v in pairs(d) do
		local tv = type(v)
		if tv == "number" or tv == "string" or tv == "boolean" then t[k] = v
		elseif k == "ids" then t[k] = v end
	end
	copy[2] = t
	table.insert(fxQueue, copy)
end

-- Runden ----------------------------------------------------------------
local function chooseArena()
	local votes = {}
	for _, p in ipairs(Players:GetPlayers()) do
		local v = p:GetAttribute("Vote")
		if v and Config.ARENAS[v] then votes[v] = (votes[v] or 0) + 1 end
	end
	local best, bc = nil, 0
	for i = 1, #Config.ARENAS do
		if (votes[i] or 0) > bc then best = i; bc = votes[i] end
	end
	return best or arenaIdx
end

local function chooseMode()
	local votes = {}
	for _, p in ipairs(Players:GetPlayers()) do
		local v = p:GetAttribute("ModeVote")
		if v and Config.MODES[v] then votes[v] = (votes[v] or 0) + 1 end
	end
	local best, bc = nil, 0
	for _, m in ipairs(Config.MODE_ORDER) do
		if (votes[m] or 0) > bc then best = m; bc = votes[m] end
	end
	return best or "classic"
end

-- stärkste Werte der Spieler auf einer Seite (Turm, Mine, Fähigkeiten)
local function sideOpts(side)
	local list = playersOn(side)
	if #list == 0 then return { ai = true } end
	local o = { ai = false, tower = 0, mine = 0, ab = { meteor = 1, rage = 1, heal = 1 } }
	for _, p in ipairs(list) do
		local d = Data.get(p)
		if d then
			o.tower = math.max(o.tower, d.tower)
			o.mine = math.max(o.mine, d.mine)
			for k in pairs(o.ab) do o.ab[k] = math.max(o.ab[k], d.ab[k]) end
		end
	end
	return o
end

local function clearUnits()
	for _, c in ipairs(UnitsFolder:GetChildren()) do c:Destroy() end
	unitInst = {}
	lastSent = {}
end

local function startRound()
	clearUnits()
	arenaIdx = chooseArena()
	mode = chooseMode()
	-- Überleben: alle spielen zusammen im Team Schwarz
	if mode == "waves" then
		for _, p in ipairs(Players:GetPlayers()) do
			if sideOf(p) == 2 then p:SetAttribute("Side", 1) end
		end
	end
	for _, p in ipairs(Players:GetPlayers()) do p:SetAttribute("Ready", false) end
	searchEnd = nil
	State:SetAttribute("SearchEnd", 0)
	State:SetAttribute("Arena", arenaIdx)
	State:SetAttribute("Mode", mode)
	State:SetAttribute("Wave", 0)
	State:SetAttribute("Winner", 0)
	Arena.build(arenaIdx)
	battle = Core.new(Config, arenaIdx, onFx, mode)
	for s = 1, 2 do battle:configureSide(s, sideOpts(s)) end
	battle:startCountdown(4)
	setPhase("countdown", 4)
	table.insert(fxQueue, { "round", arenaIdx })
end

-- Ranglisten -------------------------------------------------------------
local winsStore = nil
pcall(function() winsStore = DataStoreService:GetOrderedDataStore("PK_Wins_v1") end)
local nameCache = {}

local function updateLeaderstats(player)
	local d = Data.get(player)
	if not d then return end
	local ls = player:FindFirstChild("leaderstats")
	if not ls then
		ls = Instance.new("Folder")
		ls.Name = "leaderstats"
		local w = Instance.new("IntValue"); w.Name = "Wins"; w.Parent = ls
		local k = Instance.new("IntValue"); k.Name = "Kills"; k.Parent = ls
		ls.Parent = player
	end
	ls.Wins.Value = d.wins
	ls.Kills.Value = d.kills
end

local function refreshTop()
	if not winsStore then return end
	local ok, pages = pcall(function() return winsStore:GetSortedAsync(false, 10) end)
	if not ok or not pages then return end
	local list = {}
	for _, e in ipairs(pages:GetCurrentPage()) do
		local id = tonumber(e.key)
		local name = id and nameCache[id]
		if id and not name then
			local okN, n = pcall(function() return Players:GetNameFromUserIdAsync(id) end)
			name = okN and n or ("#" .. tostring(id))
			nameCache[id] = name
		end
		table.insert(list, { n = name or "?", w = e.value })
	end
	State:SetAttribute("Top", HttpService:JSONEncode(list))
end
task.spawn(function()
	while true do
		refreshTop()
		task.wait(60)
	end
end)

local function endRound(winner)
	State:SetAttribute("Winner", winner)
	local a = Config.ARENAS[arenaIdx]
	for _, p in ipairs(Players:GetPlayers()) do
		local s = sideOf(p)
		if s == 1 or s == 2 then
			local kills = battle.kills[p.UserId] or 0
			local won = s == winner
			local coins
			if mode == "waves" then
				won = false
				coins = 20 + battle.wave * 18 + kills * 3
				Data.setBestWave(p, battle.wave)
			else
				coins = (won and a.reward or 25) + kills * 3
				if mode == "boss" and won then coins = coins + 60 end
			end
			local vip = p:GetAttribute("Pass_vip") == true
			if vip then coins = coins * 2 end
			Data.reward(p, coins, won, kills)
			updateLeaderstats(p)
			if won and winsStore then
				local dd = Data.get(p)
				task.spawn(function() pcall(function() winsStore:SetAsync(tostring(p.UserId), dd.wins) end) end)
			end
			FxEvent:FireClient(p, { { "reward", { coins = coins, won = won, kills = kills, wave = battle.wave, vip = vip } } })
		end
	end
	-- Nach dem Kampf: Spieler entscheiden selbst (Nochmal oder Menü). Nach 30 s geht es zurück ins Menü.
	setPhase("end", 30)
end

-- Spieler-Aktionen ------------------------------------------------------
local lastAction = {}
Action.OnServerEvent:Connect(function(player, kind, a, b)
	-- einfache Flutbremse
	local now = os.clock()
	local la = lastAction[player] or 0
	if now - la < 0.05 then return end
	lastAction[player] = now

	local d = Data.get(player)
	if not d then return end
	local side = sideOf(player)

	local function joinSide(s)
		if mode == "waves" and battle and not battle.over and phase ~= "end" then s = 1 end
		player:SetAttribute("Side", s)
		player:SetAttribute("Ready", false)
		if battle and not battle.over and (phase == "battle" or phase == "countdown") then
			local wasAI = battle.ai[s].on
			local o = sideOpts(s)
			battle:setControl(s, false)
			battle.abLv[s] = o.ab
			if wasAI and #playersOn(3 - s) > 0 then
				table.insert(fxQueue, { "announce", { text = "EIN GEGNER IST BEIGETRETEN!", cls = "boss" } })
			end
		end
	end
	if kind == "side" and (a == 1 or a == 2) then
		player:SetAttribute("Training", false)
		joinSide(a)
	elseif kind == "play" then
		-- Schnelles Spiel: ins Team mit weniger Spielern
		player:SetAttribute("Training", false)
		local n1, n2 = #playersOn(1), #playersOn(2)
		if sideOf(player) == 1 then n1 = n1 - 1 elseif sideOf(player) == 2 then n2 = n2 - 1 end
		joinSide(n2 < n1 and 2 or 1)
	elseif kind == "training" then
		player:SetAttribute("Training", true)
		if sideOf(player) == 0 then joinSide(1) end
	elseif kind == "vote" and Config.ARENAS[a] then
		player:SetAttribute("Vote", a)
	elseif kind == "modeVote" and type(a) == "string" and Config.MODES[a] then
		player:SetAttribute("ModeVote", a)
	elseif kind == "again" and phase == "end" and (side == 1 or side == 2) then
		player:SetAttribute("Ready", true)
	elseif kind == "menu" then
		player:SetAttribute("Side", 0)
		player:SetAttribute("Ready", false)
	elseif kind == "spawn" and battle and (side == 1 or side == 2) and Config.UNITS[a] and phase == "battle" then
		battle:spawn(side, a, { tier = d.wlv[a], helmet = d.helmet, hpMul = 1 + d.armor * 0.15, owner = player.UserId,
			skin = player:GetAttribute("Pass_rainbow") and "rainbow" or nil })
	elseif kind == "ability" and battle and (side == 1 or side == 2) and Config.ABILITIES[a] and phase == "battle" then
		battle.abLv[side][a] = math.max(battle.abLv[side][a] or 1, d.ab[a])
		battle:useAbility(side, a, tonumber(b), player.UserId)
	elseif kind == "cmd" and battle and (side == 1 or side == 2) and (a == "attack" or a == "defend") then
		battle.cmd[side] = a
	elseif kind == "helmet" and type(a) == "string" then
		Data.buyHelmet(player, a)
	elseif kind == "upgrade" and type(a) == "string" then
		Data.upgradeWeapon(player, a)
	elseif kind == "abilityUp" and type(a) == "string" then
		Data.upgradeAbility(player, a)
	elseif kind == "extra" and type(a) == "string" then
		Data.upgradeExtra(player, a)
	end
end)

-- Robux: Game-Pässe prüfen ----------------------------------------------
local function checkPasses(player)
	for key, pass in pairs(Config.PASSES) do
		if pass.id ~= 0 then
			local ok, owns = pcall(function() return MarketplaceService:UserOwnsGamePassAsync(player.UserId, pass.id) end)
			if ok and owns then player:SetAttribute("Pass_" .. key, true) end
		end
	end
	if player:GetAttribute("Pass_vip") then
		local d = Data.get(player)
		if d and not d.owned.vipcrown then d.owned.vipcrown = true; Data.publish(player) end
	end
end
MarketplaceService.PromptGamePassPurchaseFinished:Connect(function(player, passId, bought)
	if not bought then return end
	for key, pass in pairs(Config.PASSES) do
		if pass.id == passId then player:SetAttribute("Pass_" .. key, true) end
	end
	checkPasses(player)
end)

-- Robux: Entwickler-Produkte (Münzen, Premium-Helme) ----------------------
MarketplaceService.ProcessReceipt = function(info)
	local player = Players:GetPlayerByUserId(info.PlayerId)
	if not player or not Data.get(player) then return Enum.ProductPurchaseDecision.NotProcessedYet end
	for key, prod in pairs(Config.PRODUCTS) do
		if prod.id ~= 0 and prod.id == info.ProductId then
			if Data.grantProduct(player, key, info.PurchaseId) then
				FxEvent:FireClient(player, { { "bought", { name = prod.name } } })
				return Enum.ProductPurchaseDecision.PurchaseGranted
			end
			return Enum.ProductPurchaseDecision.NotProcessedYet
		end
	end
	return Enum.ProductPurchaseDecision.NotProcessedYet
end

local function welcome(player)
	updateLeaderstats(player)
	local r = Data.daily(player)
	if r then
		task.delay(4, function()
			if player.Parent then FxEvent:FireClient(player, { { "daily", r } }) end
		end)
	end
end

Players.PlayerAdded:Connect(function(player)
	player:SetAttribute("Side", 0)
	Data.load(player)
	checkPasses(player)
	welcome(player)
	if player.Character then player.Character:Destroy() end
end)
for _, p in ipairs(Players:GetPlayers()) do
	task.spawn(function()
		p:SetAttribute("Side", 0)
		Data.load(p)
		checkPasses(p)
		welcome(p)
	end)
end
Players.PlayerRemoving:Connect(function(player)
	local s = sideOf(player)
	Data.release(player)
	lastAction[player] = nil
	task.defer(function()
		if battle and (s == 1 or s == 2) and #playersOn(s) == 0 then battle:setControl(s, true) end
	end)
end)

-- Zustand an Clients ----------------------------------------------------
local syncAcc = 0
local function sync()
	for s = 1, 2 do
		State:SetAttribute("Gold" .. s, math.floor(battle.gold[s]))
		State:SetAttribute("BaseHP" .. s, math.max(0, math.ceil(battle.bases[s].hp)))
		State:SetAttribute("BaseMax" .. s, math.ceil(battle.bases[s].maxHp))
		State:SetAttribute("Cmd" .. s, battle.cmd[s])
		State:SetAttribute("Rage" .. s, round1(battle.rage[s]))
		State:SetAttribute("AI" .. s, battle.ai[s].on)
		State:SetAttribute("Kills" .. s, battle.sideKills[s])
		for _, t in ipairs(Config.UNIT_ORDER) do
			State:SetAttribute("Cd" .. s .. "_" .. t, round1(math.max(0, battle.spawnCd[s][t] or 0)))
		end
		for _, k in ipairs(Config.ABILITY_ORDER) do
			State:SetAttribute("Ab" .. s .. "_" .. k, round1(math.max(0, battle.abCd[s][k] or 0)))
			State:SetAttribute("AbMax" .. s .. "_" .. k, round1(battle:abilityCd(s, k)))
		end
	end
	State:SetAttribute("BossId", battle.boss and battle.boss.id or 0)
	State:SetAttribute("Hill1", math.floor(battle.hill[1]))
	State:SetAttribute("Hill2", math.floor(battle.hill[2]))
	for _, u in ipairs(battle.units) do
		local c = unitInst[u.id]
		local ls = lastSent[u.id]
		if c and ls then
			if math.abs(ls.x - u.x) > 0.5 then c:SetAttribute("X", round1(u.x)); ls.x = u.x end
			if math.abs(ls.hp - u.hp) > 0.5 then c:SetAttribute("HP", math.max(0, math.ceil(u.hp))); ls.hp = u.hp end
			if ls.moving ~= u.moving then c:SetAttribute("Moving", u.moving); ls.moving = u.moving end
			if ls.dir ~= u.dir then c:SetAttribute("Dir", u.dir); ls.dir = u.dir end
			local ch = u.cheer > 0
			if c:GetAttribute("Cheer") ~= ch then c:SetAttribute("Cheer", ch) end
			local vic = u.victory == true
			if vic and not c:GetAttribute("Victory") then c:SetAttribute("Victory", true) end
		end
	end
end

RunService.Heartbeat:Connect(function(dt)
	dt = math.min(dt, 0.1)
	local now = workspace:GetServerTimeNow()
	local anyPlayer = #playersOn(1) + #playersOn(2) > 0

	if phase == "lobby" then
		local h1, h2 = #playersOn(1), #playersOn(2)
		local training = false
		for _, p in ipairs(Players:GetPlayers()) do
			if p:GetAttribute("Training") and sideOf(p) ~= 0 then training = true end
		end
		if h1 + h2 == 0 then
			if searchEnd then searchEnd = nil; State:SetAttribute("SearchEnd", 0) end
		elseif (h1 > 0 and h2 > 0) or training or chooseMode() == "waves" then
			startRound()
		else
			if not searchEnd then
				searchEnd = now + Config.SEARCH_TIME
				State:SetAttribute("SearchEnd", searchEnd)
			end
			if now >= searchEnd then startRound() end
		end
	elseif phase == "countdown" or phase == "battle" then
		battle:update(dt)
		if phase == "countdown" and now >= phaseEnd then setPhase("battle") end
		if battle.over and phase == "battle" then endRound(battle.result) end
		if not anyPlayer and phase == "battle" and not battle.over then
			-- niemand spielt mehr: Runde abbrechen
			clearUnits()
			setPhase("lobby")
		end
	elseif phase == "end" then
		battle:update(dt)
		local sided, ready = 0, 0
		for _, p in ipairs(Players:GetPlayers()) do
			local s = sideOf(p)
			if s == 1 or s == 2 then
				sided = sided + 1
				if p:GetAttribute("Ready") then ready = ready + 1 end
			end
		end
		if sided == 0 then
			clearUnits(); setPhase("lobby")
		elseif ready == sided then
			startRound()
		elseif now >= phaseEnd then
			-- wer nicht "Nochmal" gedrückt hat, kommt ins Menü
			for _, p in ipairs(Players:GetPlayers()) do
				if not p:GetAttribute("Ready") then p:SetAttribute("Side", 0) end
			end
			if ready > 0 then startRound() else clearUnits(); setPhase("lobby") end
		end
	end

	if battle then
		syncAcc = syncAcc + dt
		if syncAcc >= 1 / 12 then
			syncAcc = 0
			sync()
		end
	end
	if #fxQueue > 0 then
		FxEvent:FireAllClients(fxQueue)
		fxQueue = {}
	end
end)

setPhase("lobby")
State:SetAttribute("Arena", arenaIdx)
Arena.build(arenaIdx)
print("[PigmentKrieg] Server bereit")
