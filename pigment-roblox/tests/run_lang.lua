-- Prüft, dass ein englischsprachiger Spieler eine englische Oberfläche bekommt.
return function(M, S, out)
	M.locale = "en-us"
	local I, game = M.Instance, M.game
	local function mk(cls, name, parent, source) local o = I.new(cls); o.Name = name; if source then o.Source = source end; o.Parent = parent; return o end
	local pk = mk("Folder", "PK", game:GetService("ReplicatedStorage"))
	for _, n in ipairs({ "Config", "Pose", "Lang" }) do mk("ModuleScript", n, pk, S["shared/" .. n .. ".lua"]) end
	local server = mk("Script", "S", game:GetService("ServerScriptService"), S["server/Main.server.lua"])
	for _, n in ipairs({ "BattleCore", "Data", "Arena", "Rooms" }) do mk("ModuleScript", n, server, S["server/" .. n .. ".lua"]) end
	local client = I.new("LocalScript"); client.Name = "C"; client.Source = S["client/Main.client.lua"]
	for _, n in ipairs({ "Render", "UI" }) do mk("ModuleScript", n, client, S["client/" .. n .. ".lua"]) end
	M.run(server); local p = M.addPlayer(7); M.run(client)
	for _ = 1, 30 do M.heartbeat:Fire(1 / 30); M.render.PigmentKrieg(1 / 30); M.advance(1 / 30) end
	local texts, german = {}, {}
	for _, d in ipairs(p.PlayerGui.PigmentKrieg:GetDescendants()) do
		local t = d.Text
		if type(t) == "string" and t ~= "" then
			texts[t] = true
			if t:find("[äöüÄÖÜß]") or t:find("SCHLACHT") or t:find("SPIELEN") or t:find("Münzen") then table.insert(german, t) end
		end
	end
	for _, want in ipairs({ "PLAY", "TRAINING", "BATTLE", "HELMETS", "INVITE FRIENDS", "Volcano Crater", "King of the Hill" }) do
		assert(texts[want], "fehlt auf Englisch: " .. want)
	end
	out("Englisch ok. Noch deutsche Texte: " .. #german)
	for i = 1, math.min(12, #german) do out("   " .. german[i]) end
	assert(#M.errors == 0, M.errors[1])
end
