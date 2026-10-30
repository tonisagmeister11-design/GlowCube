# Wirtschaft, Leben und Bevölkerung – Regeln und Balancing

Dieses Dokument beschreibt die Zahlen, Formeln und Geldflüsse der reinen
Datensimulation (`scripts/economy`, `housing`, `jobs`, `npc`, `life`,
`politics`). Alle Werte sind Euro-ähnlich in einer fiktiven Stadt;
`rules.f("price_level")` multipliziert Preise/Mieten, `rules.f("wage_level")`
Löhne.

## Zeit

* 1 Spielminute ≈ 1 Echtsekunde bei 1x, `world.advance()` rechnet in 5-Minuten-Schritten.
* Standard: 3 Spieltage = 1 Lebensjahr (`days_per_year`). Kosten und Löhne laufen
  **pro Tag/Woche** wie im echten Leben (Lohn je Schicht, Miete je Woche), nicht pro Lebensjahr.
* Studiengänge: Stunden = Jahre × days_per_year × 5/7 × 6, Mindestdauer = Jahre × days_per_year × 0,6 Tage.
* Pendeln ist zeitgerafft: Fußweg 78 m/Spielminute, Bus 330 m/min + 7 min Warten, Auto 420 m/min + 4 min.

## Geldmenge

Geld bewegt sich nur über den Ledger. Neues Geld entsteht ausschließlich durch
`mint`, es verlässt die Stadt durch `burn`:

| mint (von außen) | burn (nach außen) |
|---|---|
| Startkapital beim Weltaufbau (Haushalte, Geschäfte, Firmen, Staat, Bank) | Großhandelsimporte: 78 % jeder Nachschubbestellung |
| Firmenexporte: Lohnsumme × Exportfaktor (1,45–1,8) × Produktivität × Konjunktur (±10 %) | Vorleistungen der Firmen: 8–38 % der Exporte |
| Besucher aus dem Umland kaufen in Geschäften (`BusinessAI.VISITOR_BASE` × Zonenfaktor × Qualität) | Ausschüttungen an auswärtige Anteilseigner (50 % der Firmen- und Bankdividenden) |
| Zuzug: mitgebrachtes Vermögen (1,5–15 k€) | Sachkosten der Ressorts (60 % des Überschusses über Lohnreserve) |
| Verkauf einer Spieler-Immobilie an Käufer von außerhalb (nur wenn kein Käufer in der Stadt) | Reisen/Fernkäufe wohlhabender Haushalte: 0,25 %/Tag des Guthabens über 80 k€ |
| | Wegzug von Haushalten (Restguthaben) |

Stabilisierung: Jeder Akteur hat ein Liquiditätsziel und gibt Überschüsse weiter
(Dividenden, Haushaltsausgaben, Staatsausgaben). Haushalte geben mehr aus, je mehr
sie über ihrer Reserve haben, und Importe hängen am Umsatz – dadurch pendelt sich
die Geldmenge ein.

## Steuern und Staat (`Government`)

| Satz | Standard | Erhebung |
|---|---|---|
| Einkommensteuer | 20 % × Progression `clamp(0,45 + Stundenlohn/30, 0,5, 1,6)` | bei jeder Lohnzahlung |
| Rentenbeitrag | 9 % des Bruttos | bei jeder Lohnzahlung -> Rentenkasse |
| Umsatzsteuer | 12 % (im Preis enthalten: Anteil = Preis × 0,12/1,12) | bei jedem Verkauf |
| Gewerbesteuer | 15 % des Tagesgewinns | täglich (Geschäfte, Firmen) |
| Grundsteuer | 0,012 % des Werts pro Woche (private Eigentümer); Unternehmen 8 % der Mieteinnahmen | wöchentlich |
| Grunderwerbsteuer | 3,5 % des Kaufpreises | beim Immobilienkauf |
| Erbschaftsteuer | 25 % bei Nachlass ohne Erben | beim Tod |

Effektiver Lohnabzug: Kasse (13 €/h) ≈ 26 %, Pflege (22 €/h) ≈ 32 %, Ärztin (46+ €/h) ≈ 41 %.

Haushalt: Ausgaben pro Tag = Einnahmen (gleitend) × Ausgabefaktor
`clamp(1 + (Kasse − 12 Tage Einnahmen) / (12 Tage) × 0,5, 0,6, 1,5)` + 4 % der Reserve über dem Ziel.
Aufteilung nach `budget` (Polizei & Feuerwehr 17 %, Bildung 24 %, Gesundheit 18 %, Nahverkehr 8 %,
Infrastruktur 12 %, Soziales 13 %, Wohnungsbau 3 %, Verwaltung 5 %). Ressorts zahlen die Löhne
des öffentlichen Dienstes (Schulen, Uni, Klinikum, Praxen, Polizei, Feuerwehr, Rathaus, Busdepot,
Bahnhof, Notunterkunft, Jobcenter); fehlt Geld, springt die Stadtkasse ein (`cover`).
`modifier(name)`: Finanzierungsgrad = Anteil/Standardanteil × Ausgabefaktor (0,3–2,0).

Leistungen (pro Tag): Arbeitslosengeld 36 €, Kindergeld 7 €/Kind, Wohnungslosenhilfe 12 €,
Rente 48 % des letzten Tagesbruttos (32–140 €), Wohngeld 30 % der Miete, wenn die Miete mehr als
40 % des Einkommens beträgt. Mindestlohn 12,50 €/h.

## Preise (`GoodsCatalog`, `Economy.reference_price_raw`)

Ladenpreis = Grundpreis × Stufenfaktor × (0,92 + 0,16 × Wohlstand des Viertels) × price_level
(+12 % Kiosk/Tankstelle), dazu ±5 % je Laden. Stufenfaktor Lebensmittel/Gastronomie
0,88 / 1,0 / 1,12 / 1,26 (Discount bis Luxus), Langlebiges 0,94–1,12. Geschäftsstufe = Grundstufe
der Art, +1 in Vierteln mit Wohlstand ≥ 0,85, −1 bei ≤ 0,25. Luxusartikel (Stufe 3) gibt es nur in
Läden ab Stufe 2. Die KI passt Preise täglich an: ausverkauft oder > 60 % Abverkauf → +3 %,
< 5 % → −1,5 %, Grenzen 0,8–1,5 × Referenz.

Beispiele (Grundpreis): Brot 3,20 €, Milch 1,20 €, Fertiggericht 3,90 €, Döner 6,50 €,
Mittagsmenü 12,90 €, Schnitzel 19,90 €, Steak 32 €, Degustationsmenü 89 €, Kinokarte 11,50 €,
Jeans 39,90 €, Laptop 749 €, Kleinwagen 14.900 €, Luxuslimousine 189.000 €.

Großhandelspreis = Referenzpreis × Wareneinsatz der Kategorie / 1,12 (z. B. Lebensmittel 62 %,
Café 25 %, Restaurant 32 %, Elektronik 72 %, Fahrzeuge 82 %).

Haushaltsvorrat: Lebensmittel bringen `meals = Grundpreis / 2,60` Mahlzeiten. Eine Person isst
2–3 Mahlzeiten am Tag (Frühstück, Abendessen, ggf. Mittag) → etwa 6–9 € Lebensmittel pro Person
und Tag; berufstätige NPCs kaufen mittags oft beim Imbiss/Café (7–19 €).

## Geschäfte (`BusinessAI`)

* Bestand je Artikel mit Höchstbestand (billig 50–120 Stück, teuer 2–6). Nachschub zur Öffnung
  (24-h-Läden um 5 Uhr) und mittags bei Engpass: Artikel unter 50 % werden aufgefüllt, soweit das
  Konto abzüglich eines Tageslohns reicht.
* Tageskosten: Gewerbemiete = Fläche × (0,05 + 0,20 × Wohlstand) €/m² (Marktplätze 30 %) an den
  Gewerbevermieter; Strom/Wasser 8 € + 0,025 €/m² an die Stadtwerke; Löhne je Schicht.
* Personal: Grundbelegschaft je Art (`BusinessAI.STAFF`, inkl. Inhaber), Abbau bei negativem
  Wochengewinn, Aufbau bei gutem Gewinn bis zur Obergrenze.
* Notlage (unbezahlte Rechnungen/Löhne oder Konto < halber Tagesbedarf) → `distress` +1;
  Eigentümer schießen Kapital nach (bis 30 % ihres Guthabens); bei `distress ≥ 6` und leerem
  Konto: Insolvenz (Mitarbeiter entlassen, Bestand weg, Rest an Gläubiger).
* Neugründung: Nach 3 Tagen Insolvenz 30 %/Tag Chance, dass ein Haushalt mit genug Vermögen
  das Geschäft mit neuem Namen übernimmt (Gründungskapital vom Investor, kein neues Geld).
* Wöchentliche Ausschüttung 60 % des Guthabens über 1,3 × Liquiditätsziel an den Eigentümer.

## Firmen (`Company`)

Büros, Hochhäuser, Fabriken und Lager beherbergen Firmen (IT, Finanzen, Versicherungen,
Beratung, Kanzleien, Medien, Architektur, Wohnungsbau, Gewerbeimmobilien, Maschinenbau,
Lebensmittel, Chemie, Elektrotechnik, Großhandel, Logistik, Bau, Taxi). Großhändler erhalten
alle Nachschubbestellungen (22 % Marge), Bauunternehmen 15 % aller Mieten als Instandhaltung,
Wohnungsbaugesellschaften die Mieten. Bei hoher Arbeitslosigkeit schaffen liquide Firmen neue
Stellen (bis 1,6 × Anfangsbelegschaft).

## Wohnen (`HousingMarket`)

* Preis/m² = (1500 + 7500 × Wohlstand^1,5) × (0,75 + 0,5 × Qualität) × Artfaktor × price_level
  (Artfaktor Anwesen 1,15, sonst 1,0). Wochenmiete = Preis × 0,0011 (WG-Zimmer × 1,5 + 15 €,
  mindestens 70 €; Wohnungen mindestens 140 €).
* Größen: Zimmer 12–22 m², Wohnung 26–125 m² (1–4 Zimmer), Haus 90–170 m², Villa 200–340 m²,
  Anwesen 520–900 m². Qualität aus Gebäudezustand und Viertel.
* Kauf: Preis + 5 % Nebenkosten (3,5 % Grunderwerbsteuer, 1,5 % Makler). Hypothek bis 90 %,
  Laufzeit bis 1040 Wochen, 0,09 %/Woche, Rate höchstens 35 % des Wochennettos.
* Miete wöchentlich am `rent_day`; Rückstand ≥ 2 Wochenmieten → Räumung (NPC-Haushalte suchen
  Billigeres oder werden wohnungslos; Notunterkunft 24 Betten je Gebäude, sonst Zeltlager).
* Nicht simulierte Hintergrundbewohner belegen freie Einheiten bis auf ~10 % Leerstand.
* Startsituationen des Spielers: Zimmer (≤ 160 €/Woche), eigene Wohnung (≤ 320 €), wohlhabend
  (eigenes Haus/Villa, Startgeld ≥ 150.000 €), bei Eltern, obdachlos (≤ 20 €).

## Arbeit und Bildung

* Stellen: Geschäfte nach `Occupations.BUSINESS_STAFF`, öffentliche Gebäude nach `PUBLIC_STAFF`
  (auf ~15 % der Stellen skaliert), Firmen nach Gebäudegröße. Gesamt ≈ 0,56 Stellen je Einwohner.
* Lohn = Grundlohn × Rangfaktor × wage_level × Standortfaktor (Finanzbranche +8 %, Luxus +8 %),
  mindestens Mindestlohn. Beispiele (Stufe 0): Kasse 13 €, Verkauf 14 €, Pflege 22 €, Polizei 22 €,
  Lehrkraft 28 €, IT 30 €, Ingenieur 33 €, Manager 42 €, Anwalt 45 €, Ärztin 46 € (Fachärztin 57,50 €).
* Leistung je Schicht: 52 + Fähigkeitsüberschuss × 0,4 − Faulheit × 28 + Ehrgeiz × 12 + Energieeffekt ±8;
  Beförderung nach 6 Schichten im Rang bei ausreichender Leistung (NPC mit Wahrscheinlichkeit
  12 % + 25 % × Ehrgeiz). Entlassung bei Leistung < 14 oder 3 Fehltagen (Spieler).
* NPC-Vermittlung: Fähigkeitslücken bis 15 Punkte werden durch Einarbeitung geschlossen,
  Quereinstieg in Ausbildungsberufe ab 25 mit Schulabschluss. Für Stellen mit Master/Promotion
  kommen bei Bedarf Fachkräfte von außerhalb (max. 2 pro Tag).

## Bevölkerung

* 2400 Personen (Regel `population`) in ~1060 Haushalten: Singles, Paare, Familien,
  Alleinerziehende, WGs, Senioren, ~3 % Wohnungslose. Klassen nach Pro-Kopf-Einkommen
  (arm 10 %, untere 23 %, Mitte 45 %, obere 19 %, reich 3,5 %) bestimmen Wohnort und Ersparnisse.
* Sterblichkeit nach Gompertz: Risiko/Jahr = 0,085 × e^(0,085 × (Alter − Lebenserwartung − 6,8)),
  pro Tag geteilt durch days_per_year, erhöht bei schlechter Gesundheit.
* Geburten: Paare (Frau 20–43) mit Wahrscheinlichkeit
  `Zielbevölkerung/(Lebenserwartung × days_per_year) × (Ziel/aktuell)^3 / Anzahl Paare`.
* Partnerschaften zwischen Singles, Auszug erwachsener Kinder mit Arbeit, Umzug bei zu hoher
  oder zu niedriger Miete, Zuzug unter 97 % der Zielbevölkerung, Wegzug mittelloser Haushalte.
* Lebenskrisen halten die Wohnungslosigkeit bei ~2,5 % (nur arme, arbeitslose Singles mit < 1200 €).
  Wohnungslose betteln (3–14 € Spenden), bekommen Hilfe, manchmal Tagelohn (55 €), und finden mit
  Arbeit und > 300 € wieder ein Zimmer.

## Messergebnisse

Siehe Abschnitt „Langzeitlauf“ unten (Seed 4242, mittlere Stadt, 2400 Einwohner, Lebensmodus).

## Langzeitlauf (60 Spieltage = 20 Lebensjahre)

Seed 4242, mittlere Stadt (1785 Gebäude, 171 Geschäfte, 98 Firmen), 2400 Einwohner, Lebensmodus,
Messung headless auf der Entwicklungsmaschine.

| Tag | Einwohner | Beschäftigt | Arbeitslos | Wohnungslos | insolvent | Geldmenge |
|---|---|---|---|---|---|---|
| 0 | 2402 | 1224 | 12,7 % | 49 | 0 | 75,51 Mio. |
| 10 | 2435 | 1362 | 5,9 % | 51 | 0 | 75,89 Mio. |
| 20 | 2466 | 1359 | 9,3 % | 50 | 0 | 75,19 Mio. |
| 30 | 2476 | 1403 | 9,8 % | 45 | 2 | 73,21 Mio. |
| 40 | 2486 | 1458 | 9,4 % | 41 | 1 | 72,41 Mio. |
| 50 | 2458 | 1489 | 9,2 % | 37 | 2 | 71,09 Mio. |
| 60 | 2440 | 1518 | 7,4 % | 31 | 3 | 69,73 Mio. |

* Geldmenge: −7,7 % in 60 Tagen, nie mehr als ±10 % vom Start (Testschranke: ±15 % über 6 Tage;
  Ledger-Erhaltungsfehler < 0,01 €). Der Großteil der Geldmenge liegt bei wenigen reichen
  Haushalten (0,3–3 Mio. €), die langsam durch Reisen/Fernkäufe abschmelzen.
* Tagesflüsse (typischer Werktag): NPC-Einkäufe 85–165 k€, Besucherumsatz 63–97 k€,
  Firmenexporte 250–310 k€ (Wochenende ~27 k€), Importe 70–110 k€, Großhandel 58–88 k€,
  Gewerbemieten 10,5 k€.
* Geschäfte: höchstens 3 von 171 gleichzeitig insolvent (≥ 98 % aktiv), Neugründungen finden statt.
* Mietrückstände: 0–5 Haushalte gleichzeitig; Räumungen selten.
* Median-Ersparnis nach Zone (Tag 60): Arbeiterviertel 5,6 k€, Brennpunkt 5,3 k€,
  Mietwohngebiet 7,3 k€, Wohngebiet 14,2 k€, Vorort 38,5 k€, Villenviertel 0,8 Mio. €,
  Anwesen 1,4 Mio. €, Wohnungslose 3,9 k€ (Spenden/Hilfe angespart).
* Demografie: 5–12 Geburten und 5–11 Todesfälle pro Tag, Bevölkerung 2400–2490.

## Leistung

* `WorldState.create_new` (mittel, 2400 Personen): 4,0–4,8 s, davon Stadtgenerator 2,5–3,0 s,
  Bevölkerung 0,55–0,6 s, Wirtschaft/Wohnen/Arbeit zusammen ~0,25 s.
* `world.advance(1440)` (ein Spieltag, ~2450 Personen): 0,8–1,15 s.
* `persons_near(pos, 120 m)`: ~0,15 ms.
