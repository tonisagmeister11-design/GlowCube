# Life Simulator – Architektur und Schnittstellen

Dieses Dokument ist der verbindliche Vertrag zwischen den Modulen. Wer ein
Modul baut, hält sich an die hier beschriebenen Datenformate und Methoden.
Änderungen an Verträgen werden hier nachgetragen.

## Grundsätze

* Godot 4.3, GDScript, Renderer Forward+ (muss auch im Compatibility-Renderer
  laufen, weil Tests unter Linux/Xvfb mit OpenGL rendern).
* Einheiten: 1 Einheit = 1 Meter, Y nach oben, Boden bei y = 0. Daten der Stadt
  verwenden `Vector2(x, z)`; Umrechnung mit `CityModel.v3()` / `CityModel.v2()`.
* **Simulation und Darstellung sind getrennt.** Die gesamte Spiellogik steckt in
  `RefCounted`-Klassen unter `WorldState` und läuft ohne Szenenbaum (Tests,
  Zeitraffer beim Schlafen). Szenen/Nodes lesen den Zustand und rufen
  Methoden der Systeme auf, sie halten keinen eigenen Spielzustand, der
  gespeichert werden müsste.
* **Geld bewegt sich nur über `Ledger`** (`world.ledger`). Kein System ändert
  Kontostände direkt.
* Zufall: Jedes System erzeugt eigene `RandomNumberGenerator` mit
  `seed = hash("<system>:%d" % world.seed)`; nie `randi()` global für
  Spiellogik verwenden (Reproduzierbarkeit).
* UI-Texte auf Deutsch mit echten Umlauten. Code-Kommentare auf Deutsch, ASCII
  ist in Kommentaren erlaubt. **Keine Emojis** irgendwo im Spiel – Symbole sind
  SVG-Dateien unter `assets/icons/`.
* Alle Namen von Firmen, Marken, Organisationen sind fiktiv.
* Keine leeren Platzhalterdateien. Unfertiges wird in `docs/STATUS.md`
  gekennzeichnet.

## Verzeichnisstruktur

```
project.godot                 Autoloads: Log, EventBus, Settings, Game
scenes/boot.tscn              Startszene -> Hauptmenü
scenes/menu/                  Hauptmenü, Ladebildschirm, Charaktereditor
scenes/world/world.tscn       Spielwelt (3D-Stadt, Spieler, NPCs, Verkehr, HUD)
scenes/ui/                    HUD, Telefon, Karte, Shop, Dialoge, Pause
scenes/character/             Humanoid, Vorschau
scenes/player/                Spieler
scripts/core/                 Autoloads (log, event_bus, settings, game)
scripts/world/                WorldState, GameRules, Weltszene
scripts/city/                 Stadtdaten: Katalog, Modell, Generator, Validator, Kartenrenderer
scripts/city/visual/          3D-Aufbau der Stadt, Materialien, Innenräume, Tag/Nacht
scripts/economy/              Ledger, Waren, Geschäfte, Firmen
scripts/housing/              Immobilienmarkt
scripts/jobs/                 Berufe, Arbeitsmarkt, Bildung
scripts/npc/                  Bevölkerung (Daten) + sichtbare NPC-Agenten
scripts/traffic/              Fahrzeuge, Ampeln, Busse
scripts/life/                 Spielerleben: Bedürfnisse, Alter, Fähigkeiten, Inventar
scripts/crime/                Kriminalität, Polizei, Gangs
scripts/politics/             Staat, Steuern, Haushalt, Wahlen, Ämter
scripts/character/            Prozeduraler Humanoid, Aussehen
scripts/ui/                   UI-Theme, Widgets, Panels
scripts/saving/               SaveSystem
scripts/ai/                   Optionale Groq-Anbindung
assets/                       fonts, icons (SVG), textures, audio, branding
tests/                        test_runner.tscn + test_*.gd
docs/                         Dokumentation
```

## Werkzeuge / Arbeitsablauf

* Godot-Binary (Linux): `$GODOT` =
  `/tmp/claude-0/-home-user-GlowCube/9ed70ebd-8f40-5098-b1f8-d41d8557d16f/scratchpad/godot/Godot_v4.3-stable_linux.x86_64`
* **Nach dem Anlegen neuer `class_name`-Skripte immer importieren**, sonst
  kennt Godot die Klasse nicht:
  `$GODOT --headless --path . --import`
  (zeigt zugleich alle Parse-Fehler: `... --import 2>&1 | grep -E "SCRIPT ERROR|Parse Error"`)
* Tests: `$GODOT --headless --path . res://tests/test_runner.tscn -- --filter=city`
  Testdateien `tests/test_<bereich>.gd` (extends RefCounted, Feld `var runner`,
  Methoden `test_*`, Prüfungen über `runner.check(cond, msg)`,
  `runner.check_eq(a, b, msg)`, `runner.check_near(a, b, eps, msg)`).
* Screenshots (echtes Rendering über Mesa/llvmpipe):
  `xvfb-run -a -s "-screen 0 1600x900x24" $GODOT --rendering-driver opengl3 --path . <szene>`
  und im Skript `get_viewport().get_texture().get_image().save_png(pfad)`.
  Bilder mit dem Read-Tool ansehen.
* GDScript-Fallen: `var x := dict.wert` schlägt fehl, wenn der Typ nicht
  ableitbar ist -> explizit typisieren (`var x: float = dict.wert`).
  Ein Skriptfehler im `_ready` einer Testszene beendet Godot nicht -> immer mit
  `timeout` starten.

## Autoloads

| Name | Datei | Aufgabe |
|---|---|---|
| `Log` | scripts/core/log.gd | `Log.info/warn/error(tag, msg)`, Ringpuffer `Log.lines`, Datei user://logs |
| `EventBus` | scripts/core/event_bus.gd | globale Signale (siehe Datei) |
| `Settings` | scripts/core/settings.gd | `get_value(section,key)`, `set_value`, Tastenbelegung, `apply_graphics()` |
| `Game` | scripts/core/game.gd | `Game.world`, `start_new_game(config)`, `load_game(slot)`, `save_game(slot)`, `goto_main_menu()`, Zeitstufen, `push_ui_pause()/pop_ui_pause()`, `skip_time(min)` |

Ablauf: `boot.tscn` -> `scenes/menu/main_menu.tscn` -> `Game.start_new_game(config)`
-> `scenes/menu/loading_screen.tscn` (zeigt `Game.loading_progress`) ->
WorldState wird im Thread gebaut -> `scenes/world/world.tscn` -> Weltszene
ruft nach dem Aufbau `Game.world_scene_ready()` auf.

### Konfiguration eines neuen Spiels (`Game.start_new_game(config)`)

```gdscript
{
  "seed": 12345,
  "mode": "life" | "sandbox" | "free",
  "rules": { ...GameRules.SCHEMA-Schlüssel (nur sandbox; city_size/population immer) },
  "character": {                      # vom Charaktereditor
    "name": "Alex Berger",
    "body": "female" | "male",        # gewünschte Körperdarstellung
    "age": 24.0,                      # Startalter in Jahren (0.5 .. 100)
    "appearance": { siehe Abschnitt Humanoid }
  },
  "start": {
    "situation": "auto" | "with_parents" | "own_apartment" | "shared_room" | "homeless" | "wealthy",
    "money": optional float           # Sandbox-Override
  }
}
```

## WorldState (scripts/world/world_state.gd)

Felder: `seed, rules: GameRules, city: CityModel, ledger: Ledger,
time_minutes, play_time_seconds, character, start_config, news, events` und
die Systeme `government, economy, housing, jobs, education, population,
crime, player` (in dieser Setup-/Tick-Reihenfolge).

* Zeit: `time_minutes` seit Tag 0, 00:00. Start 07:00 an Tag 0 (Montag).
  `get_day(), get_hour(), get_minute(), get_hour_f(), get_weekday(), is_weekend()`,
  `time_string(), date_string()`.
* Alter: Personen speichern `birth` (Spielminuten, darf negativ sein).
  `world.age_years(birth)`, `world.birth_for_age(age)`.
  `rules.f("days_per_year")` Spieltage = 1 Lebensjahr (Standard 3).
* `world.advance(minutes)` simuliert in Schritten von 5 Minuten. Systeme
  bekommen `step(world, 5.0)`, `on_hour(world, day, hour)`, `on_day(world, day)`.
* Ereignisse: `world.emit_event(signal_name, args)` (leitet an EventBus weiter),
  `world.notify(text, kind)`, `world.post_news(category, text)`.
* Speichern: `to_save()/from_save()` je System. Die Stadt wird beim Laden aus
  dem Seed neu generiert, darf also keinen veränderlichen Zustand enthalten.
  Alles Veränderliche (Besitzer, Bestände, Preise, Miete) liegt in den Systemen
  und referenziert Gebäude über `building.id`.

### GameRules (scripts/world/game_rules.gd)

`rules.mode`, `rules.f(key)`, `rules.b(key)`, `rules.get_rule(key)`,
`rules.describe()`. Schlüssel siehe `GameRules.SCHEMA`:
`start_money, days_per_year, aging, needs_decay, needs_can_harm, price_level,
wage_level, rent_required, crime_rate, police_strictness, death, illness,
lifespan, population, city_size`.

### Ledger (scripts/economy/ledger.gd)

`open_account(kind, owner_id, name, initial=0, allow_overdraft=false) -> id`,
`transfer(from, to, amount, reason) -> bool` (scheitert ohne Deckung),
`transfer_partial(...) -> gezahlt`, `mint(to, amount, reason)` (Geld von außen),
`burn(from, amount, reason)` (Geld nach außen), `balance(id)`, `can_pay(id, amount)`,
`player_account`, `player_log`. Invariante: `conservation_error() < 0.01`.
Kontoarten (`kind`): `player, household, business, company, government, gang, bank, npc`.

## CityModel (scripts/city/city_model.gd) – nur lesen

Generiert durch `CityGenerator.generate(seed, {"size": "small|medium|large"})`,
geprüft/repariert durch `CityValidator`. Wichtige Arrays (Dictionaries):

* `nodes[i]`: `{id, pos: Vector2, gx, gz, edges: [ids], kind, signal, roundabout, priority_axis}`
  `kind`: `signal` (Ampel), `stop` (rechts vor links), `yield` (Vorfahrt achten;
  `priority_axis` = Achse mit Vorfahrt), `roundabout` (`roundabout.r_outer/r_inner`),
  `corner`, `pass`.
* `edges[i]`: `{id, a, b, axis: "x"|"z", cls: ring|arterial|embankment|street,
  lanes (pro Richtung), lane_w, bike_w, park_w, width, sidewalk: [links, rechts],
  bridge, name, speed (m/s), length}`. Rechts = rechts in Richtung a->b.
  Helfer: `edge_dir(e)`, `edge_right(e)`, `edge_point(e, t, lateral)`,
  `lane_offset(e, forward, lane)`, `sidewalk_offset(e, side)`, `curb_offset(e, side)`,
  `edge_end_clearance(e, node_id)` (ab hier ist die Kante frei von der Kreuzung),
  `edge_road_rect(e)`, `edge_between(a, b)`, `edge_other(e, node)`, `node_box(id)`,
  `node_arms(id)` -> {"n","s","e","w": edge_id}.
* Ampeln: `signal_state(node_id, axis, t_seconds) -> "green"|"yellow"|"red"`
  (Echtzeit-Sekunden, Umlauf 34 s; Achse = Fahrtrichtung der Kante),
  `ped_signal_green(node_id, crossing_edge_axis, t)`.
* `blocks[i]`: `{id, cell, curb, inner: Rect2, zone, district, kind: normal|park|facility|promenade, special}`.
  `curb` = Bordsteinkante (Gehweg beginnt), `inner` = Grundstücksgrenze.
  Gehweg = Fläche zwischen `curb` und `inner`. Promenaden: ganze Fläche begehbar.
* `lots[i]`: `{id, block, rect, front: n|s|e|w, edge, side, zone, usage: building|green, building}`.
* `buildings[i]`: `{id, lot, block, kind, label, footprint: Rect2, floors, height,
  floor_h, front: n|s|e|w, front_dir: Vector2 (zur Straße), door: Vector2 (an der Fassade),
  sidewalk_point: Vector2 (Gehwegmitte vor dem Eingang), walkway: Rect2, driveway: Rect2,
  parking: Rect2, edge, side, zone, district, style: {facade, roof, color, trim,
  roof_color, window, balconies, condition, variant, setback_top, stalls?, tents?, pool?},
  business_type, name, brand_color, res_units, biz_units, open_air, address}`.
  `kind` siehe `CityCatalog.BUILDING_KINDS`; `business_type` siehe
  `CityCatalog.BUSINESS_TYPES` ("" = kein Geschäft).
* `props[i]`: `{id, kind, pos, rot, r, layer: street|lot|park, ref, ...}`; kinds:
  `lamp, street_tree, tree, hedge (rect, length), bench, bin, hydrant,
  traffic_light (axis, edge, arm_width), sign_stop, sign_yield, sign_roundabout,
  street_sign (names), bus_shelter (stop, dir, side), fountain, playground,
  container, pallets`. `rot`: Yaw im Bogenmaß (Blickrichtung `Vector2(sin, cos)`).
* `parks[i]`: `{id, rect, name, block, paths: [Rect2], features, promenade}`.
* `parking_areas[i]`: `{id, rect, building, spots: [{pos, rot}]}`;
  `street_parking[i]`: `{edge, side, t, pos, rot}`.
* `crosswalks[i]`: `{node, edge, center, axis, length, width, signal}`.
* `bus_stops[i]`: `{id, edge, side, t, pos (Gehweg), curb_pos, dir, name, lines}`.
* `bus_lines[i]`: `{id, name, number, color, loop, nodes: [node ids], stops (Hinweg), stops_back (Rückweg), length, via}`.
  Nicht-Ringlinien fahren `nodes` hin und rückwärts zurück.
* `districts[i]`: `{id, name, zone, label, blocks, center, bounds, wealth, crime}`.
* `river`: `{z, width, z_north, z_south, x0, x1, emb_north, emb_south}` – Wasserband
  entlang x; Brückenkanten haben `bridge = true`, Fahrbahn bleibt auf y = 0.
* `spawn_points[i]`: `{pos, building, label}`.
* Wegfindung: `road_route_nodes(from, to)` (Knotenfolge für Fahrzeuge),
  `ped_route(from, to) -> PackedVector2Array` (nur Gehwege + Zebrastreifen),
  `nearest_edge(p)`, `nearest_node(p)`, `building_at(p)`, `block_at_point(p)`,
  `district_at(p)`, `buildings_of_kind(k)`, `buildings_with_business(t)`.
* Chunks: `chunks[Vector2i] = {buildings, props, blocks, edges, parks}` mit
  `CHUNK_SIZE = 200`, `chunk_key(p)`, `chunk_rect(k)`.
* `CityMapRenderer.render(model, px_per_m, debug) -> Image` (Draufsicht),
  `world_to_px / px_to_world`.
* `issues`: Validierungsergebnis; nach Reparatur 0 Fehler (Test `test_city`).

## Systeme der Simulation (Verträge)

Alle Systeme: `class_name` + `extends RefCounted`, Methoden `setup(world)`,
`step(world, dt)`, `on_hour`, `on_day`, `to_save()`, `from_save(world, d)`,
optional `post_load(world)`. IDs sind `int`, Datensätze sind Dictionaries,
damit Speichern trivial bleibt.

### Economy (scripts/economy/economy.gd)

* `goods` Katalog (scripts/economy/goods_catalog.gd, `class_name GoodsCatalog`):
  `GOODS[id] = {name, category, base_price, unit, needs: {hunger, thirst, energy, hygiene, fun, health}, consumable, durable, icon}`.
* `businesses: Dictionary id -> {id, building, type, name, color, account,
  inventory: {good_id: {stock, price, max}}, open, close, tier, employees: [person ids],
  owner: {"kind": "npc"|"player"|"company"|"city", "id"}, revenue_today, customers_today, status: "open"|"closed"|"bankrupt"}`
* `get_business(id)`, `business_for_building(building_id) -> id or -1`,
  `is_open(world, id) -> bool`, `list_businesses(type := "")`,
  `price_of(id, good_id) -> float`,
  `buy(world, buyer_account, business_id, good_id, qty) -> {ok, reason, total}`
  (bucht über Ledger, reduziert Bestand, Umsatzsteuer an Staat),
  `npc_shop(world, household_account, business_id, budget) -> float ausgegeben`.
* Nachschub über Großhandel (Geld verlässt die Stadt per `burn`, Industrie
  erzeugt per `mint` Exporterlöse).

### HousingMarket (scripts/housing/housing_market.gd)

* `properties: Dictionary id -> {id, building, unit, kind: room|apartment|house|villa|estate|shelter_bed,
  rooms, size_m2, quality 0..1, rent_per_week, price, owner: {kind, id}, tenant: {kind:"player"|"household"|"", id},
  for_rent, for_sale, district}`
* `get_property(id)`, `properties_in_building(bid)`, `list_available(filter := {})`,
  `rent(world, tenant_kind, tenant_id, account, property_id) -> {ok, reason}`,
  `buy(world, buyer_kind, buyer_id, account, property_id) -> {ok, reason}`,
  `move_out(world, property_id)`, `home_of(kind, id) -> property id or -1`.
  Miete wird wöchentlich (alle 7 Spieltage) eingezogen; Rückstände führen zur Kündigung.

### JobMarket (scripts/jobs/job_market.gd) und Education (scripts/jobs/education.gd)

* `OCCUPATIONS` (scripts/jobs/occupations.gd, `class_name Occupations`):
  `id -> {name, category, min_age, education: none|school|vocational|bachelor|master|doctor,
  skills: {skill: min}, wage (pro Stunde), shift: [start_hour, hours], days: [0..6],
  workplaces: [building kinds oder business types], ranks: [{title, wage_mult, min_perf}], criminal_record_ok, illegal}`
* `positions`: offene/besetzte Stellen je Arbeitsplatz-Gebäude.
* `list_openings(world, for_kind, for_id)`, `apply(world, kind, id, position_id) -> {ok, reason}`,
  `job_of(kind, id) -> {occupation, building, rank, performance, ...} or {}`,
  `work(world, kind, id, hours) -> {pay, ...}`, `quit(world, kind, id)`, `fire(...)`.
* Bildung: `education.enroll(world, kind, id, program)`, `attend(world, kind, id, hours)`,
  Abschlüsse `none, school, vocational, bachelor, master, doctor`.

### Population (scripts/npc/population.gd)

* `persons: Dictionary id -> {id, first, last, body: female|male, birth, household,
  home (property id or -1), job (Dictionary oder {}), school (building id oder -1),
  traits: {friendly, shy, ambitious, lazy, risky, helpful, suspicious, social, lawful, criminal} (0..1),
  needs: {hunger, energy, hygiene, social, fun} (0..100), appearance: {...},
  activity: "sleep|work|school|shop|leisure|commute|home|wander|crime|jail|hospital",
  location: {building: id or -1, from: Vector2, to: Vector2, depart: min, arrive: min},
  relationships: {person_id: -100..100}, alive, homeless, criminal_record, gang: -1}`
* `households: Dictionary id -> {id, members, account, home, wealth}`
* `get_person(id)`, `position_of(world, id) -> Vector2` (interpoliert entlang der Route),
  `persons_near(world, pos: Vector2, radius) -> Array[int]` (räumlich indiziert),
  `visible_state(world, id) -> {pos, heading, activity, moving, building}`.
* Entfernte NPCs werden nur datenbasiert simuliert (Stunden-/Schrittlogik).

### PlayerLife (scripts/life/player_life.gd)

* Felder: `name, body, birth, appearance, needs: {hunger, thirst, energy, hygiene, social, fun, health} (0..100),
  mood, skills: {skill: 0..100}, education, account (Ledger), household (Eltern-Haushalt oder -1),
  home (property id), inventory: {good_id: qty}, job: {...}, relationships, criminal_record,
  wanted (0..5), gang, police_rank, political_office, alive, cause_of_death, life_stage`
* Lebensphasen: `baby (0-2), toddler (2-4), child (4-12), teen (12-18),
  young_adult (18-30), adult (30-50), older_adult (50-65), senior (65+)`;
  `life_stage(age)` und `age(world)`.
* Aktionen (geben `{ok, reason, ...}` zurück): `eat(world, good_id)`,
  `drink`, `sleep(world, hours)`, `shower(world)`, `rest`, `work_shift(world)`,
  `study(world, hours)`, `buy(world, business_id, good_id, qty)`,
  `use_item(world, good_id)`, `see_doctor(world)`.
* `save_meta(world) -> {character_name, age, money, job_title}` für die Speicherliste.

### Government (scripts/politics/government.gd)

* `rates: {income_tax, sales_tax, property_tax, business_tax}`,
  `budget: {police, education, health, transit, infrastructure, welfare, housing}` (Anteile),
  `account` (Staatskonto), `modifier(name) -> float` für Auswirkungen
  (`police_effectiveness, education_quality, health_quality, bus_frequency, bus_fare,
  welfare_per_day, unemployment_benefit, rent_subsidy, infrastructure_quality`).
* Ämter/Wahlen/Präsident werden von Welle 2 ergänzt.

### CrimeSystem (scripts/crime/crime_system.gd) – Welle 2

## Darstellung (Verträge)

### Weltszene `scenes/world/world.tscn`

Wurzel `Node3D` mit Skript `scripts/world/world_root.gd`, Kinder:
`Environment` (WorldEnvironment, Sonne), `City` (Stadtaufbau),
`Actors` (Spieler, NPCs, Fahrzeuge), `UI` (CanvasLayer mit HUD/Panels).
`world_root.gd` baut die Stadt (`CityBuilder`), instanziert
`scenes/player/player.tscn` am Startpunkt, startet Verkehr/NPCs und ruft
`Game.world_scene_ready()` auf.
Gruppen: `player`, `vehicle`, `npc`, `interactable`.

### Interaktion

Interagierbare Objekte sind `Area3D`-Nodes in der Gruppe `interactable` mit
Methoden `get_prompt(player) -> String` und `interact(player)`. Der Spieler
zeigt den Hinweis des nächsten Objekts (`EventBus.interaction_available`) und
ruft bei Taste `interact` dessen `interact()` auf. Panels werden über
`EventBus.open_panel.emit("shop", {"business_id": id})` geöffnet.

### Humanoid (scripts/character/humanoid.gd)

`class_name Humanoid extends Node3D`.
`setup(appearance: Dictionary, age_years: float, body: String)`,
`set_motion(speed_mps: float, grounded := true)` (steuert prozedurale Animation),
`set_action(name)` (`idle, walk, run, sit, sleep, work, talk`), `height() -> float`.
Aussehen (`appearance`):
`{skin: Color, hair_style: int, hair_color: Color, eye_color: Color, face: {jaw, nose, brows, cheeks} (0..1),
build: {weight 0..1, muscle 0..1, height 0..1}, top: {style:int, color}, bottom: {style:int, color},
shoes: {style:int, color}, accessories: [ids], beard: int}`.
Proportionen hängen vom Alter ab (Kopf-Körper-Verhältnis, Gliederlängen,
Haltung, graue Haare). Für NPCs gibt es eine günstigere LOD-Variante.
`CharacterPreview` (scenes/character/character_preview.tscn): Control mit
SubViewport, `set_character(appearance, age, body)`, Drehen per Maus.

## Rendering-Budget

* Ziel: 60 FPS auf Mittelklasse-PC bei Qualitätsstufe „hoch“.
* Stadt in Chunks (200 m); Gebäude als zusammengeführte Meshes pro Chunk,
  Requisiten als MultiMesh; Sichtweite über `Settings graphics/view_distance`.
* Kollision: einfache Boxen für Gebäude, ein Bodenquader, Kollision nur für
  Chunks im Umkreis des Spielers.
