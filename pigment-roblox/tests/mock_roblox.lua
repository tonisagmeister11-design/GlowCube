-- Minimale Roblox-Attrappe, um Server- und Client-Skripte ohne Roblox Studio durchlaufen zu lassen.
-- Sie prüft nicht, ob Roblox die Eigenschaften kennt, sondern findet Logikfehler (nil-Zugriffe, falsche Felder).
local M = {}
local clock = 0
M.errors = {}

-- Vektoren ------------------------------------------------------------
local V3mt = {}
local function V3(x, y, z) return setmetatable({ X = x or 0, Y = y or 0, Z = z or 0 }, V3mt) end
V3mt.__add = function(a, b) return V3(a.X + b.X, a.Y + b.Y, a.Z + b.Z) end
V3mt.__sub = function(a, b) return V3(a.X - b.X, a.Y - b.Y, a.Z - b.Z) end
V3mt.__mul = function(a, b)
	if type(a) == "number" then a, b = b, a end
	if type(b) == "number" then return V3(a.X * b, a.Y * b, a.Z * b) end
	return V3(a.X * b.X, a.Y * b.Y, a.Z * b.Z)
end
V3mt.__div = function(a, b) return V3(a.X / b, a.Y / b, a.Z / b) end
V3mt.__unm = function(a) return V3(-a.X, -a.Y, -a.Z) end
V3mt.__index = function(t, k)
	if k == "Magnitude" then return math.sqrt(t.X ^ 2 + t.Y ^ 2 + t.Z ^ 2) end
	if k == "Unit" then local m = math.sqrt(t.X ^ 2 + t.Y ^ 2 + t.Z ^ 2); if m == 0 then return V3() end; return V3(t.X / m, t.Y / m, t.Z / m) end
	if k == "Cross" then return function(a, b) return V3(a.Y * b.Z - a.Z * b.Y, a.Z * b.X - a.X * b.Z, a.X * b.Y - a.Y * b.X) end end
	if k == "Dot" then return function(a, b) return a.X * b.X + a.Y * b.Y + a.Z * b.Z end end
	error("Vector3 hat kein Feld " .. tostring(k), 2)
end
local Vector3 = { new = V3 }
local V2mt = {}
local function V2(x, y) return setmetatable({ X = x or 0, Y = y or 0 }, V2mt) end
V2mt.__sub = function(a, b) return V2(a.X - b.X, a.Y - b.Y) end
V2mt.__add = function(a, b) return V2(a.X + b.X, a.Y + b.Y) end
local Vector2 = { new = V2 }

local CFmt = {}
local function CFr(pos) return setmetatable({ Position = pos or V3() }, CFmt) end
CFmt.__mul = function(a, b)
	if getmetatable(b) == V3mt then return a.Position + b end
	return CFr(a.Position + b.Position)
end
CFmt.__index = function(t, k)
	if k == "ToEulerAnglesXYZ" then return function() return 0, 0, 0 end end
	if k == "LookVector" then return V3(0, 0, -1) end
	error("CFrame hat kein Feld " .. tostring(k), 2)
end
local function chkNum(...)
	for i = 1, select("#", ...) do
		local v = select(i, ...)
		if type(v) == "number" and v ~= v then error("NaN in CFrame", 3) end
	end
end
local CFrame = {
	new = function(x, y, z)
		if type(x) == "table" then return CFr(x) end
		chkNum(x, y, z)
		return CFr(V3(x, y, z))
	end,
	Angles = function(a, b, c) chkNum(a, b, c); return CFr(V3()) end,
	lookAt = function(p, t, up) assert(getmetatable(p) == V3mt and getmetatable(t) == V3mt, "lookAt braucht Vector3"); return CFr(p) end,
	fromMatrix = function(p, x, y) assert(getmetatable(p) == V3mt and getmetatable(x) == V3mt and getmetatable(y) == V3mt, "fromMatrix braucht Vector3"); return CFr(p) end,
}
local C3mt = {}
C3mt.__index = { Lerp = function(a, b, t) return setmetatable({ R = a.R + (b.R - a.R) * t, G = a.G + (b.G - a.G) * t, B = a.B + (b.B - a.B) * t }, C3mt) end }
local function C3(r, g, b) return setmetatable({ R = r, G = g, B = b }, C3mt) end
local Color3 = {
	new = function(r, g, b) return C3(r, g, b) end,
	fromHSV = function(h, s, v) assert(h >= 0 and h <= 1, "fromHSV h außerhalb 0..1"); return C3(v, v, v) end,
	fromRGB = function(r, g, b)
		assert(type(r) == "number" and type(g) == "number" and type(b) == "number", "fromRGB braucht Zahlen")
		return C3(r / 255, g / 255, b / 255)
	end,
}
local UDim = { new = function(s, o) return { Scale = s, Offset = o } end }
local UDim2 = {
	new = function(a, b, c, d) return { a, b, c, d } end,
	fromScale = function(a, b) return { a, 0, b, 0 } end,
	fromOffset = function(a, b) return { 0, a, 0, b } end,
}
local function seqOK(...) return { ... } end
local NumberSequence = { new = seqOK }
local NumberSequenceKeypoint = { new = seqOK }
local ColorSequence = { new = seqOK }
local NumberRange = { new = seqOK }
local TweenInfo = { new = seqOK }
local Random = { new = function() return { NextNumber = function(_, a, b) return a + math.random() * (b - a) end } end }

-- Enum: Enum.Foo.Bar -> "Enum.Foo.Bar"
local Enum = setmetatable({}, { __index = function(E, cat)
	local c = setmetatable({}, { __index = function(C, item)
		local v = setmetatable({ Name = item, Value = 0 }, { __tostring = function() return "Enum." .. cat .. "." .. item end })
		rawset(C, item, v)
		return v
	end })
	rawset(E, cat, c)
	return c
end })

-- Signale ------------------------------------------------------------
local function Signal()
	local s = { fns = {} }
	function s:Connect(fn) table.insert(self.fns, fn); return { Disconnect = function() end } end
	function s:Fire(...)
		for _, fn in ipairs(self.fns) do
			local ok, err = pcall(fn, ...)
			if not ok then table.insert(M.errors, tostring(err)) end
		end
	end
	return s
end
M.Signal = Signal

-- Instanzen ----------------------------------------------------------
local Instance = {}
local IMT = {}
local BASEPART = { Part = true, WedgePart = true, MeshPart = true }
local function newInstance(class, name)
	local o = { _class = class, _children = {}, _attrs = {}, _props = { Name = name or class }, _signals = {}, _alive = true }
	return setmetatable(o, IMT)
end
local methods = {}
function methods:SetAttribute(k, v) self._attrs[k] = v end
function methods:GetAttribute(k) return self._attrs[k] end
function methods:GetChildren() local t = {}; for _, c in ipairs(self._children) do table.insert(t, c) end; return t end
function methods:GetDescendants()
	local t = {}
	local function walk(o) for _, c in ipairs(o._children) do table.insert(t, c); walk(c) end end
	walk(self)
	return t
end
function methods:FindFirstChild(n) for _, c in ipairs(self._children) do if c._props.Name == n then return c end end; return nil end
function methods:WaitForChild(n)
	local c = self:FindFirstChild(n)
	if not c then error("WaitForChild würde ewig warten: " .. tostring(n) .. " in " .. tostring(self._props.Name), 2) end
	return c
end
function methods:FindFirstChildOfClass(cl) for _, c in ipairs(self._children) do if c._class == cl then return c end end; return nil end
function methods:IsA(cl)
	if cl == "BasePart" then return BASEPART[self._class] == true end
	if cl == "GuiObject" then return true end
	return self._class == cl
end
function methods:Destroy()
	self._alive = false
	if self._props.Parent then self:_setParent(nil) end
	for _, c in ipairs(self:GetChildren()) do c:Destroy() end
end
function methods:_setParent(p)
	local old = rawget(self, "_props").Parent
	if old then
		for i, c in ipairs(old._children) do if c == self then table.remove(old._children, i); break end end
		if old._signals.ChildRemoved then old._signals.ChildRemoved:Fire(self) end
	end
	self._props.Parent = p
	if p then
		table.insert(p._children, self)
		if p._signals.ChildAdded then p._signals.ChildAdded:Fire(self) end
	end
end
function methods:GetPropertyChangedSignal() return Signal() end
function methods:GetAttributeChangedSignal() return Signal() end
function methods:Clone() return newInstance(self._class, self._props.Name) end
function methods:Emit(n) assert(type(n) == "number", "Emit braucht Zahl") end
function methods:Play() end
function methods:SetTeleportData(d) self._props.TeleportData = d end
function methods:GetJoinData() return {} end
function methods:FireServer(...) M.serverEvents = (M.serverEvents or 0) + 1; local s = self._signals.OnServerEvent; if s then s:Fire(M.localPlayer, ...) end end
function methods:FireAllClients(...) M.clientEvents = (M.clientEvents or 0) + 1; local s = self._signals.OnClientEvent; if s then s:Fire(...) end end
function methods:FireClient(p, ...) M.lastClientFx = { ... }; local s = self._signals.OnClientEvent; if s then s:Fire(...) end end

IMT.__index = function(o, k)
	local m = methods[k]
	if m then return m end
	local svc = rawget(o, "_svc")
	if svc and svc[k] ~= nil then return svc[k] end
	local v = o._props[k]
	if v ~= nil then return v end
	if k == "ChildAdded" or k == "ChildRemoved" or k:match("^On") or k:match("Click") or k:match("Button1") or k == "Heartbeat" or k == "Changed" or k == "Event" or k:match("Added$") or k:match("Removing$") or k:match("^Input") or k:match("Finished$") then
		o._signals[k] = o._signals[k] or Signal()
		return o._signals[k]
	end
	local child = o:FindFirstChild(k)
	if child then return child end
	-- Standardwerte für Eigenschaften, die gelesen werden
	if k == "Size" then return V3(1, 1, 1) end
	if k == "Transparency" then return 0 end
	if k == "Parent" then return nil end
	return nil
end
IMT.__newindex = function(o, k, v)
	if k == "Parent" then o:_setParent(v); return end
	o._props[k] = v
end
IMT.__tostring = function(o) return o._class .. ":" .. tostring(o._props.Name) end
Instance.new = function(class) return newInstance(class) end

-- Dienste & Welt ------------------------------------------------------
local game = newInstance("DataModel", "Game")
local services = {}
local function service(name, extra)
	local s = newInstance(name, name)
	rawset(s, "_svc", extra or {})
	s.Parent = game
	services[name] = s
	return s
end
local workspace = service("Workspace", {
	GetServerTimeNow = function() return clock end,
	BulkMoveTo = function(self, parts, cfs)
		assert(#parts == #cfs, "BulkMoveTo: Listen ungleich lang")
		for i, p in ipairs(parts) do
			assert(getmetatable(cfs[i]) == CFmt, "BulkMoveTo: kein CFrame")
			p._props.CFrame = cfs[i]
		end
		M.moved = (M.moved or 0) + #parts
	end,
})
local camera = newInstance("Camera", "Camera")
rawset(camera, "_svc", {
	ScreenPointToRay = function(self, x, y) return { Origin = V3(30, 8.5, 46), Direction = V3((x - 640) / 640, -(y - 360) / 720, -1) } end,
})
camera._props.ViewportSize = V2(1280, 720)
camera.Parent = workspace
workspace._props.CurrentCamera = camera
newInstance("Terrain", "Terrain").Parent = workspace

M.heartbeat = Signal()
M.render = {}
service("RunService", {
	Heartbeat = M.heartbeat,
	BindToRenderStep = function(self, name, prio, fn) M.render[name] = fn end,
	IsStudio = function() return true end,
})
M.ownedPasses = {}
M.prompts = 0
service("MarketplaceService", {
	UserOwnsGamePassAsync = function(self, uid, id) return M.ownedPasses[id] == true end,
	PromptProductPurchase = function() M.prompts = M.prompts + 1 end,
	PromptGamePassPurchase = function() M.prompts = M.prompts + 1 end,
})
local players = service("Players", { GetNameFromUserIdAsync = function(self, id) return "Spieler" .. id end, GetPlayerByUserId = function(self, id) for _, c in ipairs(self._children) do if c._props.UserId == id then return c end end; return nil end, GetPlayers = function(self) local t = {}; for _, c in ipairs(self._children) do if c._class == "Player" then table.insert(t, c) end end; return t end })
service("ReplicatedStorage")
service("ServerScriptService")
service("Lighting")
service("SoundService")
service("Debris", { AddItem = function() end })
service("TweenService", { Create = function() return { Play = function() end } end })
service("GuiService", { GetGuiInset = function() return V2(0, 36), V2() end })
M.uis = service("UserInputService", { GetMouseLocation = function() return V2(640, 400) end })
M.teleports = {}
local memMap = {}
service("MemoryStoreService", { GetSortedMap = function() return {
	GetAsync = function(self, k) return memMap[k] end,
	SetAsync = function(self, k, v) memMap[k] = v; return true end,
} end })
service("TeleportService", {
	ReserveServer = function() return "ACCESS-" .. tostring(#M.teleports + 1) end,
	TeleportAsync = function(self, placeId, list, opts) table.insert(M.teleports, { players = list, access = opts.ReservedServerAccessCode }) end,
})
service("SocialService", { CanSendGameInviteAsync = function() return true end, PromptGameInvite = function() M.invites = (M.invites or 0) + 1 end })
service("DataStoreService", { GetOrderedDataStore = function() return {
	GetSortedAsync = function() error("Studio hat keinen API-Zugriff (Test)") end,
	SetAsync = function() error("Studio hat keinen API-Zugriff (Test)") end,
} end, GetDataStore = function() return {
	GetAsync = function() error("Studio hat keinen API-Zugriff (Test)") end,
	SetAsync = function() error("Studio hat keinen API-Zugriff (Test)") end,
} end })
M.json = {}
service("HttpService", {
	JSONEncode = function(self, t) return M.json.encode(t) end,
	JSONDecode = function(self, s) return M.json.decode(s) end,
})
game._props.PrivateServerId = ""
game._props.PrivateServerOwnerId = 0
game._props.PlaceId = 117942910559953
rawset(game, "_svc", {
	GetService = function(self, n) return services[n] or error("Dienst fehlt in Attrappe: " .. n) end,
	BindToClose = function() end,
})

-- task-Bibliothek mit einfachem Planer --------------------------------
local timers = {}
local function schedule(at, co, fn, args)
	table.insert(timers, { at = at, co = co, fn = fn, args = args })
end
local function runFn(fn, ...)
	local co = coroutine.create(fn)
	local ok, err = coroutine.resume(co, ...)
	if not ok then table.insert(M.errors, debug.traceback(co, tostring(err))) end
end
local task = {
	spawn = function(fn, ...) runFn(fn, ...) end,
	defer = function(fn, ...) schedule(clock, nil, fn, { ... }) end,
	delay = function(t, fn, ...) schedule(clock + t, nil, fn, { ... }) end,
	wait = function(t)
		local co = coroutine.running()
		schedule(clock + (t or 0), co)
		return coroutine.yield()
	end,
}
function M.advance(dt)
	clock = clock + dt
	local due = {}
	for i = #timers, 1, -1 do
		if timers[i].at <= clock then table.insert(due, table.remove(timers, i)) end
	end
	for _, t in ipairs(due) do
		if t.co then
			local ok, err = coroutine.resume(t.co, dt)
			if not ok then table.insert(M.errors, tostring(err)) end
		else
			runFn(t.fn, table.unpack(t.args))
		end
	end
end
function M.now() return clock end

-- Skripte laden -------------------------------------------------------
local mathx = setmetatable({ atan2 = function(y, x) return math.atan(y, x) end, clamp = function(v, a, b) if v < a then return a elseif v > b then return b end; return v end }, { __index = math })
local cache = {}
M.requireCache = cache
local function envFor(scriptInst)
	local env = {
		game = game, workspace = workspace, script = scriptInst, Instance = Instance, Vector3 = Vector3, Vector2 = Vector2,
		CFrame = CFrame, Color3 = Color3, UDim = UDim, UDim2 = UDim2, Enum = Enum, Random = Random, NumberSequence = NumberSequence,
		NumberSequenceKeypoint = NumberSequenceKeypoint, ColorSequence = ColorSequence, NumberRange = NumberRange, TweenInfo = TweenInfo,
		task = task, math = mathx, string = string, table = table, pairs = pairs, ipairs = ipairs, type = type, tostring = tostring,
		tonumber = tonumber, print = function() end, warn = function() end, pcall = pcall, setmetatable = setmetatable, os = os,
		select = select, error = error, next = next, unpack = table.unpack, getmetatable = getmetatable,
		os = { clock = function() return clock end, time = os.time },
	}
	env.require = function(mod)
		if cache[mod] ~= nil then return cache[mod] end
		local src = mod._props.Source
		assert(src, "require: keine Quelle in " .. tostring(mod))
		local fn = assert(load(src, "=" .. mod._props.Name, "t", envFor(mod)))
		local res = fn()
		cache[mod] = res
		return res
	end
	return env
end
function M.run(scriptInst)
	local fn, err = load(scriptInst._props.Source, "=" .. scriptInst._props.Name, "t", envFor(scriptInst))
	if not fn then error(err) end
	runFn(fn)
end

function M.addPlayer(id)
	local p = newInstance("Player", "Tester" .. id)
	p._props.UserId = id
	p._props.LocaleId = M.locale or "de-de"
	newInstance("PlayerGui", "PlayerGui").Parent = p
	p.Parent = players
	if not M.localPlayer then
		players._props.LocalPlayer = p
		M.localPlayer = p
	end
	if players._signals.PlayerAdded then players._signals.PlayerAdded:Fire(p) end
	return p
end

M.game, M.workspace, M.Instance, M.players = game, workspace, Instance, players
M.Vector2 = Vector2
M.Enum = Enum
M.CFrame = CFrame
return M
