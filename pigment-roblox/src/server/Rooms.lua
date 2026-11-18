-- Private Räume: Ein Spieler erstellt einen Raum mit Code, Freunde treten mit dem Code bei.
-- Technisch: ein reservierter Server (TeleportService) + Code-Tabelle im MemoryStore (über alle Server hinweg).
local TeleportService = game:GetService("TeleportService")
local MemoryStoreService = game:GetService("MemoryStoreService")

local Rooms = {}
local map = nil
pcall(function() map = MemoryStoreService:GetSortedMap("PK_Rooms_v1") end)

local LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" -- ohne 0/O und 1/I, damit man nichts verwechselt
local function newCode()
	local t = {}
	for i = 1, 5 do
		local k = math.random(1, #LETTERS)
		t[i] = LETTERS:sub(k, k)
	end
	return table.concat(t)
end

-- Ist dieser Server selbst ein privater Raum?
function Rooms.isRoom()
	return game.PrivateServerId ~= "" and game.PrivateServerOwnerId == 0
end

-- Raum-Code aus den Teleport-Daten eines beitretenden Spielers
function Rooms.codeFromJoin(player)
	local ok, data = pcall(function() return player:GetJoinData() end)
	if ok and type(data) == "table" and type(data.TeleportData) == "table" then
		return data.TeleportData.room
	end
	return nil
end

local function teleport(player, access, code)
	local opts = Instance.new("TeleportOptions")
	opts.ReservedServerAccessCode = access
	opts:SetTeleportData({ room = code })
	local ok, err = pcall(function() TeleportService:TeleportAsync(game.PlaceId, { player }, opts) end)
	return ok, err
end

-- gibt (true, code) oder (false, Fehlertext) zurück
function Rooms.create(player)
	if not map then return false, "Räume sind gerade nicht verfügbar" end
	local okR, access = pcall(function() return TeleportService:ReserveServer(game.PlaceId) end)
	if not okR or not access then return false, "In Roblox Studio gehen Räume nicht. Teste sie im veröffentlichten Spiel." end
	local code
	for _ = 1, 5 do
		code = newCode()
		local okG, existing = pcall(function() return map:GetAsync(code) end)
		if okG and existing == nil then break end
	end
	local okS = pcall(function() map:SetAsync(code, access, 3 * 3600) end)
	if not okS then return false, "Raum konnte nicht gespeichert werden" end
	local okT = teleport(player, access, code)
	if not okT then return false, "Teleport fehlgeschlagen, bitte nochmal versuchen" end
	return true, code
end

function Rooms.join(player, code)
	if type(code) ~= "string" then return false, "Ungültiger Code" end
	code = code:upper():gsub("[^A-Z0-9]", "")
	if #code ~= 5 then return false, "Der Code hat 5 Zeichen" end
	if not map then return false, "Räume sind gerade nicht verfügbar" end
	local okG, access = pcall(function() return map:GetAsync(code) end)
	if not okG then return false, "Räume sind gerade nicht verfügbar" end
	if not access then return false, "Diesen Raum gibt es nicht (mehr)" end
	local okT = teleport(player, access, code)
	if not okT then return false, "Teleport fehlgeschlagen, bitte nochmal versuchen" end
	return true, code
end

return Rooms
