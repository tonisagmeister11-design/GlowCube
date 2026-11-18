-- Baut die Arena (Boden, Berge, Deko, Burgen) und das Licht.
local Lighting = game:GetService("Lighting")
local Config = require(game:GetService("ReplicatedStorage"):WaitForChild("PK"):WaitForChild("Config"))

local Arena = {}
local K = Config.K
local folder = nil
local towers = {}

local function rgb(t) return Color3.fromRGB(t[1], t[2], t[3]) end
local function shade(c, k) return Color3.new(math.clamp(c.R * k, 0, 1), math.clamp(c.G * k, 0, 1), math.clamp(c.B * k, 0, 1)) end

local function part(props)
	local p = Instance.new(props.Shape and "Part" or (props.Class or "Part"))
	p.Anchored = true
	p.CanCollide = false
	p.CanQuery = false
	p.CanTouch = false
	p.TopSurface = Enum.SurfaceType.Smooth
	p.BottomSurface = Enum.SurfaceType.Smooth
	p.Material = props.Material or Enum.Material.SmoothPlastic
	for k, v in pairs(props) do
		if k ~= "Class" and k ~= "Material" and k ~= "Parent" then p[k] = v end
	end
	p.Parent = props.Parent or folder
	return p
end

local function setLighting(a)
	Lighting.ClockTime = a.clock
	Lighting.Brightness = a.id == "neon" and 1 or 2.2
	Lighting.Ambient = shade(rgb(a.fog), 0.45)
	Lighting.OutdoorAmbient = shade(rgb(a.fog), 0.7)
	Lighting.FogColor = rgb(a.fog)
	Lighting.FogStart = 60
	Lighting.FogEnd = 260
	Lighting.GlobalShadows = true
	local atm = Lighting:FindFirstChildOfClass("Atmosphere") or Instance.new("Atmosphere")
	atm.Density = 0.32
	atm.Offset = 0.1
	atm.Color = rgb(a.fog)
	atm.Decay = rgb(a.sky)
	atm.Glare = a.id == "volcano" and 2 or 0.5
	atm.Haze = 1.5
	atm.Parent = Lighting
	local bloom = Lighting:FindFirstChildOfClass("BloomEffect") or Instance.new("BloomEffect")
	bloom.Intensity = 0.6
	bloom.Size = 30
	bloom.Threshold = 0.9
	bloom.Parent = Lighting
	local cc = Lighting:FindFirstChildOfClass("ColorCorrectionEffect") or Instance.new("ColorCorrectionEffect")
	cc.Contrast = 0.12
	cc.Saturation = a.id == "plains" and -0.15 or 0.05
	cc.Parent = Lighting
end

local function buildTower(side)
	local S = Config.SIDES[side]
	local col = rgb(S.color)
	local rimCol = side == 1 and Color3.fromRGB(60, 60, 66) or Color3.fromRGB(200, 200, 194)
	local x = (side == 1 and 130 or Config.WORLD_W - 130) * K
	local m = Instance.new("Model")
	m.Name = "Burg" .. side
	m.Parent = folder
	local function p(size, pos, color, mat)
		return part({ Size = size, CFrame = CFrame.new(x + pos.X, pos.Y, pos.Z), Color = color or col, Material = mat, Parent = m })
	end
	p(Vector3.new(11, 1, 9), Vector3.new(0, 0.5, -1), rimCol)
	p(Vector3.new(9.5, 7, 7.5), Vector3.new(0, 4, -1))
	p(Vector3.new(7.6, 15, 6), Vector3.new(0, 14.5, -1))
	p(Vector3.new(9.6, 1.6, 7.6), Vector3.new(0, 22.6, -1), rimCol)
	for i = -1, 1, 2 do
		for j = -1, 1, 2 do
			p(Vector3.new(2, 1.8, 2), Vector3.new(i * 3.6, 24.3, -1 + j * 2.8))
		end
	end
	-- Tor und Fenster zur Kamera
	local dark = side == 1 and Color3.fromRGB(40, 40, 46) or Color3.fromRGB(170, 170, 162)
	p(Vector3.new(3, 4.4, 0.3), Vector3.new(0, 2.6, 2.8), dark)
	local winCol = side == 1 and Color3.fromRGB(255, 207, 90) or Color3.fromRGB(70, 50, 20)
	p(Vector3.new(1.2, 2.2, 0.3), Vector3.new(0, 14, 2.05), winCol, side == 1 and Enum.Material.Neon or nil)
	p(Vector3.new(1.2, 1.8, 0.3), Vector3.new(0, 10.4, 2.05), winCol, side == 1 and Enum.Material.Neon or nil)
	-- Fahne
	p(Vector3.new(0.3, 7, 0.3), Vector3.new(0, 28.5, -1), Color3.fromRGB(80, 80, 80))
	local dir = S.dir
	p(Vector3.new(5, 2.6, 0.15), Vector3.new(dir * 2.6, 30.4, -1), col)
	towers[side] = m
end

function Arena.build(idx)
	if folder then folder:Destroy() end
	towers = {}
	folder = Instance.new("Folder")
	folder.Name = "PKArena"
	folder.Parent = workspace
	local a = Config.ARENAS[idx]
	setLighting(a)
	local W = Config.WORLD_W * K
	local g = rgb(a.ground)
	part({ Size = Vector3.new(W + 400, 6, 140), CFrame = CFrame.new(W / 2, -3, -20), Color = g, Material = Enum.Material.Ground, CanCollide = true })
	part({ Size = Vector3.new(W + 400, 0.2, 7), CFrame = CFrame.new(W / 2, -0.09, 0), Color = shade(g, 0.88), Material = Enum.Material.Sand })
	-- Berge im Hintergrund
	local rnd = Random.new(idx * 977 + 13)
	local far = rgb(a.far)
	for i = -6, 40 do
		local h = rnd:NextNumber(18, 48)
		local w = rnd:NextNumber(16, 30)
		local z = rnd:NextNumber(-95, -70)
		local cx = i * 9
		local wedge = part({ Class = "WedgePart", Size = Vector3.new(4, h, w / 2), Color = shade(far, rnd:NextNumber(0.85, 1.1)), Material = Enum.Material.Slate })
		wedge.CFrame = CFrame.new(cx - w / 4, h / 2, z) * CFrame.Angles(0, math.rad(90), 0)
		local wedge2 = part({ Class = "WedgePart", Size = Vector3.new(4, h, w / 2), Color = wedge.Color, Material = Enum.Material.Slate })
		wedge2.CFrame = CFrame.new(cx + w / 4, h / 2, z) * CFrame.Angles(0, math.rad(-90), 0)
	end
	-- Deko je Arena
	for i = 1, 26 do
		local x = rnd:NextNumber(30, W - 30)
		local z = rnd:NextNumber(-30, -8)
		if a.id == "plains" then
			part({ Size = Vector3.new(rnd:NextNumber(2, 5), rnd:NextNumber(1.5, 3.5), rnd:NextNumber(2, 4)), CFrame = CFrame.new(x, 0.8, z) * CFrame.Angles(0, rnd:NextNumber(0, 6), 0.2), Color = shade(g, 0.8), Material = Enum.Material.Rock })
		elseif a.id == "volcano" then
			part({ Class = "WedgePart", Size = Vector3.new(2, rnd:NextNumber(4, 9), 3), CFrame = CFrame.new(x, 2.5, z), Color = Color3.fromRGB(40, 25, 22), Material = Enum.Material.Basalt })
			if i % 3 == 0 then
				local lava = part({ Size = Vector3.new(rnd:NextNumber(4, 8), 0.2, rnd:NextNumber(2, 4)), CFrame = CFrame.new(x, 0.1, z + 4), Color = Color3.fromRGB(255, 110, 30), Material = Enum.Material.Neon })
				local l = Instance.new("PointLight"); l.Color = lava.Color; l.Range = 14; l.Brightness = 2; l.Parent = lava
			end
		elseif a.id == "ice" then
			part({ Size = Vector3.new(1.2, rnd:NextNumber(3, 6), 1.2), CFrame = CFrame.new(x, 2, z) * CFrame.Angles(0, 0.7, rnd:NextNumber(-0.3, 0.3)), Color = Color3.fromRGB(200, 235, 255), Material = Enum.Material.Ice, Transparency = 0.2 })
		elseif a.id == "neon" then
			if i % 2 == 0 then
				part({ Size = Vector3.new(1.6, 16, 1.6), CFrame = CFrame.new(x, 8, z), Color = Color3.fromRGB(26, 18, 48) })
				local top = part({ Size = Vector3.new(2, 0.5, 2), CFrame = CFrame.new(x, 16.2, z), Color = (i % 4 == 0) and Color3.fromRGB(255, 79, 216) or Color3.fromRGB(70, 230, 255), Material = Enum.Material.Neon })
				local l = Instance.new("PointLight"); l.Color = top.Color; l.Range = 22; l.Brightness = 3; l.Parent = top
			end
		elseif a.id == "temple" then
			if i % 2 == 0 then
				part({ Shape = Enum.PartType.Cylinder, Size = Vector3.new(14, 2.2, 2.2), CFrame = CFrame.new(x, 7, z) * CFrame.Angles(0, 0, math.rad(90)), Color = Color3.fromRGB(170, 135, 80), Material = Enum.Material.Marble })
				part({ Size = Vector3.new(3.2, 0.8, 3.2), CFrame = CFrame.new(x, 14.3, z), Color = Color3.fromRGB(150, 118, 70), Material = Enum.Material.Marble })
			end
		end
	end
	if a.id == "neon" then
		for i = 0, math.floor(W / 10) do
			part({ Size = Vector3.new(0.15, 0.05, 40), CFrame = CFrame.new(i * 10, 0.03, 8), Color = Color3.fromRGB(255, 79, 216), Material = Enum.Material.Neon, Transparency = 0.5 })
		end
	end
	buildTower(1)
	buildTower(2)
end

-- Burg stürzt ein
function Arena.collapse(side)
	local m = towers[side]
	if not m then return end
	local rnd = Random.new()
	for _, p in ipairs(m:GetDescendants()) do
		if p:IsA("BasePart") then
			p.CanCollide = true
			p.Anchored = false
			p.AssemblyLinearVelocity = Vector3.new(rnd:NextNumber(-30, 30), rnd:NextNumber(5, 40), rnd:NextNumber(-10, 25))
			p.AssemblyAngularVelocity = Vector3.new(rnd:NextNumber(-4, 4), rnd:NextNumber(-4, 4), rnd:NextNumber(-4, 4))
		end
	end
end

return Arena
