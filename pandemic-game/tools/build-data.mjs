// Erzeugt src/data/world.json: Laender (Bevoelkerung, Wohlstand, Urbanisierung,
// Klima, Nachbarn, Flughafen/Hafen, Hafenposition), Seeweg-Graph und die
// Kartengeometrie (TopoJSON 110m, ohne Antarktis).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import * as topojson from 'topojson-client';
import { geoArea, geoCentroid, geoContains } from 'd3-geo';

const require = createRequire(import.meta.url);
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const topo = structuredClone(require('world-atlas/countries-110m.json'));
const wc = require('world-countries');

// ISO3: Bevoelkerung (Mio.), Wohlstand 0..1, Urbanisierung 0..1
const TABLE = `FJI .93 .35 .58|TZA 67 .12 .37|ESH .6 .2 .87|CAN 39 .9 .82|USA 335 1 .83|KAZ 20 .45 .58|UZB 36 .2 .5|PNG 10 .12 .13|IDN 278 .35 .58|ARG 46 .45 .92|CHL 19.6 .55 .88|COD 102 .05 .47|SOM 18 .03 .47|KEN 55 .15 .29|SDN 48 .06 .36|TCD 18 .05 .24|HTI 11.7 .06 .59|DOM 11.3 .35 .84|RUS 144 .5 .75|BHS .41 .6 .83|FLK .0035 .6 .8|NOR 5.5 1 .84|GRL .056 .7 .87|TLS 1.3 .1 .32|ZAF 60 .3 .68|LSO 2.3 .08 .3|MEX 129 .4 .81|URY 3.4 .55 .95|BRA 216 .38 .88|BOL 12.4 .22 .71|PER 34 .3 .79|COL 52 .3 .82|PAN 4.5 .5 .69|CRI 5.2 .45 .82|NIC 7 .12 .59|HND 10.6 .12 .6|SLV 6.3 .2 .75|GTM 18 .2 .53|BLZ .41 .25 .46|VEN 28 .2 .88|GUY .8 .4 .27|SUR .62 .3 .66|FRA 68 .85 .82|ECU 18 .28 .65|PRI 3.2 .6 .94|JAM 2.8 .28 .57|CUB 11 .25 .77|ZWE 16.6 .08 .32|BWA 2.7 .3 .72|NAM 2.6 .25 .54|SEN 18 .1 .49|MLI 23 .05 .45|MRT 4.9 .1 .56|BEN 13.7 .08 .49|NER 27 .03 .17|NGA 224 .12 .54|CMR 28.6 .1 .59|TGO 9 .07 .44|GHA 34 .15 .59|CIV 28.9 .15 .53|GIN 14 .07 .38|GNB 2.1 .05 .45|LBR 5.4 .04 .53|SLE 8.8 .04 .44|BFA 23 .05 .32|CAF 5.7 .02 .43|COG 6.1 .1 .69|GAB 2.4 .3 .91|GNQ 1.7 .25 .74|ZMB 20.5 .08 .46|MWI 20.9 .04 .18|MOZ 33.9 .04 .38|SWZ 1.2 .15 .24|AGO 36.7 .12 .68|BDI 13.2 .02 .15|ISR 9.8 .85 .93|LBN 5.4 .25 .89|MDG 30 .05 .4|PSE 5.4 .12 .77|GMB 2.8 .06 .64|TUN 12.4 .25 .7|DZA 45.6 .25 .75|JOR 11.3 .25 .92|ARE 9.5 .9 .88|QAT 2.7 1 .99|KWT 4.3 .8 1|IRQ 45.5 .2 .71|OMN 4.6 .55 .88|VUT .33 .1 .26|KHM 16.9 .1 .26|THA 71.8 .35 .54|LAO 7.6 .12 .38|MMR 54.6 .08 .32|VNM 98.9 .25 .4|PRK 26.2 .08 .63|KOR 51.7 .85 .81|MNG 3.4 .25 .69|IND 1428 .15 .36|BGD 173 .12 .4|BTN .78 .2 .44|NPL 30.9 .08 .22|PAK 240 .1 .38|AFG 42 .04 .27|TJK 10.1 .08 .28|KGZ 7 .1 .38|TKM 6.5 .25 .54|IRN 89 .3 .77|SYR 23 .06 .57|ARM 2.8 .3 .64|SWE 10.5 .95 .89|BLR 9.2 .35 .8|UKR 37 .2 .7|POL 36.7 .6 .6|AUT 9.1 .9 .59|HUN 9.6 .55 .73|MDA 2.5 .2 .43|ROU 19 .45 .55|LTU 2.8 .6 .69|LVA 1.9 .55 .69|EST 1.3 .65 .7|DEU 84 .9 .78|BGR 6.4 .4 .77|GRC 10.3 .5 .81|TUR 85 .4 .77|ALB 2.8 .3 .64|HRV 3.9 .5 .59|CHE 8.8 1 .74|LUX .66 1 .92|BEL 11.8 .85 .98|NLD 17.9 .9 .93|PRT 10.4 .55 .67|ESP 48 .7 .81|IRL 5.3 .95 .64|NCL .29 .6 .72|SLB .74 .08 .26|NZL 5.2 .8 .87|AUS 26.6 .9 .86|LKA 22 .2 .19|CHN 1410 .45 .65|TWN 23.4 .75 .8|ITA 59 .75 .72|DNK 5.9 .95 .88|GBR 68 .85 .84|ISL .39 .95 .94|AZE 10.4 .3 .57|GEO 3.7 .3 .6|PHL 117 .2 .48|MYS 34 .45 .78|BRN .45 .7 .79|SVN 2.1 .65 .56|FIN 5.6 .9 .86|SVK 5.4 .55 .54|CZE 10.9 .65 .74|ERI 3.7 .03 .42|JPN 124 .8 .92|PRY 6.9 .3 .63|YEM 34 .04 .39|SAU 36.9 .7 .85|CYP 1.3 .6 .67|MAR 37.8 .2 .65|EGY 112 .2 .43|LBY 6.9 .3 .81|ETH 126 .06 .23|DJI 1.1 .15 .78|UGA 48.6 .07 .26|RWA 14 .08 .18|BIH 3.2 .3 .5|MKD 1.8 .35 .59|SRB 6.6 .35 .57|MNE .62 .4 .68|XKX 1.8 .25 .5|TTO 1.5 .5 .53|SSD 11 .02 .21`;
const DATA = Object.fromEntries(TABLE.split('|').map((s) => {
  const [k, p, w, u] = s.trim().split(/\s+/);
  return [k, { pop: +p * 1e6, wealth: +w, urban: +u }];
}));
const ARID = new Set('ESH MRT MLI NER TCD SDN EGY LBY DZA SAU ARE QAT KWT OMN YEM IRQ IRN AFG PAK MNG TKM UZB KAZ JOR SYR NAM BWA AUS DJI SOM ERI TUN MAR ISR PSE'.split(' '));
const COLD_EXTRA = new Set('NPL BTN KGZ TJK MNG ISL GRL CHE AUT'.split(' '));
// Grosse Drehkreuze fuer den Flugverkehr
const HUBS = { USA: 3, GBR: 2.5, DEU: 2.2, FRA: 2.2, ARE: 2.5, CHN: 2.5, JPN: 2, NLD: 1.8, QAT: 1.8, TUR: 1.8, IND: 1.8, AUS: 1.5, BRA: 1.5, CAN: 1.5, RUS: 1.5, KOR: 1.5, ESP: 1.6, ITA: 1.5, THA: 1.6, ZAF: 1.4, EGY: 1.3, MEX: 1.4, SAU: 1.3, SGP: 2, MYS: 1.2, IDN: 1.2, CHE: 1.2, AUT: 1.1, ETH: 1.2, KEN: 1.1, NGA: 1.1, ARG: 1.1, COL: 1.1, PER: 1.0, CHL: 1.0 };
// Kuenstliche Bruecken/Tunnel (zaehlen als Landgrenze)
const EXTRA_LINKS = [['GBR', 'FRA'], ['DNK', 'SWE'], ['DNK', 'DEU'], ['ESP', 'MAR'], ['TUR', 'GRC'], ['JPN', 'KOR'], ['MYS', 'IDN'], ['PNG', 'IDN']];

// Seeweg-Knoten (lon, lat) und Kanten (duerfen kein Land kreuzen)
const SEA = {
  NATL_W: [-50, 40], NATL_E: [-22, 44], US_E: [-70, 36], CAN_E: [-52, 47], GRL_S: [-44, 57], ISL_S: [-20, 60], NSEA: [3, 57], NOR_W: [4, 66], BARENTS: [36, 72],
  BALTIC: [19, 57], BISCAY: [-9, 45], GIBR: [-8, 35.8], MED_W: [5, 38], MED_C: [17, 35], MED_E: [29, 33.5], AEGEAN: [25, 38.5], BLACK: [34, 43], ADRIA: [15.5, 42.5],
  SUEZ: [33, 29], REDSEA: [38, 20], ADEN: [48, 12.5], ARAB: [62, 16], GULF: [53, 26], BENGAL: [88, 14], IND_C: [76, 0], IND_S: [80, -30],
  MALACCA: [98, 5], SCS: [113, 12], ECS: [125, 29], JPN_E: [146, 35], NPAC_W: [170, 38], NPAC_E: [-145, 38], CAL: [-122, 30], ALASKA: [-150, 55],
  PAC_MEX: [-102, 14], PAN_P: [-80, 6], PAN_A: [-79, 11], CARIB: [-72, 15], GULFMX: [-90, 25], FLORIDA: [-77, 27], SATL_W: [-33, -8],
  BRA_S: [-44, -28], ARG: [-56, -42], HORN: [-67, -57], PERU: [-83, -15], SPAC: [-115, -25], HAWAII: [-155, 18], NZ_E: [179, -38],
  TASMAN: [160, -38], AUS_S: [130, -38], AUS_W: [108, -25], INDO: [112, -7], CORAL: [155, -15], PHIL: [130, 12], GUINEA: [2, 1],
  WAFR: [-20, 10], CANARY: [-18, 28], SAFR_W: [10, -25], CAPE: [19, -37], MOZ: [42, -20], EAFR: [48, -5], OKHOTSK: [150, 52], BERING: [-178, 57],
};
const SEA_EDGES = `NATL_W-NATL_E NATL_W-US_E NATL_W-CAN_E NATL_W-FLORIDA NATL_W-SATL_W NATL_E-BISCAY NATL_E-ISL_S NATL_E-CANARY NATL_E-GIBR CAN_E-GRL_S GRL_S-ISL_S ISL_S-NSEA ISL_S-NOR_W NSEA-NOR_W NOR_W-BARENTS NSEA-BALTIC NSEA-BISCAY BISCAY-GIBR GIBR-MED_W GIBR-CANARY MED_W-MED_C MED_C-MED_E MED_C-AEGEAN MED_C-ADRIA AEGEAN-MED_E AEGEAN-BLACK MED_E-SUEZ SUEZ-REDSEA REDSEA-ADEN ADEN-ARAB ADEN-EAFR ARAB-GULF ARAB-IND_C ARAB-BENGAL IND_C-BENGAL IND_C-MALACCA IND_C-EAFR IND_C-IND_S IND_S-AUS_W IND_S-CAPE IND_S-MOZ BENGAL-MALACCA MALACCA-SCS MALACCA-INDO SCS-ECS SCS-PHIL ECS-JPN_E ECS-PHIL JPN_E-NPAC_W JPN_E-OKHOTSK OKHOTSK-BERING BERING-ALASKA NPAC_W-NPAC_E NPAC_W-HAWAII NPAC_E-CAL NPAC_E-ALASKA NPAC_E-HAWAII HAWAII-CAL HAWAII-SPAC CAL-PAC_MEX PAC_MEX-PAN_P PAN_P-PAN_A PAN_P-PERU PAN_A-CARIB CARIB-GULFMX CARIB-FLORIDA CARIB-SATL_W GULFMX-FLORIDA FLORIDA-US_E SATL_W-BRA_S SATL_W-GUINEA SATL_W-WAFR BRA_S-ARG ARG-HORN HORN-PERU PERU-SPAC SPAC-NZ_E NZ_E-TASMAN TASMAN-AUS_S TASMAN-CORAL CORAL-PHIL CORAL-INDO CORAL-NPAC_W AUS_S-AUS_W AUS_W-INDO WAFR-CANARY WAFR-GUINEA GUINEA-SAFR_W SAFR_W-CAPE CAPE-MOZ MOZ-EAFR SAFR_W-BRA_S PHIL-NPAC_W INDO-PHIL`;

const PORT_NODE = { RUS: 'BALTIC', USA: 'US_E', ITA: 'MED_C', CAN: 'CAN_E' };
const byNum = Object.fromEntries(wc.map((c) => [c.ccn3, c]));
const geoms = topo.objects.countries.geometries;
const NAME_FIX = { 'N. Cyprus': 'CYP', Somaliland: 'SOM', Kosovo: 'XKX' };
for (const g of geoms) {
  g.properties.iso = byNum[g.id]?.cca3 || NAME_FIX[g.properties.name] || null;
}
// Antarktis & franz. Suedgebiete entfernen, Teilgebiete zusammenfuehren
topo.objects.countries.geometries = geoms.filter((g) => g.properties.iso && !['ATA', 'ATF'].includes(g.properties.iso));
const merged = {};
for (const g of topo.objects.countries.geometries) (merged[g.properties.iso] ||= []).push(g);
const newGeoms = [];
for (const [iso, gs] of Object.entries(merged)) {
  const m = gs.length > 1 ? topojson.mergeArcs(topo, gs) : { ...gs[0] };
  newGeoms.push({ type: m.type, arcs: m.arcs, id: iso, properties: { iso } });
}
topo.objects.countries.geometries = newGeoms;
const neigh = topojson.neighbors(newGeoms);
const features = topojson.feature(topo, topo.objects.countries).features;

function nearestSea(lon, lat) {
  let best = null; let bd = 1e9;
  for (const [k, [x, y]] of Object.entries(SEA)) {
    let dx = Math.abs(x - lon); if (dx > 180) dx = 360 - dx;
    const d = dx * dx * Math.cos((lat * Math.PI) / 180) ** 2 + (y - lat) ** 2;
    if (d < bd) { bd = d; best = k; }
  }
  return best;
}

const countries = [];
for (let i = 0; i < newGeoms.length; i++) {
  const iso = newGeoms[i].id;
  const info = wc.find((c) => c.cca3 === iso);
  const d = DATA[iso];
  if (!d) throw new Error('Keine Daten fuer ' + iso);
  const f = features[i];
  const [clon, clat] = info?.latlng ? [info.latlng[1], info.latlng[0]] : geoCentroid(f);
  const cen = geoCentroid(f);
  const lat = geoContains(f, cen) ? cen[1] : clat;
  const lon = geoContains(f, cen) ? cen[0] : clon;
  const alat = Math.abs(lat);
  let temp = 29 - 0.42 * alat - (alat > 45 ? (alat - 45) * 0.35 : 0);
  if (COLD_EXTRA.has(iso)) temp -= 6;
  if (iso === 'GRL') temp = -15;
  let hum = ARID.has(iso) ? 0.2 : alat < 16 ? 0.85 : alat < 30 ? 0.6 : 0.55;
  if (iso === 'AUS') hum = 0.3;
  const climate = temp < 6 ? 'kalt' : ARID.has(iso) ? 'arid' : temp > 23 ? (hum > 0.7 ? 'tropisch' : 'heiß') : 'gemäßigt';
  const area = info?.area || geoArea(f) * 6371 ** 2;
  const landlocked = info ? info.landlocked : iso === 'XKX';
  const nb = neigh[i].map((j) => newGeoms[j].id);
  for (const [a, b] of EXTRA_LINKS) {
    if (a === iso && !nb.includes(b)) nb.push(b);
    if (b === iso && !nb.includes(a)) nb.push(a);
  }
  const medical = Math.min(1, 0.12 + d.wealth * 0.85 + (['CUB', 'KOR', 'JPN', 'TWN'].includes(iso) ? 0.1 : 0));
  const density = d.pop / Math.max(area, 1);
  const travel = Math.min(1, 0.15 + d.wealth * 0.55 + d.urban * 0.2 + Math.log10(d.pop / 1e6 + 1) * 0.08 + (HUBS[iso] ? 0.15 : 0));
  const airport = iso !== 'GRL' && iso !== 'MDG' && iso !== 'FLK' && (d.pop > 1.2e6 || d.wealth > 0.55 || iso === 'ISL');
  let port = !landlocked && d.pop > 3e5;
  if (['GRL', 'MDG', 'ISL', 'FLK', 'NZL', 'CUB'].includes(iso)) port = true;
  // Hafenposition: Kuestenpunkt, der dem naechsten Seeweg-Knoten am naechsten liegt
  let portPos = null; let seaNode = null;
  if (port) {
    const coords = [];
    const polys = f.geometry.type === 'Polygon' ? [f.geometry.coordinates] : f.geometry.coordinates;
    for (const p of polys) for (const pt of p[0]) coords.push(pt);
    seaNode = PORT_NODE[iso] || nearestSea(lon, lat);
    const [sx, sy] = SEA[seaNode];
    let bd = 1e9;
    for (const [x, y] of coords) {
      const dd = (x - sx) ** 2 + (y - sy) ** 2;
      if (dd < bd) { bd = dd; portPos = [+x.toFixed(2), +y.toFixed(2)]; }
    }
  }
  countries.push({
    iso, name: info?.translations?.deu?.common || (iso === 'XKX' ? 'Kosovo' : iso), pop: Math.round(d.pop),
    wealth: d.wealth, urban: d.urban, medical: +medical.toFixed(2), temp: +temp.toFixed(1), hum, climate,
    area: Math.round(area), density: +density.toFixed(1), travel: +travel.toFixed(2), hub: HUBS[iso] || 1,
    lon: +lon.toFixed(2), lat: +lat.toFixed(2), neighbors: nb, airport, port, portPos, seaNode,
    region: info?.subregion || info?.region || '',
  });
}
// Kartenname-Korrekturen
const RENAME = { PSE: 'Palästina', ESH: 'Westsahara', XKX: 'Kosovo', TWN: 'Taiwan', PRK: 'Nordkorea', KOR: 'Südkorea', COD: 'DR Kongo', COG: 'Kongo', CAF: 'Zentralafrika', FLK: 'Falklandinseln' };
for (const c of countries) if (RENAME[c.iso]) c.name = RENAME[c.iso];

const out = {
  countries,
  sea: { nodes: SEA, edges: SEA_EDGES.split(/\s+/).map((e) => e.split('-')) },
  topo,
};
for (const [a, b] of out.sea.edges) if (!SEA[a] || !SEA[b]) throw new Error('Seekante ' + a + '-' + b);
fs.mkdirSync(path.join(root, 'src', 'data'), { recursive: true });
fs.writeFileSync(path.join(root, 'src', 'data', 'world.json'), JSON.stringify(out));
console.log(`Laender: ${countries.length}, Weltbevoelkerung: ${(countries.reduce((a, c) => a + c.pop, 0) / 1e9).toFixed(2)} Mrd., ` +
  `Flughaefen: ${countries.filter((c) => c.airport).length}, Haefen: ${countries.filter((c) => c.port).length}, ` +
  `Groesse: ${(fs.statSync(path.join(root, 'src', 'data', 'world.json')).size / 1024).toFixed(0)} KB`);
const aut = countries.find((c) => c.iso === 'AUT');
console.log(JSON.stringify(aut));
