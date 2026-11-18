-- Sprachen: Deutsch ist der Quelltext, Englisch für alle anderen Spieler.
-- Lang.t("Deutscher Text") liefert die Übersetzung, wenn der Spieler kein Deutsch eingestellt hat.
local Lang = {}
Lang.german = false

local EN = {
	-- Menü
	["SCHLACHT"] = "BATTLE", ["HELME"] = "HELMETS", ["WAFFEN"] = "WEAPONS", ["SHOP R$"] = "SHOP R$",
	["SPIELEN"] = "PLAY", ["TRAINING"] = "TRAINING",
	["Gegen echte Spieler"] = "Fight real players", ["Gegen den Computer"] = "Against the computer",
	["WÄHLE DEIN TEAM"] = "CHOOSE YOUR TEAM", ["ODER WÄHLE DEIN TEAM"] = "OR PICK YOUR TEAM",
	["SCHWARZ"] = "BLACK", ["WEISS"] = "WHITE", ["Schwarz"] = "Black", ["Weiß"] = "White",
	["ARENA WÄHLEN (Abstimmung für die nächste Runde)"] = "CHOOSE ARENA (vote for next round)",
	["SPIELMODUS (Abstimmung)"] = "GAME MODE (vote)",
	["Tippe auf ein Team, um mitzukämpfen. Ist ein Team leer, spielt der Computer."] = "Tap PLAY to find an opponent. If nobody joins, the computer takes over.",
	["Steuerung PC: 1–7 Krieger · R Meteor · F Kriegsschrei · H Heilwelle · Q/E Verteidigen/Angriff · A/D oder Mausrad: Kamera\nHandy: Knöpfe unten antippen, übers Feld wischen = Kamera"] =
		"PC: 1–7 units · R Meteor · F War Cry · H Heal Wave · Q/E Defend/Attack · A/D or mouse wheel: camera\nMobile: tap the buttons, swipe the field = camera",
	["Computer"] = "Computer", ["COMPUTER"] = "COMPUTER", ["DEIN TEAM"] = "YOUR TEAM", ["Spieler"] = "players",
	["Spieler online"] = "players online", ["FREUNDE EINLADEN"] = "INVITE FRIENDS", ["TOP 10 SIEGE"] = "TOP 10 WINS",
	["Noch keine Einträge"] = "No entries yet", ["Siege"] = "Wins",
	["SUCHE GEGNER …"] = "SEARCHING FOR OPPONENT …", ["Jetzt gegen Computer"] = "Play vs computer now",
	["Kommt niemand, startet der Kampf in %d s gegen den Computer."] = "If nobody joins, the battle starts vs the computer in %d s.",
	["TÄGLICHE BELOHNUNG"] = "DAILY REWARD", ["Tag %d in Folge!"] = "Day %d in a row!", ["ABHOLEN"] = "CLAIM",
	["EIN GEGNER IST BEIGETRETEN!"] = "AN OPPONENT JOINED!",
	["MIT FREUNDEN SPIELEN"] = "PLAY WITH FRIENDS", ["RAUM ERSTELLEN"] = "CREATE ROOM", ["BEITRETEN"] = "JOIN",
	["Code eingeben"] = "Enter code", ["Erstelle einen privaten Raum und gib deinem Freund den Code."] = "Create a private room and give your friend the code.",
	["WARTE AUF DEINEN FREUND"] = "WAITING FOR YOUR FRIEND", ["Raum-Code: %s  ·  Sag ihn deinem Freund!"] = "Room code: %s  ·  Tell your friend!",
	["RAUM %s"] = "ROOM %s", ["Wird verbunden …"] = "Connecting …",
	["Räume sind gerade nicht verfügbar"] = "Rooms are not available right now",
	["In Roblox Studio gehen Räume nicht. Teste sie im veröffentlichten Spiel."] = "Rooms don't work in Roblox Studio. Test them in the published game.",
	["Raum konnte nicht gespeichert werden"] = "Could not save the room", ["Teleport fehlgeschlagen, bitte nochmal versuchen"] = "Teleport failed, please try again",
	["Ungültiger Code"] = "Invalid code", ["Der Code hat 5 Zeichen"] = "The code has 5 characters", ["Diesen Raum gibt es nicht (mehr)"] = "This room doesn't exist (anymore)",
	["Einladen ist gerade nicht möglich"] = "Inviting is not possible right now",
	-- HUD
	["MENÜ"] = "MENU", ["VERTEIDIGEN (Q)"] = "DEFEND (Q)", ["ANGRIFF (E)"] = "ATTACK (E)", ["BOSS-TITAN"] = "BOSS TITAN",
	["Tippe aufs Schlachtfeld für den Meteor"] = "Tap the battlefield to drop the meteor", ["Abbrechen"] = "Cancel",
	["GLEICH GEHT'S LOS"] = "GET READY", ["KAMPF"] = "BATTLE", ["RUNDE VORBEI"] = "ROUND OVER", ["WARTEN"] = "WAITING",
	["GOLD"] = "GOLD", ["KRIEGSSCHREI %.1f s"] = "WAR CRY %.1f s", ["HÜGEL"] = "HILL",
	-- Ansagen
	["KAMPF!"] = "FIGHT!", ["BOSS ERSCHEINT!"] = "BOSS INCOMING!", ["BOSS BESIEGT!"] = "BOSS DEFEATED!",
	["BURG WIRD ANGEGRIFFEN!"] = "CASTLE UNDER ATTACK!", ["BURG ZERSTÖRT!"] = "CASTLE DESTROYED!", ["BURG GEFALLEN!"] = "CASTLE FALLEN!",
	["KRIEGSSCHREI!"] = "WAR CRY!", ["DOPPEL-KILL"] = "DOUBLE KILL", ["TRIPPEL-KILL"] = "TRIPLE KILL", ["MASSAKER!"] = "RAMPAGE!",
	["UNAUFHALTBAR!"] = "UNSTOPPABLE!", ["HÜGEL EROBERT!"] = "HILL CAPTURED!", ["VERLÄNGERUNG!"] = "OVERTIME!", ["WELLE %d"] = "WAVE %d", ["BLOCK"] = "BLOCK",
	-- Ende
	["SIEG!"] = "VICTORY!", ["NIEDERLAGE"] = "DEFEAT", ["NOCHMAL  ▶"] = "PLAY AGAIN  ▶", ["BEREIT ✓"] = "READY ✓",
	["ZUM MENÜ  (Shop · Waffen · Modus)"] = "MENU  (Shop · Weapons · Mode)",
	["So lange habt ihr durchgehalten!"] = "That's how long you survived!",
	["Das %s-Pigment ist zerflossen."] = "The %s pigment has melted away.", ["Das %s-Pigment hat gewonnen."] = "The %s pigment won.",
	["+%d Münzen  ·  %d besiegt"] = "+%d coins  ·  %d defeated",
	["Warte auf die anderen Spieler … (%d s)"] = "Waiting for other players … (%d s)",
	["Ohne Auswahl geht es in %d s zurück ins Menü"] = "Back to menu in %d s",
	-- Shop & Schmiede
	["Ausgerüstet"] = "Equipped", ["Anziehen"] = "Equip", ["%d Münzen"] = "%d coins", ["Mit VIP-Pass"] = "With VIP Pass",
	["MAX"] = "MAX", ["Verbessern · %d"] = "Upgrade · %d", ["Stufe %d"] = "Level %d", ["Gekauft ✓"] = "Owned ✓",
	["Gekauft: %s  Danke!"] = "Purchased: %s  Thank you!", ["Du hast %s schon!"] = "You already own %s!",
	["Dieser Artikel ist noch nicht eingerichtet (Produkt-ID fehlt)"] = "This item is not set up yet",
	["Dieser Pass ist noch nicht eingerichtet (Pass-ID fehlt)"] = "This pass is not set up yet",
	["Sofort aufs Konto, für Waffen und Helme"] = "Instantly added, for weapons and helmets",
	["Premium-Helm für deine ganze Armee"] = "Premium helmet for your whole army", [" · für immer"] = " · forever",
	["Zu wenig Gold für %s"] = "Not enough gold for %s", ["%s lädt noch %d s"] = "%s ready in %d s",
	["Münzen"] = "coins",
	-- Einheiten
	["Klinge"] = "Blade", ["Speer"] = "Spear", ["Bogen"] = "Bow", ["Schild"] = "Shield", ["Ninja"] = "Ninja", ["Magier"] = "Mage", ["Titan"] = "Titan",
	["Schneller Schwertkämpfer"] = "Fast swordsman", ["Lange Reichweite"] = "Long reach", ["Schießt Pfeile"] = "Shoots arrows",
	["Blockt von vorne"] = "Blocks from the front", ["Blitzschnelle Dolche"] = "Lightning-fast daggers",
	["Explodierende Kugeln"] = "Exploding orbs", ["Hammer mit Flächenschaden"] = "Hammer with area damage",
	-- Stufen
	["Holz"] = "Wood", ["Eisen"] = "Iron", ["Gold"] = "Gold", ["Diamant"] = "Diamond", ["Plasma"] = "Plasma",
	-- Helme
	["Ohne Helm"] = "No helmet", ["Ritterhelm"] = "Knight helm", ["Ninja-Band"] = "Ninja band", ["Wikingerhelm"] = "Viking helm",
	["Spartaner"] = "Spartan", ["Gesichts-Helm"] = "Face helm", ["Königskrone"] = "Royal crown", ["Heiligenschein"] = "Halo",
	["Neon-Visier"] = "Neon visor", ["Flammenkopf"] = "Flame head", ["Drachenhelm"] = "Dragon helm", ["VIP-Krone"] = "VIP crown",
	-- Fähigkeiten & Extras
	["Meteor"] = "Meteor", ["Kriegsschrei"] = "War Cry", ["Heilwelle"] = "Heal Wave",
	["Meteor auf eine Stelle"] = "Drop a meteor anywhere", ["8 Sek. schneller und stärker"] = "8 s faster and stronger",
	["Heilt Armee und Burg"] = "Heals army and castle", ["Rüstung"] = "Armor", ["Pigment-Mine"] = "Pigment mine", ["Burg-Turm"] = "Castle tower",
	["+15 % Leben für alle Krieger"] = "+15% health for all units", ["+3 Gold pro Sekunde"] = "+3 gold per second",
	["Mehr Burgleben, Turm schießt schneller"] = "More castle health, faster tower",
	-- Arenen
	["Graue Steppe"] = "Grey Steppe", ["Vulkankrater"] = "Volcano Crater", ["Eiswüste"] = "Ice Desert",
	["Neon-Kolosseum"] = "Neon Colosseum", ["Goldener Tempel"] = "Golden Temple",
	-- Modi
	["Klassisch"] = "Classic", ["Überleben"] = "Survival", ["Boss-Ansturm"] = "Boss Rush", ["Turbo"] = "Turbo",
	["Hügel-König"] = "King of the Hill", ["Sudden Death"] = "Sudden Death",
	["Zerstöre die feindliche Burg"] = "Destroy the enemy castle",
	["Halte gegen endlose Wellen durch (alle im Team Schwarz)"] = "Survive endless waves together",
	["Alle 45 Sekunden kommt ein Boss-Titan"] = "A boss titan every 45 seconds",
	["Doppelt so viel Gold, halbe Wartezeiten"] = "Double gold, half the cooldowns",
	["Halte die Mitte! Wer zuerst 100 Punkte hat, gewinnt"] = "Hold the middle! First to 100 points wins",
	["Schwache Burgen, harte Treffer, schnelle Runden"] = "Weak castles, hard hits, fast rounds",
	-- Robux
	["VIP"] = "VIP", ["Regenbogen-Waffen"] = "Rainbow Weapons",
	["Doppelte Münzen nach jedem Kampf + VIP-Krone"] = "Double coins after every battle + VIP crown",
	["Alle deine Waffen leuchten in Regenbogenfarben"] = "All your weapons glow in rainbow colors",
	["1.000 Münzen"] = "1,000 coins", ["6.000 Münzen"] = "6,000 coins", ["20.000 Münzen"] = "20,000 coins",
}

function Lang.setLocale(localeId)
	Lang.german = type(localeId) == "string" and localeId:sub(1, 2):lower() == "de"
end

function Lang.t(s, ...)
	if type(s) ~= "string" then return s end
	local out = s
	if not Lang.german and EN[s] then out = EN[s] end
	if select("#", ...) > 0 then out = string.format(out, ...) end
	return out
end

-- Ansage-Texte wie "WELLE 5" übersetzen
function Lang.say(text)
	local n = text:match("^WELLE (%d+)$")
	if n then return Lang.t("WELLE %d", tonumber(n)) end
	return Lang.t(text)
end

return Lang
