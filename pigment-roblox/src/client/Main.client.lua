-- Pigment Krieg: Client. Kamera, Steuerung, Animation aller Einheiten, Effekte.
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")
local UserInputService = game:GetService("UserInputService")
local SoundService = game:GetService("SoundService")
local Debris = game:GetService("Debris")

local player = Players.LocalPlayer
local PK = ReplicatedStorage:WaitForChild("PK")
local Config = require(PK:WaitForChild("Config"))
local Pose = require(PK:WaitForChild("Pose"))
local Lang = require(PK:WaitForChild("Lang"))
local T = Lang.t
local Render = require(script:WaitForChild("Render"))
local UI = require(script:WaitForChild("UI"))
local remotes = PK:WaitForChild("Remotes")
local FxEvent = remotes:WaitForChild("Fx")
local Action = remotes:WaitForChild("Action")
local State = PK:WaitForChild("State")
local UnitsFolder = State:WaitForChild("Units")

local K = Config.K
local WORLD = Config.WORLD_W * K
local clamp, lerp, easeIn = Pose.clamp, Pose.lerp, Pose.easeIn

UI.init({ Render = Render, Action = Action, State = State })

------------------------------------------------------------------ Töne
local SOUNDS = {
	swing = "rbxasset://sounds/swordslash.wav",
	lunge = "rbxasset://sounds/swordlunge.wav",
	spawn = "rbxasset://sounds/unsheath.wav",
	hit = "rbxasset://sounds/collide.wav",
	ping = "rbxasset://sounds/electronicpingshort.wav",
	boom = "rbxasset://sounds/Rocket shot.wav",
}
local lastSound = {}
local function sound(name, vol, pitch, minGap)
	local t = os.clock()
	if lastSound[name] and t - lastSound[name] < (minGap or 0.05) then return end
	lastSound[name] = t
	local s = Instance.new("Sound")
	s.SoundId = SOUNDS[name]
	s.Volume = vol or 0.5
	s.PlaybackSpeed = (pitch or 1) * (0.92 + math.random() * 0.16)
	s.Parent = SoundService
	s:Play()
	Debris:AddItem(s, 3)
end

------------------------------------------------------------------ Einheiten
local units = {}
local time = 0

local function mySide() return player:GetAttribute("Side") or 0 end

local function addUnit(cfg)
	local id = tonumber(cfg.Name)
	if not id or units[id] then return end
	local info = {
		type = cfg:GetAttribute("Type"), side = cfg:GetAttribute("Side"), tier = cfg:GetAttribute("Tier") or 1,
		helmet = cfg:GetAttribute("Helmet") or "none", scale = cfg:GetAttribute("Scale") or 1,
		skin = cfg:GetAttribute("Skin"),
	}
	if not info.type or not info.side then return end
	local st = Render.buildStick(info)
	local u = {
		id = id, cfg = cfg, st = st, type = info.type, side = info.side, scale = info.scale,
		x = cfg:GetAttribute("X") or 0, z = cfg:GetAttribute("Z") or 17, dir = cfg:GetAttribute("Dir") or 1,
		speed = cfg:GetAttribute("Speed") or 60, phase = math.random() * 6.28, seed = math.random() * 100,
		born = time, hurt = 0, blockT = 0, flash = 0, healT = 0, cheer = 0, combo = 0, attacking = false, atkT = 0,
		boss = cfg:GetAttribute("Boss") == true,
	}
	units[id] = u
	Render.splat(u.x, u.z, 14 * u.scale, u.side)
	Render.emit("ink" .. u.side, Render.W(u.x, -2, u.z), 10, 0)
	if u.side == mySide() then sound("spawn", 0.35, 1.2, 0.08) end
end

local function removeUnit(id)
	local u = units[id]
	if not u then return end
	u.st.model:Destroy()
	units[id] = nil
end

UnitsFolder.ChildAdded:Connect(addUnit)
for _, c in ipairs(UnitsFolder:GetChildren()) do addUnit(c) end
UnitsFolder.ChildRemoved:Connect(function(cfg)
	local id = tonumber(cfg.Name)
	local u = id and units[id]
	if u and not u.dead then removeUnit(id) end
end)

local function currentJoints(u)
	local p = Pose.compute(u, time)
	local J = Pose.solve(p, u.scale, u.dir)
	for k, v in pairs(J) do
		if type(v) == "table" then J[k] = { v[1] + u.x, v[2] } end
	end
	return J, p
end

------------------------------------------------------------------ Kamera
local cam = workspace.CurrentCamera
local camX = 30
local autoT = 0
local shake = 0
local FOV, DIST = 45, 46
local keys = {}
local function halfWidth()
	local vp = cam.ViewportSize
	return math.tan(math.rad(FOV / 2)) * DIST * (vp.X / math.max(vp.Y, 1))
end
local function clampCam()
	local hw = halfWidth()
	if hw * 2 > WORLD + 12 then camX = WORLD / 2 else camX = clamp(camX, hw - 6, WORLD - hw + 6) end
end

local function updateCamera(dt)
	cam.CameraType = Enum.CameraType.Scriptable
	cam.FieldOfView = FOV
	local side = mySide()
	local phase = State:GetAttribute("Phase")
	if keys.left then camX = camX - 60 * dt; autoT = 3 end
	if keys.right then camX = camX + 60 * dt; autoT = 3 end
	autoT = autoT - dt
	if autoT <= 0 then
		local target
		if phase == "end" then
			local w = State:GetAttribute("Winner") or 0
			if w ~= 0 then target = (w == 1 and Config.WORLD_W - 130 or 130) * K end
		elseif side ~= 0 then
			local dir = Config.SIDES[side].dir
			local front = nil
			for _, u in pairs(units) do
				if not u.dead and u.side == side then
					if not front or u.x * dir > front * dir then front = u.x end
				end
			end
			front = front or (side == 1 and 130 or Config.WORLD_W - 130)
			for _, u in pairs(units) do
				if not u.dead and u.side ~= side and (u.x - front) * dir < 300 and (u.x - front) * dir > 0 then
					front = u.x
				end
			end
			target = front * K + dir * halfWidth() * 0.15
		else
			local sum, n = 0, 0
			for _, u in pairs(units) do if not u.dead then sum = sum + u.x; n = n + 1 end end
			target = n > 0 and (sum / n) * K or WORLD / 2
		end
		if target then camX = lerp(camX, target, 1 - 0.3 ^ dt) end
	end
	clampCam()
	shake = shake * (0.002 ^ dt)
	local sx = (math.random() - 0.5) * shake
	local sy = (math.random() - 0.5) * shake * 0.6
	local eye = Vector3.new(camX + sx, 8.5 + sy, DIST)
	cam.CFrame = CFrame.lookAt(eye, Vector3.new(camX + sx * 0.5, 5.5, 0))
end

------------------------------------------------------------------ Steuerung
local targeting = false
local aimPart = Render.newPart(Render.root, Color3.fromRGB(255, 80, 50), Enum.PartType.Cylinder, Enum.Material.Neon, Vector3.new(0.1, 28, 28))
aimPart.Transparency = 1
aimPart.CastShadow = false

local function cancelAim()
	targeting = false
	UI.targeting = false
	aimPart.Transparency = 1
end
local function screenToWorldX(pos)
	local ray = cam:ScreenPointToRay(pos.X, pos.Y)
	if math.abs(ray.Direction.Z) < 1e-4 then return nil end
	local t = -ray.Origin.Z / ray.Direction.Z
	local hit = ray.Origin + ray.Direction * t
	return hit.X / K
end
local function setAim(pos)
	local x = screenToWorldX(pos)
	if not x then return end
	aimPart.CFrame = CFrame.new(x * K, 0.12, 0) * CFrame.Angles(0, 0, math.pi / 2)
	aimPart.Transparency = 0.55
end

local function canAct() return mySide() ~= 0 and State:GetAttribute("Phase") == "battle" end

UI.onSpawn = function(t)
	if not canAct() then return end
	local def = Config.UNITS[t]
	local gold = State:GetAttribute("Gold" .. mySide()) or 0
	if gold < def.cost then UI.toast(T("Zu wenig Gold für %s", T(def.name)), 1.2); return end
	Action:FireServer("spawn", t)
end
UI.onAbility = function(k)
	if not canAct() then return end
	local cd = State:GetAttribute("Ab" .. mySide() .. "_" .. k) or 0
	if cd > 0 then UI.toast(T("%s lädt noch %d s", T(Config.ABILITIES[k].name), math.ceil(cd)), 1.2); return end
	if k == "meteor" then
		if targeting then cancelAim(); return end
		targeting = true
		UI.targeting = true
		local vp = cam.ViewportSize
		setAim(Vector2.new(vp.X * 0.65, vp.Y * 0.6))
		return
	end
	Action:FireServer("ability", k)
end
UI.onCmd = function(c) Action:FireServer("cmd", c) end
UI.onCancelAim = cancelAim

local drag = nil
UserInputService.InputBegan:Connect(function(input, gpe)
	if gpe then return end
	local t = input.UserInputType
	if t == Enum.UserInputType.MouseButton1 or t == Enum.UserInputType.Touch then
		drag = { start = input.Position, cam = camX, moved = 0, input = input }
		if targeting then setAim(input.Position) end
	elseif t == Enum.UserInputType.Keyboard then
		local kc = input.KeyCode
		local nums = { Enum.KeyCode.One, Enum.KeyCode.Two, Enum.KeyCode.Three, Enum.KeyCode.Four, Enum.KeyCode.Five, Enum.KeyCode.Six, Enum.KeyCode.Seven }
		for i, n in ipairs(nums) do if kc == n then UI.onSpawn(Config.UNIT_ORDER[i]) end end
		if kc == Enum.KeyCode.R then
			UI.onAbility("meteor")
			if targeting then
				local inset = game:GetService("GuiService"):GetGuiInset()
				setAim(UserInputService:GetMouseLocation() - Vector2.new(0, inset.Y))
			end
		elseif kc == Enum.KeyCode.F then UI.onAbility("rage")
		elseif kc == Enum.KeyCode.H then UI.onAbility("heal")
		elseif kc == Enum.KeyCode.Q then UI.onCmd("defend")
		elseif kc == Enum.KeyCode.E then UI.onCmd("attack")
		elseif kc == Enum.KeyCode.A or kc == Enum.KeyCode.Left then keys.left = true
		elseif kc == Enum.KeyCode.D or kc == Enum.KeyCode.Right then keys.right = true
		elseif kc == Enum.KeyCode.Escape then cancelAim()
		elseif kc == Enum.KeyCode.M then UI.menuForced = not UI.menuForced end
	end
end)
UserInputService.InputChanged:Connect(function(input, gpe)
	local t = input.UserInputType
	if t == Enum.UserInputType.MouseWheel and not gpe then
		camX = camX - input.Position.Z * 5
		autoT = 3
		return
	end
	if t == Enum.UserInputType.MouseMovement and targeting and not drag then setAim(input.Position) end
	if drag and (t == Enum.UserInputType.MouseMovement or (t == Enum.UserInputType.Touch and input == drag.input)) then
		local dx = input.Position.X - drag.start.X
		drag.moved = math.max(drag.moved, math.abs(dx))
		if targeting and drag.moved < 12 then setAim(input.Position); return end
		local studsPerPixel = halfWidth() * 2 / math.max(cam.ViewportSize.X, 1)
		camX = drag.cam - dx * studsPerPixel
		autoT = 3.5
	end
end)
UserInputService.InputEnded:Connect(function(input)
	local t = input.UserInputType
	if t == Enum.UserInputType.Keyboard then
		local kc = input.KeyCode
		if kc == Enum.KeyCode.A or kc == Enum.KeyCode.Left then keys.left = false end
		if kc == Enum.KeyCode.D or kc == Enum.KeyCode.Right then keys.right = false end
	end
	if drag and (t == Enum.UserInputType.MouseButton1 or (t == Enum.UserInputType.Touch and input == drag.input)) then
		if targeting and drag.moved < 12 then
			local x = screenToWorldX(input.Position)
			if x then Action:FireServer("ability", "meteor", x) end
			cancelAim()
		end
		drag = nil
	end
end)

------------------------------------------------------------------ Effekte vom Server
local streak, streakT = 0, 0
local baseWarnT = 0

local handlers = {}
handlers.attack = function(e)
	local u = units[e[2]]
	if not u or u.dead then return end
	local rm = (State:GetAttribute("Rage" .. u.side) or 0) > 0 and 1.45 or 1
	u.attacking = true
	u.atkStart = time
	u.atkDur = (e[4] or 0.6) / rm
	u.atkT = 0
	u.combo = e[5] or 0
	if u.type == "sword" or u.type == "giant" or u.type == "shield" or u.type == "ninja" then
		task.delay(u.atkDur * 0.35, function() sound("swing", 0.25, u.type == "giant" and 0.7 or 1.1, 0.06) end)
	elseif u.type == "spear" then
		task.delay(u.atkDur * 0.45, function() sound("lunge", 0.25, 1, 0.06) end)
	end
end
handlers.death = function(e)
	local id, dir, power, cut, owner = e[2], e[3], e[4], e[5], e[6]
	local u = units[id]
	if not u or u.dead then return end
	local J = currentJoints(u)
	local rd = Render.makeRagdoll(J, dir * 380 * power, -480 * power, u.scale)
	if cut then rd.cut = true; rd.bleed = 0.7; rd.P[1].px = rd.P[1].px - dir * 5; rd.P[1].py = rd.P[1].py + 7 end
	u.dead = { t = 0, rd = rd, splat = false }
	if u.st.trail then u.st.trail.Enabled = false; u.st.trail = nil end
	Render.setColorMode(u.st, "normal")
	Render.emit("ink" .. u.side, Render.W(u.x, -40 * u.scale, u.z), 26, dir)
	if owner == player.UserId then
		streak = streak + 1
		streakT = 1.6
		if streak == 2 then UI.say("DOPPEL-KILL", "kill")
		elseif streak == 3 then UI.say("TRIPPEL-KILL", "kill")
		elseif streak == 5 then UI.say("MASSAKER!", "kill")
		elseif streak == 8 then UI.say("UNAUFHALTBAR!", "kill") end
	end
end
handlers.proj = function(e)
	Render.addProj(e)
	local kind = e[3]
	if kind == "arrow" or kind == "bolt" then sound("swing", 0.12, 1.8, 0.05)
	elseif kind == "orb" then sound("ping", 0.2, 0.6, 0.08)
	elseif kind == "meteor" then sound("boom", 0.4, 0.5, 0.2) end
end
handlers.projEnd = function(e) Render.endProj(e[2].id) end
handlers.hit = function(e)
	local d = e[2]
	local u = units[d.id]
	local pos = Render.W(d.x, d.y, d.z)
	if u and not u.dead then
		u.hurt = 0.22
		u.flash = 0.1
		if d.blocked then u.blockT = 0.3 end
	end
	if d.blocked then
		Render.emit("spark", pos + Vector3.new(d.dir * -1, 0, 0), 10, 0)
		sound("hit", 0.3, 1.6, 0.04)
		if math.random() < 0.35 then Render.number(d.x, d.y - 50, d.z, "BLOCK", Color3.fromRGB(159, 215, 255), false) end
		return
	end
	Render.emit("ink" .. d.side, pos, d.heavy and 22 or 12, d.dir)
	if d.crit or (d.tier and d.tier >= 3) then Render.emit("spark", pos, 6, 0) end
	local mine = d.src == mySide()
	local col = d.crit and Color3.fromRGB(255, 210, 61) or (mine and Color3.new(1, 1, 1) or Color3.fromRGB(255, 107, 107))
	Render.number(d.x, d.y - 30, d.z, (d.crit and "!" or "") .. math.floor(d.amount + 0.5), col, d.crit)
	sound("hit", d.heavy and 0.45 or 0.3, d.heavy and 0.8 or 1.1, 0.03)
	if d.crit then shake = shake + 0.25 end
end
handlers.clash = function(e)
	local d = e[2]
	Render.emit("spark", Render.W(d.x, d.y, d.z), 16, 0)
	sound("hit", 0.35, 1.9, 0.05)
end
handlers.slam = function(e)
	local d = e[2]
	Render.ring(d.x, d.z, d.r * 1.3, Color3.new(1, 1, 1), 0.45)
	Render.emit("dust", Render.W(d.x, 0, d.z), 18, 0)
	shake = shake + (d.boss and 1.2 or 0.7)
	sound("boom", 0.3, 0.8, 0.1)
end
handlers.explode = function(e)
	local d = e[2]
	Render.blast(d.x, -10, d.z, d.r, d.big, d.side)
	if d.big then shake = shake + 2.2; sound("boom", 0.7, 0.6, 0.1) else shake = shake + 0.3; sound("boom", 0.25, 1.4, 0.06) end
end
handlers.base = function(e)
	local d = e[2]
	Render.emit("ink" .. d.side, Render.W(d.x, -(80 + math.random() * 100), 10), 6, 0)
	if d.side == mySide() and baseWarnT <= 0 then
		baseWarnT = 18
		UI.say("BURG WIRD ANGEGRIFFEN!", "warn")
	end
end
handlers.ability = function(e)
	local d = e[2]
	local bx = d.side == 1 and 130 or Config.WORLD_W - 130
	if d.k == "rage" then
		Render.ring(bx, 17, 500, Color3.fromRGB(255, 70, 40), 0.8)
		for _, u in pairs(units) do if u.side == d.side and not u.dead then u.cheer = 0.8 end end
		sound("boom", 0.3, 1.6, 0.2)
		if d.side == mySide() then UI.say("KRIEGSSCHREI!", "rage") end
	elseif d.k == "heal" then
		Render.ring(bx, 17, 1400, Color3.fromRGB(110, 255, 150), 1.1)
		for _, id in ipairs(d.ids or {}) do
			local u = units[id]
			if u and not u.dead then
				u.healT = 1.2
				Render.emit("heal", Render.W(u.x, -40 * u.scale, u.z), 8, 0)
			end
		end
		sound("ping", 0.4, 1.3, 0.2)
	end
end
handlers.announce = function(e)
	if mySide() ~= 0 then UI.say(e[2].text, e[2].cls) end
	if e[2].cls == "boss" then sound("boom", 0.6, 0.5, 0.3) end
end
handlers.shake = function(e) shake = shake + (e[2].amount or 0.5) end
handlers.over = function(e)
	local w = e[2]
	local s = mySide()
	shake = shake + 2.5
	sound("boom", 0.8, 0.5, 0.2)
	if s ~= 0 then UI.say(w == s and "BURG ZERSTÖRT!" or "BURG GEFALLEN!", w == s and "ok" or "warn") end
	Render.emit("ink" .. (3 - w), Render.W((w == 1 and Config.WORLD_W - 130 or 130), -120, 10), 120, 0)
end
handlers.round = function(e)
	Render.clearDecals()
	Render.clearProjs()
	cancelAim()
	autoT = 0
	local s = mySide()
	camX = (s == 2 and Config.WORLD_W - 300 or 300) * K
	UI.reward = nil
end
handlers.bought = function(e)
	UI.toast(T("Gekauft: %s  Danke!", T(tostring(e[2].name))), 3)
	sound("ping", 0.6, 1.2, 0.1)
end
handlers.room = function(e)
	local d = e[2]
	if not d.ok then UI.toast(T(d.msg), 3.5) end
end
handlers.daily = function(e)
	UI.showDaily(e[2])
	sound("ping", 0.6, 1.5, 0.1)
end
handlers.reward = function(e)
	UI.showReward(e[2])
	sound("ping", 0.5, 1, 0.1)
end

FxEvent.OnClientEvent:Connect(function(batch)
	for _, e in ipairs(batch) do
		local h = handlers[e[1]]
		if h then
			local ok, err = pcall(h, e)
			if not ok then warn("[PigmentKrieg] Effekt-Fehler", e[1], err) end
		end
	end
end)

------------------------------------------------------------------ Hügel-Zone
local hillPart = Render.newPart(Render.root, Color3.fromRGB(255, 204, 61), Enum.PartType.Cylinder, Enum.Material.Neon, Vector3.new(0.12, 34, 34))
hillPart.CastShadow = false
hillPart.Transparency = 1
hillPart.CFrame = CFrame.new(Config.WORLD_W / 2 * K, 0.08, 0) * CFrame.Angles(0, 0, math.pi / 2)
local lastHill = { 0, 0 }
local function updateHill(dt)
	local on = State:GetAttribute("Mode") == "hill" and (State:GetAttribute("Phase") == "battle" or State:GetAttribute("Phase") == "countdown")
	if not on then hillPart.Transparency = 1; return end
	local h1, h2 = State:GetAttribute("Hill1") or 0, State:GetAttribute("Hill2") or 0
	local col = Color3.fromRGB(255, 204, 61)
	if h1 > lastHill[1] then col = Color3.fromRGB(40, 40, 48) elseif h2 > lastHill[2] then col = Color3.fromRGB(250, 250, 250) end
	lastHill[1], lastHill[2] = h1, h2
	hillPart.Color = hillPart.Color:Lerp(col, math.min(1, dt * 3))
	hillPart.Transparency = 0.55 + math.sin(time * 3) * 0.1
end

------------------------------------------------------------------ Bild-Schleife
local TRAIL_WIN = { sword = { 0.36, 0.62 }, spear = { 0.47, 0.62 }, ninja = { 0.35, 0.66 }, giant = { 0.46, 0.62 }, shield = { 0.38, 0.58 } }

RunService:BindToRenderStep("PigmentKrieg", Enum.RenderPriority.Camera.Value + 1, function(dt)
	dt = math.min(dt, 0.05)
	time = time + dt
	streakT = streakT - dt
	if streakT <= 0 then streak = 0 end
	baseWarnT = baseWarnT - dt
	local parts, cfs = {}, {}
	for id, u in pairs(units) do
		if u.dead then
			local d = u.dead
			d.t = d.t + dt
			local rd = d.rd
			Render.stepRagdoll(rd, dt)
			if rd.bleed and rd.bleed > 0 then
				rd.bleed = rd.bleed - dt
				if math.random() < 0.5 then Render.emit("ink" .. u.side, Render.W(rd.P[2].x, rd.P[2].y, u.z), 2, 0) end
			end
			if not d.splat and rd.P[3].y >= -2 * u.scale then
				d.splat = true
				Render.splat(rd.P[3].x, u.z, 20 * u.scale, u.side)
				Render.emit("dust", Render.W(rd.P[3].x, 0, u.z), 3, 0)
			end
			local J, p = Render.ragdollJoints(rd, u.dir)
			Render.place(u.st, J, u.z, u.dir, p, time, parts, cfs)
			if d.t > 4 then Render.setTransparency(u.st, clamp((d.t - 4) / 1.2, 0, 1)) end
			if d.t > 5.3 then removeUnit(id) end
		else
			local cfg = u.cfg
			local tx = cfg:GetAttribute("X") or u.x
			u.moving = cfg:GetAttribute("Moving") == true
			u.dir = cfg:GetAttribute("Dir") or u.dir
			u.victory = cfg:GetAttribute("Victory") == true
			if cfg:GetAttribute("Cheer") and not u.cheerSeen then u.cheer = 0.8; u.cheerSeen = true end
			if not cfg:GetAttribute("Cheer") then u.cheerSeen = false end
			local rage = (State:GetAttribute("Rage" .. u.side) or 0) > 0
			local rm = rage and 1.45 or 1
			if u.moving then
				u.x = u.x + u.dir * u.speed * rm * dt
				u.phase = u.phase + dt * u.speed * rm * 0.11 / u.scale
			end
			u.x = u.x + (tx - u.x) * math.min(1, dt * 6)
			if u.attacking then
				u.atkT = (time - u.atkStart) / u.atkDur
				if u.atkT >= 1 then u.attacking = false; u.atkT = 0 end
			end
			u.hurt = math.max(0, u.hurt - dt)
			u.blockT = math.max(0, u.blockT - dt)
			u.flash = u.flash - dt
			u.healT = u.healT - dt
			u.cheer = math.max(0, u.cheer - dt)
			local p = Pose.compute(u, time)
			local J = Pose.solve(p, u.scale, u.dir)
			local rise = clamp(1 - (time - u.born) / 0.5, 0, 1)
			local yOff = easeIn(rise) * 92 * u.scale
			for k, v in pairs(J) do
				if type(v) == "table" then J[k] = { v[1] + u.x, v[2] + yOff } end
			end
			Render.place(u.st, J, u.z, u.dir, p, time, parts, cfs)
			local mode = "normal"
			if u.flash > 0 then mode = "flash" elseif u.healT > 0 then mode = "heal" elseif rage then mode = "rage" end
			Render.setColorMode(u.st, mode)
			Render.updateSkin(u.st, time)
			if u.st.trail then
				local w = TRAIL_WIN[u.type]
				u.st.trail.Enabled = u.attacking and w ~= nil and u.atkT >= w[1] and u.atkT <= w[2]
			end
			if rage and math.random() < dt * 4 then Render.emit("ember", Render.W(u.x, -math.random() * 60 * u.scale, u.z), 1, 0) end
		end
	end
	Render.updateProjs(dt, parts, cfs)
	if #parts > 0 then workspace:BulkMoveTo(parts, cfs, Enum.BulkMoveMode.FireCFrameChanged) end
	Render.updateDecals(dt)
	updateHill(dt)
	updateCamera(dt)
	UI.update()
end)

-- Charakter wird nicht gebraucht (Strategie-Ansicht)
player.CharacterAdded:Connect(function(c) task.defer(function() c:Destroy() end) end)
print("[PigmentKrieg] Client bereit")
