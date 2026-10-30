# KI-Server für Ugljan Rad (Cloudflare Workers AI)

Kostenlos im Rahmen des Cloudflare-Gratis-Kontingents, ohne fremden API-Key.

## Einrichten (einmalig, am PC)

1. Konto anlegen: https://dash.cloudflare.com/sign-up (kostenlos)
2. Node.js installieren: https://nodejs.org (LTS-Version)
3. In diesem Ordner im Terminal:
   ```
   npx wrangler login
   npx wrangler deploy
   ```
4. Wrangler zeigt am Ende die Adresse, z. B. `https://ugljan-rad-ki.DEINNAME.workers.dev`

## Testen

```
curl -X POST https://ugljan-rad-ki.DEINNAME.workers.dev/ki \
  -H "Content-Type: application/json" -H "X-App-Id: ugljan-rad-2026" \
  -d '{"frage":"Was kann man in Kukljica am Abend machen?"}'
```

## Kosten im Blick

Im Cloudflare-Dashboard unter "AI" > "Workers AI" siehst du den Verbrauch.
Das Gratis-Kontingent wird täglich zurückgesetzt; ist es aufgebraucht, antwortet der Server
mit "KI gerade nicht verfügbar", es entstehen keine Kosten (solange du keinen bezahlten Tarif buchst).
