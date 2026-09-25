// Geräteerkennung und Querformat-Zwang.
// - Erkennt Handy/Tablet gegenüber Laptop/PC (auch Touch-Laptops zählen als PC).
// - Handys laufen immer im Querformat: wo möglich per Vollbild + Orientierungs-
//   sperre (Android), sonst wird die ganze Oberfläche um 90° gedreht (z.B.
//   iPhone oder aktivierte Rotationssperre).
// - toLocal() rechnet Bildschirm-Koordinaten (Touch/Maus) in die Koordinaten
//   der (ggf. gedrehten) Oberfläche um.

export const device = { mobile: false, phone: false, rotated: false, landscape: true, kind: 'pc' };

function detect() {
  const ua = navigator.userAgent || '';
  const uaMobile = !!(navigator.userAgentData && navigator.userAgentData.mobile) ||
    /Android|iPhone|iPad|iPod|Mobile|Silk|Kindle|Opera Mini|IEMobile/i.test(ua) ||
    (/Macintosh/.test(ua) && (navigator.maxTouchPoints || 0) > 1);           // iPadOS meldet sich als Mac
  const mq = (q) => !!(window.matchMedia && window.matchMedia(q).matches);
  const touchOnly = mq('(pointer: coarse)') && mq('(hover: none)');         // Finger ist das Hauptgerät
  const touch = (navigator.maxTouchPoints || 0) > 0 || 'ontouchstart' in window;
  const shortSide = Math.min(screen.width || innerWidth, screen.height || innerHeight, Math.max(innerWidth, innerHeight));
  const mobile = uaMobile || touchOnly || (touch && Math.min(innerWidth, innerHeight) < 600);
  const phone = mobile && (shortSide < 600 || /iPhone|iPod|Android.*Mobile/i.test(ua));
  return { mobile, phone };
}

// Klassen setzen und bei Bedarf die Oberfläche ins Querformat drehen
export function applyDevice() {
  const { mobile, phone } = detect();
  const portraitViewport = innerHeight > innerWidth;
  const rotated = phone && portraitViewport;
  Object.assign(device, { mobile, phone, rotated, landscape: !portraitViewport || rotated, kind: phone ? 'phone' : mobile ? 'tablet' : 'pc' });
  const root = document.documentElement;
  root.classList.toggle('mobile', mobile);
  root.classList.toggle('rotated', rotated);
  root.classList.toggle('landscape', device.landscape);
  root.classList.toggle('portrait', !device.landscape);
  root.classList.toggle('compact', Math.min(innerWidth, innerHeight) < 520);
  // vw/vh-Ersatz für das CSS: bezieht sich auf die Spielfläche, nicht den Bildschirm
  const appW = rotated ? innerHeight : innerWidth, appH = rotated ? innerWidth : innerHeight;
  root.style.setProperty('--vw', appW / 100 + 'px');
  root.style.setProperty('--vh', appH / 100 + 'px');
  const app = document.getElementById('app');
  if (app) {
    // gedreht: Breite = Bildschirmhöhe, Höhe = Bildschirmbreite (exakte Pixel,
    // damit ein- und ausblendende Browserleisten berücksichtigt werden)
    app.style.width = rotated ? innerHeight + 'px' : '';
    app.style.height = rotated ? innerWidth + 'px' : '';
  }
  return device;
}

// Beim ersten Antippen auf dem Handy: Vollbild + Querformat sperren (wo erlaubt)
export function lockLandscapeOnFirstTap() {
  if (!device.phone) return;
  const go = async () => {
    document.removeEventListener('click', go, true);
    try {
      const el = document.documentElement;
      const fs = el.requestFullscreen || el.webkitRequestFullscreen;
      if (fs && !(document.fullscreenElement || document.webkitFullscreenElement)) await fs.call(el, { navigationUI: 'hide' });
      if (screen.orientation && screen.orientation.lock) await screen.orientation.lock('landscape');
    } catch (_) { /* nicht unterstützt (z.B. iPhone) -> Oberfläche bleibt per CSS gedreht */ }
  };
  document.addEventListener('click', go, true);
}

// Bildschirm-Koordinaten (clientX/Y) -> Koordinaten innerhalb eines Elements
export function toLocal(el, clientX, clientY) {
  let x = clientX, y = clientY;
  if (device.rotated) { x = clientY; y = innerWidth - clientX; }   // 90° im Uhrzeigersinn gedreht
  let ox = 0, oy = 0;
  for (let n = el; n && n.id !== 'app'; n = n.offsetParent) { ox += n.offsetLeft || 0; oy += n.offsetTop || 0; }
  return { x: x - ox, y: y - oy };
}
