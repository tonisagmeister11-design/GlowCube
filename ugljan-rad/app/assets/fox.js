/* Velo, der Fuchs: Maskottchen von Velora als Vektorgrafik mit beweglichen Teilen.
   Fox.svg(pose) liefert das SVG, die Bewegungen kommen aus plus.css (.fx-<pose>). */
(function () {
  'use strict';
  var O = '#f47c20', O2 = '#e8661a', CR = '#fde3c8', DK = '#2b1a12', PAW = '#4a2c1d', BL = '#3b82e6', BL2 = '#8cc4ff', BLD = '#1d2f4f';
  var n = 0;
  function arm(d, cls) {
    return '<g class="' + cls + '"><path d="' + d + '" stroke="' + DK + '" stroke-width="17" stroke-linecap="round" fill="none"/><path d="' + d + '" stroke="' + O + '" stroke-width="11.5" stroke-linecap="round" fill="none"/>';
  }
  function paw(x, y, r) { return '<circle cx="' + x + '" cy="' + y + '" r="' + (r || 9) + '" fill="' + PAW + '" stroke="' + DK + '" stroke-width="2.5"/>'; }
  function head() {
    return '<g class="f-head">' +
      '<g class="f-earl"><path d="M90 96 L72 28 L120 70 Z" fill="' + O + '" stroke="' + DK + '" stroke-width="3.5" stroke-linejoin="round"/><path d="M72 28 L77 50 L93 42 Z" fill="' + DK + '"/><path d="M89 86 L80 48 L108 72 Z" fill="' + CR + '"/></g>' +
      '<g class="f-earr"><path d="M162 96 L180 28 L132 70 Z" fill="' + O + '" stroke="' + DK + '" stroke-width="3.5" stroke-linejoin="round"/><path d="M180 28 L175 50 L159 42 Z" fill="' + DK + '"/><path d="M163 86 L172 48 L144 72 Z" fill="' + CR + '"/></g>' +
      '<path d="M126 62 C164 62 182 88 187 116 L172 123 C164 146 146 157 126 157 C106 157 88 146 80 123 L65 116 C70 88 88 62 126 62 Z" fill="' + O + '" stroke="' + DK + '" stroke-width="3.5" stroke-linejoin="round"/>' +
      '<path d="M73 117 C90 110 110 112 126 125 C142 112 162 110 179 117 C168 142 148 155 126 155 C104 155 84 142 73 117 Z" fill="' + CR + '"/>' +
      '<path d="M81 99 C79 52 173 52 171 99 C150 90 102 90 81 99 Z" fill="' + BL + '" stroke="' + DK + '" stroke-width="3.5" stroke-linejoin="round"/>' +
      '<path d="M88 92 C104 86 148 86 164 92" stroke="' + BL2 + '" stroke-width="3" fill="none" stroke-linecap="round" opacity=".7"/>' +
      '<path d="M106 63 L101 77 M126 59 V74 M146 63 L151 77" stroke="' + BLD + '" stroke-width="6.5" stroke-linecap="round"/>' +
      '<path d="M86 100 Q91 127 105 145" stroke="' + BLD + '" stroke-width="4.5" fill="none" stroke-linecap="round"/>' +
      '<g class="f-eyes">' +
      '<g class="e e-happy" stroke="' + DK + '" stroke-width="4.8" fill="none" stroke-linecap="round"><path d="M99 113 Q107 102 116 113"/><path d="M136 113 Q145 102 153 113"/></g>' +
      '<g class="e e-open"><ellipse cx="108" cy="110" rx="5.8" ry="7.5" fill="' + DK + '"/><ellipse cx="144" cy="110" rx="5.8" ry="7.5" fill="' + DK + '"/><circle cx="110" cy="107" r="2.2" fill="#fff"/><circle cx="146" cy="107" r="2.2" fill="#fff"/></g>' +
      '<g class="e e-sad"><ellipse cx="108" cy="112" rx="5" ry="6" fill="' + DK + '"/><ellipse cx="144" cy="112" rx="5" ry="6" fill="' + DK + '"/><circle cx="109.5" cy="110" r="1.8" fill="#fff"/><circle cx="145.5" cy="110" r="1.8" fill="#fff"/><path d="M97 101 L115 104 M155 101 L137 104" stroke="' + DK + '" stroke-width="3.5" stroke-linecap="round"/></g>' +
      '<g class="e e-sleep" stroke="' + DK + '" stroke-width="4.2" fill="none" stroke-linecap="round"><path d="M99 111 Q107 117 116 111"/><path d="M136 111 Q145 117 153 111"/></g>' +
      '</g>' +
      '<ellipse cx="126" cy="127" rx="6" ry="4.3" fill="' + DK + '"/>' +
      '<g class="mo mo-laugh"><path d="M109 133 Q126 157 143 133 Q126 140 109 133 Z" fill="' + DK + '"/><path d="M117 142 Q126 151 135 142 Q126 138 117 142 Z" fill="#ff6b6b"/></g>' +
      '<path class="mo mo-smile" d="M113 134 Q119.5 141 126 134 Q132.5 141 139 134" stroke="' + DK + '" stroke-width="3.6" fill="none" stroke-linecap="round" stroke-linejoin="round"/>' +
      '<path class="mo mo-frown" d="M114 142 Q126 133 138 142" stroke="' + DK + '" stroke-width="3.6" fill="none" stroke-linecap="round"/>' +
      '<ellipse class="mo mo-o" cx="126" cy="139" rx="4.5" ry="5.5" fill="' + DK + '"/>' +
      '<ellipse cx="94" cy="129" rx="6.5" ry="3.6" fill="#ff8f6b" opacity=".55"/><ellipse cx="158" cy="129" rx="6.5" ry="3.6" fill="#ff8f6b" opacity=".55"/>' +
      '<path class="f-tear" d="M100 118 q-4 7 0 10 q4 -3 0 -10z" fill="#5aa9ff"/>' +
      '</g>';
  }
  function tail() {
    return '<g class="f-tail"><path d="M106 214 C84 242 32 240 20 206 C10 178 26 148 54 144 C46 168 58 198 106 198 Z" fill="' + O + '" stroke="' + DK + '" stroke-width="3.5" stroke-linejoin="round"/>' +
      '<path d="M20 206 C10 178 26 148 54 144 L47 158 L55 163 L43 172 L50 179 L36 187 L40 194 Z" fill="' + CR + '" stroke="' + DK + '" stroke-width="3" stroke-linejoin="round"/>' +
      '<path d="M90 222 C70 230 46 226 36 214" stroke="' + O2 + '" stroke-width="3" fill="none" stroke-linecap="round" opacity=".7"/></g>';
  }
  function torso() {
    return '<path d="M98 160 C93 186 93 208 97 228 L155 228 C159 208 159 186 154 160 C142 153 110 153 98 160 Z" fill="' + BL + '" stroke="' + DK + '" stroke-width="3.5" stroke-linejoin="round"/>' +
      '<path d="M99 221 H153" stroke="' + BL2 + '" stroke-width="3"/><path d="M113 158 Q126 167 139 158" stroke="' + BL2 + '" stroke-width="3" fill="none" stroke-linecap="round"/>' +
      '<g stroke="#fff" stroke-width="2.8" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="114" cy="198" r="7.5"/><circle cx="139" cy="198" r="7.5"/><path d="M114 198 L122 185 H134 L139 198 M122 185 L127 198 H114 M133 185 L131 180 M119 182 H126"/></g>';
  }
  function sleeve(x) { return '<ellipse cx="' + x + '" cy="168" rx="12" ry="10.5" fill="' + BL + '" stroke="' + DK + '" stroke-width="3"/>'; }
  function legs() {
    return '<g class="f-legs"><path d="M106 224 L100 262 L119 262 L123 226 Z" fill="' + O + '" stroke="' + DK + '" stroke-width="3.5" stroke-linejoin="round"/>' +
      '<path d="M131 226 L135 262 L154 262 L148 224 Z" fill="' + O + '" stroke="' + DK + '" stroke-width="3.5" stroke-linejoin="round"/>' +
      '<rect x="93" y="256" width="31" height="15" rx="7.5" fill="' + PAW + '" stroke="' + DK + '" stroke-width="3"/><rect x="131" y="256" width="31" height="15" rx="7.5" fill="' + PAW + '" stroke="' + DK + '" stroke-width="3"/></g>';
  }
  function arms() {
    return '' +
      arm('M97 170 Q76 188 94 205', 'a al-hip') + paw(97, 206) + '</g>' +
      arm('M97 171 Q88 194 90 214', 'a al-down') + paw(90, 216) + '</g>' +
      arm('M97 166 Q68 146 52 108', 'a al-up') + paw(50, 103) + '</g>' +
      arm('M97 166 Q86 120 110 74', 'a al-hold') + paw(112, 70) + '</g>' +
      arm('M155 168 Q180 168 187 141', 'a ar-thumb') + paw(189, 135, 11) + '<rect x="184" y="112" width="10" height="20" rx="5" fill="' + PAW + '" stroke="' + DK + '" stroke-width="2.5"/>' +
      '<g class="f-spark" stroke="#ffb703" stroke-width="5" stroke-linecap="round"><path d="M205 112 L214 98"/><path d="M200 102 L202 88"/><path d="M211 120 L224 114"/></g></g>' +
      arm('M155 171 Q164 194 162 214', 'a ar-down') + paw(162, 216) + '</g>' +
      arm('M155 166 Q184 146 200 108', 'a ar-up') + paw(202, 103) + '</g>' +
      arm('M155 166 Q166 120 142 74', 'a ar-hold') + paw(140, 70) + '</g>' +
      '<g class="a ar-wave"><g class="f-wave">' + arm('M155 166 Q172 142 180 114', 'x').replace('<g class="x">', '') + '<ellipse cx="182" cy="104" rx="11" ry="13" fill="' + PAW + '" stroke="' + DK + '" stroke-width="2.5"/><path d="M175 94 v-6 M182 92 v-7 M189 94 v-6" stroke="' + PAW + '" stroke-width="5" stroke-linecap="round"/></g></g>' +
      arm('M155 170 Q176 162 150 146', 'a ar-chin') + paw(148, 145) + '</g>';
  }
  function extras(id) {
    return '<g class="f-zzz" fill="#5f7184" font-family="Inter,sans-serif" font-weight="800"><text x="170" y="60" font-size="18">z</text><text x="184" y="42" font-size="24">z</text><text x="200" y="20" font-size="30">Z</text></g>' +
      '<g class="f-dots" fill="#5f7184"><circle cx="176" cy="66" r="5"/><circle cx="192" cy="50" r="7"/><circle cx="212" cy="30" r="9"/></g>' +
      '<g class="f-medal"><circle cx="126" cy="14" r="26" fill="#ffd43b" opacity=".35"/><path d="M116 -14 L121 8 L126 3 L131 8 L136 -14 Z" fill="#e5463c" stroke="' + DK + '" stroke-width="2.5" stroke-linejoin="round"/>' +
      '<circle cx="126" cy="16" r="17" fill="url(#' + id + 'g)" stroke="' + DK + '" stroke-width="3"/><path d="M126 6 l3 7 7.5 .5 -5.8 4.8 2 7.3 -6.7 -4 -6.7 4 2 -7.3 -5.8 -4.8 7.5 -.5z" fill="#fff" opacity=".9"/></g>';
  }
  function bike() {
    var sp = '';
    for (var i = 0; i < 8; i++) { var a = i * Math.PI / 4; sp += 'M0 0 L' + (Math.cos(a) * 26).toFixed(1) + ' ' + (Math.sin(a) * 26).toFixed(1) + ' '; }
    var wheel = function (x) { return '<g transform="translate(' + x + ' 252)"><circle r="28" fill="none" stroke="' + DK + '" stroke-width="6"/><g class="f-spin"><path d="' + sp + '" stroke="#9fb0bf" stroke-width="1.8"/></g><circle r="4" fill="' + DK + '"/></g>'; };
    return '<g class="f-bike">' + wheel(60) + wheel(196) +
      '<path d="M60 252 L104 210 L170 210 L196 252 M104 210 L128 252 L170 210 M60 252 H128 M170 210 L176 192 M166 190 H190 M98 202 H118" stroke="#e5463c" stroke-width="6" stroke-linecap="round" stroke-linejoin="round" fill="none"/>' +
      '<g transform="translate(128 252)"><g class="f-crank"><path d="M0 -14 V14" stroke="' + DK + '" stroke-width="5" stroke-linecap="round"/><rect x="-7" y="-17" width="14" height="5" rx="2" fill="' + DK + '"/><rect x="-7" y="12" width="14" height="5" rx="2" fill="' + DK + '"/></g><circle r="7" fill="none" stroke="' + DK + '" stroke-width="3"/></g></g>';
  }
  function rideLeg(cls) {
    return '<g class="' + cls + '"><path d="M116 214 L136 236 L128 262" stroke="' + DK + '" stroke-width="17" stroke-linecap="round" stroke-linejoin="round" fill="none"/><path d="M116 214 L136 236 L128 262" stroke="' + O + '" stroke-width="11.5" stroke-linecap="round" stroke-linejoin="round" fill="none"/><ellipse cx="129" cy="264" rx="11" ry="6.5" fill="' + PAW + '" stroke="' + DK + '" stroke-width="2.5"/></g>';
  }
  function svg(pose) {
    var id = 'fx' + (++n);
    var defs = '<defs><radialGradient id="' + id + 'g" cx="40%" cy="35%"><stop offset="0" stop-color="#fff3b0"/><stop offset=".55" stop-color="#ffcf33"/><stop offset="1" stop-color="#e09a00"/></radialGradient></defs>';
    var shadow = '<ellipse class="f-shadow" cx="126" cy="276" rx="58" ry="7" fill="rgba(13,43,62,.15)"/>';
    if (pose === 'ride') {
      return '<svg viewBox="0 0 252 290" class="fox">' + defs + shadow +
        '<g class="f-lines" stroke="#9fb0bf" stroke-width="4" stroke-linecap="round"><path d="M2 196 H26"/><path d="M8 222 H24"/><path d="M0 248 H18"/></g>' +
        '<g class="f-ride">' + rideLeg('f-legb') + '<g class="f-up" transform="translate(-6 -16) rotate(10 126 220)">' + tail() + torso() + sleeve(97) + sleeve(155) +
        arm('M155 168 Q182 166 196 190', 'ra') + paw(196, 192) + '</g>' + arm('M97 170 Q130 190 172 194', 'ra') + paw(174, 194) + '</g>' + head() + '</g>' + bike() + rideLeg('f-legf') + '</g></svg>';
    }
    return '<svg viewBox="0 0 252 290" class="fox">' + defs + shadow + '<g class="f-body">' + tail() + legs() + torso() + sleeve(97) + sleeve(155) + arms() + head() + '</g>' + extras(id) + '</svg>';
  }
  window.Fox = { svg: svg };
})();
