-- Spielerdaten: Münzen, Helme, Waffenstufen. Speichert per DataStore.
-- Läuft auch in Roblox Studio ohne API-Zugriff (dann nur für die aktuelle Sitzung).
local DataStoreService = game:GetService("DataStoreService")
local HttpService = game:GetService("HttpService")
local RunService = game:GetService("RunService")

local Config = require(game:GetService("ReplicatedStorage"):WaitForChild("PK"):WaitForChild("Config"))

local Data = {}
local cache = {}
local store = nil
local okStore = pcall(function()
	store = DataStoreService:GetDataStore("PigmentKrieg_v1")
end)
if not okStore then store = nil end

local function merge(d)
	local base = Config.newData()
	if type(d) ~= "table" then return base end
	for k, v in pairs(base) do
		if d[k] == nil then d[k] = v end
	end
	for k, v in pairs(base.wlv) do if d.wlv[k] == nil then d.wlv[k] = v end end
	for k, v in pairs(base.ab) do if d.ab[k] == nil then d.ab[k] = v end end
	d.owned.none = true
	return d
end

function Data.publish(player)
	local d = cache[player]
	if d then player:SetAttribute("Data", HttpService:JSONEncode(d)) end
end

function Data.load(player)
	local d = nil
	if store then
		local ok, res = pcall(function() return store:GetAsync("p_" .. player.UserId) end)
		if ok then d = res; Data.storeWorks = true
		else
			Data.storeWorks = false warn("[PigmentKrieg] DataStore nicht erreichbar (in Studio: Game Settings > Security > API Services). Fortschritt wird nicht gespeichert.") end
	end
	cache[player] = merge(d)
	Data.publish(player)
	return cache[player]
end

function Data.get(player) return cache[player] end

function Data.save(player)
	local d = cache[player]
	if not d or not store then return end
	pcall(function() store:SetAsync("p_" .. player.UserId, d) end)
end

function Data.release(player)
	Data.save(player)
	cache[player] = nil
end

function Data.saveAll()
	for p in pairs(cache) do Data.save(p) end
end

-- Käufe ------------------------------------------------------------
local function helmetById(id)
	for _, h in ipairs(Config.HELMETS) do if h.id == id then return h end end
	return nil
end

function Data.buyHelmet(player, id)
	local d, h = cache[player], helmetById(id)
	if not d or not h then return false end
	if h.pass and not player:GetAttribute("Pass_" .. h.pass) then return false end
	if not d.owned[id] then
		if d.coins < h.price then return false end
		d.coins = d.coins - h.price
		d.owned[id] = true
	end
	d.helmet = id
	Data.publish(player)
	return true
end

function Data.upgradeWeapon(player, t)
	local d = cache[player]
	if not d or not Config.UNITS[t] then return false end
	local lv = d.wlv[t]
	if lv >= 5 then return false end
	local cost = Config.UPGRADE_COST[lv]
	if d.coins < cost then return false end
	d.coins = d.coins - cost
	d.wlv[t] = lv + 1
	Data.publish(player)
	return true
end

function Data.upgradeAbility(player, k)
	local d = cache[player]
	if not d or not Config.ABILITIES[k] then return false end
	local lv = d.ab[k]
	if lv >= 3 then return false end
	local cost = Config.ABILITY_COST[lv]
	if d.coins < cost then return false end
	d.coins = d.coins - cost
	d.ab[k] = lv + 1
	Data.publish(player)
	return true
end

function Data.upgradeExtra(player, id)
	local d = cache[player]
	if not d then return false end
	for _, x in ipairs(Config.EXTRAS) do
		if x.id == id then
			local lv = d[id]
			if lv >= x.max then return false end
			local cost = Config.extraCost(x, lv)
			if d.coins < cost then return false end
			d.coins = d.coins - cost
			d[id] = lv + 1
			Data.publish(player)
			return true
		end
	end
	return false
end

function Data.reward(player, coins, won, kills)
	local d = cache[player]
	if not d then return end
	d.coins = d.coins + coins
	d.games = d.games + 1
	d.kills = d.kills + kills
	if won then d.wins = d.wins + 1 end
	Data.publish(player)
	task.spawn(Data.save, player)
end

-- Robux-Kauf gutschreiben (einmal pro Kauf-ID). Gibt true zurück, wenn gespeichert.
function Data.grantProduct(player, key, purchaseId)
	local d = cache[player]
	local prod = Config.PRODUCTS[key]
	if not d or not prod then return false end
	d.receipts = d.receipts or {}
	if d.receipts[purchaseId] then return true end
	if prod.coins then d.coins = d.coins + prod.coins end
	if prod.helmet then d.owned[prod.helmet] = true; d.helmet = prod.helmet end
	d.receipts[purchaseId] = true
	Data.publish(player)
	-- In Studio ohne Speicherzugriff gilt der Kauf nur für diese Sitzung
	if store and Data.storeWorks ~= false then
		local ok = pcall(function() store:SetAsync("p_" .. player.UserId, d) end)
		return ok
	end
	return true
end

function Data.setBestWave(player, w)
	local d = cache[player]
	if d and w > (d.bestWave or 0) then d.bestWave = w; Data.publish(player) end
end

-- Autosave
task.spawn(function()
	while true do
		task.wait(90)
		Data.saveAll()
	end
end)
if not RunService:IsStudio() then
	game:BindToClose(function() Data.saveAll() end)
end

return Data
