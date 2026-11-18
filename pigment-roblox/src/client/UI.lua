-- Pigment Krieg: komplette Benutzeroberfläche (Menü, Shop, Schmiede, Kampf-HUD). Funktioniert mit Maus und Touch.
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local HttpService = game:GetService("HttpService")
local TweenService = game:GetService("TweenService")
local UserInputService = game:GetService("UserInputService")
local MarketplaceService = game:GetService("MarketplaceService")

local PK = ReplicatedStorage:WaitForChild("PK")
local Config = require(PK:WaitForChild("Config"))
local Pose = require(PK:WaitForChild("Pose"))
local Lang = require(PK:WaitForChild("Lang"))
local T = Lang.t
local SocialService = game:GetService("SocialService")

local UI = {}
local player = Players.LocalPlayer

local INK = Color3.fromRGB(11, 11, 13)
local PAPER = Color3.fromRGB(246, 246, 242)
local GLASS = Color3.fromRGB(18, 18, 24)
local GOLD = Color3.fromRGB(255, 204, 61)
local MUTE = Color3.fromRGB(170, 166, 160)
local RED = Color3.fromRGB(255, 74, 74)
local GREEN = Color3.fromRGB(92, 255, 138)
local DISPLAY = Enum.Font.FredokaOne
local BODY = Enum.Font.GothamBold

local function make(class, props, children)
	local o = Instance.new(class)
	for k, v in pairs(props or {}) do
		if k ~= "Parent" then o[k] = v end
	end
	for _, c in ipairs(children or {}) do c.Parent = o end
	if props and props.Parent then o.Parent = props.Parent end
	return o
end
local function corner(r) return make("UICorner", { CornerRadius = UDim.new(0, r or 12) }) end
local function stroke(c, t, tr) return make("UIStroke", { Color = c or Color3.new(1, 1, 1), Thickness = t or 1, Transparency = tr or 0.85, ApplyStrokeMode = Enum.ApplyStrokeMode.Border }) end
local function pad(n) return make("UIPadding", { PaddingLeft = UDim.new(0, n), PaddingRight = UDim.new(0, n), PaddingTop = UDim.new(0, n), PaddingBottom = UDim.new(0, n) }) end
local function label(props)
	local max = props.Max or 40
	props.Max = nil
	if props.Text then props.Text = T(props.Text) end
	props.BackgroundTransparency = 1
	props.Font = props.Font or BODY
	props.TextColor3 = props.TextColor3 or PAPER
	props.TextScaled = props.TextScaled ~= false
	return make("TextLabel", props, { make("UITextSizeConstraint", { MaxTextSize = max }) })
end
local function button(props, children)
	local max, r = props.Max or 22, props.R or 12
	props.Max = nil
	props.R = nil
	if props.Text then props.Text = T(props.Text) end
	props.AutoButtonColor = true
	props.Font = props.Font or BODY
	props.TextColor3 = props.TextColor3 or PAPER
	props.TextScaled = true
	local b = make("TextButton", props, children)
	corner(r).Parent = b
	make("UITextSizeConstraint", { MaxTextSize = max }).Parent = b
	make("UIPadding", { PaddingLeft = UDim.new(0, 6), PaddingRight = UDim.new(0, 6), PaddingTop = UDim.new(0, 4), PaddingBottom = UDim.new(0, 4) }).Parent = b
	return b
end

------------------------------------------------------------------ Vorschau-Figuren
local function preview(parent, info, poseInfo)
	local Render = UI.Render
	local vp = make("ViewportFrame", { BackgroundTransparency = 1, Size = UDim2.fromScale(1, 1), Ambient = Color3.fromRGB(200, 200, 200), LightColor = Color3.new(1, 1, 1), LightDirection = Vector3.new(-1, -1, -1), Parent = parent })
	local cam = Instance.new("Camera")
	cam.FieldOfView = 40
	cam.Parent = vp
	vp.CurrentCamera = cam
	local st = Render.buildStick(info, vp)
	local p = Pose.compute(poseInfo or { type = info.type, moving = false, seed = 1 }, 0.3)
	local J = Pose.solve(p, info.scale or 1, 1)
	local parts, cfs = {}, {}
	Render.place(st, J, 17, 1, p, 0.3, parts, cfs)
	for i, part in ipairs(parts) do part.CFrame = cfs[i] end
	local h = 9 * (info.scale or 1)
	cam.CFrame = CFrame.lookAt(Vector3.new(0.6, h * 0.5, h * 1.55), Vector3.new(0.6, h * 0.48, 0))
	return vp
end

------------------------------------------------------------------ Aufbau
function UI.init(ctx)
	Lang.setLocale(player.LocaleId)
	UI.Render = ctx.Render
	UI.Action = ctx.Action
	UI.State = ctx.State
	local gui = make("ScreenGui", { Name = "PigmentKrieg", ResetOnSpawn = false, IgnoreGuiInset = true, ZIndexBehavior = Enum.ZIndexBehavior.Sibling, Parent = player:WaitForChild("PlayerGui") })
	UI.gui = gui
	-- Alles liegt in "base"; UIScale vergrößert/verkleinert es passend zum Bildschirm.
	-- Damit base trotzdem den ganzen Bildschirm füllt, bekommt es die Größe 1/Skalierung.
	local base = make("Frame", { BackgroundTransparency = 1, Size = UDim2.fromScale(1, 1), Parent = gui })
	local scale = make("UIScale", { Parent = base })
	local function rescale()
		local v = workspace.CurrentCamera.ViewportSize
		local k = math.clamp(math.min(v.X / 1280, v.Y / 720), 0.5, 1.5)
		scale.Scale = k
		base.Size = UDim2.fromScale(1 / k, 1 / k)
	end
	rescale()
	workspace.CurrentCamera:GetPropertyChangedSignal("ViewportSize"):Connect(rescale)
	UI.base = base

	UI.buildHud(base)
	UI.buildMenu(base)
	UI.buildOverlays(base)
	UI.buildExtras(base)
	return UI
end

function UI.data()
	local raw = player:GetAttribute("Data")
	if not raw then return nil end
	local ok, d = pcall(function() return HttpService:JSONDecode(raw) end)
	return ok and d or nil
end

------------------------------------------------------------------ HUD
function UI.buildHud(base)
	local hud = make("Frame", { Name = "Hud", BackgroundTransparency = 1, Size = UDim2.fromScale(1, 1), Visible = false, Parent = base })
	UI.hud = hud
	-- obere Leiste
	local top = make("Frame", { BackgroundTransparency = 1, Size = UDim2.new(1, -24, 0, 54), Position = UDim2.fromOffset(12, 10), Parent = hud })
	local function hpBar(side, pos, anchor)
		local f = make("Frame", { BackgroundTransparency = 1, Size = UDim2.new(0.36, 0, 1, 0), Position = pos, AnchorPoint = anchor, Parent = top })
		label({ Text = side == 1 and "SCHWARZ" or "WEISS", Size = UDim2.new(1, 0, 0, 18), TextXAlignment = side == 1 and Enum.TextXAlignment.Left or Enum.TextXAlignment.Right, Font = DISPLAY, Max = 16, Parent = f })
		local bar = make("Frame", { BackgroundColor3 = Color3.new(0, 0, 0), BackgroundTransparency = 0.4, Size = UDim2.new(1, 0, 0, 14), Position = UDim2.fromOffset(0, 22), Parent = f }, { corner(7), stroke(nil, 1, 0.8) })
		local fill = make("Frame", { BackgroundColor3 = side == 1 and Color3.fromRGB(40, 40, 46) or PAPER, Size = UDim2.fromScale(1, 1), AnchorPoint = Vector2.new(side == 1 and 0 or 1, 0), Position = UDim2.fromScale(side == 1 and 0 or 1, 0), Parent = bar }, { corner(7) })
		make("UIGradient", { Color = side == 1 and ColorSequence.new(Color3.fromRGB(70, 70, 80), Color3.fromRGB(20, 20, 24)) or ColorSequence.new(Color3.new(1, 1, 1), Color3.fromRGB(200, 200, 194)), Parent = fill })
		local you = label({ Text = "", Size = UDim2.new(1, 0, 0, 14), Position = UDim2.fromOffset(0, 38), TextXAlignment = side == 1 and Enum.TextXAlignment.Left or Enum.TextXAlignment.Right, TextColor3 = GOLD, Max = 13, Parent = f })
		return fill, you
	end
	UI.hp1, UI.you1 = hpBar(1, UDim2.fromScale(0, 0), Vector2.new(0, 0))
	UI.hp2, UI.you2 = hpBar(2, UDim2.new(1, -110, 0, 0), Vector2.new(1, 0))
	UI.arenaLabel = label({ Text = "", Size = UDim2.new(0.2, 0, 0, 18), Position = UDim2.new(0.5, -55, 0, 2), AnchorPoint = Vector2.new(0.5, 0), TextColor3 = MUTE, Max = 15, Parent = top })
	UI.phaseLabel = label({ Text = "", Size = UDim2.new(0.2, 0, 0, 22), Position = UDim2.new(0.5, -55, 0, 22), AnchorPoint = Vector2.new(0.5, 0), Font = DISPLAY, Max = 20, Parent = top })
	UI.menuBtn = button({ Text = "MENÜ", BackgroundColor3 = GLASS, BackgroundTransparency = 0.15, Size = UDim2.fromOffset(96, 40), Position = UDim2.new(1, 0, 0, 2), AnchorPoint = Vector2.new(1, 0), Font = DISPLAY, Max = 18, Parent = top })
	UI.menuBtn.MouseButton1Click:Connect(function() UI.menuForced = true end)

	-- Gold & Befehle
	UI.goldLabel = label({ Text = "0", Size = UDim2.fromOffset(260, 34), Position = UDim2.fromOffset(14, 74), TextXAlignment = Enum.TextXAlignment.Left, Font = DISPLAY, TextColor3 = GOLD, Max = 30, TextStrokeTransparency = 0.4, Parent = hud })
	UI.rageLabel = label({ Text = "", Size = UDim2.fromOffset(260, 24), Position = UDim2.fromOffset(14, 108), TextXAlignment = Enum.TextXAlignment.Left, Font = DISPLAY, TextColor3 = Color3.fromRGB(255, 106, 60), Max = 20, TextStrokeTransparency = 0.4, Parent = hud })
	local cmds = make("Frame", { BackgroundTransparency = 1, Size = UDim2.fromOffset(330, 40), Position = UDim2.new(1, -12, 0, 74), AnchorPoint = Vector2.new(1, 0), Parent = hud }, {
		make("UIListLayout", { FillDirection = Enum.FillDirection.Horizontal, HorizontalAlignment = Enum.HorizontalAlignment.Right, Padding = UDim.new(0, 8) }),
	})
	UI.cmdDef = button({ Text = "VERTEIDIGEN (Q)", BackgroundColor3 = GLASS, Size = UDim2.fromOffset(160, 40), Max = 15, Parent = cmds })
	UI.cmdAtk = button({ Text = "ANGRIFF (E)", BackgroundColor3 = PAPER, TextColor3 = INK, Size = UDim2.fromOffset(150, 40), Max = 15, Parent = cmds })
	UI.cmdDef.MouseButton1Click:Connect(function() UI.onCmd("defend") end)
	UI.cmdAtk.MouseButton1Click:Connect(function() UI.onCmd("attack") end)

	-- Boss
	UI.bossFrame = make("Frame", { BackgroundTransparency = 1, Size = UDim2.fromOffset(520, 40), Position = UDim2.new(0.5, 0, 0, 120), AnchorPoint = Vector2.new(0.5, 0), Visible = false, Parent = hud })
	label({ Text = "BOSS-TITAN", Font = DISPLAY, TextColor3 = Color3.fromRGB(255, 179, 179), Size = UDim2.new(1, 0, 0, 18), Max = 18, TextStrokeTransparency = 0.3, Parent = UI.bossFrame })
	local bb = make("Frame", { BackgroundColor3 = Color3.new(0, 0, 0), BackgroundTransparency = 0.3, Size = UDim2.new(1, 0, 0, 14), Position = UDim2.fromOffset(0, 22), Parent = UI.bossFrame }, { corner(7), stroke(RED, 2, 0) })
	UI.bossFill = make("Frame", { BackgroundColor3 = RED, Size = UDim2.fromScale(1, 1), Parent = bb }, { corner(7) })

	-- untere Leiste
	local bar = make("Frame", { BackgroundColor3 = Color3.new(0, 0, 0), BackgroundTransparency = 0.45, Size = UDim2.new(1, 0, 0, 132), Position = UDim2.fromScale(0, 1), AnchorPoint = Vector2.new(0, 1), BorderSizePixel = 0, Parent = hud })
	make("UIGradient", { Rotation = 90, Transparency = NumberSequence.new({ NumberSequenceKeypoint.new(0, 1), NumberSequenceKeypoint.new(0.35, 0.2), NumberSequenceKeypoint.new(1, 0) }), Parent = bar })
	local row = make("Frame", { BackgroundTransparency = 1, Size = UDim2.new(1, -20, 0, 116), Position = UDim2.new(0.5, 0, 1, -8), AnchorPoint = Vector2.new(0.5, 1), Parent = bar }, {
		make("UIListLayout", { FillDirection = Enum.FillDirection.Horizontal, HorizontalAlignment = Enum.HorizontalAlignment.Center, VerticalAlignment = Enum.VerticalAlignment.Bottom, Padding = UDim.new(0, 8) }),
	})
	UI.unitBtns = {}
	for i, t in ipairs(Config.UNIT_ORDER) do
		local def = Config.UNITS[t]
		local b = make("TextButton", { Text = "", AutoButtonColor = true, BackgroundColor3 = Color3.fromRGB(62, 64, 74), Size = UDim2.fromOffset(96, 112), LayoutOrder = i, Parent = row }, {
			corner(14), make("UIStroke", { Color = Color3.new(1, 1, 1), Transparency = 0.8, Thickness = 2 }),
			make("UIGradient", { Rotation = 90, Color = ColorSequence.new(Color3.fromRGB(110, 112, 124), Color3.fromRGB(42, 44, 52)) }),
		})
		local holder = make("Frame", { BackgroundTransparency = 1, Size = UDim2.new(1, -8, 1, -30), Position = UDim2.fromOffset(4, 6), Parent = b })
		label({ Text = tostring(def.cost), Font = DISPLAY, TextColor3 = GOLD, Size = UDim2.fromOffset(44, 18), Position = UDim2.new(1, -6, 0, 4), AnchorPoint = Vector2.new(1, 0), TextXAlignment = Enum.TextXAlignment.Right, Max = 16, TextStrokeTransparency = 0.5, ZIndex = 3, Parent = b })
		label({ Text = tostring(i), Size = UDim2.fromOffset(16, 14), Position = UDim2.fromOffset(6, 5), TextColor3 = MUTE, Max = 12, ZIndex = 3, Parent = b })
		label({ Text = string.upper(T(def.name)), Size = UDim2.new(1, -6, 0, 18), Position = UDim2.new(0.5, 0, 1, -5), AnchorPoint = Vector2.new(0.5, 1), Max = 14, ZIndex = 3, Parent = b })
		local cd = make("Frame", { BackgroundColor3 = Color3.new(0, 0, 0), BackgroundTransparency = 0.4, Size = UDim2.fromScale(1, 0), Position = UDim2.fromScale(0, 1), AnchorPoint = Vector2.new(0, 1), ZIndex = 4, Parent = b }, { corner(14) })
		b.MouseButton1Down:Connect(function() UI.onSpawn(t) end)
		UI.unitBtns[t] = { btn = b, holder = holder, cd = cd, stroke = b:FindFirstChildOfClass("UIStroke") }
	end
	make("Frame", { BackgroundTransparency = 1, Size = UDim2.fromOffset(10, 10), LayoutOrder = 50, Parent = row })
	UI.abBtns = {}
	for i, k in ipairs(Config.ABILITY_ORDER) do
		local A = Config.ABILITIES[k]
		local b = make("TextButton", { Text = "", AutoButtonColor = true, BackgroundColor3 = Color3.fromRGB(74, 36, 34), Size = UDim2.fromOffset(78, 78), LayoutOrder = 60 + i, Parent = row }, {
			make("UICorner", { CornerRadius = UDim.new(1, 0) }), make("UIStroke", { Color = Color3.fromRGB(255, 106, 60), Transparency = 0.6, Thickness = 2 }),
		})
		label({ Text = A.icon, Font = DISPLAY, Size = UDim2.new(1, 0, 0.55, 0), Position = UDim2.fromScale(0, 0.08), Max = 32, ZIndex = 3, Parent = b })
		label({ Text = string.upper(T(A.name)), Size = UDim2.new(0.9, 0, 0.2, 0), Position = UDim2.fromScale(0.05, 0.62), Max = 11, ZIndex = 3, Parent = b })
		label({ Text = A.key, Size = UDim2.fromOffset(18, 16), Position = UDim2.new(1, -4, 0, 0), AnchorPoint = Vector2.new(1, 0), TextColor3 = MUTE, Max = 12, ZIndex = 3, Parent = b })
		local cd = make("Frame", { BackgroundColor3 = Color3.new(0, 0, 0), BackgroundTransparency = 0.35, Size = UDim2.fromScale(1, 0), Position = UDim2.fromScale(0, 1), AnchorPoint = Vector2.new(0, 1), ZIndex = 4, Parent = b }, { make("UICorner", { CornerRadius = UDim.new(1, 0) }) })
		b.MouseButton1Down:Connect(function() UI.onAbility(k) end)
		UI.abBtns[k] = { btn = b, cd = cd, stroke = b:FindFirstChildOfClass("UIStroke") }
	end

	UI.aimHint = make("Frame", { BackgroundColor3 = Color3.fromRGB(120, 20, 10), BackgroundTransparency = 0.1, Size = UDim2.fromOffset(470, 44), Position = UDim2.fromScale(0.5, 0.3), AnchorPoint = Vector2.new(0.5, 0.5), Visible = false, Parent = hud }, { corner(22), stroke(Color3.fromRGB(255, 106, 60), 1, 0) })
	label({ Text = "Tippe aufs Schlachtfeld für den Meteor", Size = UDim2.new(1, -140, 1, -12), Position = UDim2.fromOffset(16, 6), TextXAlignment = Enum.TextXAlignment.Left, Max = 18, Parent = UI.aimHint })
	local cancel = button({ Text = "Abbrechen", BackgroundColor3 = Color3.fromRGB(60, 10, 6), Size = UDim2.fromOffset(110, 32), Position = UDim2.new(1, -6, 0.5, 0), AnchorPoint = Vector2.new(1, 0.5), Max = 15, R = 16, Parent = UI.aimHint })
	cancel.MouseButton1Click:Connect(function() UI.onCancelAim() end)
end

------------------------------------------------------------------ Menü
function UI.buildMenu(base)
	local menu = make("Frame", { Name = "Menu", BackgroundColor3 = GLASS, BackgroundTransparency = 0.08, Size = UDim2.fromOffset(1000, 620), Position = UDim2.fromScale(0.5, 0.5), AnchorPoint = Vector2.new(0.5, 0.5), Parent = base }, {
		corner(20), stroke(nil, 1, 0.85), make("UISizeConstraint", { MaxSize = Vector2.new(1000, 620) }),
	})
	UI.menu = menu
	local title = make("Frame", { BackgroundTransparency = 1, Size = UDim2.new(1, -40, 0, 64), Position = UDim2.fromOffset(20, 14), Parent = menu })
	local t1 = label({ Text = "PIGMENT", Font = DISPLAY, TextColor3 = INK, Size = UDim2.fromOffset(250, 56), TextXAlignment = Enum.TextXAlignment.Right, Max = 56, TextStrokeColor3 = Color3.fromRGB(120, 120, 120), TextStrokeTransparency = 0.2, Parent = title })
	label({ Text = "KRIEG", Font = DISPLAY, TextColor3 = PAPER, Size = UDim2.fromOffset(200, 56), Position = UDim2.fromOffset(262, 0), TextXAlignment = Enum.TextXAlignment.Left, Max = 56, TextStrokeTransparency = 0.6, Parent = title })
	t1.ZIndex = 2
	UI.coinLabel = label({ Text = "0", Font = DISPLAY, TextColor3 = GOLD, Size = UDim2.fromOffset(220, 40), Position = UDim2.new(1, -60, 0, 8), AnchorPoint = Vector2.new(1, 0), TextXAlignment = Enum.TextXAlignment.Right, Max = 30, Parent = title })
	UI.closeBtn = button({ Text = "X", BackgroundColor3 = Color3.fromRGB(50, 50, 58), Size = UDim2.fromOffset(44, 44), Position = UDim2.new(1, 0, 0, 6), AnchorPoint = Vector2.new(1, 0), Font = DISPLAY, Max = 22, Parent = title })
	UI.closeBtn.MouseButton1Click:Connect(function() UI.menuForced = false end)

	local tabs = make("Frame", { BackgroundTransparency = 1, Size = UDim2.new(1, -40, 0, 44), Position = UDim2.fromOffset(20, 84), Parent = menu }, {
		make("UIListLayout", { FillDirection = Enum.FillDirection.Horizontal, Padding = UDim.new(0, 8) }),
	})
	local body = make("Frame", { BackgroundTransparency = 1, Size = UDim2.new(1, -40, 1, -146), Position = UDim2.fromOffset(20, 136), Parent = menu })
	UI.panes = {}
	UI.tabBtns = {}
	local names = { { "play", "SCHLACHT" }, { "shop", "HELME" }, { "forge", "WAFFEN" }, { "robux", "SHOP R$" } }
	for i, n in ipairs(names) do
		local b = button({ Text = n[2], BackgroundColor3 = n[1] == "robux" and Color3.fromRGB(0, 120, 60) or Color3.fromRGB(40, 40, 48), Size = UDim2.new(1 / 4, -6, 1, 0), LayoutOrder = i, Font = DISPLAY, Max = 20, Parent = tabs })
		b.MouseButton1Click:Connect(function() UI.setTab(n[1]) end)
		UI.tabBtns[n[1]] = b
		local pane = make("ScrollingFrame", { BackgroundTransparency = 1, Size = UDim2.fromScale(1, 1), CanvasSize = UDim2.new(), AutomaticCanvasSize = Enum.AutomaticSize.Y, ScrollBarThickness = 6, ScrollingDirection = Enum.ScrollingDirection.Y, Visible = false, BorderSizePixel = 0, Parent = body })
		UI.panes[n[1]] = pane
	end

	-- Schlacht: Team + Arena
	local play = UI.panes.play
	make("UIListLayout", { Padding = UDim.new(0, 10), SortOrder = Enum.SortOrder.LayoutOrder, Parent = play })
	-- Großer Spielen-Bereich
	local top = make("Frame", { BackgroundTransparency = 1, Size = UDim2.new(1, -10, 0, 96), LayoutOrder = -3, Parent = play })
	UI.playBtn = button({ Text = "SPIELEN", BackgroundColor3 = Color3.fromRGB(40, 200, 90), TextColor3 = PAPER, Size = UDim2.new(0.5, -6, 1, -26), Font = DISPLAY, Max = 46, R = 18, Parent = top })
	make("UIStroke", { Color = Color3.fromRGB(10, 90, 40), Thickness = 3, ApplyStrokeMode = Enum.ApplyStrokeMode.Border, Parent = UI.playBtn })
	make("UIGradient", { Rotation = 90, Color = ColorSequence.new(Color3.fromRGB(255, 255, 255), Color3.fromRGB(170, 170, 170)), Parent = UI.playBtn })
	label({ Text = "Gegen echte Spieler", TextColor3 = GREEN, Size = UDim2.new(0.5, -6, 0, 22), Position = UDim2.new(0, 0, 1, -22), Max = 16, Parent = top })
	UI.trainBtn = button({ Text = "TRAINING", BackgroundColor3 = Color3.fromRGB(70, 72, 84), TextColor3 = PAPER, Size = UDim2.new(0.25, -6, 1, -26), Position = UDim2.new(0.5, 6, 0, 0), Font = DISPLAY, Max = 28, R = 18, Parent = top })
	label({ Text = "Gegen den Computer", TextColor3 = MUTE, Size = UDim2.new(0.25, -6, 0, 22), Position = UDim2.new(0.5, 6, 1, -22), Max = 14, Parent = top })
	UI.inviteBtn = button({ Text = "FREUNDE EINLADEN", BackgroundColor3 = Color3.fromRGB(0, 120, 220), TextColor3 = PAPER, Size = UDim2.new(0.25, -6, 1, -26), Position = UDim2.new(0.75, 6, 0, 0), Font = DISPLAY, Max = 20, R = 18, Parent = top })
	UI.onlineLabel = label({ Text = "", TextColor3 = MUTE, Size = UDim2.new(0.25, -6, 0, 22), Position = UDim2.new(0.75, 6, 1, -22), Max = 14, Parent = top })
	UI.playBtn.MouseButton1Click:Connect(function() UI.Action:FireServer("play"); UI.menuForced = false end)
	UI.trainBtn.MouseButton1Click:Connect(function() UI.Action:FireServer("training"); UI.menuForced = false end)
	UI.inviteBtn.MouseButton1Click:Connect(function() UI.invite() end)
	label({ Text = "ODER WÄHLE DEIN TEAM", TextColor3 = MUTE, Size = UDim2.new(1, 0, 0, 20), TextXAlignment = Enum.TextXAlignment.Left, Max = 16, LayoutOrder = 1, Parent = play })
	local teams = make("Frame", { BackgroundTransparency = 1, Size = UDim2.new(1, -10, 0, 120), LayoutOrder = 2, Parent = play }, {
		make("UIListLayout", { FillDirection = Enum.FillDirection.Horizontal, Padding = UDim.new(0, 12) }),
	})
	UI.teamBtns = {}
	for side = 1, 2 do
		local b = make("TextButton", { Text = "", AutoButtonColor = true, BackgroundColor3 = side == 1 and Color3.fromRGB(14, 14, 17) or PAPER, Size = UDim2.new(0.5, -6, 1, 0), Parent = teams }, { corner(16) })
		local st = make("UIStroke", { Color = GOLD, Thickness = 3, Transparency = 1, Parent = b })
		local holder = make("Frame", { BackgroundTransparency = 1, Size = UDim2.fromOffset(110, 110), Position = UDim2.fromOffset(6, 5), Parent = b })
		label({ Text = side == 1 and "SCHWARZ" or "WEISS", Font = DISPLAY, TextColor3 = side == 1 and PAPER or INK, Size = UDim2.new(1, -140, 0, 40), Position = UDim2.fromOffset(126, 24), TextXAlignment = Enum.TextXAlignment.Left, Max = 34, Parent = b })
		UI.teamInfo = UI.teamInfo or {}
		UI.teamInfo[side] = label({ Text = "", TextColor3 = side == 1 and MUTE or Color3.fromRGB(90, 90, 90), Size = UDim2.new(1, -140, 0, 22), Position = UDim2.fromOffset(126, 68), TextXAlignment = Enum.TextXAlignment.Left, Max = 16, Parent = b })
		b.MouseButton1Click:Connect(function()
			UI.Action:FireServer("side", side)
			UI.menuForced = false
		end)
		UI.teamBtns[side] = { btn = b, stroke = st, holder = holder }
	end
	label({ Text = "ARENA WÄHLEN (Abstimmung für die nächste Runde)", TextColor3 = MUTE, Size = UDim2.new(1, 0, 0, 20), TextXAlignment = Enum.TextXAlignment.Left, Max = 16, LayoutOrder = 3, Parent = play })
	local arenas = make("Frame", { BackgroundTransparency = 1, Size = UDim2.new(1, -10, 0, 120), LayoutOrder = 4, Parent = play }, {
		make("UIGridLayout", { CellSize = UDim2.new(0.2, -8, 0, 116), CellPadding = UDim2.fromOffset(8, 8) }),
	})
	UI.arenaBtns = {}
	for i, a in ipairs(Config.ARENAS) do
		local b = make("TextButton", { Text = "", AutoButtonColor = true, BackgroundColor3 = Color3.fromRGB(a.fog[1], a.fog[2], a.fog[3]), Parent = arenas }, { corner(12) })
		make("UIGradient", { Rotation = 90, Color = ColorSequence.new(Color3.fromRGB(a.sky[1], a.sky[2], a.sky[3]), Color3.fromRGB(a.ground[1], a.ground[2], a.ground[3])), Parent = b })
		local st = make("UIStroke", { Color = GOLD, Thickness = 3, Transparency = 1, Parent = b })
		label({ Text = a.name, Font = DISPLAY, Size = UDim2.new(1, -12, 0, 24), Position = UDim2.new(0, 6, 1, -48), TextXAlignment = Enum.TextXAlignment.Left, Max = 18, TextStrokeTransparency = 0.3, Parent = b })
		label({ Text = string.rep("●", i) .. string.rep("○", 5 - i) .. "   +" .. a.reward .. " " .. T("Münzen"), TextColor3 = GOLD, Size = UDim2.new(1, -12, 0, 18), Position = UDim2.new(0, 6, 1, -22), TextXAlignment = Enum.TextXAlignment.Left, Max = 13, TextStrokeTransparency = 0.3, Parent = b })
		b.MouseButton1Click:Connect(function() UI.Action:FireServer("vote", i); UI.myVote = i end)
		UI.arenaBtns[i] = { btn = b, stroke = st }
	end
	label({ Text = "SPIELMODUS (Abstimmung)", TextColor3 = MUTE, Size = UDim2.new(1, 0, 0, 20), TextXAlignment = Enum.TextXAlignment.Left, Max = 16, LayoutOrder = 5, Parent = play })
	local modes = make("Frame", { BackgroundTransparency = 1, Size = UDim2.new(1, -10, 0, 84), LayoutOrder = 6, Parent = play }, {
		make("UIGridLayout", { CellSize = UDim2.new(0.25, -8, 0, 80), CellPadding = UDim2.fromOffset(8, 8) }),
	})
	UI.modeBtns = {}
	local modeCols = { classic = Color3.fromRGB(60, 60, 72), waves = Color3.fromRGB(120, 40, 30), boss = Color3.fromRGB(110, 20, 60), turbo = Color3.fromRGB(20, 80, 120) }
	for _, m in ipairs(Config.MODE_ORDER) do
		local M = Config.MODES[m]
		local b = make("TextButton", { Text = "", AutoButtonColor = true, BackgroundColor3 = modeCols[m], Parent = modes }, { corner(12) })
		local st = make("UIStroke", { Color = GOLD, Thickness = 3, Transparency = 1, Parent = b })
		label({ Text = M.name, Font = DISPLAY, Size = UDim2.new(1, -12, 0, 26), Position = UDim2.fromOffset(6, 8), Max = 20, Parent = b })
		label({ Text = M.desc, TextColor3 = Color3.fromRGB(220, 220, 220), Size = UDim2.new(1, -12, 0, 36), Position = UDim2.fromOffset(6, 38), Max = 13, TextWrapped = true, Font = Enum.Font.Gotham, Parent = b })
		b.MouseButton1Click:Connect(function() UI.Action:FireServer("modeVote", m); UI.myMode = m end)
		UI.modeBtns[m] = { btn = b, stroke = st }
	end
	UI.lobbyHint = label({ Text = "Tippe auf ein Team, um mitzukämpfen. Ist ein Team leer, spielt der Computer.", TextColor3 = MUTE, Size = UDim2.new(1, 0, 0, 40), Max = 16, LayoutOrder = 7, TextWrapped = true, Parent = play })
	local help = "Steuerung PC: 1–7 Krieger · R Meteor · F Kriegsschrei · H Heilwelle · Q/E Verteidigen/Angriff · A/D oder Mausrad: Kamera\nHandy: Knöpfe unten antippen, übers Feld wischen = Kamera"
	label({ Text = help, TextColor3 = MUTE, Size = UDim2.new(1, 0, 0, 44), Max = 14, LayoutOrder = 8, TextWrapped = true, Font = Enum.Font.Gotham, Parent = play })
	label({ Text = "TOP 10 SIEGE", TextColor3 = GOLD, Font = DISPLAY, Size = UDim2.new(1, 0, 0, 24), TextXAlignment = Enum.TextXAlignment.Left, Max = 20, LayoutOrder = -2, Parent = play })
	UI.topLabel = label({ Text = "Noch keine Einträge", TextColor3 = PAPER, Size = UDim2.new(1, -10, 0, 48), TextXAlignment = Enum.TextXAlignment.Left, TextYAlignment = Enum.TextYAlignment.Top, TextWrapped = true, Max = 15, Font = Enum.Font.Gotham, LayoutOrder = -1, Parent = play })

	-- Shop
	local shop = UI.panes.shop
	make("UIGridLayout", { CellSize = UDim2.fromOffset(176, 280), CellPadding = UDim2.fromOffset(10, 10), Parent = shop })
	UI.shopItems = {}
	for _, h in ipairs(Config.HELMETS) do
		local f = make("Frame", { BackgroundColor3 = Color3.fromRGB(34, 34, 42), Parent = shop }, { corner(14) })
		local st = make("UIStroke", { Color = GOLD, Thickness = 2, Transparency = 1, Parent = f })
		local holder = make("Frame", { BackgroundColor3 = Color3.fromRGB(70, 72, 82), Size = UDim2.new(1, -16, 0, 140), Position = UDim2.fromOffset(8, 8), Parent = f }, { corner(10) })
		label({ Text = h.name, Font = DISPLAY, Size = UDim2.new(1, -12, 0, 24), Position = UDim2.fromOffset(6, 152), Max = 18, Parent = f })
		local b = button({ Text = "", BackgroundColor3 = GOLD, TextColor3 = INK, Size = UDim2.new(1, -16, 0, 40), Position = UDim2.new(0, 8, 0, 184), Max = 16, Parent = f })
		b.MouseButton1Click:Connect(function()
			if h.pass then UI.buyPass(h.pass) else UI.Action:FireServer("helmet", h.id) end
		end)
		local rb = nil
		if h.robux then
			local prod = Config.PRODUCTS[h.robux]
			rb = button({ Text = "R$ " .. prod.robux, BackgroundColor3 = Color3.fromRGB(0, 150, 70), Size = UDim2.new(1, -16, 0, 40), Position = UDim2.new(0, 8, 0, 230), Max = 16, Parent = f })
			rb.MouseButton1Click:Connect(function() UI.buyProduct(h.robux) end)
		end
		if h.premium then st.Color = Color3.fromRGB(255, 77, 245); st.Transparency = 0.3 end
		UI.shopItems[h.id] = { frame = f, stroke = st, holder = holder, btn = b, rbtn = rb, def = h }
	end

	-- Schmiede
	local forge = UI.panes.forge
	make("UIListLayout", { Padding = UDim.new(0, 8), SortOrder = Enum.SortOrder.LayoutOrder, Parent = forge })
	UI.forgeRows = {}
	local order = 0
	local function row(key, kind, titleText, desc)
		order = order + 1
		local f = make("Frame", { BackgroundColor3 = Color3.fromRGB(34, 34, 42), Size = UDim2.new(1, -12, 0, 92), LayoutOrder = order, Parent = forge }, { corner(14) })
		local holder = make("Frame", { BackgroundColor3 = Color3.fromRGB(70, 72, 82), Size = UDim2.fromOffset(76, 76), Position = UDim2.fromOffset(8, 8), Parent = f }, { corner(10) })
		local name = label({ Text = titleText, Font = DISPLAY, Size = UDim2.new(1, -330, 0, 26), Position = UDim2.fromOffset(98, 10), TextXAlignment = Enum.TextXAlignment.Left, Max = 20, Parent = f })
		label({ Text = desc, TextColor3 = MUTE, Size = UDim2.new(1, -330, 0, 18), Position = UDim2.fromOffset(98, 38), TextXAlignment = Enum.TextXAlignment.Left, Max = 14, Font = Enum.Font.Gotham, Parent = f })
		local pips = make("Frame", { BackgroundTransparency = 1, Size = UDim2.fromOffset(200, 10), Position = UDim2.fromOffset(98, 64), Parent = f }, {
			make("UIListLayout", { FillDirection = Enum.FillDirection.Horizontal, Padding = UDim.new(0, 5) }),
		})
		local b = button({ Text = "", BackgroundColor3 = GOLD, TextColor3 = INK, Size = UDim2.fromOffset(210, 44), Position = UDim2.new(1, -12, 0.5, 0), AnchorPoint = Vector2.new(1, 0.5), Max = 16, Parent = f })
		b.MouseButton1Click:Connect(function() UI.Action:FireServer(kind, key) end)
		UI.forgeRows[key] = { frame = f, holder = holder, name = name, pips = pips, btn = b, kind = kind }
	end
	for _, t in ipairs(Config.UNIT_ORDER) do row(t, "upgrade", Config.UNITS[t].name, Config.UNITS[t].desc) end
	for _, k in ipairs(Config.ABILITY_ORDER) do row(k, "abilityUp", Config.ABILITIES[k].name, Config.ABILITIES[k].desc) end
	for _, x in ipairs(Config.EXTRAS) do row(x.id, "extra", x.name, x.desc) end

	-- Robux-Shop
	local rpane = UI.panes.robux
	make("UIGridLayout", { CellSize = UDim2.fromOffset(300, 150), CellPadding = UDim2.fromOffset(10, 10), SortOrder = Enum.SortOrder.LayoutOrder, Parent = rpane })
	local function card(order, title, desc, price, color, onBuy)
		local f = make("Frame", { BackgroundColor3 = color, LayoutOrder = order, Parent = rpane }, { corner(14), stroke(GOLD, 2, 0.5) })
		make("UIGradient", { Rotation = 90, Color = ColorSequence.new(Color3.new(1, 1, 1), Color3.fromRGB(150, 150, 150)), Parent = f })
		label({ Text = title, Font = DISPLAY, Size = UDim2.new(1, -20, 0, 32), Position = UDim2.fromOffset(10, 10), TextXAlignment = Enum.TextXAlignment.Left, Max = 26, TextStrokeTransparency = 0.5, Parent = f })
		label({ Text = desc, Size = UDim2.new(1, -20, 0, 40), Position = UDim2.fromOffset(10, 44), TextXAlignment = Enum.TextXAlignment.Left, Max = 14, TextWrapped = true, Font = Enum.Font.Gotham, Parent = f })
		local b = button({ Text = "R$ " .. price, BackgroundColor3 = Color3.fromRGB(0, 170, 80), Size = UDim2.new(1, -20, 0, 42), Position = UDim2.new(0, 10, 1, -52), Font = DISPLAY, Max = 22, Parent = f })
		b.MouseButton1Click:Connect(onBuy)
		return b
	end
	UI.passBtns = {}
	local o = 0
	for _, key in ipairs({ "vip", "rainbow" }) do
		local P = Config.PASSES[key]
		o = o + 1
		UI.passBtns[key] = card(o, (key == "vip" and "👑 " or "🌈 ") .. T(P.name), T(P.desc) .. T(" · für immer"), P.robux, key == "vip" and Color3.fromRGB(150, 110, 10) or Color3.fromRGB(120, 40, 160), function() UI.buyPass(key) end)
	end
	for _, key in ipairs(Config.PRODUCT_ORDER) do
		local P = Config.PRODUCTS[key]
		o = o + 1
		local desc = P.coins and "Sofort aufs Konto, für Waffen und Helme" or "Premium-Helm für deine ganze Armee"
		card(o, (P.coins and "🪙 " or "⛑ ") .. T(P.name), T(desc), P.robux, P.coins and Color3.fromRGB(40, 90, 60) or Color3.fromRGB(70, 30, 90), function() UI.buyProduct(key) end)
	end

	UI.setTab("play")
end

function UI.buyProduct(key)
	local P = Config.PRODUCTS[key]
	if not P or P.id == 0 then UI.toast("Dieser Artikel ist noch nicht eingerichtet (Produkt-ID fehlt)", 2.5); return end
	MarketplaceService:PromptProductPurchase(player, P.id)
end
function UI.buyPass(key)
	local P = Config.PASSES[key]
	if player:GetAttribute("Pass_" .. key) then UI.toast(T("Du hast %s schon!", T(P.name)), 2); return end
	if not P or P.id == 0 then UI.toast("Dieser Pass ist noch nicht eingerichtet (Pass-ID fehlt)", 2.5); return end
	MarketplaceService:PromptGamePassPurchase(player, P.id)
end

function UI.setTab(t)
	UI.tab = t
	for k, p in pairs(UI.panes) do p.Visible = k == t end
	for k, b in pairs(UI.tabBtns) do
		b.BackgroundColor3 = k == t and PAPER or Color3.fromRGB(40, 40, 48)
		b.TextColor3 = k == t and INK or PAPER
	end
end

------------------------------------------------------------------ Overlays
function UI.buildOverlays(base)
	UI.announce = label({ Text = "", Font = DISPLAY, Size = UDim2.fromOffset(900, 110), Position = UDim2.fromScale(0.5, 0.3), AnchorPoint = Vector2.new(0.5, 0.5), Max = 96, TextStrokeTransparency = 0, Visible = false, ZIndex = 20, Parent = base })
	UI.annScale = make("UIScale", { Parent = UI.announce })
	UI.toastLabel = make("TextLabel", { Text = "", Font = DISPLAY, TextColor3 = INK, BackgroundColor3 = PAPER, Size = UDim2.fromOffset(560, 44), Position = UDim2.fromScale(0.5, 0.17), AnchorPoint = Vector2.new(0.5, 0.5), TextScaled = true, Visible = false, ZIndex = 21, Parent = base }, { corner(22), pad(8), make("UITextSizeConstraint", { MaxTextSize = 20 }) })
	UI.endPanel = make("Frame", { BackgroundColor3 = GLASS, BackgroundTransparency = 0.05, Size = UDim2.fromOffset(480, 430), Position = UDim2.fromScale(0.5, 0.5), AnchorPoint = Vector2.new(0.5, 0.5), Visible = false, ZIndex = 15, Parent = base }, { corner(20), stroke(nil, 1, 0.8) })
	UI.againBtn = button({ Text = "NOCHMAL  ▶", BackgroundColor3 = GOLD, TextColor3 = INK, Size = UDim2.new(1, -40, 0, 58), Position = UDim2.fromOffset(20, 236), Font = DISPLAY, Max = 28, ZIndex = 16, Parent = UI.endPanel })
	UI.toMenuBtn = button({ Text = "ZUM MENÜ  (Shop · Waffen · Modus)", BackgroundColor3 = PAPER, TextColor3 = INK, Size = UDim2.new(1, -40, 0, 50), Position = UDim2.fromOffset(20, 302), Font = DISPLAY, Max = 20, ZIndex = 16, Parent = UI.endPanel })
	UI.againBtn.MouseButton1Click:Connect(function() UI.Action:FireServer("again"); UI.againPressed = true end)
	local inv2 = button({ Text = "FREUNDE EINLADEN", BackgroundColor3 = Color3.fromRGB(0, 120, 220), Size = UDim2.new(1, -40, 0, 40), Position = UDim2.fromOffset(20, 362), Font = DISPLAY, Max = 18, ZIndex = 16, Parent = UI.endPanel })
	inv2.MouseButton1Click:Connect(function() UI.invite() end)
	UI.toMenuBtn.MouseButton1Click:Connect(function() UI.Action:FireServer("menu"); UI.menuForced = false; UI.setTab("play") end)
	UI.endTitle = label({ Text = "SIEG!", Font = DISPLAY, Size = UDim2.new(1, -20, 0, 80), Position = UDim2.fromOffset(10, 16), Max = 72, ZIndex = 16, Parent = UI.endPanel })
	UI.endSub = label({ Text = "", TextColor3 = MUTE, Size = UDim2.new(1, -20, 0, 26), Position = UDim2.fromOffset(10, 100), Max = 18, ZIndex = 16, Parent = UI.endPanel })
	UI.endCoins = label({ Text = "", Font = DISPLAY, TextColor3 = GOLD, Size = UDim2.new(1, -20, 0, 40), Position = UDim2.fromOffset(10, 134), Max = 32, ZIndex = 16, Parent = UI.endPanel })
	UI.endNext = label({ Text = "", TextColor3 = MUTE, Size = UDim2.new(1, -20, 0, 22), Position = UDim2.fromOffset(10, 200), Max = 16, ZIndex = 16, Parent = UI.endPanel })
	UI.waveLabel = label({ Text = "", Font = DISPLAY, TextColor3 = Color3.fromRGB(255, 140, 90), Size = UDim2.fromOffset(300, 30), Position = UDim2.new(0.5, 0, 0, 84), AnchorPoint = Vector2.new(0.5, 0), Max = 26, TextStrokeTransparency = 0.3, Parent = UI.hud })
end

function UI.invite()
	local ok, can = pcall(function() return SocialService:CanSendGameInviteAsync(player) end)
	if ok and can then pcall(function() SocialService:PromptGameInvite(player) end)
	else UI.toast("Einladen ist gerade nicht möglich", 2) end
end

function UI.buildExtras(base)
	-- Gegnersuche
	UI.searchPanel = make("Frame", { BackgroundColor3 = GLASS, BackgroundTransparency = 0.05, Size = UDim2.fromOffset(520, 230), Position = UDim2.fromScale(0.5, 0.42), AnchorPoint = Vector2.new(0.5, 0.5), Visible = false, ZIndex = 14, Parent = base }, { corner(20), stroke(GREEN, 2, 0.3) })
	UI.searchTitle = label({ Text = "SUCHE GEGNER …", Font = DISPLAY, TextColor3 = GREEN, Size = UDim2.new(1, -20, 0, 50), Position = UDim2.fromOffset(10, 12), Max = 40, ZIndex = 15, Parent = UI.searchPanel })
	UI.searchInfo = label({ Text = "", TextColor3 = MUTE, Size = UDim2.new(1, -30, 0, 40), Position = UDim2.fromOffset(15, 66), Max = 16, TextWrapped = true, ZIndex = 15, Parent = UI.searchPanel })
	local b1 = button({ Text = "Jetzt gegen Computer", BackgroundColor3 = Color3.fromRGB(70, 72, 84), Size = UDim2.new(0.5, -25, 0, 52), Position = UDim2.new(0, 20, 1, -72), Font = DISPLAY, Max = 18, ZIndex = 15, Parent = UI.searchPanel })
	local b2 = button({ Text = "FREUNDE EINLADEN", BackgroundColor3 = Color3.fromRGB(0, 120, 220), Size = UDim2.new(0.5, -25, 0, 52), Position = UDim2.new(0.5, 5, 1, -72), Font = DISPLAY, Max = 18, ZIndex = 15, Parent = UI.searchPanel })
	b1.MouseButton1Click:Connect(function() UI.Action:FireServer("training") end)
	b2.MouseButton1Click:Connect(function() UI.invite() end)
	-- Tägliche Belohnung
	UI.dailyPanel = make("Frame", { BackgroundColor3 = Color3.fromRGB(60, 40, 0), Size = UDim2.fromOffset(420, 260), Position = UDim2.fromScale(0.5, 0.45), AnchorPoint = Vector2.new(0.5, 0.5), Visible = false, ZIndex = 30, Parent = base }, { corner(22), stroke(GOLD, 3, 0) })
	make("UIGradient", { Rotation = 90, Color = ColorSequence.new(Color3.fromRGB(255, 220, 120), Color3.fromRGB(150, 90, 10)), Parent = UI.dailyPanel })
	label({ Text = "TÄGLICHE BELOHNUNG", Font = DISPLAY, Size = UDim2.new(1, -20, 0, 40), Position = UDim2.fromOffset(10, 14), Max = 30, TextStrokeTransparency = 0.3, ZIndex = 31, Parent = UI.dailyPanel })
	UI.dailyStreak = label({ Text = "", Size = UDim2.new(1, -20, 0, 24), Position = UDim2.fromOffset(10, 60), Max = 18, TextStrokeTransparency = 0.5, ZIndex = 31, Parent = UI.dailyPanel })
	UI.dailyCoins = label({ Text = "", Font = DISPLAY, TextColor3 = PAPER, Size = UDim2.new(1, -20, 0, 60), Position = UDim2.fromOffset(10, 94), Max = 52, TextStrokeTransparency = 0.2, ZIndex = 31, Parent = UI.dailyPanel })
	local claim = button({ Text = "ABHOLEN", BackgroundColor3 = Color3.fromRGB(40, 200, 90), Size = UDim2.new(1, -60, 0, 54), Position = UDim2.new(0, 30, 1, -72), Font = DISPLAY, Max = 28, ZIndex = 31, Parent = UI.dailyPanel })
	claim.MouseButton1Click:Connect(function() UI.dailyPanel.Visible = false end)
	-- Hügel-Punkte
	UI.hillFrame = make("Frame", { BackgroundTransparency = 1, Size = UDim2.fromOffset(420, 44), Position = UDim2.new(0.5, 0, 0, 118), AnchorPoint = Vector2.new(0.5, 0), Visible = false, Parent = UI.hud })
	label({ Text = "HÜGEL", Font = DISPLAY, TextColor3 = GOLD, Size = UDim2.new(1, 0, 0, 16), Max = 16, TextStrokeTransparency = 0.3, Parent = UI.hillFrame })
	UI.hillBars = {}
	for s = 1, 2 do
		local bg = make("Frame", { BackgroundColor3 = Color3.new(0, 0, 0), BackgroundTransparency = 0.4, Size = UDim2.new(0.5, -4, 0, 16), Position = UDim2.new(s == 1 and 0 or 0.5, s == 1 and 0 or 4, 0, 22), Parent = UI.hillFrame }, { corner(8) })
		UI.hillBars[s] = make("Frame", { BackgroundColor3 = s == 1 and Color3.fromRGB(30, 30, 36) or PAPER, Size = UDim2.fromScale(0, 1), AnchorPoint = Vector2.new(s == 1 and 1 or 0, 0), Position = UDim2.fromScale(s == 1 and 1 or 0, 0), Parent = bg }, { corner(8), stroke(GOLD, 1, 0.4) })
	end
end

function UI.showDaily(r)
	UI.dailyStreak.Text = T("Tag %d in Folge!", r.streak)
	UI.dailyCoins.Text = "+" .. r.coins .. " 🪙"
	UI.dailyPanel.Visible = true
end

local annToken = 0
function UI.say(text, cls)
	annToken = annToken + 1
	local my = annToken
	local a = UI.announce
	local colors = { ok = GOLD, boss = Color3.fromRGB(255, 61, 61), kill = Color3.fromRGB(255, 90, 90), warn = Color3.fromRGB(255, 179, 71), rage = Color3.fromRGB(255, 106, 60), count = PAPER }
	a.Text = Lang.say(text)
	a.TextColor3 = colors[cls] or PAPER
	a.Visible = true
	a.Rotation = cls == "count" and 0 or -3
	UI.annScale.Scale = 2.4
	a.TextTransparency = 1
	TweenService:Create(UI.annScale, TweenInfo.new(0.35, Enum.EasingStyle.Back, Enum.EasingDirection.Out), { Scale = 1 }):Play()
	TweenService:Create(a, TweenInfo.new(0.15), { TextTransparency = 0 }):Play()
	task.delay(cls == "count" and 0.75 or 1.3, function()
		if my ~= annToken then return end
		TweenService:Create(a, TweenInfo.new(0.25), { TextTransparency = 1 }):Play()
		task.delay(0.26, function() if my == annToken then a.Visible = false end end)
	end)
end

local toastToken = 0
function UI.toast(msg, t)
	toastToken = toastToken + 1
	local my = toastToken
	UI.toastLabel.Text = T(msg)
	UI.toastLabel.Visible = true
	task.delay(t or 2, function() if my == toastToken then UI.toastLabel.Visible = false end end)
end

function UI.showReward(r)
	UI.reward = r
end

------------------------------------------------------------------ Daten -> Anzeige
local lastDataRaw, lastSide = nil, nil
function UI.refreshData(force)
	local raw = player:GetAttribute("Data")
	local side = player:GetAttribute("Side") or 0
	if not force and raw == lastDataRaw and side == lastSide then return end
	lastDataRaw, lastSide = raw, side
	local d = UI.data()
	if not d then return end
	local viewSide = side == 0 and 1 or side
	UI.coinLabel.Text = T("%d Münzen", d.coins)
	-- Team-Karten
	for s = 1, 2 do
		local tb = UI.teamBtns[s]
		tb.stroke.Transparency = side == s and 0 or 1
		for _, c in ipairs(tb.holder:GetChildren()) do c:Destroy() end
		preview(tb.holder, { type = "sword", side = s, tier = d.wlv.sword, helmet = d.helmet, scale = 1 })
	end
	-- Einheiten-Knöpfe
	for t, ub in pairs(UI.unitBtns) do
		for _, c in ipairs(ub.holder:GetChildren()) do c:Destroy() end
		preview(ub.holder, { type = t, side = viewSide, tier = d.wlv[t], helmet = d.helmet, scale = 1 })
	end
	-- Shop
	for id, it in pairs(UI.shopItems) do
		for _, c in ipairs(it.holder:GetChildren()) do if c:IsA("ViewportFrame") then c:Destroy() end end
		preview(it.holder, { type = "sword", side = viewSide, tier = d.wlv.sword, helmet = id, scale = 1 })
		local owned = d.owned[id] == true
		local eq = d.helmet == id
		it.stroke.Transparency = eq and 0 or 1
		if it.rbtn then it.rbtn.Visible = not owned end
		if eq then it.btn.Text = T("Ausgerüstet"); it.btn.BackgroundColor3 = Color3.fromRGB(58, 58, 64); it.btn.TextColor3 = MUTE
		elseif owned then it.btn.Text = T("Anziehen"); it.btn.BackgroundColor3 = PAPER; it.btn.TextColor3 = INK
		elseif it.def.pass then
			it.btn.Text = T("Mit VIP-Pass"); it.btn.BackgroundColor3 = Color3.fromRGB(0, 150, 70); it.btn.TextColor3 = PAPER
		else
			it.btn.Text = T("%d Münzen", it.def.price)
			local can = d.coins >= it.def.price
			it.btn.BackgroundColor3 = can and GOLD or Color3.fromRGB(58, 58, 64)
			it.btn.TextColor3 = can and INK or MUTE
		end
	end
	-- Schmiede
	local function pips(f, lv, max, color)
		for _, c in ipairs(f:GetChildren()) do if c:IsA("Frame") then c:Destroy() end end
		for i = 1, max do
			local col = type(color) == "table" and color[i] or color
			make("Frame", { BackgroundColor3 = col, BackgroundTransparency = i <= lv and 0 or 0.85, Size = UDim2.fromOffset(28, 8), Parent = f }, { corner(4) })
		end
	end
	local tierCols = {}
	for i, T in ipairs(Config.TIERS) do tierCols[i] = Color3.fromRGB(T.color[1], T.color[2], T.color[3]) end
	for key, r in pairs(UI.forgeRows) do
		local lv, max, cost, title
		if r.kind == "upgrade" then
			lv = d.wlv[key]; max = 5; cost = Config.UPGRADE_COST[lv]
			title = T(Config.UNITS[key].name) .. "  ·  " .. T(Config.TIERS[lv].name)
			pips(r.pips, lv, 5, tierCols)
			if not r.hasPreview or r.previewTier ~= lv or r.previewSide ~= viewSide or r.previewHelmet ~= d.helmet then
				for _, c in ipairs(r.holder:GetChildren()) do if c:IsA("ViewportFrame") then c:Destroy() end end
				preview(r.holder, { type = key, side = viewSide, tier = lv, helmet = d.helmet, scale = 1 })
				r.hasPreview = true; r.previewTier = lv; r.previewSide = viewSide; r.previewHelmet = d.helmet
			end
		elseif r.kind == "abilityUp" then
			lv = d.ab[key]; max = 3; cost = Config.ABILITY_COST[lv]
			title = T(Config.ABILITIES[key].name) .. "  ·  " .. T("Stufe %d", lv)
			pips(r.pips, lv, 3, Color3.fromRGB(255, 106, 60))
			if not r.hasPreview then
				label({ Text = Config.ABILITIES[key].icon, Font = DISPLAY, Size = UDim2.fromScale(1, 1), Max = 40, Parent = r.holder })
				r.hasPreview = true
			end
		else
			local x
			for _, e in ipairs(Config.EXTRAS) do if e.id == key then x = e end end
			lv = d[key]; max = x.max; cost = Config.extraCost(x, lv)
			title = T(x.name) .. "  ·  " .. T("Stufe %d", lv)
			pips(r.pips, lv, max, GOLD)
			if not r.hasPreview then
				label({ Text = key == "armor" and "🛡" or (key == "mine" and "⛏" or "🏰"), Size = UDim2.fromScale(1, 1), Max = 40, Parent = r.holder })
				r.hasPreview = true
			end
		end
		r.name.Text = title
		local maxed = lv >= max
		local can = not maxed and d.coins >= cost
		if maxed then r.btn.Text = "MAX"
		elseif r.kind == "upgrade" then r.btn.Text = T(Config.TIERS[lv + 1].name) .. " · " .. cost
		else r.btn.Text = T("Verbessern · %d", cost) end
		r.btn.BackgroundColor3 = can and GOLD or Color3.fromRGB(58, 58, 64)
		r.btn.TextColor3 = can and INK or MUTE
	end
end

------------------------------------------------------------------ Laufende Anzeige
local lastCount = nil
function UI.update(view)
	local S = UI.State
	local side = player:GetAttribute("Side") or 0
	local phase = S:GetAttribute("Phase") or "lobby"
	local now = workspace:GetServerTimeNow()
	UI.refreshData(false)

	local menuOpen = side == 0 or UI.menuForced == true
	UI.menu.Visible = menuOpen
	UI.closeBtn.Visible = side ~= 0
	UI.hud.Visible = side ~= 0 and not menuOpen
	for i, ab in pairs(UI.arenaBtns) do ab.stroke.Transparency = (UI.myVote == i) and 0 or 1 end
	for m, mb in pairs(UI.modeBtns) do mb.stroke.Transparency = ((UI.myMode or "classic") == m) and 0 or 1 end
	for key, b in pairs(UI.passBtns) do
		if player:GetAttribute("Pass_" .. key) then b.Text = T("Gekauft ✓"); b.BackgroundColor3 = Color3.fromRGB(58, 58, 64) end
	end
	for s = 1, 2 do
		local ai = S:GetAttribute("AI" .. s)
		local n = 0
		for _, p in ipairs(Players:GetPlayers()) do if p:GetAttribute("Side") == s then n = n + 1 end end
		UI.teamInfo[s].Text = n > 0 and (n .. " " .. T("Spieler")) or (ai == false and "" or T("Computer"))
	end

	local arenaIdx = S:GetAttribute("Arena") or 1
	local mode = S:GetAttribute("Mode") or "classic"
	UI.arenaLabel.Text = string.upper(T(Config.ARENAS[arenaIdx].name) .. "  ·  " .. T(Config.MODES[mode].name))
	local wave = S:GetAttribute("Wave") or 0
	UI.waveLabel.Text = (mode == "waves" and wave > 0) and T("WELLE %d", wave) or ""
	for s = 1, 2 do
		local hp, mx = S:GetAttribute("BaseHP" .. s) or 1, S:GetAttribute("BaseMax" .. s) or 1
		local fill = s == 1 and UI.hp1 or UI.hp2
		fill.Size = UDim2.fromScale(math.clamp(hp / math.max(mx, 1), 0, 1), 1)
		local you = s == 1 and UI.you1 or UI.you2
		you.Text = (side == s) and T("DEIN TEAM") or (S:GetAttribute("AI" .. s) and T("COMPUTER") or "")
	end

	-- Online, Bestenliste, Gegnersuche, Hügel
	UI.onlineLabel.Text = #Players:GetPlayers() .. " " .. T("Spieler online")
	local topRaw = S:GetAttribute("Top")
	if topRaw and topRaw ~= UI.lastTop then
		UI.lastTop = topRaw
		local ok, list = pcall(function() return HttpService:JSONDecode(topRaw) end)
		if ok and type(list) == "table" and #list > 0 then
			local parts = {}
			for i, e in ipairs(list) do table.insert(parts, i .. ". " .. tostring(e.n) .. "  " .. tostring(e.w)) end
			UI.topLabel.Text = table.concat(parts, "   ·   ")
		end
	end
	local sEnd = S:GetAttribute("SearchEnd") or 0
	UI.searchPanel.Visible = side ~= 0 and phase == "lobby" and sEnd > now and not menuOpen
	if UI.searchPanel.Visible then
		UI.searchInfo.Text = T("Kommt niemand, startet der Kampf in %d s gegen den Computer.", math.max(0, math.ceil(sEnd - now)))
	end
	UI.hillFrame.Visible = mode == "hill" and (phase == "battle" or phase == "countdown")
	if UI.hillFrame.Visible then
		for s2 = 1, 2 do UI.hillBars[s2].Size = UDim2.fromScale(math.clamp((S:GetAttribute("Hill" .. s2) or 0) / 100, 0, 1), 1) end
	end

	-- Phase
	local pend = S:GetAttribute("PhaseEnd") or 0
	if phase == "countdown" then
		local n = math.max(0, math.ceil(pend - now))
		UI.phaseLabel.Text = T("GLEICH GEHT'S LOS")
		if n ~= lastCount and side ~= 0 then
			lastCount = n
			if n > 0 then UI.say(tostring(n), "count") end
		end
	elseif phase == "battle" then
		if lastCount ~= nil and lastCount <= 1 and side ~= 0 then UI.say("KAMPF!", "ok") end
		lastCount = nil
		UI.phaseLabel.Text = T("KAMPF")
	elseif phase == "end" then
		UI.phaseLabel.Text = T("RUNDE VORBEI")
	else
		UI.phaseLabel.Text = T("WARTEN")
	end

	-- Ende
	local showEnd = phase == "end" and side ~= 0 and not menuOpen
	UI.endPanel.Visible = showEnd
	if showEnd then
		local winner = S:GetAttribute("Winner") or 0
		local won = winner == side
		if mode == "waves" then
			UI.endTitle.Text = T("WELLE %d", wave)
			UI.endTitle.TextColor3 = Color3.fromRGB(255, 140, 90)
			UI.endSub.Text = T("So lange habt ihr durchgehalten!")
		else
			UI.endTitle.Text = T(won and "SIEG!" or "NIEDERLAGE")
			UI.endTitle.TextColor3 = won and GOLD or RED
			local en = T(Config.SIDES[3 - side].name)
			UI.endSub.Text = won and T("Das %s-Pigment ist zerflossen.", en) or T("Das %s-Pigment hat gewonnen.", en)
		end
		local r = UI.reward
		UI.endCoins.Text = r and (T("+%d Münzen  ·  %d besiegt", r.coins, r.kills) .. (r.vip and "  ·  VIP x2" or "")) or ""
		local left = math.max(0, math.ceil(pend - now))
		if UI.againPressed then
			UI.endNext.Text = T("Warte auf die anderen Spieler … (%d s)", left)
			UI.againBtn.Text = T("BEREIT ✓")
		else
			UI.endNext.Text = T("Ohne Auswahl geht es in %d s zurück ins Menü", left)
			UI.againBtn.Text = T("NOCHMAL  ▶")
		end
	else
		UI.reward = (phase == "end") and UI.reward or nil
		UI.againPressed = false
	end

	if side == 0 then return end
	local gold = S:GetAttribute("Gold" .. side) or 0
	UI.goldLabel.Text = T("GOLD") .. " " .. gold .. "    ☠ " .. (S:GetAttribute("Kills" .. side) or 0)
	local rage = S:GetAttribute("Rage" .. side) or 0
	UI.rageLabel.Text = rage > 0 and T("KRIEGSSCHREI %.1f s", rage) or ""
	local cmd = S:GetAttribute("Cmd" .. side) or "attack"
	UI.cmdAtk.BackgroundColor3 = cmd == "attack" and PAPER or GLASS
	UI.cmdAtk.TextColor3 = cmd == "attack" and INK or PAPER
	UI.cmdDef.BackgroundColor3 = cmd == "defend" and PAPER or GLASS
	UI.cmdDef.TextColor3 = cmd == "defend" and INK or PAPER
	for t, ub in pairs(UI.unitBtns) do
		local def = Config.UNITS[t]
		local cd = S:GetAttribute("Cd" .. side .. "_" .. t) or 0
		ub.cd.Size = UDim2.fromScale(1, math.clamp(cd / def.spawnCd, 0, 1))
		local can = gold >= def.cost and cd <= 0 and phase == "battle"
		ub.btn.BackgroundTransparency = gold >= def.cost and 0 or 0.5
		ub.stroke.Color = can and GOLD or Color3.new(1, 1, 1)
		ub.stroke.Transparency = can and 0.3 or 0.8
	end
	for k, ab in pairs(UI.abBtns) do
		local cd = S:GetAttribute("Ab" .. side .. "_" .. k) or 0
		local mx = S:GetAttribute("AbMax" .. side .. "_" .. k) or 1
		ab.cd.Size = UDim2.fromScale(1, math.clamp(cd / math.max(mx, 0.1), 0, 1))
		local ready = cd <= 0 and phase == "battle"
		ab.stroke.Transparency = ready and 0 or 0.7
		ab.stroke.Thickness = (UI.targeting and k == "meteor") and 5 or (ready and (2 + math.abs(math.sin(os.clock() * 4)) * 2) or 2)
	end
	local bossId = S:GetAttribute("BossId") or 0
	local bossCfg = bossId ~= 0 and S.Units:FindFirstChild(tostring(bossId))
	UI.bossFrame.Visible = bossCfg ~= nil and bossCfg ~= false
	if bossCfg then
		UI.bossFill.Size = UDim2.fromScale(math.clamp((bossCfg:GetAttribute("HP") or 0) / math.max(1, bossCfg:GetAttribute("MaxHP") or 1), 0, 1), 1)
	end
	UI.aimHint.Visible = UI.targeting == true
end

return UI
