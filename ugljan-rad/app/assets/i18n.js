/* Velora: Sprachen. Die App ist auf Deutsch geschrieben; dieses Modul uebersetzt sichtbare Texte
   (Textknoten, Platzhalter, Titel) laufend in die gewaehlte Sprache. Soul antwortet direkt in der Sprache. */
(function () {
  'use strict';
  var LANGS = [['de', 'Deutsch', 'DE'], ['en', 'English', 'GB'], ['fr', 'Français', 'FR'], ['it', 'Italiano', 'IT'], ['es', 'Español', 'ES']];
  // [Deutsch, Englisch, Franzoesisch, Italienisch, Spanisch]
  var T = [
    ['Ort, Strand, Restaurant, Straße', 'Place, beach, restaurant, street', 'Lieu, plage, restaurant, rue', 'Luogo, spiaggia, ristorante, via', 'Lugar, playa, restaurante, calle'],
    ['Essen', 'Food', 'Manger', 'Cibo', 'Comida'], ['Cafe und Bar', 'Café and bar', 'Café et bar', 'Caffè e bar', 'Café y bar'], ['Strände', 'Beaches', 'Plages', 'Spiagge', 'Playas'],
    ['Aussicht', 'Viewpoints', 'Points de vue', 'Panorami', 'Miradores'], ['Wasser und WC', 'Water and WC', 'Eau et WC', 'Acqua e WC', 'Agua y WC'], ['Einkaufen', 'Shopping', 'Courses', 'Negozi', 'Compras'],
    ['Rad', 'Bike', 'Vélo', 'Bici', 'Bici'], ['Unterkunft', 'Stay', 'Hébergement', 'Alloggio', 'Alojamiento'], ['Fähre, Hafen, Bus', 'Ferry, port, bus', 'Ferry, port, bus', 'Traghetto, porto, bus', 'Ferry, puerto, bus'],
    ['Soul', 'Soul', 'Soul', 'Soul', 'Soul'], ['Tour-Idee', 'Tour idea', 'Idée de tour', 'Idea giro', 'Idea de ruta'], ['In der Nähe', 'Nearby', 'À proximité', 'Nei dintorni', 'Cerca'], ['Fahrt starten', 'Start ride', 'Démarrer', 'Inizia giro', 'Empezar'],
    ['Essen in der Nähe', 'Food nearby', 'Manger à proximité', 'Cibo nei dintorni', 'Comida cerca'], ['Kartenlegende', 'Map legend', 'Légende', 'Legenda', 'Leyenda'], ['Legende', 'Legend', 'Légende', 'Legenda', 'Leyenda'],
    ['Radweg', 'Cycle path', 'Piste cyclable', 'Ciclabile', 'Carril bici'], ['Radroute (markiert)', 'Signed bike route', 'Itinéraire balisé', 'Percorso segnalato', 'Ruta señalizada'], ['Ruhige Straße', 'Quiet road', 'Route calme', 'Strada tranquilla', 'Calle tranquila'],
    ['Landstraße', 'Country road', 'Route de campagne', 'Strada provinciale', 'Carretera'], ['Feldweg', 'Farm track', 'Chemin de terre', 'Sterrato', 'Camino de tierra'], ['Grober Weg', 'Rough track', 'Chemin difficile', 'Sentiero sconnesso', 'Camino abrupto'],
    ['Pfad', 'Trail', 'Sentier', 'Sentiero', 'Sendero'], ['Hauptstraße', 'Main road', 'Route principale', 'Strada principale', 'Carretera principal'], ['Fußweg', 'Footpath', 'Chemin piéton', 'Pedonale', 'Peatonal'],
    ['Einstellungen', 'Settings', 'Réglages', 'Impostazioni', 'Ajustes'], ['Dein Fahrrad', 'Your bike', 'Ton vélo', 'La tua bici', 'Tu bici'], ['Fitness', 'Fitness', 'Forme', 'Forma', 'Forma'],
    ['Gemütlich', 'Relaxed', 'Tranquille', 'Tranquillo', 'Tranquilo'], ['Normal', 'Normal', 'Normal', 'Normale', 'Normal'], ['Sportlich', 'Sporty', 'Sportif', 'Sportivo', 'Deportivo'],
    ['Routenstil', 'Route style', 'Style d\'itinéraire', 'Stile percorso', 'Estilo de ruta'], ['Schnell & leicht', 'Fast & easy', 'Rapide et facile', 'Veloce e facile', 'Rápida y fácil'], ['Am schönsten', 'Most beautiful', 'Le plus beau', 'Il più bello', 'La más bonita'],
    ['City / Trekking', 'City / Trekking', 'Ville / Trekking', 'City / Trekking', 'Ciudad / Trekking'], ['Rennrad', 'Road bike', 'Vélo de route', 'Bici da corsa', 'Bici de carretera'], ['Mountainbike', 'Mountain bike', 'VTT', 'Mountain bike', 'Bici de montaña'],
    ['E-Bike', 'E-bike', 'VAE', 'E-bike', 'Bici eléctrica'], ['Gravel', 'Gravel', 'Gravel', 'Gravel', 'Gravel'], ['Familie / Kinder', 'Family / Kids', 'Famille / Enfants', 'Famiglia / Bambini', 'Familia / Niños'],
    ['Navigation', 'Navigation', 'Navigation', 'Navigazione', 'Navegación'], ['Sprachansagen', 'Voice guidance', 'Guidage vocal', 'Guida vocale', 'Guía por voz'], ['Wind einrechnen', 'Include wind', 'Tenir compte du vent', 'Considera il vento', 'Tener en cuenta el viento'],
    ['Karte', 'Map', 'Carte', 'Mappa', 'Mapa'], ['Relief (Berge schattiert)', 'Relief shading', 'Relief ombré', 'Rilievo ombreggiato', 'Relieve sombreado'], ['Höhenlinien (alle 25 m)', 'Contour lines (25 m)', 'Courbes de niveau (25 m)', 'Curve di livello (25 m)', 'Curvas de nivel (25 m)'],
    ['Radrouten hervorheben', 'Highlight bike routes', 'Itinéraires vélo en évidence', 'Evidenzia percorsi bici', 'Resaltar rutas ciclistas'], ['Ortsnamen', 'Place names', 'Noms de lieux', 'Nomi dei luoghi', 'Nombres de lugares'],
    ['Meine Fahrten', 'My rides', 'Mes sorties', 'I miei giri', 'Mis rutas'], ['Freie Fahrt starten', 'Start free ride', 'Sortie libre', 'Giro libero', 'Ruta libre'], ['Über die App', 'About', 'À propos', 'Info sull\'app', 'Acerca de'],
    ['Vibration', 'Vibration', 'Vibration', 'Vibrazione', 'Vibración'], ['Aus', 'Off', 'Désactivé', 'Spento', 'Apagado'], ['Leicht', 'Light', 'Léger', 'Leggera', 'Suave'], ['Stark', 'Strong', 'Fort', 'Forte', 'Fuerte'],
    ['Nachtmodus', 'Night mode', 'Mode nuit', 'Modalità notte', 'Modo noche'], ['Automatisch', 'Automatic', 'Automatique', 'Automatico', 'Automático'], ['An', 'On', 'Activé', 'Acceso', 'Activado'],
    ['Farbe', 'Colour', 'Couleur', 'Colore', 'Color'], ['Navigationspfeil', 'Navigation arrow', 'Flèche de navigation', 'Freccia di navigazione', 'Flecha de navegación'],
    ['Statistik', 'Statistics', 'Statistiques', 'Statistiche', 'Estadísticas'], ['Meine Routen', 'My routes', 'Mes itinéraires', 'I miei percorsi', 'Mis rutas'], ['Tagesplaner', 'Day planner', 'Planificateur', 'Pianificatore', 'Planificador'], ['Offline-Karten', 'Offline maps', 'Cartes hors ligne', 'Mappe offline', 'Mapas sin conexión'],
    ['Strecke', 'Distance', 'Distance', 'Distanza', 'Distancia'], ['Fahrzeit', 'Ride time', 'Durée', 'Durata', 'Tiempo'], ['Ankunft', 'Arrival', 'Arrivée', 'Arrivo', 'Llegada'], ['Bergauf', 'Uphill', 'Montée', 'Salita', 'Subida'],
    ['Bergab', 'Downhill', 'Descente', 'Discesa', 'Bajada'], ['Max. Steigung', 'Max. gradient', 'Pente max.', 'Pendenza max.', 'Pendiente máx.'], ['Schönheit', 'Beauty', 'Beauté', 'Bellezza', 'Belleza'],
    ['Auf Radrouten', 'On bike routes', 'Sur itinéraires vélo', 'Su percorsi bici', 'En rutas ciclistas'], ['Radwege und ruhig', 'Cycle paths & quiet', 'Pistes et calme', 'Ciclabili e tranquille', 'Carriles y tranquilo'],
    ['Abbrechen', 'Cancel', 'Annuler', 'Annulla', 'Cancelar'], ['Los fahren', 'Let\'s go', 'C\'est parti', 'Partiamo', 'Vamos'], ['Zwischenziel', 'Add stop', 'Étape', 'Tappa', 'Parada'], ['Umkehren', 'Reverse', 'Inverser', 'Inverti', 'Invertir'],
    ['Ohne Zwischenziele', 'Without stops', 'Sans étapes', 'Senza tappe', 'Sin paradas'], ['Belag', 'Surface', 'Revêtement', 'Fondo', 'Superficie'], ['Wegart', 'Way type', 'Type de voie', 'Tipo di strada', 'Tipo de vía'], ['Anstiege', 'Climbs', 'Montées', 'Salite', 'Subidas'],
    ['Hierhin fahren', 'Ride here', 'Y aller', 'Vai qui', 'Ir aquí'], ['Speichern', 'Save', 'Enregistrer', 'Salva', 'Guardar'], ['Noch', 'Left', 'Reste', 'Restano', 'Quedan'], ['Restzeit', 'Time left', 'Temps restant', 'Tempo restante', 'Tiempo restante'],
    ['Höhe', 'Altitude', 'Altitude', 'Altitudine', 'Altitud'], ['Noch hoch', 'Climb left', 'Montée restante', 'Salita restante', 'Subida restante'], ['km gefahren', 'km ridden', 'km parcourus', 'km percorsi', 'km recorridos'], ['Geklettert', 'Climbed', 'Grimpé', 'Saliti', 'Subidos'],
    ['Navigation beenden', 'End navigation', 'Arrêter', 'Termina navigazione', 'Terminar navegación'], ['Fahrt beenden und speichern', 'End and save ride', 'Terminer et enregistrer', 'Termina e salva', 'Terminar y guardar'],
    ['Live-Standort teilen', 'Share live location', 'Partager ma position', 'Condividi posizione', 'Compartir ubicación'], ['Tour vorschlagen', 'Suggest a tour', 'Proposer un tour', 'Proponi un giro', 'Sugerir ruta'],
    ['Dauer', 'Duration', 'Durée', 'Durata', 'Duración'], ['Schwierigkeit', 'Difficulty', 'Difficulté', 'Difficoltà', 'Dificultad'], ['Mittel', 'Medium', 'Moyen', 'Media', 'Media'], ['Schwer', 'Hard', 'Difficile', 'Difficile', 'Difícil'],
    ['Egal', 'Any', 'Peu importe', 'Qualsiasi', 'Da igual'], ['Schön', 'Nice', 'Beau', 'Bello', 'Bonita'], ['Sehr schön', 'Very nice', 'Très beau', 'Bellissimo', 'Muy bonita'], ['Rückenwind auf dem Heimweg', 'Tailwind on the way home', 'Vent arrière au retour', 'Vento a favore al ritorno', 'Viento a favor de vuelta'],
    ['Deine Fahrten', 'Your rides', 'Tes sorties', 'I tuoi giri', 'Tus rutas'], ['Diese Woche', 'This week', 'Cette semaine', 'Questa settimana', 'Esta semana'], ['Wochenrückblick', 'Weekly review', 'Bilan de la semaine', 'Riepilogo settimanale', 'Resumen semanal'],
    ['Zurück', 'Back', 'Retour', 'Indietro', 'Atrás'], ['Schließen', 'Close', 'Fermer', 'Chiudi', 'Cerrar'], ['Weiter', 'Continue', 'Continuer', 'Continua', 'Continuar'], ['Löschen', 'Delete', 'Supprimer', 'Elimina', 'Eliminar'], ['Kopieren', 'Copy', 'Copier', 'Copia', 'Copiar'],
    ['Frag Soul ...', 'Ask Soul ...', 'Demande à Soul ...', 'Chiedi a Soul ...', 'Pregunta a Soul ...'], ['Neu', 'New', 'Nouveau', 'Nuovo', 'Nuevo'], ['Deine Rad-KI, direkt in der App. Plant Routen, kennt Wetter und Orte, wertet deine Fahrten aus.', 'Your cycling AI, right in the app. Plans routes, knows weather and places, analyses your rides.', 'Ton IA vélo, directement dans l\'app. Itinéraires, météo, lieux et analyse de tes sorties.', 'La tua IA per la bici, direttamente nell\'app. Percorsi, meteo, luoghi e analisi dei tuoi giri.', 'Tu IA ciclista, en la app. Rutas, tiempo, lugares y análisis de tus salidas.'],
    ['Soul kann jetzt richtig sprechen', 'Soul can now really talk', 'Soul sait maintenant vraiment parler', 'Ora Soul sa parlare davvero', 'Soul ya sabe hablar de verdad'],
    ['Lade einmalig ein echtes KI-Sprachmodell. Danach versteht Soul jede Frage, plaudert natürlich und läuft direkt auf deinem Gerät: ohne Internet, privat und kostenlos. WLAN empfohlen.', 'Download a real AI language model once. Then Soul understands any question, chats naturally and runs right on your device: offline, private and free. Wi-Fi recommended.', 'Télécharge une seule fois un vrai modèle de langage. Soul comprend alors chaque question, discute naturellement et fonctionne sur ton appareil : hors ligne, privé et gratuit. Wi-Fi conseillé.', 'Scarica una volta un vero modello linguistico. Poi Soul capisce ogni domanda, chiacchiera in modo naturale e funziona sul tuo dispositivo: offline, privato e gratis. Consigliato il Wi-Fi.', 'Descarga una vez un modelo de lenguaje real. Después Soul entiende cada pregunta, conversa con naturalidad y funciona en tu dispositivo: sin internet, privado y gratis. Se recomienda Wi-Fi.'],
    ['Später', 'Later', 'Plus tard', 'Più tardi', 'Más tarde'], ['Laden', 'Download', 'Télécharger', 'Scarica', 'Descargar'], ['Fortsetzen', 'Resume', 'Reprendre', 'Riprendi', 'Reanudar'], ['Pausieren', 'Pause', 'Pause', 'Pausa', 'Pausar'],
    ['Empfohlen', 'Recommended', 'Recommandé', 'Consigliato', 'Recomendado'], ['Benutzen', 'Use', 'Utiliser', 'Usa', 'Usar'], ['Löschen', 'Delete', 'Supprimer', 'Elimina', 'Eliminar'], ['Verwalten', 'Manage', 'Gérer', 'Gestisci', 'Gestionar'], ['Fertig', 'Done', 'Terminé', 'Fatto', 'Listo'],
    ['Sprachmodell verwalten', 'Manage language model', 'Gérer le modèle', 'Gestisci modello', 'Gestionar modelo'], ['Echtes Sprachmodell laden', 'Get the real language model', 'Télécharger le vrai modèle', 'Scarica il vero modello', 'Descargar el modelo real'],
    ['Sprachmodell aktiv, offline auf deinem Gerät', 'Language model active, offline on your device', 'Modèle actif, hors ligne sur ton appareil', 'Modello attivo, offline sul tuo dispositivo', 'Modelo activo, sin conexión en tu dispositivo'],
    ['wacht auf ...', 'waking up ...', 'se réveille ...', 'si sveglia ...', 'despertando ...'], ['bereit', 'ready', 'prêt', 'pronto', 'listo'], ['konnte nicht starten', 'could not start', 'n\'a pas pu démarrer', 'non è partito', 'no pudo iniciar'], ['Wird geprüft', 'Checking', 'Vérification', 'Verifica', 'Comprobando'],
    ['Schnell, läuft auf jedem Handy. 1,1 GB', 'Fast, runs on any phone. 1.1 GB', 'Rapide, fonctionne sur tout téléphone. 1,1 Go', 'Veloce, funziona su ogni telefono. 1,1 GB', 'Rápido, funciona en cualquier móvil. 1,1 GB'],
    ['Klüger und natürlicher, braucht ein starkes Gerät. 2,5 GB', 'Smarter and more natural, needs a strong device. 2.5 GB', 'Plus intelligent et naturel, demande un appareil puissant. 2,5 Go', 'Più intelligente e naturale, serve un dispositivo potente. 2,5 GB', 'Más listo y natural, necesita un dispositivo potente. 2,5 GB'],
    ['Offline-Modus', 'Offline mode', 'Mode hors ligne', 'Modalità offline', 'Modo sin conexión'], ['Schließen', 'Close', 'Fermer', 'Chiudi', 'Cerrar'],
    ['Soul antwortet normalerweise über das Internet. Für unterwegs ohne Netz kannst du zusätzlich ein Sprachmodell auf dein Gerät laden. Das braucht viel Speicher und ein starkes Gerät. WLAN empfohlen.', 'Soul normally answers over the internet. For rides without signal you can also download a language model to your device. It needs a lot of storage and a strong device. Wi-Fi recommended.', 'Soul répond normalement via internet. Pour rouler sans réseau, tu peux aussi télécharger un modèle sur ton appareil. Il faut beaucoup d\'espace et un appareil puissant. Wi-Fi conseillé.', 'Soul di solito risponde via internet. Per i giri senza rete puoi scaricare anche un modello sul dispositivo. Serve molto spazio e un dispositivo potente. Consigliato il Wi-Fi.', 'Soul normalmente responde por internet. Para salir sin cobertura puedes descargar también un modelo en tu dispositivo. Necesita mucho espacio y un dispositivo potente. Se recomienda Wi-Fi.'],
    ['Soul denkt nach', 'Soul is thinking', 'Soul réfléchit', 'Soul sta pensando', 'Soul está pensando'], ['Schaut sich die Gegend an', 'Looking at the area', 'Regarde les environs', 'Guarda la zona', 'Mira la zona'], ['Prüft echte Orte', 'Checking real places', 'Vérifie les lieux', 'Controlla i luoghi', 'Comprueba lugares'], ['Gleich fertig', 'Almost done', 'Presque fini', 'Quasi fatto', 'Casi listo'],
    ['Mehr mit Plus', 'More with Plus', 'Plus avec Plus', 'Di più con Plus', 'Más con Plus'], ['Plus freischalten', 'Unlock Plus', 'Débloquer Plus', 'Sblocca Plus', 'Desbloquear Plus'], ['pro Monat', 'per month', 'par mois', 'al mese', 'al mes'], ['pro Jahr', 'per year', 'par an', 'all\'anno', 'al año'],
    ['Wie willst du fahren?', 'How do you want to ride?', 'Comment veux-tu rouler ?', 'Come vuoi pedalare?', '¿Cómo quieres ir?'], ['Konto', 'Account', 'Compte', 'Account', 'Cuenta'], ['Sprache', 'Language', 'Langue', 'Lingua', 'Idioma'], ['Land', 'Country', 'Pays', 'Paese', 'País'],
    ['Hallo, ich bin Velo!', 'Hi, I am Velo!', 'Salut, je suis Velo !', 'Ciao, sono Velo!', '¡Hola, soy Velo!'],
    ['Starte deine erste Fahrt. Ich zähle mit und feiere jede Serie mit dir.', 'Start your first ride. I keep count and celebrate every streak with you.', 'Lance ta première sortie. Je compte et fête chaque série avec toi.', 'Inizia il tuo primo giro. Conto e festeggio ogni serie con te.', 'Empieza tu primera ruta. Cuento y celebro cada racha contigo.'],
    ['Warte auf GPS. Suche ein Ziel oder tippe auf die Karte.', 'Waiting for GPS. Search a destination or tap the map.', 'En attente du GPS. Cherche une destination ou touche la carte.', 'In attesa del GPS. Cerca una meta o tocca la mappa.', 'Esperando GPS. Busca un destino o toca el mapa.'],
    ['Lust auf eine Runde?', 'Fancy a ride?', 'Envie d\'un tour ?', 'Voglia di un giro?', '¿Te apetece una vuelta?'], ['Guten Morgen!', 'Good morning!', 'Bonjour !', 'Buongiorno!', '¡Buenos días!'], ['Guten Abend!', 'Good evening!', 'Bonsoir !', 'Buonasera!', '¡Buenas tardes!'], ['Gute Nacht!', 'Good night!', 'Bonne nuit !', 'Buona notte!', '¡Buenas noches!'],
    ['Noch nichts geladen. Mit GPS und Internet erscheinen hier Restaurants und Cafés in deiner Nähe.', 'Nothing loaded yet. With GPS and internet, restaurants and cafés near you appear here.', 'Rien encore. Avec GPS et internet, les restaurants et cafés proches apparaissent ici.', 'Ancora niente. Con GPS e internet qui compaiono ristoranti e caffè vicini.', 'Aún nada. Con GPS e internet aparecen aquí restaurantes y cafés cercanos.'],
    ['Abmelden', 'Log out', 'Se déconnecter', 'Esci', 'Cerrar sesión'], ['Konto löschen', 'Delete account', 'Supprimer le compte', 'Elimina account', 'Eliminar cuenta'], ['Anonyme Nutzungsdaten teilen', 'Share anonymous usage data', 'Partager des données anonymes', 'Condividi dati anonimi', 'Compartir datos anónimos']
  ];
  var IDX = { de: 0, en: 1, fr: 2, it: 3, es: 4 }, MAP = {}, lang = 'de';
  function build() { MAP = {}; var k = IDX[lang] || 0; if (!k) return; T.forEach(function (r) { MAP[r[0]] = r[k]; }); }
  function t(s) { return lang === 'de' ? s : MAP[s] || s; }
  function walk(root) {
    if (lang === 'de' || !root) return;
    var w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null), n;
    while ((n = w.nextNode())) {
      var v = n.nodeValue, s = v.trim(); if (!s || s.length > 120) continue;
      var r = MAP[s]; if (r && r !== s) n.nodeValue = v.replace(s, r);
    }
    if (root.querySelectorAll) root.querySelectorAll('[placeholder],[title]').forEach(function (e) {
      ['placeholder', 'title'].forEach(function (a) { var x = e.getAttribute(a); if (x && MAP[x]) e.setAttribute(a, MAP[x]); });
    });
  }
  var obs = null;
  function set(l) {
    lang = IDX[l] != null ? l : 'de'; build();
    document.documentElement.lang = lang;
    if (!obs && window.MutationObserver) {
      obs = new MutationObserver(function (ms) { if (lang === 'de') return; ms.forEach(function (m) { m.addedNodes.forEach(function (n) { if (n.nodeType === 1) walk(n); else if (n.nodeType === 3 && n.parentNode) walk(n.parentNode); }); }); });
      obs.observe(document.body, { childList: true, subtree: true });
    }
    walk(document.body);
  }
  window.I18N = { LANGS: LANGS, set: set, t: t, get lang() { return lang; } };
})();
