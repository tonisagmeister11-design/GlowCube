-- Szenario: kompletter Spielablauf in der Roblox-Attrappe. Gibt Zeilen über out() aus.
return function(M, S, out, decode)
	local I = M.Instance
	local game = M.game
	local function mk(cls, name, parent, source)
		local o = I.new(cls); o.Name = name
		if source then o.Source = source end
		o.Parent = parent
		return o
	end
	local function check(stage)
		if #M.errors > 0 then
			out("FEHLER bei: " .. stage)
			for i = 1, math.min(8, #M.errors) do out("   " .. M.errors[i]) end
			error("abgebrochen")
		end
		out("ok: " .. stage)
	end
	local RS = game:GetService("ReplicatedStorage")
	local SSS = game:GetService("ServerScriptService")
	local pk = mk("Folder", "PK", RS)
	mk("ModuleScript", "Config", pk, S["shared/Config.lua"])
	mk("ModuleScript", "Pose", pk, S["shared/Pose.lua"])
	mk("ModuleScript", "Lang", pk, S["shared/Lang.lua"])
	local server = mk("Script", "PigmentKriegServer", SSS, S["server/Main.server.lua"])
	for _, n in ipairs({ "BattleCore", "Data", "Arena", "Rooms" }) do mk("ModuleScript", n, server, S["server/" .. n .. ".lua"]) end
	local client = I.new("LocalScript"); client.Name = "PigmentKriegClient"; client.Source = S["client/Main.client.lua"]
	for _, n in ipairs({ "Render", "UI" }) do mk("ModuleScript", n, client, S["client/" .. n .. ".lua"]) end

	M.run(server); check("Server startet")
	local p = M.addPlayer(101); check("Spieler tritt bei, Daten geladen")
	M.run(client); check("Client startet (Oberfläche, Renderer)")
	local state = pk.State
	local action = pk.Remotes.Action
	local dt = 1 / 30
	local function tick(sec)
		for _ = 1, math.floor(sec / dt + 0.5) do
			M.heartbeat:Fire(dt)
			M.render.PigmentKrieg(dt)
			M.advance(dt)
		end
	end
	local uis = M.uis
	local function key(kc)
		local inp = { UserInputType = M.Enum.UserInputType.Keyboard, KeyCode = M.Enum.KeyCode[kc] }
		uis.InputBegan:Fire(inp, false); uis.InputEnded:Fire(inp, false)
	end
	local function click(x, y)
		local inp = { UserInputType = M.Enum.UserInputType.MouseButton1, Position = M.Vector2.new(x, y) }
		uis.InputBegan:Fire(inp, false); uis.InputEnded:Fire(inp, false)
	end
	local function phase() return state:GetAttribute("Phase") end

	tick(2); check("Lobby läuft (Phase " .. tostring(phase()) .. ")")
	local gui = p.PlayerGui.PigmentKrieg
	local nb = 0
	for _, d in ipairs(gui:GetDescendants()) do if d._class == "TextButton" then nb = nb + 1 end end
	out("   Knöpfe in der Oberfläche: " .. nb)
	-- echte Menü-Knöpfe drücken: Arena 2 abstimmen, dann Team Schwarz
	local function pressWithText(txt)
		for _, d in ipairs(gui:GetDescendants()) do
			if d._class == "TextButton" and d.Text == txt then d.MouseButton1Click:Fire(); return true end
			if d._class == "TextButton" then
				for _, c in ipairs(d:GetDescendants()) do
					if c._class == "TextLabel" and c.Text == txt then
						d.MouseButton1Click:Fire(); return true
					end
				end
			end
		end
		return false
	end
	assert(pressWithText("Vulkankrater"), "Arena-Knopf nicht gefunden")
	tick(0.1)
	assert(pressWithText("SCHWARZ"), "Team-Knopf nicht gefunden")
	tick(0.3); check("Arena gewählt und Team Schwarz beigetreten")
	assert(phase() == "lobby" and (state:GetAttribute("SearchEnd") or 0) > 0, "Gegnersuche startet nicht")
	local searchPanel
	for _, d in ipairs(gui:GetDescendants()) do if d._class == "TextLabel" and tostring(d.Text):find("SUCHE GEGNER") then searchPanel = d end end
	assert(searchPanel, "Suchanzeige fehlt")
	check("Gegnersuche läuft (15 s, dann Computer)")
	tick(15.5)
	out("   Phase " .. tostring(phase()) .. ", Arena " .. tostring(state:GetAttribute("Arena")))
	tick(4.5); check("Countdown")
	assert(phase() == "battle", "kein Kampf gestartet")
	-- eigener Held: finden, nach rechts laufen, zuschlagen
	local function myHeroCfg()
		for _, c in ipairs(state.Units:GetChildren()) do
			if c:GetAttribute("Hero") and c:GetAttribute("Owner") == 101 and (c:GetAttribute("HP") or 0) > 0 then return c end
		end
	end
	tick(1)
	local hc = myHeroCfg()
	assert(hc, "Kein eigener Held im Kampf")
	local x0 = hc:GetAttribute("X")
	local inp = { UserInputType = M.Enum.UserInputType.Keyboard, KeyCode = M.Enum.KeyCode.D }
	uis.InputBegan:Fire(inp, false); tick(1.5); uis.InputEnded:Fire(inp, false); tick(0.3)
	hc = myHeroCfg()
	if hc then
		local x1 = hc:GetAttribute("X")
		out(string.format("   Held lief von %.0f nach %.0f", x0, x1))
		assert(x1 > x0 + 80, "Held läuft nicht")
		local sp = { UserInputType = M.Enum.UserInputType.Keyboard, KeyCode = M.Enum.KeyCode.Space }
		uis.InputBegan:Fire(sp, false); tick(2); uis.InputEnded:Fire(sp, false)
	end
	check("Eigener Held: erscheint, läuft mit D, schlägt mit Leertaste")
	local keys = { "One", "Two", "Three", "Four", "Five", "Six", "Seven" }
	for rnd = 1, 40 do
		for _, k in ipairs(keys) do key(k); tick(0.12) end
		if rnd % 6 == 1 then key("R"); tick(0.05); click(900, 500) end
		if rnd % 9 == 1 then key("F") end
		if rnd % 11 == 1 then key("H") end
		if rnd == 15 then key("Q") end
		if rnd == 18 then key("E") end
		tick(2)
		if phase() ~= "battle" then break end
	end
	check("Kampf: alle 7 Krieger, Meteor, Kriegsschrei, Heilwelle, Befehle")

	out(string.format("   Gold %s, Burgen %s / %s, Einheiten %d, bewegte Teile %d",
		tostring(state:GetAttribute("Gold1")), tostring(state:GetAttribute("BaseHP1")), tostring(state:GetAttribute("BaseHP2")), #state.Units:GetChildren(), M.moved or 0))
	for _ = 1, 500 do
		if phase() == "end" then break end
		key("One"); key("Five"); key("Seven"); key("Two"); key("Six"); tick(1)
	end
	check("Runde zu Ende gespielt")
	out("   Phase " .. tostring(phase()) .. ", Gewinner Seite " .. tostring(state:GetAttribute("Winner")))
	local d = decode(p:GetAttribute("Data"))
	out("   Münzen " .. d.coins .. ", Spiele " .. d.games .. ", Kills " .. d.kills)
	action:FireServer("helmet", "face"); tick(0.1)
	action:FireServer("upgrade", "sword"); tick(0.1)
	action:FireServer("abilityUp", "meteor"); tick(0.1)
	action:FireServer("extra", "mine"); tick(0.1)
	d = decode(p:GetAttribute("Data"))
	out("   Nach Einkauf: Münzen " .. d.coins .. ", Helm " .. d.helmet .. ", Klinge Stufe " .. d.wlv.sword .. ", Meteor Stufe " .. d.ab.meteor .. ", Mine " .. d.mine)
	tick(1); check("Shop und Schmiede, Oberfläche aktualisiert")
	-- Nach dem Kampf: es darf NICHT automatisch weitergehen
	tick(12)
	assert(phase() == "end", "Runde startete ohne Nochmal, Phase " .. tostring(phase()))
	check("Endbildschirm wartet auf Entscheidung")
	-- Modus Überleben wählen, Nochmal drücken (über echte Knöpfe)
	assert(pressWithText("Überleben"), "Modus-Knopf fehlt")
	tick(0.2)
	local again
	for _, d in ipairs(gui:GetDescendants()) do if d._class == "TextButton" and tostring(d.Text):find("NOCHMAL") then again = d end end
	assert(again, "Nochmal-Knopf fehlt")
	again.MouseButton1Click:Fire()
	tick(0.5)
	assert(phase() == "countdown", "Nochmal startet keine Runde: " .. tostring(phase()))
	assert(state:GetAttribute("Mode") == "waves", "Modus nicht übernommen")
	tick(4.5)
	for _ = 1, 400 do
		if phase() == "end" then break end
		key("One"); key("Four"); key("Three"); key("Seven"); tick(1)
	end
	check("Überleben gespielt bis Welle " .. tostring(state:GetAttribute("Wave")))
	d = decode(p:GetAttribute("Data"))
	out("   Beste Welle " .. tostring(d.bestWave) .. ", Münzen " .. d.coins)
	-- Zum Menü
	local toMenu
	for _, x in ipairs(gui:GetDescendants()) do if x._class == "TextButton" and tostring(x.Text):find("ZUM MENÜ") then toMenu = x end end
	toMenu.MouseButton1Click:Fire()
	tick(0.5)
	assert(p:GetAttribute("Side") == 0 and phase() == "lobby", "Zum-Menü-Knopf geht nicht")
	check("Zum Menü zurück, Lobby")
	-- Robux: Produkt und Pass simulieren
	local Cfg
	for _, v in pairs(M.requireCache) do if type(v) == "table" and v.PRODUCTS then Cfg = v end end
	Cfg.PRODUCTS.coins1.id = 111
	Cfg.PRODUCTS.dragon.id = 112
	Cfg.PASSES.vip.id = 222
	local MS = game:GetService("MarketplaceService")
	local before = decode(p:GetAttribute("Data")).coins
	assert(MS.ProcessReceipt({ PlayerId = 101, ProductId = 111, PurchaseId = "kauf1" }) == M.Enum.ProductPurchaseDecision.PurchaseGranted, "Kauf nicht bestätigt")
	MS.ProcessReceipt({ PlayerId = 101, ProductId = 111, PurchaseId = "kauf1" })
	MS.ProcessReceipt({ PlayerId = 101, ProductId = 112, PurchaseId = "kauf2" })
	d = decode(p:GetAttribute("Data"))
	assert(d.coins == before + 1000, "Münzpaket falsch gutgeschrieben: " .. d.coins .. " statt " .. (before + 1000))
	assert(d.owned.dragon and d.helmet == "dragon", "Drachenhelm nicht freigeschaltet")
	M.ownedPasses[222] = true
	MS.PromptGamePassPurchaseFinished:Fire(p, 222, true)
	assert(p:GetAttribute("Pass_vip") == true, "VIP nicht erkannt")
	d = decode(p:GetAttribute("Data"))
	assert(d.owned.vipcrown, "VIP-Krone fehlt")
	action:FireServer("helmet", "vipcrown"); tick(0.1)
	check("Robux: Münzpaket (doppelt geschützt), Drachenhelm, VIP-Pass, VIP-Krone")
	-- Robux-Shop-Knöpfe im Menü lösen Kauf-Dialog aus
	local before2 = M.prompts
	for _, x in ipairs(gui:GetDescendants()) do if x._class == "TextButton" and tostring(x.Text):find("^R%$") then x.MouseButton1Click:Fire() end end
	out("   Kauf-Dialoge geöffnet: " .. (M.prompts - before2))
	-- Neue Runde: Boss-Ansturm mit VIP-Krone
	assert(pressWithText("Boss-Ansturm"))
	tick(0.2)
	assert(pressWithText("TRAINING"), "Training-Knopf fehlt")
	tick(5)
	assert(phase() == "battle", "Training startet nicht sofort: " .. tostring(phase()))
	for _ = 1, 60 do key("One"); key("Seven"); key("Six"); tick(1) end
	action:FireServer("modeVote", "steal"); tick(0.2)
	check("Boss-Ansturm als Team Weiß mit VIP-Krone (Phase " .. tostring(phase()) .. ", Boss " .. tostring(state:GetAttribute("BossId")) .. ")")
	out("   Server-Aktionen " .. tostring(M.serverEvents) .. ", Effekt-Pakete " .. tostring(M.clientEvents))
	-- Zweiter Spieler: SPIELEN -> kommt ins andere Team, Computer wird ersetzt
	local p2 = M.addPlayer(202)
	tick(0.5)
	action.OnServerEvent:Fire(p2, "play")
	tick(1)
	assert(p2:GetAttribute("Side") == 2, "Spieler 2 nicht ins freie Team: " .. tostring(p2:GetAttribute("Side")))
	assert(state:GetAttribute("AI2") == false, "Computer nicht durch Spieler ersetzt")
	for _ = 1, 20 do key("One"); action.OnServerEvent:Fire(p2, "spawn", "giant"); tick(1) end
	check("PvP: zweiter Spieler ersetzt den Computer")
	-- Hügel-König PvP
	for _ = 1, 400 do if phase() == "end" then break end; key("Seven"); key("Two"); action.OnServerEvent:Fire(p2, "spawn", "sword"); tick(1) end
	action.OnServerEvent:Fire(p2, "modeVote", "hill"); tick(0.1)
	action:FireServer("modeVote", "hill"); tick(0.1)
	action:FireServer("again"); tick(0.1)
	action.OnServerEvent:Fire(p2, "again"); tick(0.5)
	assert(state:GetAttribute("Mode") == "hill", "Hügel-Modus nicht gewählt")
	tick(4.5)
	for _ = 1, 300 do
		if phase() == "end" then break end
		key("One"); key("Four"); action.OnServerEvent:Fire(p2, "spawn", "sword"); tick(1)
	end
	check("Hügel-König PvP: Punkte " .. tostring(state:GetAttribute("Hill1")) .. " / " .. tostring(state:GetAttribute("Hill2")) .. ", Gewinner " .. tostring(state:GetAttribute("Winner")))
	local ls = p.leaderstats
	out("   Rangliste von Spieler 1: Wins " .. ls.Wins.Value .. ", Kills " .. ls.Kills.Value .. ", Einladungen " .. tostring(M.invites))
	-- Private Räume: erstellen und mit Code beitreten
	action:FireServer("roomCreate"); tick(0.2)
	assert(#M.teleports == 1, "Raum erstellen teleportiert nicht")
	local fx = M.lastClientFx[1][1]
	assert(fx[1] == "room" and fx[2].ok and #fx[2].code == 5, "Kein Raum-Code zurück")
	local code = fx[2].code
	action.OnServerEvent:Fire(p2, "roomJoin", "zzzzz"); tick(0.2)
	assert(#M.teleports == 1, "Falscher Code darf nicht teleportieren")
	tick(3.5)
	action.OnServerEvent:Fire(p2, "roomJoin", string.lower(code)); tick(0.2)
	assert(#M.teleports == 2 and M.teleports[2].access == M.teleports[1].access, "Freund landet nicht im selben Raum")
	check("Private Räume: Code " .. code .. " erstellt, Freund tritt bei, falscher Code abgelehnt")
	-- Spieler verlässt das Spiel
	p.Parent = nil
	M.players._signals.PlayerRemoving = M.players._signals.PlayerRemoving
	tick(2); check("Spieler verlässt Spiel")
	return true
end
