// Spielwerte aller Entwicklungen. Alles ist rein fiktive Spielmechanik:
// inf = Übertragbarkeit, sev = Schweregrad, leth = Letalität (Spielpunkte).
// mods: Multiplikatoren für die Welt-Simulation (Klima, Wohlstand, Verkehr ...).

export const TRANSMISSION = [
  { id: 'air1', name: 'Luft 1', icon: '💨', cost: 9, inf: 3, mods: { urban: 0.15, air: 0.25, arid: 0.1 }, desc: 'Der Erreger hält sich länger in der Luft. Vorteil in Städten und im Flugverkehr.' },
  { id: 'air2', name: 'Luft 2', icon: '🌪', cost: 16, inf: 5, req: ['air1'], mods: { urban: 0.2, air: 0.4, arid: 0.15 }, desc: 'Stark verbesserte Luftübertragung. Flugzeuge tragen die Krankheit deutlich häufiger weiter.' },
  { id: 'water1', name: 'Wasser 1', icon: '💧', cost: 9, inf: 3, mods: { humid: 0.15, sea: 0.3, poor: 0.1 }, desc: 'Überdauert im Wasser. Vorteil in feuchten Regionen und im Schiffsverkehr.' },
  { id: 'water2', name: 'Wasser 2', icon: '🌊', cost: 15, inf: 5, req: ['water1'], mods: { humid: 0.2, sea: 0.45, poor: 0.15 }, desc: 'Stark verbesserte Wasserübertragung. Häfen werden zu Brennpunkten.' },
  { id: 'blood1', name: 'Blut 1', icon: '🩸', cost: 8, inf: 2, sev: 1, mods: { poor: 0.2, hospital: 0.1 }, desc: 'Übertragung über Blutkontakt. Stark in Regionen mit schwacher Versorgung.' },
  { id: 'blood2', name: 'Blut 2', icon: '💉', cost: 14, inf: 4, sev: 1, req: ['blood1'], mods: { poor: 0.25, hospital: 0.2 }, desc: 'Verbesserte Blutübertragung. Auch Krankenhäuser werden zum Risiko.' },
  { id: 'animal1', name: 'Tiere 1', icon: '🐄', cost: 8, inf: 2, mods: { rural: 0.25, land: 0.15 }, desc: 'Nutztiere tragen den Erreger. Vorteil in ländlichen Gebieten.' },
  { id: 'animal2', name: 'Tiere 2', icon: '🐖', cost: 14, inf: 4, req: ['animal1'], mods: { rural: 0.35, land: 0.25 }, desc: 'Tierhandel verbreitet die Krankheit über Grenzen hinweg.' },
  { id: 'insect1', name: 'Insekten 1', icon: '🦟', cost: 10, inf: 3, mods: { hot: 0.2, humid: 0.2, land: 0.1 }, desc: 'Insekten verbreiten den Erreger. Stark in warmen, feuchten Ländern.' },
  { id: 'insect2', name: 'Insekten 2', icon: '🪰', cost: 16, inf: 5, req: ['insect1'], mods: { hot: 0.3, humid: 0.3, land: 0.2 }, desc: 'Insektenschwärme tragen die Krankheit auch über Grenzen.' },
  { id: 'food1', name: 'Lebensmittel 1', icon: '🍖', cost: 8, inf: 2, mods: { urban: 0.1, sea: 0.1, rich: 0.1 }, desc: 'Verbreitung über Lebensmittelketten. Erreicht auch wohlhabende Länder.' },
  { id: 'food2', name: 'Lebensmittel 2', icon: '🥫', cost: 13, inf: 4, req: ['food1'], mods: { urban: 0.15, sea: 0.2, rich: 0.2 }, desc: 'Internationaler Lebensmittelhandel wird zum Verbreitungsweg.' },
  { id: 'contact1', name: 'Körperkontakt 1', icon: '🤝', cost: 7, inf: 3, mods: { urban: 0.2, land: 0.1 }, desc: 'Direkter Kontakt überträgt den Erreger. Vorteil in dichten Städten.' },
  { id: 'contact2', name: 'Körperkontakt 2', icon: '👥', cost: 12, inf: 4, req: ['contact1'], mods: { urban: 0.25, land: 0.15, air: 0.1 }, desc: 'Schon flüchtiger Kontakt reicht aus. Menschenmengen werden gefährlich.' },
  { id: 'env1', name: 'Umwelt 1', icon: '🌿', cost: 10, inf: 2, mods: { cold: 0.15, hot: 0.15, arid: 0.15, humid: 0.1 }, desc: 'Der Erreger übersteht Umwelteinflüsse besser.' },
  { id: 'env2', name: 'Umwelt 2', icon: '🌍', cost: 17, inf: 4, req: ['env1'], mods: { cold: 0.25, hot: 0.25, arid: 0.25, humid: 0.15 }, desc: 'Überdauert in nahezu jeder Umgebung.' },
  { id: 'xair', name: 'Extreme Luftübertragung', icon: '☁', cost: 26, inf: 8, req: ['air2', 'water2'], mods: { air: 0.5, sea: 0.4, urban: 0.2 }, desc: 'Höchste Übertragbarkeit über Luft- und Wasserwege.' },
  { id: 'xzoo', name: 'Extreme Tierübertragung', icon: '🐾', cost: 24, inf: 8, req: ['animal2', 'insect2'], mods: { land: 0.4, rural: 0.3, hot: 0.2 }, desc: 'Tiere und Insekten tragen die Krankheit überall hin.' },
];

export const ABILITIES = [
  { id: 'cold1', name: 'Kälteresistenz 1', icon: '❄', cost: 9, mods: { cold: 0.3 }, desc: 'Bessere Verbreitung in kalten Ländern.' },
  { id: 'cold2', name: 'Kälteresistenz 2', icon: '🧊', cost: 15, req: ['cold1'], mods: { cold: 0.45 }, desc: 'Kalte Länder bieten kaum noch Schutz.' },
  { id: 'heat1', name: 'Hitzeresistenz 1', icon: '☀', cost: 10, mods: { hot: 0.3 }, desc: 'Bessere Verbreitung in heißen Ländern.' },
  { id: 'heat2', name: 'Hitzeresistenz 2', icon: '🔥', cost: 16, req: ['heat1'], mods: { hot: 0.45 }, desc: 'Hitze bremst die Krankheit kaum noch.' },
  { id: 'arid1', name: 'Umweltresistenz 1', icon: '🏜', cost: 9, mods: { arid: 0.3, humid: 0.1 }, desc: 'Übersteht trockene Regionen besser.' },
  { id: 'arid2', name: 'Umweltresistenz 2', icon: '🌵', cost: 15, req: ['arid1'], mods: { arid: 0.45, humid: 0.2 }, desc: 'Trockenheit ist kein Hindernis mehr.' },
  { id: 'drug1', name: 'Medikamentenresistenz 1', icon: '💊', cost: 12, mods: { rich: 0.3 }, desc: 'Medizinische Behandlung in reichen Ländern wirkt schlechter.' },
  { id: 'drug2', name: 'Medikamentenresistenz 2', icon: '🧪', cost: 20, req: ['drug1'], mods: { rich: 0.45 }, desc: 'Moderne Medizin verliert an Wirkung.' },
  { id: 'gene1', name: 'Genetische Resistenz 1', icon: '🧬', cost: 16, cureReq: 0.1, desc: 'Erschwert die Heilmittelforschung: Anforderung +10 %.' },
  { id: 'gene2', name: 'Genetische Resistenz 2', icon: '🧬', cost: 26, req: ['gene1'], cureReq: 0.18, desc: 'Erschwert die Heilmittelforschung weiter: Anforderung +18 %.' },
  { id: 'shuffle1', name: 'Genetische Neuordnung 1', icon: '🔀', cost: 18, cureSet: 0.1, desc: 'Wirft die Heilmittelforschung zurück (−10 % Fortschritt beim Entwickeln). Am wirksamsten, wenn die Forschung schon läuft.' },
  { id: 'shuffle2', name: 'Genetische Neuordnung 2', icon: '🔀', cost: 26, req: ['shuffle1'], cureSet: 0.12, desc: 'Wirft die Forschung erneut zurück (−12 %).' },
  { id: 'shuffle3', name: 'Genetische Neuordnung 3', icon: '🔀', cost: 34, req: ['shuffle2'], cureSet: 0.15, desc: 'Massiver Rückschlag für die Forschung (−15 %).' },
];

// Symptom-Hexraster (wie im Original-Bildschirm): q/r = Hex-Koordinaten.
// start: Einstiegssymptome. organ: leuchtet im Körper-Hologramm auf.
export const SYMPTOMS = [
  // obere Gruppe: Atemwege & Kreislauf
  { id: 'cough', name: 'Husten', icon: '😮‍💨', q: 3, r: 1, start: true, cost: 4, inf: 3, sev: 1, organ: 'Lungs', desc: 'Leichtes Husten. Erhöht die Übertragbarkeit, wird aber kaum bemerkt.' },
  { id: 'sneeze', name: 'Niesen', icon: '🤧', q: 4, r: 0, cost: 6, inf: 4, sev: 1, organ: 'Lungs', desc: 'Häufiges Niesen verbreitet die Krankheit in Menschenmengen.' },
  { id: 'pneumonia', name: 'Lungenentzündung', icon: '🫁', q: 2, r: 1, cost: 8, inf: 3, sev: 3, organ: 'Lungs', desc: 'Atembeschwerden. Deutlich spürbar für Betroffene.' },
  { id: 'edema', name: 'Lungenödem', icon: '🫧', q: 2, r: 0, cost: 13, inf: 4, sev: 5, leth: 3, organ: 'Lungs', desc: 'Schwere Atemnot. Steigert Übertragbarkeit und Letalität.' },
  { id: 'fibrosis', name: 'Lungenfibrose', icon: '🧫', q: 1, r: 1, cost: 16, sev: 4, leth: 5, organ: 'Lungs', desc: 'Dauerhafte Schäden. Erschwert zudem die Behandlung.', cureMul: 0.97 },
  { id: 'sweat', name: 'Schwitzen', icon: '💦', q: 5, r: 0, cost: 5, inf: 2, sev: 1, organ: 'Skin', desc: 'Starkes Schwitzen. Leichte Steigerung der Übertragbarkeit.' },
  { id: 'fever', name: 'Fieber', icon: '🌡', q: 4, r: 1, cost: 8, inf: 3, sev: 3, leth: 1, organ: 'Brain', desc: 'Hohes Fieber. Betroffene fallen auf.' },
  { id: 'anaemia', name: 'Blutarmut', icon: '🩸', q: 1, r: 0, cost: 6, sev: 2, leth: 1, organ: 'Heart', desc: 'Schwäche und Müdigkeit.' },
  { id: 'hemorrhage', name: 'Blutungen', icon: '❣', q: 0, r: 1, cost: 18, inf: 4, sev: 6, leth: 7, organ: 'Heart', desc: 'Innere Blutungen. Sehr auffällig und gefährlich.' },
  { id: 'shock', name: 'Kreislaufkollaps', icon: '💔', q: 5, r: 1, cost: 22, sev: 8, leth: 12, organ: 'Heart', desc: 'Plötzlicher Zusammenbruch des Kreislaufs.' },
  { id: 'organ', name: 'Totales Organversagen', icon: '☠', q: 3, r: 2, cost: 34, sev: 12, leth: 20, organ: 'Liver', desc: 'Versagen mehrerer Organe führt schnell zum Tod.' },
  { id: 'immune', name: 'Immunschwäche', icon: '🛡', q: 4, r: 2, cost: 16, inf: 2, sev: 4, leth: 4, organ: 'Heart', desc: 'Geschwächte Abwehr. Andere Symptome wirken stärker.', lethMul: 1.1 },
  // untere Gruppe: Verdauung, Haut, Nerven
  { id: 'nausea', name: 'Übelkeit', icon: '🤢', q: 1, r: 5, start: true, cost: 3, inf: 1, sev: 1, organ: 'Stomach', desc: 'Leichte Übelkeit. Kaum auffällig.' },
  { id: 'vomit', name: 'Erbrechen', icon: '🤮', q: 2, r: 4, cost: 5, inf: 3, sev: 2, organ: 'Stomach', desc: 'Häufiges Erbrechen. Steigert die Übertragbarkeit.' },
  { id: 'diarrhoea', name: 'Durchfall', icon: '🚽', q: 3, r: 4, cost: 7, inf: 4, sev: 2, leth: 1, organ: 'Intestines', desc: 'Starke Verbreitung in Regionen mit schlechter Versorgung.' },
  { id: 'dysentery', name: 'Ruhr', icon: '⚠', q: 3, r: 5, cost: 14, inf: 5, sev: 5, leth: 5, organ: 'Intestines', desc: 'Schwere Verdauungskrankheit. Sehr ansteckend.' },
  { id: 'rash', name: 'Ausschlag', icon: '🔴', q: 0, r: 4, start: true, cost: 3, inf: 2, sev: 1, organ: 'Skin', desc: 'Leichter Hautausschlag.' },
  { id: 'lesions', name: 'Hautläsionen', icon: '🟤', q: 0, r: 5, cost: 8, inf: 4, sev: 3, organ: 'Skin', desc: 'Offene Hautstellen erhöhen die Übertragbarkeit.' },
  { id: 'cysts', name: 'Zysten', icon: '⚪', q: 1, r: 4, cost: 6, sev: 2, organ: 'Kidneys', desc: 'Geschwulste unter der Haut.' },
  { id: 'abscess', name: 'Abszesse', icon: '🟣', q: 1, r: 3, cost: 10, inf: 3, sev: 4, organ: 'Skin', desc: 'Schmerzhafte Entzündungen. Auffällig.' },
  { id: 'necrosis', name: 'Gewebezerfall', icon: '🦴', q: 0, r: 3, cost: 22, inf: 5, sev: 8, leth: 10, organ: 'Skin', desc: 'Schwere Gewebeschäden. Extrem auffällig.' },
  { id: 'insomnia', name: 'Schlaflosigkeit', icon: '😵', q: 4, r: 4, start: true, cost: 3, sev: 1, organ: 'Brain', desc: 'Betroffene schlafen kaum.' },
  { id: 'paranoia', name: 'Paranoia', icon: '👁', q: 5, r: 4, cost: 5, sev: 2, organ: 'Brain', desc: 'Misstrauen erschwert koordinierte Gegenmaßnahmen.', cureMul: 0.98 },
  { id: 'seizures', name: 'Krampfanfälle', icon: '⚡', q: 5, r: 5, cost: 12, sev: 5, leth: 3, organ: 'Brain', desc: 'Neurologische Schäden. Sehr auffällig.' },
  { id: 'insanity', name: 'Wahnsinn', icon: '🌀', q: 4, r: 5, cost: 20, inf: 5, sev: 7, organ: 'Brain', desc: 'Chaos in betroffenen Ländern bremst die Forschung.', cureMul: 0.9 },
  { id: 'paralysis', name: 'Lähmung', icon: '🧍', q: 5, r: 3, cost: 18, sev: 6, leth: 4, organ: 'Brain', desc: 'Lähmungen legen ganze Regionen lahm. Bremst die Forschung.', cureMul: 0.93 },
  { id: 'coma', name: 'Koma', icon: '💤', q: 4, r: 3, cost: 24, sev: 9, leth: 8, organ: 'Brain', desc: 'Betroffene fallen ins Koma. Forschung stockt.', cureMul: 0.92 },
  { id: 'tumours', name: 'Tumore', icon: '🧿', q: 2, r: 5, cost: 16, sev: 5, leth: 6, organ: 'Liver', desc: 'Wuchernde Geschwulste an Organen.' },
  { id: 'inflammation', name: 'Entzündung', icon: '🔥', q: 2, r: 3, cost: 7, inf: 2, sev: 2, organ: 'Kidneys', desc: 'Entzündungen im ganzen Körper.' },
];

export const HEX_NEIGHBORS = (q, r) => {
  // "odd-q"-Layout (Spalten versetzt)
  const odd = q & 1;
  const d = odd
    ? [[1, 0], [1, 1], [0, -1], [0, 1], [-1, 0], [-1, 1]]
    : [[1, -1], [1, 0], [0, -1], [0, 1], [-1, -1], [-1, 0]];
  return d.map(([dq, dr]) => [q + dq, r + dr]);
};
