# Kriminalität, Polizei, Banden und Politik

Dateien: `scripts/crime/crime_system.gd` (CrimeSystem), `scripts/crime/police.gd`
(PoliceCareer, `world.crime.police`), `scripts/crime/gangs.gd` (Gangs,
`world.crime.gangs`), `scripts/crime/contract_access.gd` (defensiver Zugriff auf
Partnersysteme), `scripts/politics/politics.gd` (Politics, `world.politics`).
Alle Namen von Organisationen und Parteien sind frei erfunden.

## Grundregeln

* Geld bewegt sich nur über den Ledger. Beute kommt vom echten Opfer
  (Haushaltskonto, Geschäftskonto, Lagerbestand), Strafen und Kaution gehen an
  `government.account`. Reparaturen nach Sachbeschädigung und das
  Infrastrukturprogramm sind `burn` (auswärtige Firmen).
* Zufall: `crime:<seed>`, `gangs:<seed>`, `police:<seed>`, `politics:<seed>`;
  Auftragsbrett je Bande/Tag eigener Seed. Zustände werden gespeichert.
* Sandbox-Regeln: `crime_rate` skaliert NPC-Taten (0 = keine),
  `police_strictness` skaliert Festnahmen, Strafen und Haft.

## Polizeiwirkung (gemeinsame Größe)

```
eff = government.modifier("police_effectiveness")         # hängt am Polizeibudget
      (Fallback: 0.4 + 0.6 * budget.police / budget_police_bei_Spielstart)
      * (1 + 0.01 * (Dienstgrad+1), wenn Spieler Polizist ist)
strict = rules.police_strictness * policies["police_strictness"] (Gesetz, 0.5..1.5)
```

## Taten des Spielers (`attempt`)

Erfolgschance = Grundwert der Tat + (Fähigkeit − 20)/250 + Nachtbonus
+ (Kriminalität des Stadtteils − 0.3)·0.15 − (eff − 1)·0.12 − (strict − 1)·0.05,
begrenzt auf 5..95 %. Zeugen: `population.persons_near(pos, 30 m)` ohne
Schlafende, Inhaftierte und eigene Bandenmitglieder; jeder meldet mit
(0.08 + 0.25·lawful − 0.1·criminal)·(1 − stealth/200), nachts ·0.6, bei
Scheitern ·2. Zusätzlich identifiziert das Opfer mit `victim_reports`
(Raub 85 %). Gesehen → Fahndung +Stufen der Tat (+1 bei Scheitern), Anklage.
Ungesehen (kein Bagatelldelikt) → offener Fall gegen den Spieler, den die
NPC-Polizei später lösen kann (Haftbefehl).

| Tat | Schwere | Opfer | Fahndung | Strafe | Haft (h) |
|---|---|---|---|---|---|
| Taschendiebstahl | Vergehen | Person (Haushalt) | 1 | 250 € | 4 |
| Ladendiebstahl | Ordnungswidrigkeit | Geschäftsbestand | 1 | 150 € | 0 |
| Einbruch | Verbrechen | Haushalt | 2 | 900 € | 36 |
| Raubüberfall | Verbrechen | Geschäftskasse | 3 | 1800 € | 72 |
| Autodiebstahl | Verbrechen | Fahrzeug (Hehlerware) | 2 | 1200 € | 48 |
| Schmuggel/illegaler Handel | Verbrechen | Abnehmer in der Nähe | 2 | 1000 € | 36 |
| Sachbeschädigung | Ordnungswidrigkeit | Geschäft (Reparatur) | 1 | 300 € | 0 |
| Schutzgelderpressung | Verbrechen | Geschäftskasse | 2 | 1500 € | 48 |

## Fahndung, Festnahme, Strafe

* Stufe 0..5. Festnahme pro Stunde: [0, 6, 14, 24, 36, 50] % · eff ·
  (0.4 + 0.6·strict); versteckt ·0.15, in Gebäuden ·0.5.
* Abklingen: ohne Sichtung für 90·(0.6 + 0.4·strict) min (versteckt halb so
  lang) sinkt die Stufe um 1. Sichtung pro Stunde 25 %·eff.
* Strafe = Summe der Anklagen · (0.5 + 0.5·strict) · (1 + 0.25·frühere
  Verbrechen); Haft · (0.3 + 0.7·strict). Teilzahlung möglich, Rest 40 € = 1 h.
  Selbst stellen: ·0.6. Kaution: 100 € + 35 €/h · (0.5 + 0.5·strict).
* Vorstrafe: Eintrag in `crime.record` und `player.criminal_record`;
  `has_record(kind, id, severity)` prüft „mindestens diese Schwere“.
  Folgen: Polizeidienst gesperrt (Verbrechen), Ämter ab Bürgermeister gesperrt,
  Skandale in der Politik, Jobs lesen `criminal_record`.

## NPC-Kriminalität (stündlich je Stadtteil, Poisson)

```
λ = 0.12 · crime · (1.5 − wealth) · rules.crime_rate · 1/(0.35 + 0.65·eff)
    · 1/(0.8 + 0.2·strict) · welfare_factor · Täteranteil · (1 + Bandenstärke/200)
    · Tageszeit (Nacht 1.5) · Streife des Spielers (0.6)
welfare_factor = 1 − 0.25·(Sozialbudget/Start · policies.welfare_level − 1)   (0.65..1.4)
```

Täter: Personen mit `traits.criminal ≥ 0.6` oder Bandenmitglieder. Beute
Opfer → Haushalt des Täters (Bandenkasse bei Erpressung/Schmuggel/Raub).
Sofort aufgeklärt mit 0.22·eff·(0.6 + 0.4·strict) → Vorstrafe und Haft des NPC
(`person.activity = "jail"`), sonst offener Fall mit Beweislage. Die NPC-Polizei
arbeitet täglich an Fällen; nach 10 Tagen werden sie eingestellt.

## Polizei-Laufbahn

Eintritt: ab 18, kein Verbrechen in der Akte, Schulabschluss, Gesundheit ≥ 50,
Fitness ≥ 15, nicht gesucht/in Haft, keine Bande. Gibt es einen Polizeiberuf im
Berufskatalog, läuft der Eintritt über den JobMarket.

| Dienstgrad | Leistung | Fälle | Festnahmen | Tage | Abschluss | Lohn/h |
|---|---|---|---|---|---|---|
| Polizeianwärter | – | – | – | – | Schule | 14 |
| Polizeimeister | 45 | 1 | 2 | 1 | Schule | 18 |
| Polizeiobermeister | 55 | 3 | 5 | 1 | Schule | 21 |
| Kommissar | 62 | 6 | 8 | 2 | Berufsausbildung | 26 |
| Hauptkommissar | 70 | 10 | 12 | 2 | Berufsausbildung | 32 |
| Polizeirat | 78 | 15 | 16 | 3 | Bachelor | 40 |
| Polizeipräsident | 85 | 22 | 22 | 3 | Master | 55 |

Dienst: Streife (senkt NPC-Taten im Stadtteil, stellt Täter, findet Spuren),
Fall übernehmen, ermitteln (Beweise ab 60 % → Tatverdächtiger bekannt),
festnehmen. Verbrechen des Spielers → Entlassung; Doppelleben mit Bande →
täglich 8 % Entdeckung (Entlassung + Vorstrafe „Bestechlichkeit“).

## Banden

Rostgraben-Crew (Straßengang), Schwarze Möwen (Schmuggler), Neon-Syndikat
(Schutzgeld), Famiglia Valcorvo (Mafia-Familie, Anführer „Pate“). Revier =
passende Stadtteile mit hoher Kriminalität, Versteck = passendes Gebäude im
Revier, Mitglieder = kriminelle NPCs des Reviers, Kasse = Ledger-Konto „gang“.
Täglich: Schutzgeld aus Revier-Geschäften, Bandenlohn an Mitglieder,
Stärke ± (Mitglieder, Einnahmen, Polizeidruck eff·strict), Revierkampf
zwischen zwei Banden (Nachricht, Gebietswechsel bei klarem Sieg).

Laufbahn: Kontakt im Versteck → Aufnahmeauftrag → Ränge. Ruf/Aufträge/
Loyalität/Vertrauen des Bosses (Beziehung) und Tage im Rang:

| Rang | Ruf | Aufträge | Loyalität | Vertrauen | Zusatz |
|---|---|---|---|---|---|
| Kurier | 12 | 3 | – | – | – |
| Soldat | 25 | 6 | 40 | – | 1 Tag im Rang |
| Vollstrecker | 38 | 10 | 48 | 10 | 2 harte Aufträge |
| Leutnant | 52 | 15 | 58 | 20 | 4 harte Aufträge |
| Capo | 66 | 20 | 66 | 32 | 1 Anwerbung |
| Unterboss | 80 | 26 | 75 | 45 | 2 verteidigte Reviere |
| Boss/Pate | 92 | 32 | 82 | 55 | Entscheidung |

Spitze: `boss_decision("challenge")` (Machtprobe, Erfolg 10..85 %, Scheitern:
Soldat, −40 Ruf, −40 Gesundheit), `"succeed"` (nur wenn der Anführer verhaftet
wurde oder sich zurückzieht), `"wait"`. Aussage gegen die Bande in Haft halbiert
die Haft, schwächt die Bande und führt zum Ausschluss.

Aufträge (`event`-Folge): Paket ausliefern (pickup, deliver; Kontrollrisiko),
Schutzgeld eintreiben (collect je Geschäft, 20 % Anteil), Auto stehlen (steal,
deliver), Laden ausrauben (rob, 40 % an die Bande), Revier verteidigen (fight),
Mitglied anwerben (talk).

## Politik

Parteien: Bürgerbund für Ordnung, Soziale Allianz, Lebenswertes Havenbrück,
Partei des Fortschritts (Ideologie je Thema −1..1, Beliebtheit).

| Amt | Wahl alle | Alter | Einfluss | Gebühr | Vorstrafe | Vorher | Bezüge/Tag |
|---|---|---|---|---|---|---|---|
| Parteimitglied | – | 16 | – | 50 € | egal | – | – |
| Stadtrat (9 Sitze) | 10 Tage | 18 | 10 | 200 € | kein Verbrechen | – | 60 |
| Bürgermeister | 14 Tage | 21 | 30 | 1000 € | kein Verbrechen | Stadtrat oder Einfluss 50 | 220 |
| Abgeordneter (15 Sitze) | 18 Tage | 21 | 35 | 1500 € | kein Verbrechen | – | 180 |
| Minister | Ernennung | 25 | 55 | – | nur Ordnungswidrigk. | Abgeordneter, Partei des Präsidenten | 300 |
| Präsident | 24 Tage | 30 | 70 | 5000 € | nur Ordnungswidrigk. | Bürgermeister/Abgeordneter/Minister | 500 |

Wahl: Einzelämter Softmax über Kandidatenwert 3·Beliebtheit + 1.2·Einfluss/100
+ Amtsbonus (Zufriedenheit − 0.5)·1.5 − Skandal + Rauschen. Listenwahlen:
Parteianteile → Sitze (größte Reste); der Spieler zieht ein, wenn die Sitze
seiner Partei seinen Listenplatz (aus dem Einfluss) erreichen.
Zufriedenheit: Arbeitslosigkeit, Kriminalität (Taten pro 1000 Einwohner),
Steuerlast, Sozialleistungen, Bildungsindex, Gesundheit, Nahverkehr, Wohnen.
Zustimmung im Amt folgt der Zufriedenheit; Steuererhöhungen senken sie.
< 15 % an 3 Tagen → Misstrauensvotum. Vorstrafen werden als Skandal bekannt
(Verbrechen ab Bürgermeister → Amtsenthebung).

Befugnisse und Wirkung der Hebel (`apply_decision`):

| Hebel | Wer | Ziel im Government | Wirkung in den Systemen |
|---|---|---|---|
| Einkommen-/Umsatz-/Unternehmensteuer | Präsident, Finanzminister | `set_rate` | Einnahmen (Economy/Jobs), Zufriedenheit/Zustimmung sinken |
| Grundsteuer | + Bürgermeister, Ratsantrag | `set_rate` | wie oben (Housing) |
| Polizeibudget | Präsident, Innenminister | `set_budget("police")` | eff ↑ → NPC-Taten ↓, Aufklärung ↑, Festnahmen ↑ |
| Polizeibefugnisse (Gesetz) | Präsident, Innenminister | `policies.police_strictness` | strict ↑ → Strafen, Haft, Festnahmen ↑, Taten ↓ |
| Bildungs-/Gesundheitsbudget | Präsident, Fachminister | `set_budget` | langfristiger Index (`policies.education_index`) |
| Nahverkehr/Infrastruktur/Wohnen | + Bürgermeister | `set_budget` | Busverkehr, `infrastructure_index`, Wohnen |
| Sozialbudget, Grundsicherung, Arbeitslosengeld | Präsident, Sozialminister | `set_budget("welfare")`, `policies.welfare_level`, `policies.unemployment_benefit_level` | Armutskriminalität ↓ (welfare_factor), Population/Government zahlen |
| Fahrpreis | Bürgermeister | `policies.bus_fare` | Verkehr |
| Mietpreisbremse | Präsident, Sozialminister | `policies.rent_cap` (0/1) | Housing |
| Infrastrukturprogramm | Bürgermeister, Verkehrsminister | Staatskonto `burn` | `infrastructure_index` ↑ |

Budgetanteile werden so gesetzt, dass ihre Summe gleich bleibt (übrige Posten
anteilig). Ohne Spieler im Amt passen NPC-Amtsinhaber alle zwei Tage einen
Hebel an das schwächste Thema an (innerhalb 70..150 % des Startwerts).
