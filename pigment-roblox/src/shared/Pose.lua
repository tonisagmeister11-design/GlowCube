-- Prozedurale Stickman-Animationen (gleiche Mathematik wie die Web-Version).
-- Winkel: 0 = gerade nach unten, positiv = nach vorne (Blickrichtung), ±pi = nach oben.
-- Koordinaten: y nach unten (Bildschirm-Logik); der Renderer dreht sie für 3D um.
local Pose = {}
local PI, TAU = math.pi, math.pi * 2

local function clamp(v, a, b)
	if v < a then return a elseif v > b then return b end
	return v
end
local function lerp(a, b, t) return a + (b - a) * t end
local function easeOut(t) return 1 - (1 - t) ^ 3 end
local function easeIn(t) return t * t * t end
local function easeInOut(t)
	if t < 0.5 then return 4 * t * t * t end
	return 1 - ((-2 * t + 2) ^ 3) / 2
end
local function seg(t, a, b) return clamp((t - a) / (b - a), 0, 1) end
Pose.clamp, Pose.lerp, Pose.easeIn, Pose.easeOut = clamp, lerp, easeIn, easeOut

function Pose.base()
	return { lean = 0, lT = 0.08, lS = -0.05, rT = -0.08, rS = -0.12, lU = 0.15, lF = 0.45, rU = 0.25, rF = 1.0,
		w = 2.4, w2 = 2.4, lunge = 0, crouch = 0, headTilt = 0, bob = 0, spin = 0, draw = 0, charge = 0 }
end

local function walk(p, q, amp)
	local s, c = math.sin(q), math.cos(q)
	p.lT = 0.55 * s * amp
	p.lS = p.lT - (0.12 + 0.95 * math.max(0, c)) * amp
	p.rT = -0.55 * s * amp
	p.rS = p.rT - (0.12 + 0.95 * math.max(0, -c)) * amp
	p.lU = -0.45 * s * amp + 0.1
	p.lF = p.lU + 0.5
	p.lean = 0.12 * amp
	p.bob = math.abs(math.sin(q)) * 2.2 * amp
end

local function attack(u, p, t)
	local ty = u.type
	if ty == "sword" then
		if t < 0.38 then
			local k = easeOut(seg(t, 0, 0.38))
			p.rU = lerp(0.35, -2.5, k); p.rF = p.rU - 0.25; p.w = p.rU - 0.35; p.lean = lerp(0.04, -0.18, k); p.lU = lerp(0.1, 0.9, k); p.lF = p.lU + 0.3
		elseif t < 0.56 then
			local k = easeIn(seg(t, 0.38, 0.56))
			p.rU = lerp(-2.5, -4.95, k); p.rF = p.rU + 0.12; p.w = p.rU + 0.25; p.lean = lerp(-0.18, 0.42, k); p.lunge = 9 * k
			p.lU = lerp(0.9, -0.5, k); p.lF = p.lU + 0.4; p.lT = lerp(0, 0.55, k); p.lS = p.lT - 0.4; p.rT = lerp(0, -0.5, k); p.rS = p.rT
		else
			local k = easeInOut(seg(t, 0.56, 1))
			p.rU = lerp(1.33, 0.35, k); p.rF = lerp(1.45, 1.2, k); p.w = lerp(1.58, 2.5, k); p.lean = lerp(0.42, 0.04, k); p.lunge = 9 * (1 - k)
			p.lU = lerp(-0.5, 0.1, k); p.lF = p.lU + 0.4; p.lT = 0.55 * (1 - k); p.lS = p.lT - 0.4 * (1 - k); p.rT = -0.5 * (1 - k); p.rS = p.rT
		end
	elseif ty == "spear" then
		if t < 0.45 then
			local k = easeOut(seg(t, 0, 0.45))
			p.rU = lerp(0.5, -0.25, k); p.rF = 1.5; p.lU = lerp(0.95, 0.35, k); p.lF = 1.5; p.lean = lerp(0, -0.2, k); p.lunge = -6 * k; p.rT = -0.3 * k; p.rS = p.rT - 0.2 * k
		elseif t < 0.6 then
			local k = easeIn(seg(t, 0.45, 0.6))
			p.rU = lerp(-0.25, 1.4, k); p.rF = 1.57; p.lU = lerp(0.35, 1.5, k); p.lF = 1.57; p.lean = lerp(-0.2, 0.45, k); p.lunge = lerp(-6, 16, k)
			p.lT = 0.7 * k; p.lS = p.lT - 0.6 * k; p.rT = -0.6 * k; p.rS = p.rT
		else
			local k = easeInOut(seg(t, 0.6, 1))
			p.rU = lerp(1.4, 0.5, k); p.lU = lerp(1.5, 0.95, k); p.rF = 1.5; p.lF = 1.5; p.lean = lerp(0.45, 0, k); p.lunge = 16 * (1 - k)
			p.lT = 0.7 * (1 - k); p.lS = p.lT - 0.6 * (1 - k); p.rT = -0.6 * (1 - k); p.rS = p.rT
		end
		p.w = 1.6 + p.lean * 0.15
	elseif ty == "archer" then
		p.lU = 1.5; p.lF = 1.56; p.w = 1.56; p.lean = 0.02
		if t < 0.6 then
			local k = easeOut(seg(t, 0, 0.6))
			p.rU = lerp(0.9, 1.25, k); p.rF = lerp(1.5, -1.45, k); p.draw = k; p.lean = -0.08 * k; p.rT = -0.25 * k; p.lT = 0.2 * k
		else
			local k = easeOut(seg(t, 0.6, 1))
			p.rU = lerp(1.0, 0.4, k); p.rF = lerp(-1.6, 0.9, k); p.draw = 0; p.lean = lerp(-0.12, 0.02, k); p.rT = -0.25 * (1 - k); p.lT = 0.2 * (1 - k)
		end
	elseif ty == "ninja" then
		local alt = u.combo % 2 == 0
		local k1, k2, k3 = seg(t, 0, 0.35), seg(t, 0.35, 0.65), seg(t, 0.65, 1)
		local a
		if k2 > 0 then
			if k3 > 0 then a = 1.65 * (1 - easeInOut(k3)) + 0.6 * easeInOut(k3) else a = lerp(-0.4, 1.65, easeIn(k2)) end
		else
			a = lerp(0.6, -0.4, easeOut(k1))
		end
		if alt then p.rU = a; p.rF = a + 0.15; p.w = a + 0.1 else p.lU = a; p.lF = a + 0.15; p.w2 = a + 0.1 end
		p.lean = 0.3 + ((k2 > 0 and k3 == 0) and 0.2 or 0)
		p.lunge = k2 > 0 and 8 * (1 - k3) or 0
		if u.combo % 4 == 3 then
			p.spin = easeInOut(seg(t, 0.1, 0.9)) * TAU
			p.bob = math.sin(seg(t, 0, 1) * PI) * 18
		end
	elseif ty == "shield" then
		if t < 0.4 then
			local k = easeOut(seg(t, 0, 0.4))
			p.rU = lerp(0.25, -1.9, k); p.rF = p.rU - 0.4; p.w = p.rU - 0.5; p.lean = lerp(0.06, -0.12, k)
		elseif t < 0.55 then
			local k = easeIn(seg(t, 0.4, 0.55))
			p.rU = lerp(-1.9, 1.5, k); p.rF = p.rU + 0.1; p.w = p.rU + 0.2; p.lean = lerp(-0.12, 0.35, k); p.lunge = 7 * k; p.lU = 1.4; p.lF = 1.55
		else
			local k = easeInOut(seg(t, 0.55, 1))
			p.rU = lerp(1.5, 0.25, k); p.rF = lerp(1.6, 0.9, k); p.w = lerp(1.7, 2.5, k); p.lean = lerp(0.35, 0.06, k); p.lunge = 7 * (1 - k)
		end
	elseif ty == "mage" then
		if t < 0.58 then
			local k = easeOut(seg(t, 0, 0.58))
			p.rU = lerp(0.35, -2.7, k); p.rF = lerp(0.5, -2.9, k); p.w = lerp(PI - 0.12, -3.0, k); p.lU = lerp(0.2, 1.4, k); p.lF = lerp(0.9, 1.6, k)
			p.lean = lerp(0.04, -0.2, k); p.bob = 6 * k; p.charge = k
		elseif t < 0.7 then
			local k = easeIn(seg(t, 0.58, 0.7))
			p.rU = lerp(-2.7, -4.7, k); p.rF = p.rU; p.w = p.rU; p.lean = lerp(-0.2, 0.4, k); p.lunge = 8 * k; p.bob = 6 * (1 - k); p.charge = 1 - k
		else
			local k = easeInOut(seg(t, 0.7, 1))
			p.rU = lerp(1.58, 0.35, k); p.rF = lerp(1.58, 0.5, k); p.w = lerp(1.58, PI - 0.12, k); p.lean = lerp(0.4, 0.04, k); p.lunge = 8 * (1 - k)
		end
	elseif ty == "giant" then
		if t < 0.48 then
			local k = easeOut(seg(t, 0, 0.48))
			p.rU = lerp(0.45, -2.9, k); p.lU = lerp(0.6, -2.75, k); p.rF = p.rU - 0.15; p.lF = p.lU - 0.1; p.w = p.rU - 0.2; p.lean = lerp(0.05, -0.28, k); p.bob = 4 * k
		elseif t < 0.6 then
			local k = easeIn(seg(t, 0.48, 0.6))
			p.rU = lerp(-2.9, -4.7, k); p.lU = lerp(-2.75, -4.65, k); p.rF = p.rU + 0.1; p.lF = p.lU + 0.1; p.w = p.rU + 0.15
			p.lean = lerp(-0.28, 0.6, k); p.crouch = 8 * k; p.lT = 0.5 * k; p.lS = p.lT - 0.7 * k; p.rT = -0.5 * k; p.rS = p.rT - 0.3 * k
		else
			local k = easeInOut(seg(t, 0.6, 1))
			p.rU = lerp(1.58, 0.45, k); p.lU = lerp(1.63, 0.6, k); p.rF = lerp(1.68, 1.6, k); p.lF = lerp(1.73, 1.55, k); p.w = lerp(1.73, 2.7, k)
			p.lean = lerp(0.6, 0.05, k); p.crouch = 8 * (1 - k); p.lT = 0.5 * (1 - k); p.lS = p.lT - 0.7 * (1 - k); p.rT = -0.5 * (1 - k); p.rS = p.rT - 0.3 * (1 - k)
		end
	end
end

-- u: { type, moving, phase, seed, attacking, atkT, combo, hurt, blockT, cheer, victory }
function Pose.compute(u, time)
	local p = Pose.base()
	local ty = u.type
	local q = u.phase or 0
	if u.moving then
		walk(p, q, ty == "ninja" and 1.25 or (ty == "giant" and 0.75 or 1))
	else
		local br = math.sin(time * 2.4 + (u.seed or 0)) * 0.04
		p.lean = 0.04 + br; p.lU = 0.12 + br; p.lF = 0.4
	end
	if ty == "sword" then p.rU = 0.35 + (u.moving and 0.25 * math.sin(q) or 0); p.rF = 1.2; p.w = 2.5 end
	if ty == "spear" then p.rU = 0.5; p.rF = 1.45; p.lU = 0.95; p.lF = 1.5; p.w = 1.62 end
	if ty == "archer" then p.lU = 0.9; p.lF = 1.25; p.rU = 0.3; p.rF = 0.9; p.w = 1.3 end
	if ty == "ninja" then p.rU = 0.6; p.rF = 1.9; p.lU = -0.4; p.lF = 0.4; p.w = 2.1; p.w2 = -2.6; p.lean = p.lean + 0.22; p.crouch = 3 end
	if ty == "giant" then p.rU = 0.45; p.rF = 1.6; p.lU = 0.6; p.lF = 1.55; p.w = 2.7; p.lean = p.lean + 0.05 end
	if ty == "shield" then p.lU = 1.05; p.lF = 1.45; p.rU = 0.25; p.rF = 0.9; p.w = 2.5; p.lean = p.lean + 0.06 end
	if ty == "mage" then
		p.rU = 0.35; p.rF = 0.5; p.w = PI - 0.12; p.lU = 0.2 + math.sin(time * 3 + (u.seed or 0)) * 0.15; p.lF = 0.9
		if not u.moving then p.bob = 1.5 + math.sin(time * 2.2 + (u.seed or 0)) * 1.5 end
	end
	if u.attacking then attack(u, p, clamp(u.atkT or 0, 0, 1)) end
	if (u.blockT or 0) > 0 then
		local k = clamp(u.blockT / 0.3, 0, 1)
		p.lU = lerp(p.lU, 1.5, k); p.lF = lerp(p.lF, 1.62, k); p.lean = p.lean - 0.12 * k; p.crouch = 4 * k; p.lunge = -3 * k
	end
	if (u.cheer or 0) > 0 then
		local k = math.sin(clamp(u.cheer / 0.8, 0, 1) * PI)
		p.rU = lerp(p.rU, -2.8, k); p.rF = lerp(p.rF, -3.0, k); p.w = lerp(p.w, -3.1, k); p.lU = lerp(p.lU, -2.5, k); p.lF = lerp(p.lF, -2.7, k)
		p.lean = p.lean - 0.15 * k; p.headTilt = -0.3 * k
	end
	if (u.hurt or 0) > 0 then
		local h = u.hurt / 0.22
		p.lean = p.lean - 0.35 * h; p.headTilt = -0.35 * h; p.lU = p.lU - 0.6 * h
	end
	if u.victory then
		local v = math.sin(time * 9 + (u.seed or 0))
		p.rU = -2.9 + v * 0.2; p.rF = -3.0; p.w = -3.1; p.lU = -2.6; p.lF = -2.8; p.lean = -0.1; p.bob = math.max(0, v) * 6
	end
	return p
end

local function limb(px, py, a, L, dir)
	return px + math.sin(a) * L * dir, py + math.cos(a) * L
end

-- Gelenke relativ zu den Füßen (0,0). Liefert Tabelle mit {x,y}-Paaren.
function Pose.solve(p, s, dir)
	local TH, SH, TO, UA, FA = 15 * s, 15 * s, 24 * s, 12 * s, 12 * s
	local lKx, lKy = limb(0, 0, p.lT, TH, dir)
	local lFx, lFy = limb(lKx, lKy, p.lS, SH, dir)
	local rKx, rKy = limb(0, 0, p.rT, TH, dir)
	local rFx, rFy = limb(rKx, rKy, p.rS, SH, dir)
	local oy = -math.max(lFy, rFy) - p.bob + p.crouch * s
	local ox = p.lunge * s * dir
	local J = {}
	J.hip = { ox, oy }
	J.lK = { lKx + ox, lKy + oy }; J.lFt = { lFx + ox, lFy + oy }
	J.rK = { rKx + ox, rKy + oy }; J.rFt = { rFx + ox, rFy + oy }
	J.neck = { ox + math.sin(p.lean) * TO * dir, oy - math.cos(p.lean) * TO }
	J.sh = { lerp(ox, J.neck[1], 0.9), lerp(oy, J.neck[2], 0.9) }
	local x, y = limb(J.sh[1], J.sh[2], p.lU, UA, dir); J.lE = { x, y }
	x, y = limb(x, y, p.lF, FA, dir); J.lH = { x, y }
	x, y = limb(J.sh[1], J.sh[2], p.rU, UA, dir); J.rE = { x, y }
	x, y = limb(x, y, p.rF, FA, dir); J.rH = { x, y }
	local hr = 7.6 * s
	local ha = p.lean + p.headTilt
	J.headR = hr
	J.headA = ha
	J.head = { J.neck[1] + math.sin(ha) * (hr + 1.5 * s) * dir, J.neck[2] - math.cos(ha) * (hr + 1.5 * s) }
	-- Salto (Ninja): alles um die Hüfte drehen
	if p.spin and p.spin ~= 0 then
		local c, sn = math.cos(p.spin * dir), math.sin(p.spin * dir)
		for k, v in pairs(J) do
			if type(v) == "table" then
				local dx, dy = v[1] - ox, v[2] - oy
				J[k] = { ox + dx * c - dy * sn, oy + dx * sn + dy * c }
			end
		end
		J.headA = J.headA + p.spin
	end
	return J
end

return Pose
