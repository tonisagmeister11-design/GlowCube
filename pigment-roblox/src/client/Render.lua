-- Pigment Krieg: zeichnet Stickmen, Waffen, Helme, Ragdolls, Projektile und Effekte (nur auf dem Client).
local TweenService = game:GetService("TweenService")
local Debris = game:GetService("Debris")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local PK = ReplicatedStorage:WaitForChild("PK")
local Config = require(PK:WaitForChild("Config"))
local Pose = require(PK:WaitForChild("Pose"))

local Render = {}
local K = Config.K
local PI = math.pi
local V3 = Vector3.new
local CF = CFrame.new
local ZAXIS = V3(0, 0, 1)

local root = Instance.new("Folder")
root.Name = "PKLocal"
root.Parent = workspace
Render.root = root

local function rgb(t) return Color3.fromRGB(t[1], t[2], t[3]) end
local BODY = { rgb(Config.SIDES[1].color), rgb(Config.SIDES[2].color) }
local BODY_BACK = { Color3.fromRGB(34, 34, 40), Color3.fromRGB(214, 214, 206) }
local RAGE = { Color3.fromRGB(110, 12, 8), Color3.fromRGB(255, 176, 160) }
local FLASH = { Color3.fromRGB(150, 20, 20), Color3.fromRGB(255, 120, 120) }

function Render.zOf(z) return (17 - z) * 0.12 end
local zOf = Render.zOf
-- Spielkoordinaten (y nach unten) -> Welt
local function W(x, y, z) return V3(x * K, -y * K, zOf(z)) end
Render.W = W

local function newPart(parent, color, shape, mat, size)
	local p = Instance.new("Part")
	p.Anchored = true
	p.CanCollide = false
	p.CanQuery = false
	p.CanTouch = false
	p.CastShadow = true
	p.TopSurface = Enum.SurfaceType.Smooth
	p.BottomSurface = Enum.SurfaceType.Smooth
	if shape then p.Shape = shape end
	p.Material = mat or Enum.Material.SmoothPlastic
	p.Color = color
	if size then p.Size = size end
	p.Parent = parent
	return p
end
Render.newPart = newPart

local function boneCF(a, b)
	local mid = (a + b) * 0.5
	if (b - a).Magnitude < 1e-4 then return CF(mid) end
	return CFrame.lookAt(mid, b, ZAXIS) * CFrame.Angles(0, PI / 2, 0)
end

-- Rahmen entlang einer Waffe: X = Waffenrichtung, Y = senkrecht, Z = zur Kamera
local function weaponFrame(pos, ang, dir)
	local vx = V3(math.sin(ang) * dir, -math.cos(ang), 0)
	local vy = V3(-vx.Y, vx.X, 0)
	return CFrame.fromMatrix(pos, vx, vy)
end

------------------------------------------------------------------ Bauen
local BONES = {
	{ "hip", "lK", 1 }, { "lK", "lFt", 1 }, { "hip", "rK", 1 }, { "rK", "rFt", 1 },
	{ "hip", "neck", 1.25 }, { "sh", "lE", 0.92 }, { "lE", "lH", 0.92 }, { "sh", "rE", 0.92 }, { "rE", "rH", 0.92 },
}
local BACK = { [1] = true, [2] = true, [6] = true, [7] = true }
local BALLS = { "lK", "rK", "lFt", "rFt", "lE", "rE", "lH", "rH", "hip", "neck" }

local function tierLook(tier)
	local T = Config.TIERS[tier] or Config.TIERS[1]
	local mat = Enum.Material.Metal
	if tier == 1 then mat = Enum.Material.Wood elseif T.neon then mat = Enum.Material.Neon end
	return rgb(T.color), mat
end

local WOOD = Color3.fromRGB(90, 60, 36)

-- Teile einer Waffe: { part, a (entlang), b (quer), extra CFrame }
local function buildWeapon(m, ty, tier, s, side)
	local col, mat = tierLook(tier)
	local list, list2, board = {}, nil, nil
	local function add(L, shape, color, material, size, a, b, extra)
		local p = newPart(m, color, shape, material, size * s)
		p.CastShadow = false
		table.insert(L, { p = p, a = a * s, b = (b or 0) * s, x = extra })
		return p
	end
	local trailParts = nil
	if ty == "sword" then
		add(list, nil, WOOD, Enum.Material.Wood, V3(0.6, 0.2, 0.2), -0.25)
		add(list, nil, Color3.fromRGB(70, 60, 50), Enum.Material.Metal, V3(0.16, 1.0, 0.24), 0.05)
		local blade = add(list, nil, col, mat, V3(2.8, 0.34, 0.08), 1.55)
		trailParts = { blade, 1.3 * s, -1.25 * s }
	elseif ty == "spear" then
		add(list, Enum.PartType.Cylinder, WOOD, Enum.Material.Wood, V3(6.6, 0.22, 0.22), 0.7)
		local tip = add(list, nil, col, mat, V3(0.7, 0.7, 0.08), 4.3, 0, CFrame.Angles(0, 0, PI / 4))
		trailParts = { tip, 0.3 * s, -0.3 * s }
	elseif ty == "archer" then
		local bc = tier == 1 and Color3.fromRGB(122, 79, 38) or col
		add(list, nil, bc, tier == 1 and Enum.Material.Wood or mat, V3(0.22, 1.5, 0.16), -0.45)
		add(list, nil, bc, tier == 1 and Enum.Material.Wood or mat, V3(0.22, 1.3, 0.16), -0.8, 1.2, CFrame.Angles(0, 0, math.rad(28)))
		add(list, nil, bc, tier == 1 and Enum.Material.Wood or mat, V3(0.22, 1.3, 0.16), -0.8, -1.2, CFrame.Angles(0, 0, math.rad(-28)))
		add(list, nil, Color3.fromRGB(230, 230, 230), Enum.Material.SmoothPlastic, V3(0.04, 3.4, 0.04), -1.15)
		local arrow = add(list, nil, Color3.fromRGB(216, 201, 168), Enum.Material.Wood, V3(3.0, 0.1, 0.1), 0.2)
		arrow.Transparency = 1
		list[#list].skip = true
	elseif ty == "ninja" then
		add(list, nil, Color3.fromRGB(30, 30, 30), Enum.Material.SmoothPlastic, V3(0.35, 0.16, 0.16), -0.1)
		local b1 = add(list, nil, col, mat, V3(1.3, 0.22, 0.06), 0.7)
		list2 = {}
		add(list2, nil, Color3.fromRGB(30, 30, 30), Enum.Material.SmoothPlastic, V3(0.35, 0.16, 0.16), -0.1)
		add(list2, nil, col, mat, V3(1.3, 0.22, 0.06), 0.7)
		trailParts = { b1, 0.6 * s, -0.6 * s }
	elseif ty == "shield" then
		add(list, Enum.PartType.Cylinder, WOOD, Enum.Material.Wood, V3(2.0, 0.22, 0.22), 0.9)
		local head = add(list, Enum.PartType.Ball, tier == 1 and Color3.fromRGB(125, 125, 125) or col, tier == 1 and Enum.Material.Metal or mat, V3(0.9, 0.9, 0.9), 2.0)
		trailParts = { head, 0.4 * s, -0.4 * s }
		board = newPart(m, tier == 1 and Color3.fromRGB(138, 90, 46) or col, nil, tier == 1 and Enum.Material.WoodPlanks or mat, V3(0.45, 3.9, 2.4) * s)
		local boss = newPart(m, BODY[side], Enum.PartType.Ball, Enum.Material.SmoothPlastic, V3(0.8, 0.8, 0.8) * s)
		board = { board, boss }
	elseif ty == "mage" then
		add(list, Enum.PartType.Cylinder, Color3.fromRGB(74, 50, 32), Enum.Material.Wood, V3(4.4, 0.22, 0.22), 0.8)
		local crystalCol = tier == 1 and (side == 1 and Color3.fromRGB(155, 107, 255) or Color3.fromRGB(107, 208, 255)) or col
		add(list, Enum.PartType.Ball, crystalCol, Enum.Material.Neon, V3(0.6, 0.6, 0.6), 3.3)
		local orb = add(list, Enum.PartType.Ball, crystalCol, Enum.Material.Neon, V3(1, 1, 1), 3.4)
		orb.Transparency = 1
		local light = Instance.new("PointLight"); light.Color = crystalCol; light.Range = 0; light.Brightness = 3; light.Parent = orb
	elseif ty == "giant" then
		add(list, Enum.PartType.Cylinder, Color3.fromRGB(78, 52, 32), Enum.Material.Wood, V3(4.2, 0.3, 0.3), 1.5)
		local head = add(list, nil, tier == 1 and Color3.fromRGB(109, 109, 109) or col, tier == 1 and Enum.Material.Metal or mat, V3(1.2, 2.2, 1.3), 3.6)
		trailParts = { head, 0, -1.0 * s }
	end
	local trail = nil
	if trailParts then
		local a0 = Instance.new("Attachment"); a0.Position = V3(trailParts[2], 0, 0); a0.Parent = trailParts[1]
		local a1 = Instance.new("Attachment"); a1.Position = V3(trailParts[3], 0, 0); a1.Parent = trailParts[1]
		trail = Instance.new("Trail")
		trail.Attachment0 = a0; trail.Attachment1 = a1
		trail.Lifetime = 0.16
		trail.MinLength = 0
		trail.FaceCamera = true
		trail.LightEmission = (Config.TIERS[tier] and Config.TIERS[tier].neon) and 1 or 0.3
		trail.Color = ColorSequence.new((tier >= 3) and col or (side == 1 and Color3.fromRGB(30, 30, 30) or Color3.new(1, 1, 1)))
		trail.Transparency = NumberSequence.new({ NumberSequenceKeypoint.new(0, 0.25), NumberSequenceKeypoint.new(1, 1) })
		trail.WidthScale = NumberSequence.new(1)
		trail.Enabled = false
		trail.Parent = trailParts[1]
	end
	return list, list2, board, trail
end

local SKIN = Color3.fromRGB(255, 214, 170)

local function buildHelmet(m, id, s, side)
	local r = 7.6 * s * K
	local list = {}
	local function add(shape, color, mat, size, ox, oy, oz, extra)
		local p = newPart(m, color, shape, mat, size)
		table.insert(list, { p = p, o = V3(ox, oy, oz), x = extra })
		return p
	end
	if id == "none" then
		add(nil, side == 1 and Color3.new(1, 1, 1) or Color3.new(0, 0, 0), Enum.Material.Neon, V3(r * 0.5, r * 0.2, 0.05), r * 0.42, r * 0.12, r * 0.88)
	elseif id == "knight" then
		add(Enum.PartType.Ball, Color3.fromRGB(165, 175, 186), Enum.Material.Metal, V3(1, 1, 1) * r * 2.36, 0, r * 0.06, 0)
		add(nil, Color3.fromRGB(15, 15, 15), Enum.Material.SmoothPlastic, V3(r * 1.0, r * 0.2, r * 2.2), r * 0.62, r * 0.08, 0)
		add(nil, Color3.fromRGB(192, 57, 43), Enum.Material.Fabric, V3(r * 1.3, r * 0.4, r * 0.32), -r * 0.6, r * 1.2, 0, CFrame.Angles(0, 0, math.rad(-30)))
	elseif id == "viking" then
		add(Enum.PartType.Ball, Color3.fromRGB(143, 154, 165), Enum.Material.Metal, V3(1, 1, 1) * r * 2.26, 0, r * 0.12, 0)
		add(Enum.PartType.Cylinder, Color3.fromRGB(107, 74, 42), Enum.Material.Wood, V3(r * 0.4, r * 2.32, r * 2.32), 0, -r * 0.15, 0, CFrame.Angles(0, 0, PI / 2))
		add(nil, Color3.fromRGB(239, 230, 207), Enum.Material.SmoothPlastic, V3(r * 0.42, r * 1.5, r * 0.42), r * 1.05, r * 1.0, 0, CFrame.Angles(0, 0, math.rad(-35)))
		add(nil, Color3.fromRGB(239, 230, 207), Enum.Material.SmoothPlastic, V3(r * 0.42, r * 1.5, r * 0.42), -r * 1.05, r * 1.0, 0, CFrame.Angles(0, 0, math.rad(35)))
	elseif id == "ninja" then
		add(Enum.PartType.Cylinder, Color3.fromRGB(196, 22, 28), Enum.Material.Fabric, V3(r * 0.45, r * 2.12, r * 2.12), 0, r * 0.35, 0, CFrame.Angles(0, 0, PI / 2))
		add(nil, Color3.fromRGB(196, 22, 28), Enum.Material.Fabric, V3(r * 1.6, r * 0.24, r * 0.08), -r * 1.6, r * 0.4, r * 0.3, "wave")
		add(nil, Color3.fromRGB(196, 22, 28), Enum.Material.Fabric, V3(r * 1.3, r * 0.22, r * 0.08), -r * 1.45, r * 0.05, r * 0.4, "wave2")
	elseif id == "sparta" then
		add(Enum.PartType.Ball, Color3.fromRGB(226, 172, 70), Enum.Material.Metal, V3(1, 1, 1) * r * 2.3, 0, r * 0.06, 0)
		add(nil, Color3.fromRGB(179, 20, 27), Enum.Material.Fabric, V3(r * 2.4, r * 0.75, r * 0.3), -r * 0.15, r * 1.25, 0)
		add(nil, Color3.fromRGB(15, 15, 15), Enum.Material.SmoothPlastic, V3(r * 0.25, r * 1.2, r * 2.35), r * 0.75, -r * 0.2, 0)
	elseif id == "crown" then
		add(Enum.PartType.Cylinder, Color3.fromRGB(240, 180, 30), Enum.Material.Metal, V3(r * 0.6, r * 1.9, r * 1.9), 0, r * 0.95, 0, CFrame.Angles(0, 0, PI / 2))
		for i = -1, 1 do
			add(nil, Color3.fromRGB(255, 215, 70), Enum.Material.Metal, V3(r * 0.32, r * 0.7, r * 0.32), i * r * 0.62, r * 1.5, r * 0.6, CFrame.Angles(0, 0, -i * 0.25))
		end
		add(Enum.PartType.Ball, Color3.fromRGB(224, 32, 74), Enum.Material.Neon, V3(1, 1, 1) * r * 0.38, 0, r * 0.95, r * 0.95)
	elseif id == "face" then
		-- Bild auf einer unsichtbaren Platte vor dem Kopf; der Kopf dahinter bildet den Helmrand
		local custom = Config.FACE_IMAGE ~= ""
		local plate = add(nil, SKIN, Enum.Material.SmoothPlastic, custom and V3(r * 2.3, r * 3.1, 0.04) or V3(r * 1.9, r * 1.9, 0.04), 0, custom and r * 0.25 or 0, r * (custom and 1.3 or 1.15))
		plate.Transparency = 1
		plate.CastShadow = false
		local dec = Instance.new("Decal")
		dec.Face = Enum.NormalId.Back
		dec.Texture = custom and Config.FACE_IMAGE or "rbxasset://textures/face.png"
		dec.Parent = plate
	end
	return list
end

-- erzeugt einen Stickman. info: { type, side, tier, helmet, scale }
function Render.buildStick(info, parent)
	local s = info.scale or 1
	local side = info.side
	local m = Instance.new("Model")
	m.Name = info.type
	m.Parent = parent or root
	local lw = 4.2 * s * K
	local st = { model = m, s = s, side = side, type = info.type, bones = {}, balls = {}, all = {} }
	for i, b in ipairs(BONES) do
		local p = newPart(m, BACK[i] and BODY_BACK[side] or BODY[side], Enum.PartType.Cylinder, nil, V3(1, lw * b[3], lw * b[3]))
		st.bones[i] = p
	end
	for _, name in ipairs(BALLS) do
		local d = (name == "hip" or name == "neck") and lw * 1.15 or lw * 0.98
		st.balls[name] = newPart(m, BODY[side], Enum.PartType.Ball, nil, V3(d, d, d))
	end
	local hr = 7.6 * s * K
	local hd = hr * 2
	local headCol = BODY[side]
	if info.helmet == "face" then
		if Config.FACE_IMAGE ~= "" then hd = hr * 2.6 else hd = hr * 2.4; headCol = SKIN; st.skinHead = true end
	end
	st.head = newPart(m, headCol, Enum.PartType.Ball, nil, V3(hd, hd, hd))
	st.weapon, st.weapon2, st.board, st.trail = buildWeapon(m, info.type, info.tier or 1, s, side)
	st.helmet = buildHelmet(m, info.helmet or "none", s, side)
	st.helmetId = info.helmet
	-- Längen der Knochen einmalig (bleiben konstant)
	local lens = { 15, 15, 15, 15, 24, 12, 12, 12, 12 }
	for i, p in ipairs(st.bones) do
		p.Size = V3(lens[i] * s * K + lw * 0.2, p.Size.Y, p.Size.Z)
	end
	for _, d in ipairs(m:GetDescendants()) do
		if d:IsA("BasePart") then table.insert(st.all, d) end
	end
	st.colorMode = "normal"
	return st
end

function Render.setColorMode(st, mode)
	if st.colorMode == mode then return end
	st.colorMode = mode
	local side = st.side
	local c, cb = BODY[side], BODY_BACK[side]
	if mode == "flash" then c = FLASH[side]; cb = c
	elseif mode == "rage" then c = RAGE[side]; cb = c
	elseif mode == "heal" then c = Color3.fromRGB(80, 255, 140); cb = c end
	for i, p in ipairs(st.bones) do p.Color = BACK[i] and cb or c end
	for _, p in pairs(st.balls) do p.Color = c end
	if not st.skinHead then st.head.Color = c end
end

function Render.setTransparency(st, t)
	for _, p in ipairs(st.all) do
		if p:GetAttribute("baseT") == nil then p:SetAttribute("baseT", p.Transparency) end
		local b = p:GetAttribute("baseT")
		p.Transparency = b + (1 - b) * t
	end
end

------------------------------------------------------------------ Platzieren
-- J: absolute Spielkoordinaten der Gelenke ({x,y}), z: Tiefe. Fügt Teile/CFrames an die Listen an.
function Render.place(st, J, z, dir, p, time, parts, cfs)
	local Z = zOf(z)
	local function w(v) return V3(v[1] * K, -v[2] * K, Z) end
	local pts = {}
	for k, v in pairs(J) do
		if type(v) == "table" then pts[k] = w(v) end
	end
	for i, b in ipairs(BONES) do
		local a, c = pts[b[1]], pts[b[2]]
		table.insert(parts, st.bones[i]); table.insert(cfs, boneCF(a, c))
	end
	for name, ball in pairs(st.balls) do
		table.insert(parts, ball); table.insert(cfs, CF(pts[name]))
	end
	local hp = pts.head
	table.insert(parts, st.head); table.insert(cfs, CF(hp))
	-- Waffe
	local s = st.s
	local function placeWeapon(list, hand, ang)
		local f = weaponFrame(hand, ang, dir)
		for _, e in ipairs(list) do
			if not e.skip then
				local c = f * CF(e.a, e.b, 0)
				if e.x then c = c * e.x end
				table.insert(parts, e.p); table.insert(cfs, c)
			end
		end
	end
	if st.type == "archer" then
		placeWeapon(st.weapon, pts.lH, p.w)
		local arrow = st.weapon[5]
		if arrow then
			arrow.p.Transparency = (p.draw or 0) > 0.05 and 0 or 1
			local f = weaponFrame(pts.lH, p.w, dir)
			table.insert(parts, arrow.p); table.insert(cfs, f * CF(-1.4 * s * (p.draw or 0) + 0.3 * s, 0, 0))
		end
	else
		placeWeapon(st.weapon, pts.rH, p.w)
	end
	if st.weapon2 then placeWeapon(st.weapon2, pts.lH, p.w2) end
	if st.board then
		local bf = CF(pts.lH + V3(dir * 0.25 * s, 0, 0)) * CFrame.Angles(0, math.rad(50) * dir, -(p.lean or 0) * 0.6 * dir)
		table.insert(parts, st.board[1]); table.insert(cfs, bf)
		table.insert(parts, st.board[2]); table.insert(cfs, bf * CF(0, 0, 0))
	end
	if st.type == "mage" then
		local orb = st.weapon[3]
		local ch = p.charge or 0
		orb.p.Transparency = ch > 0.02 and 0.15 or 1
		local d = (0.4 + ch * 1.6) * s
		if math.abs(orb.p.Size.X - d) > 0.02 then orb.p.Size = V3(d, d, d) end
		local light = orb.p:FindFirstChildOfClass("PointLight")
		if light then light.Range = ch * 14 end
	end
	-- Helm im Kopf-Rahmen
	local hcf = CF(hp) * CFrame.Angles(0, 0, -(J.headA or 0) * dir)
	for _, e in ipairs(st.helmet) do
		local o = e.o
		local c = hcf * CF(o.X * dir, o.Y, o.Z)
		if e.x == "wave" then c = c * CFrame.Angles(0, 0, math.sin(time * 14) * 0.3 * dir)
		elseif e.x == "wave2" then c = c * CFrame.Angles(0, 0, (-0.4 + math.sin(time * 12 + 1) * 0.3) * dir)
		elseif e.x then
			local rx, ry, rz = e.x:ToEulerAnglesXYZ()
			c = c * CFrame.Angles(rx, ry, rz * dir)
		end
		table.insert(parts, e.p); table.insert(cfs, c)
	end
end

------------------------------------------------------------------ Ragdoll (Verlet)
local RD = { { 1, 2 }, { 2, 3 }, { 3, 4 }, { 4, 5 }, { 3, 6 }, { 6, 7 }, { 2, 8 }, { 8, 9 }, { 2, 10 }, { 10, 11 }, { 1, 3 } }
local NAMES = { "head", "neck", "hip", "lK", "lFt", "rK", "rFt", "lE", "lH", "rE", "rH" }

function Render.makeRagdoll(J, vx, vy, scale)
	local P = {}
	local h = 70 * scale
	for i, n in ipairs(NAMES) do
		local v = J[n]
		local up = Pose.clamp(-v[2] / h, 0, 1)
		local k = 0.35 + 0.9 * up
		local dt = 1 / 60
		P[i] = { x = v[1], y = v[2], px = v[1] - (vx * k + (math.random() - 0.5) * 60) * dt, py = v[2] - (vy * (0.5 + 0.6 * up) + (math.random() - 0.5) * 60) * dt }
	end
	local L = {}
	for i, s in ipairs(RD) do
		local a, b = P[s[1]], P[s[2]]
		L[i] = math.sqrt((a.x - b.x) ^ 2 + (a.y - b.y) ^ 2)
	end
	return { P = P, L = L, pdt = 1 / 60, cut = false, headR = J.headR, settled = 0 }
end

function Render.stepRagdoll(r, dt)
	if dt <= 0 then return end
	local ratio = dt / r.pdt
	r.pdt = dt
	local G = 1500 * dt * dt
	for _, p in ipairs(r.P) do
		local vx, vy = (p.x - p.px) * ratio * 0.996, (p.y - p.py) * ratio * 0.996
		p.px, p.py = p.x, p.y
		p.x = p.x + vx
		p.y = p.y + vy + G
		if p.y > 0 then
			p.y = 0
			p.py = vy * 0.28
			p.px = p.x - vx * 0.5
		end
	end
	for _ = 1, 5 do
		for i, s in ipairs(RD) do
			if not (r.cut and (i == 1 or i == 11)) then
				local A, B = r.P[s[1]], r.P[s[2]]
				local dx, dy = B.x - A.x, B.y - A.y
				local d = math.sqrt(dx * dx + dy * dy)
				if d < 1e-4 then d = 1e-4 end
				local diff = (d - r.L[i]) / d * 0.5
				A.x = A.x + dx * diff; A.y = A.y + dy * diff
				B.x = B.x - dx * diff; B.y = B.y - dy * diff
			end
		end
		for _, p in ipairs(r.P) do if p.y > 0 then p.y = 0 end end
	end
end

-- Ragdoll-Punkte in Gelenk-Tabelle umwandeln (für Render.place)
function Render.ragdollJoints(r, dir)
	local J = {}
	for i, n in ipairs(NAMES) do J[n] = { r.P[i].x, r.P[i].y } end
	J.sh = J.neck
	J.headR = r.headR
	local nk, hd = J.neck, J.head
	J.headA = math.atan2((hd[1] - nk[1]) * dir, -(hd[2] - nk[2]))
	local e, h = J.rE, J.rH
	local we = math.atan2((h[1] - e[1]) * dir, (h[2] - e[2])) + 0.3
	local e2, h2 = J.lE, J.lH
	local we2 = math.atan2((h2[1] - e2[1]) * dir, (h2[2] - e2[2])) + 0.3
	return J, { w = we, w2 = we2, lean = 0, draw = 0, charge = 0 }
end

------------------------------------------------------------------ Effekte
local fxPart = newPart(root, Color3.new(), nil, nil, V3(0.1, 0.1, 0.1))
fxPart.Transparency = 1
fxPart.CastShadow = false

local function emitter(name, props)
	local a = Instance.new("Attachment")
	a.Name = name
	a.Parent = fxPart
	local e = Instance.new("ParticleEmitter")
	e.Enabled = false
	for k, v in pairs(props) do e[k] = v end
	e.Parent = a
	return { a = a, e = e }
end
local SMOKE = "rbxasset://textures/particles/smoke_main.dds"
local SPARK = "rbxasset://textures/particles/sparkles_main.dds"
local FIRE = "rbxasset://textures/particles/fire_main.dds"
local function ns(a, b) return NumberSequence.new({ NumberSequenceKeypoint.new(0, a), NumberSequenceKeypoint.new(1, b) }) end
local function nr(a, b) return NumberRange.new(a, b) end

local E = {}
for side = 1, 2 do
	E["ink" .. side] = emitter("ink" .. side, {
		Texture = SMOKE, Color = ColorSequence.new(side == 1 and Color3.fromRGB(5, 5, 5) or Color3.new(1, 1, 1)),
		Size = ns(0.32, 0.06), Transparency = ns(0, 0.2), Lifetime = nr(0.35, 0.8), Speed = nr(10, 32),
		SpreadAngle = Vector2.new(40, 40), Acceleration = V3(0, -70, 0), Drag = 1.5, LightInfluence = 0.6,
		Rotation = nr(0, 360), EmissionDirection = Enum.NormalId.Top,
	})
end
E.spark = emitter("spark", { Texture = SPARK, Color = ColorSequence.new(Color3.fromRGB(255, 224, 138)), Size = ns(0.5, 0), Lifetime = nr(0.15, 0.35),
	Speed = nr(18, 40), SpreadAngle = Vector2.new(180, 180), LightEmission = 1, Acceleration = V3(0, -40, 0) })
E.dust = emitter("dust", { Texture = SMOKE, Color = ColorSequence.new(Color3.fromRGB(185, 171, 148)), Size = ns(1.2, 3.2), Transparency = ns(0.35, 1),
	Lifetime = nr(0.6, 1.2), Speed = nr(6, 22), SpreadAngle = Vector2.new(80, 15), Drag = 3, EmissionDirection = Enum.NormalId.Top })
E.fire = emitter("fire", { Texture = FIRE, Color = ColorSequence.new(Color3.fromRGB(255, 190, 90), Color3.fromRGB(255, 60, 20)), Size = ns(3, 0.5), Transparency = ns(0, 1),
	Lifetime = nr(0.4, 0.9), Speed = nr(10, 40), SpreadAngle = Vector2.new(180, 180), LightEmission = 1, Drag = 2 })
E.heal = emitter("heal", { Texture = SPARK, Color = ColorSequence.new(Color3.fromRGB(110, 255, 150)), Size = ns(0.8, 0), Lifetime = nr(0.6, 1.1),
	Speed = nr(3, 8), SpreadAngle = Vector2.new(30, 30), LightEmission = 1, Acceleration = V3(0, 6, 0) })
E.ember = emitter("ember", { Texture = SPARK, Color = ColorSequence.new(Color3.fromRGB(255, 90, 40)), Size = ns(0.35, 0), Lifetime = nr(0.4, 0.8),
	Speed = nr(2, 6), SpreadAngle = Vector2.new(25, 25), LightEmission = 1, Acceleration = V3(0, 8, 0) })

function Render.emit(kind, pos, n, dir)
	local em = E[kind]
	if not em then return end
	em.a.WorldCFrame = CF(pos) * CFrame.Angles(0, 0, -(dir or 0) * 0.8)
	em.e:Emit(n)
end

-- Tintenflecken am Boden
local decals = {}
function Render.splat(x, z, radius, side)
	local d = radius * K * 2
	local p = newPart(root, side == 1 and Color3.fromRGB(8, 8, 8) or Color3.fromRGB(250, 250, 250), Enum.PartType.Cylinder, nil, V3(0.03, d, d * 0.9))
	p.CastShadow = false
	p.CFrame = CF(x * K, 0.03 + #decals * 0.0004, zOf(z)) * CFrame.Angles(0, math.random() * 6, PI / 2)
	table.insert(decals, { p = p, t = 0 })
	if #decals > 90 then
		local old = table.remove(decals, 1)
		old.p:Destroy()
	end
end
function Render.updateDecals(dt)
	for i = #decals, 1, -1 do
		local d = decals[i]
		d.t = d.t + dt
		if d.t > 25 then
			d.p.Transparency = math.min(1, (d.t - 25) / 5)
			if d.t > 30 then d.p:Destroy(); table.remove(decals, i) end
		end
	end
end
function Render.clearDecals()
	for _, d in ipairs(decals) do d.p:Destroy() end
	decals = {}
end

local function tween(obj, t, props, style)
	local tw = TweenService:Create(obj, TweenInfo.new(t, style or Enum.EasingStyle.Quad, Enum.EasingDirection.Out), props)
	tw:Play()
	return tw
end
Render.tween = tween

function Render.ring(x, z, radius, color, t)
	local p = newPart(root, color or Color3.new(1, 1, 1), Enum.PartType.Cylinder, Enum.Material.Neon, V3(0.1, 0.5, 0.5))
	p.CastShadow = false
	p.Transparency = 0.25
	p.CFrame = CF(x * K, 0.15, zOf(z)) * CFrame.Angles(0, 0, PI / 2)
	local d = radius * K * 2
	tween(p, t or 0.45, { Size = V3(0.1, d, d), Transparency = 1 })
	Debris:AddItem(p, (t or 0.45) + 0.1)
end

function Render.blast(x, y, z, radius, big, side)
	local pos = W(x, y, z)
	local col = big and Color3.fromRGB(255, 160, 60) or (side == 1 and Color3.fromRGB(160, 110, 255) or Color3.fromRGB(110, 210, 255))
	local p = newPart(root, col, Enum.PartType.Ball, Enum.Material.Neon, V3(1, 1, 1))
	p.CastShadow = false
	p.CFrame = CF(pos)
	local d = radius * K * (big and 2.2 or 1.6)
	tween(p, big and 0.45 or 0.3, { Size = V3(d, d, d), Transparency = 1 })
	local l = Instance.new("PointLight"); l.Color = col; l.Range = big and 40 or 16; l.Brightness = big and 8 or 4; l.Parent = p
	tween(l, big and 0.6 or 0.35, { Brightness = 0 })
	Debris:AddItem(p, 0.7)
	Render.ring(x, z, radius * 1.4, Color3.new(1, 1, 1), 0.5)
	Render.emit("ink" .. side, pos, big and 70 or 22, 0)
	Render.emit("dust", W(x, 0, z), big and 26 or 8, 0)
	if big then Render.emit("fire", pos, 40, 0) end
	Render.splat(x, z, radius * 0.45, side)
end

-- Schadenszahlen
local numbers = 0
function Render.number(x, y, z, text, color, big)
	if numbers > 40 then return end
	numbers = numbers + 1
	local a = Instance.new("Attachment")
	a.WorldPosition = W(x + (math.random() - 0.5) * 16, y, z)
	a.Parent = workspace.Terrain
	local bb = Instance.new("BillboardGui")
	bb.Size = UDim2.fromOffset(big and 120 or 80, big and 44 or 30)
	bb.AlwaysOnTop = true
	bb.LightInfluence = 0
	bb.Parent = a
	local t = Instance.new("TextLabel")
	t.BackgroundTransparency = 1
	t.Size = UDim2.fromScale(1, 1)
	t.Font = Enum.Font.FredokaOne
	t.TextScaled = true
	t.Text = text
	t.TextColor3 = color
	t.TextStrokeTransparency = 0.2
	t.Parent = bb
	tween(bb, 0.9, { StudsOffsetWorldSpace = V3(0, 3, 0) })
	tween(t, 0.9, { TextTransparency = 1, TextStrokeTransparency = 1 }, Enum.EasingStyle.Quint)
	task.delay(0.95, function() a:Destroy(); numbers = numbers - 1 end)
end

------------------------------------------------------------------ Projektile
local projs = {}
function Render.addProj(d)
	local id, kind = d[2], d[3]
	local pr = { id = id, kind = kind, x = d[4], y = d[5], z = d[6], tz = d[7], vx = d[8], vy = d[9], g = d[10], side = d[11], tier = d[12] }
	local col, mat = tierLook(pr.tier or 1)
	if kind == "arrow" or kind == "bolt" then
		local shaft = newPart(root, Color3.fromRGB(216, 201, 168), nil, Enum.Material.Wood, V3(kind == "bolt" and 2.6 or 2.0, 0.12, 0.12))
		local tip = newPart(root, col, nil, mat, V3(0.35, 0.3, 0.3))
		shaft.CastShadow = false; tip.CastShadow = false
		pr.parts = { shaft, tip }
	elseif kind == "orb" then
		local c = pr.tier >= 3 and col or (pr.side == 1 and Color3.fromRGB(160, 110, 255) or Color3.fromRGB(110, 210, 255))
		local ball = newPart(root, c, Enum.PartType.Ball, Enum.Material.Neon, V3(0.9, 0.9, 0.9))
		local core = newPart(root, BODY[pr.side], Enum.PartType.Ball, nil, V3(0.5, 0.5, 0.5))
		ball.Transparency = 0.2; ball.CastShadow = false; core.CastShadow = false
		local l = Instance.new("PointLight"); l.Color = c; l.Range = 10; l.Brightness = 3; l.Parent = ball
		local a0 = Instance.new("Attachment"); a0.Position = V3(0, 0.35, 0); a0.Parent = ball
		local a1 = Instance.new("Attachment"); a1.Position = V3(0, -0.35, 0); a1.Parent = ball
		local tr = Instance.new("Trail"); tr.Attachment0 = a0; tr.Attachment1 = a1; tr.Lifetime = 0.3; tr.LightEmission = 1
		tr.Color = ColorSequence.new(c); tr.Transparency = ns(0.2, 1); tr.FaceCamera = true; tr.Parent = ball
		pr.parts = { ball, core }
	elseif kind == "meteor" then
		local rock = newPart(root, BODY[pr.side], Enum.PartType.Ball, Enum.Material.Slate, V3(4, 4, 4))
		local glow = newPart(root, Color3.fromRGB(255, 120, 40), Enum.PartType.Ball, Enum.Material.Neon, V3(5.2, 5.2, 5.2))
		glow.Transparency = 0.5; rock.CastShadow = false; glow.CastShadow = false
		local f = Instance.new("Fire"); f.Size = 14; f.Heat = 20; f.Color = Color3.fromRGB(255, 140, 50); f.SecondaryColor = Color3.fromRGB(255, 50, 20); f.Parent = glow
		local l = Instance.new("PointLight"); l.Color = Color3.fromRGB(255, 140, 60); l.Range = 40; l.Brightness = 5; l.Parent = glow
		pr.parts = { rock, glow }
	end
	projs[id] = pr
end

function Render.endProj(id)
	local pr = projs[id]
	if not pr then return end
	projs[id] = nil
	if pr.kind == "arrow" or pr.kind == "bolt" then
		-- Pfeil bleibt kurz stecken
		for _, p in ipairs(pr.parts) do Debris:AddItem(p, 2) end
		task.delay(1.4, function()
			for _, p in ipairs(pr.parts) do if p.Parent then tween(p, 0.5, { Transparency = 1 }) end end
		end)
	else
		for _, p in ipairs(pr.parts) do p:Destroy() end
	end
end

function Render.updateProjs(dt, parts, cfs)
	for id, pr in pairs(projs) do
		pr.vy = pr.vy + pr.g * dt
		pr.x = pr.x + pr.vx * dt
		pr.y = math.min(0, pr.y + pr.vy * dt)
		pr.z = pr.z + (pr.tz - pr.z) * math.min(1, dt * 3)
		local pos = W(pr.x, pr.y, pr.z)
		if pr.kind == "arrow" or pr.kind == "bolt" then
			local v = V3(pr.vx, -pr.vy, 0)
			if v.Magnitude > 1 then
				local f = CFrame.fromMatrix(pos, v.Unit, V3(-v.Unit.Y, v.Unit.X, 0))
				table.insert(parts, pr.parts[1]); table.insert(cfs, f)
				table.insert(parts, pr.parts[2]); table.insert(cfs, f * CF(pr.parts[1].Size.X / 2, 0, 0) * CFrame.Angles(0, 0, PI / 4))
			end
		else
			for _, p in ipairs(pr.parts) do table.insert(parts, p); table.insert(cfs, CF(pos)) end
			if pr.kind == "meteor" and math.random() < 0.6 then Render.emit("fire", pos, 3, 0) end
		end
		if pr.y >= 0 and pr.kind ~= "meteor" then Render.endProj(id) end
	end
end

function Render.clearProjs()
	for id in pairs(projs) do Render.endProj(id) end
end

return Render
