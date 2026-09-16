# Geparkte Zeichenschicht

Diese Dateien sind **nicht Teil des Builds**. Sie stehen auf der Zeichen-API,
die es bis Minecraft 1.21 gab und die in 26.2 ersetzt wurde.

Nichts davon ist verloren - der Code ist vollstaendig und war gegen die alte
API uebersetzbar. Er muss auf das neue Modell umgeschrieben werden.

## Was sich geaendert hat

Bis 1.21 hiess es "zeichne jetzt": man bekam ein `GuiGraphics` und rief
darauf `fill()` und `drawString()`. Ab 26.2 heisst es "reiche ein, was zu
zeichnen ist": die Oberflaeche meldet Render-Knoten an einen Sammler, und
gezeichnet wird spaeter in einem eigenen Durchgang.

Ermittelt mit `probe-api.py` und `probe-search.py` gegen die echten JARs:

| frueher | in 26.2 |
| --- | --- |
| `Renderable.render(GuiGraphics, int, int, float)` | `Renderable.extractRenderState(GuiGraphicsExtractor, int, int, float)` |
| `net.minecraft.client.gui.GuiGraphics` | `net.minecraft.client.gui.GuiGraphicsExtractor` |
| `MultiBufferSource` | `net.minecraft.client.renderer.OrderedSubmitNodeCollector` |
| `WorldRenderEvents` / `WorldRenderContext` | `FabricOrderedSubmitNodeCollector`, `SubmitRenderPhase`, `SubmitRenderPhases` |
| `HudRenderCallback` | kein direkter Ersatz gefunden |
| `keyPressed(int, int, int)` | `keyPressed(KeyEvent)` |
| `charTyped(char, int)` | `charTyped(CharacterEvent)` |
| `mouseClicked(double, double, int)` | `mouseClicked(MouseButtonEvent, boolean)` |
| `mouseReleased(double, double, int)` | `mouseReleased(MouseButtonEvent)` |
| `mouseDragged(double, double, int, double, double)` | `mouseDragged(MouseButtonEvent, double, double)` |
| `mouseScrolled(double, double, double, double)` | unveraendert |

Die Fabric-Gegenstuecke liegen in
`net.fabricmc.fabric.api.client.rendering.v1` und
`net.fabricmc.fabric.api.client.renderer.v1.render`.

## Was hier liegt

| Datei | Aufgabe |
| --- | --- |
| `gui/ClickGuiScreen.java` | Das Fenster: Kategorien, Modulkarten, Suche, Tastenbelegung |
| `gui/BlockListScreen.java` | Blockliste von X-Ray mit Registry-Suche |
| `gui/Row.java` | Eine Zeile der Liste |
| `hud/HudRenderer.java` | Wasserzeichen und Modulliste |
| `util/Render2D.java` | Runde Rechtecke, Verlaeufe, Text - nur auf fill() und fillGradient() gebaut |
| `util/Render3D.java` | Linienkaesten und Tracers in der Welt |
| `util/Theme.java`, `util/ColorUtil.java`, `util/Anim.java` | Farben und weiche Uebergaenge - haengen an keiner Minecraft-API |
| `module/render/StorageEsp.java` | Kisten durch Waende |
| `module/render/EntityEsp.java`, `Tracers.java`, `EntityGroups.java` | Kaesten und Linien zu Entities |
| `module/misc/ClickGuiModule.java` | Oeffnet das Fenster |
| `module/player/AutoRespawn.java` | Braucht den Totenbildschirm - `Minecraft.screen` gibt es nicht mehr zum Lesen, und `LocalPlayer.respawn()` auch nicht. Der Weg fuehrt ueber `player.connection` und ein `ServerboundClientCommandPacket`, das ist aber noch nicht geprueft. |

`Theme`, `ColorUtil` und `Anim` benutzen ueberhaupt keine Minecraft-Klassen.
Die koennen unveraendert zurueckwandern.
