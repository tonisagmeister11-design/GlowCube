-- Pigment Krieg: gemeinsame Spieldaten (Server + Client)
-- Einheiten im Spiel rechnen in "Pixeln" wie die Web-Version; 1 Pixel = 0.1 Stud.
local Config = {}

Config.K = 0.1            -- Studs pro Spiel-Einheit
Config.WORLD_W = 2600     -- Länge des Schlachtfelds (Spiel-Einheiten)
Config.MAX_UNITS = 45     -- pro Seite

-- Seite 1 = Schwarz (links), Seite 2 = Weiß (rechts)
Config.SIDES = {
	{ name = "Schwarz", color = { 14, 14, 17 }, ink = { 0, 0, 0 }, dir = 1 },
	{ name = "Weiß", color = { 246, 246, 240 }, ink = { 255, 255, 255 }, dir = -1 },
}

Config.UNIT_ORDER = { "sword", "spear", "archer", "shield", "ninja", "mage", "giant" }
Config.UNITS = {
	sword  = { name = "Klinge", hp = 120, dmg = 18, range = 34, cd = 0.85, atk = 0.62, speed = 80, cost = 30, spawnCd = 1.2, scale = 1.0, desc = "Schneller Schwertkämpfer" },
	spear  = { name = "Speer", hp = 155, dmg = 25, range = 62, cd = 1.10, atk = 0.70, speed = 66, cost = 50, spawnCd = 2.0, scale = 1.05, desc = "Lange Reichweite" },
	archer = { name = "Bogen", hp = 80, dmg = 16, range = 370, cd = 1.55, atk = 0.95, speed = 70, cost = 55, spawnCd = 2.2, scale = 1.0, desc = "Schießt Pfeile", ranged = true },
	shield = { name = "Schild", hp = 330, dmg = 11, range = 30, cd = 1.15, atk = 0.55, speed = 52, cost = 75, spawnCd = 3.0, scale = 1.08, desc = "Blockt von vorne" },
	ninja  = { name = "Ninja", hp = 95, dmg = 12, range = 28, cd = 0.30, atk = 0.26, speed = 135, cost = 70, spawnCd = 2.6, scale = 0.92, desc = "Blitzschnelle Dolche" },
	mage   = { name = "Magier", hp = 90, dmg = 34, range = 300, cd = 2.6, atk = 1.10, speed = 62, cost = 95, spawnCd = 4.0, scale = 1.0, desc = "Explodierende Kugeln", ranged = true, aoe = 62 },
	giant  = { name = "Titan", hp = 720, dmg = 62, range = 60, cd = 2.10, atk = 1.15, speed = 40, cost = 160, spawnCd = 6.0, scale = 1.75, desc = "Hammer mit Flächenschaden", aoe = 85 },
}
Config.HIT_AT = { sword = 0.5, spear = 0.55, archer = 0.62, ninja = 0.5, giant = 0.58, shield = 0.52, mage = 0.64 }

Config.TIERS = {
	{ name = "Holz", color = { 154, 106, 58 }, neon = false },
	{ name = "Eisen", color = { 195, 204, 214 }, neon = false },
	{ name = "Gold", color = { 255, 204, 61 }, neon = false },
	{ name = "Diamant", color = { 127, 244, 255 }, neon = true },
	{ name = "Plasma", color = { 255, 77, 245 }, neon = true },
}
Config.UPGRADE_COST = { 120, 260, 480, 800 } -- Holz->Eisen, ->Gold, ->Diamant, ->Plasma

Config.HELMETS = {
	{ id = "none", name = "Ohne Helm", price = 0 },
	{ id = "knight", name = "Ritterhelm", price = 120 },
	{ id = "ninja", name = "Ninja-Band", price = 150 },
	{ id = "viking", name = "Wikingerhelm", price = 180 },
	{ id = "sparta", name = "Spartaner", price = 220 },
	{ id = "face", name = "Gesichts-Helm", price = 250 },
	{ id = "crown", name = "Königskrone", price = 400 },
	-- Premium: mit vielen Münzen ODER Robux
	{ id = "halo", name = "Heiligenschein", price = 3000, robux = "halo", premium = true },
	{ id = "visor", name = "Neon-Visier", price = 4000, robux = "visor", premium = true },
	{ id = "flame", name = "Flammenkopf", price = 6000, robux = "flame", premium = true },
	{ id = "dragon", name = "Drachenhelm", price = 8000, robux = "dragon", premium = true },
	-- nur mit dem VIP-Pass
	{ id = "vipcrown", name = "VIP-Krone", price = 0, pass = "vip", premium = true },
}

-- Spielmodi (Abstimmung im Menü)
Config.MODE_ORDER = { "classic", "waves", "boss", "turbo" }
Config.MODES = {
	classic = { name = "Klassisch", desc = "Zerstöre die feindliche Burg" },
	waves = { name = "Überleben", desc = "Halte gegen endlose Wellen durch (alle im Team Schwarz)" },
	boss = { name = "Boss-Ansturm", desc = "Alle 45 Sekunden kommt ein Boss-Titan" },
	turbo = { name = "Turbo", desc = "Doppelt so viel Gold, halbe Wartezeiten" },
}

-- Robux-Shop. Die IDs bekommst du auf create.roblox.com (siehe README, Abschnitt Robux-Shop).
-- id = 0 bedeutet: noch nicht eingerichtet -> der Knopf zeigt "Bald".
Config.PASSES = {
	vip = { id = 0, name = "VIP", robux = 199, desc = "Doppelte Münzen nach jedem Kampf + VIP-Krone" },
	rainbow = { id = 0, name = "Regenbogen-Waffen", robux = 149, desc = "Alle deine Waffen leuchten in Regenbogenfarben" },
}
Config.PRODUCTS = {
	coins1 = { id = 0, name = "1.000 Münzen", robux = 25, coins = 1000 },
	coins2 = { id = 0, name = "6.000 Münzen", robux = 99, coins = 6000 },
	coins3 = { id = 0, name = "20.000 Münzen", robux = 249, coins = 20000 },
	halo = { id = 0, name = "Heiligenschein", robux = 49, helmet = "halo" },
	visor = { id = 0, name = "Neon-Visier", robux = 75, helmet = "visor" },
	flame = { id = 0, name = "Flammenkopf", robux = 99, helmet = "flame" },
	dragon = { id = 0, name = "Drachenhelm", robux = 149, helmet = "dragon" },
}
Config.PRODUCT_ORDER = { "coins1", "coins2", "coins3", "halo", "visor", "flame", "dragon" }
-- Gesichts-Helm: Lade assets/face.png in Roblox Studio als Bild hoch und trage die ID hier ein,
-- z. B. "1234567890" oder "rbxassetid://1234567890". Leer = Gesichts-Helm zeigt ein gezeichnetes Gesicht.
Config.FACE_IMAGE = ""

Config.ABILITY_ORDER = { "meteor", "rage", "heal" }
Config.ABILITIES = {
	meteor = { name = "Meteor", icon = "☄", cd = 32, key = "R", desc = "Meteor auf eine Stelle" },
	rage = { name = "Kriegsschrei", icon = "!", cd = 42, key = "F", desc = "8 Sek. schneller und stärker" },
	heal = { name = "Heilwelle", icon = "+", cd = 48, key = "H", desc = "Heilt Armee und Burg" },
}
Config.ABILITY_COST = { 220, 450 }

Config.EXTRAS = {
	{ id = "armor", name = "Rüstung", desc = "+15 % Leben für alle Krieger", max = 5, base = 150 },
	{ id = "mine", name = "Pigment-Mine", desc = "+3 Gold pro Sekunde", max = 5, base = 140 },
	{ id = "tower", name = "Burg-Turm", desc = "Mehr Burgleben, Turm schießt schneller", max = 5, base = 160 },
}

Config.ARENAS = {
	{ id = "plains", name = "Graue Steppe", diff = 1.0, reward = 90, sky = { 120, 132, 150 }, fog = { 199, 184, 162 }, ground = { 110, 106, 98 }, far = { 106, 115, 129 }, clock = 17.2 },
	{ id = "volcano", name = "Vulkankrater", diff = 1.3, reward = 130, sky = { 90, 20, 10 }, fog = { 184, 64, 26 }, ground = { 61, 44, 39 }, far = { 74, 26, 18 }, clock = 18.3 },
	{ id = "ice", name = "Eiswüste", diff = 1.55, reward = 170, sky = { 150, 185, 220 }, fog = { 220, 232, 243 }, ground = { 142, 163, 184 }, far = { 142, 167, 193 }, clock = 13 },
	{ id = "neon", name = "Neon-Kolosseum", diff = 1.8, reward = 230, sky = { 20, 10, 45 }, fog = { 69, 32, 107 }, ground = { 41, 35, 61 }, far = { 37, 22, 69 }, clock = 0.5 },
	{ id = "temple", name = "Goldener Tempel", diff = 2.1, reward = 310, sky = { 140, 95, 40 }, fog = { 230, 178, 90 }, ground = { 138, 106, 62 }, far = { 154, 113, 64 }, clock = 17.8 },
}

function Config.newData()
	return {
		coins = 300,
		helmet = "none",
		owned = { none = true },
		wlv = { sword = 1, spear = 1, archer = 1, shield = 1, ninja = 1, mage = 1, giant = 1 },
		ab = { meteor = 1, rage = 1, heal = 1 },
		armor = 0, mine = 0, tower = 0,
		wins = 0, kills = 0, games = 0, bestWave = 0,
		receipts = {},
	}
end

function Config.extraCost(extra, lv)
	return math.floor(extra.base * (1.7 ^ lv) + 0.5)
end

return Config
