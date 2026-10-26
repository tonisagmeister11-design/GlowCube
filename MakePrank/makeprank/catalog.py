"""Central effect registry: names, parameters, limits and UI metadata.

Every effect MakePrank can run is declared here and nowhere else. The package
validator, the Creator UI, the consent dialog and the network whitelist all read
this registry, so an effect that is not listed cannot be stored, shown or
triggered.
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Any

MAX_ACTIONS = 30
MAX_TEXT_LENGTH = 4000
MAX_TITLE_LENGTH = 120
MAX_ACTION_DURATION_SECONDS = 120
MAX_SESSION_DURATION_SECONDS = 1800
MAX_TONE_MILLISECONDS = 1500
MAX_VOLUME_PERCENT = 40
MAX_CHAT_LINES = 12

COLOR_RE = re.compile(r"^#[0-9a-fA-F]{6}$")
RESOURCE_REF_RE = re.compile(r"^resource:[a-z0-9_\-]{1,40}\.(png|gif)$")

CATEGORIES = (
    "Dialoge",
    "Benachrichtigungen",
    "Attrappen",
    "Bild & Ton",
    "Text & Overlay",
    "Visuelle Effekte",
)


class ParamError(ValueError):
    """Raised when an effect parameter is invalid."""


@dataclass(frozen=True)
class ParamSpec:
    name: str
    kind: str  # "str" | "int" | "bool" | "choice" | "color" | "image"
    default: Any
    label: str
    minimum: int = 0
    maximum: int = 0
    choices: tuple[str, ...] = ()
    max_len: int = 0
    help: str = ""

    def clean(self, value: Any) -> Any:
        """Validate ``value`` and return the normalised value."""
        if self.kind == "bool":
            if not isinstance(value, bool):
                raise ParamError(f"{self.label}: erwartet Ja/Nein.")
            return value
        if self.kind == "int":
            if not isinstance(value, int) or isinstance(value, bool):
                raise ParamError(f"{self.label}: erwartet eine ganze Zahl.")
            if not self.minimum <= value <= self.maximum:
                raise ParamError(f"{self.label}: Wert muss zwischen {self.minimum} und {self.maximum} liegen.")
            return value
        if self.kind == "choice":
            if not isinstance(value, str) or value not in self.choices:
                raise ParamError(f"{self.label}: ungültige Auswahl {value!r}.")
            return value
        if self.kind == "color":
            if not isinstance(value, str) or not COLOR_RE.fullmatch(value):
                raise ParamError(f"{self.label}: erwartet eine Farbe wie #RRGGBB.")
            return value.lower()
        if self.kind == "image":
            if not isinstance(value, str):
                raise ParamError(f"{self.label}: ungültige Bildangabe.")
            if value in BUILTIN_IMAGES or RESOURCE_REF_RE.fullmatch(value):
                return value
            raise ParamError(f"{self.label}: unbekannte Bildquelle {value!r}.")
        if self.kind == "str":
            if not isinstance(value, str) or len(value) > self.max_len:
                raise ParamError(f"{self.label}: Text fehlt oder ist länger als {self.max_len} Zeichen.")
            if _has_control_chars(value, allow_newline=False):
                raise ParamError(f"{self.label}: Steuerzeichen sind nicht erlaubt.")
            return value
        raise ParamError(f"{self.label}: interner Fehler (Typ {self.kind}).")


BUILTIN_IMAGES = ("builtin:ghost", "builtin:eyes", "builtin:clown")


def _has_control_chars(value: str, allow_newline: bool) -> bool:
    for ch in value:
        if ch == "\n" and allow_newline:
            continue
        if ord(ch) < 32 or ord(ch) == 127:
            return True
    return False


def clean_text(value: Any, label: str, max_len: int) -> str:
    """Validate free text (newlines allowed, other control characters rejected)."""
    if not isinstance(value, str) or len(value) > max_len:
        raise ParamError(f"{label}: Text fehlt oder ist länger als {max_len} Zeichen.")
    if _has_control_chars(value, allow_newline=True):
        raise ParamError(f"{label}: Steuerzeichen sind nicht erlaubt.")
    return value


@dataclass(frozen=True)
class EffectSpec:
    kind: str
    label: str
    category: str
    description: str
    risk: str  # "niedrig" | "mittel"
    risk_note: str
    default_title: str
    default_text: str
    params: tuple[ParamSpec, ...] = ()
    always_audio: bool = False
    allows_resource_image: bool = False
    default_duration: int = 8

    def param(self, name: str) -> ParamSpec:
        for spec in self.params:
            if spec.name == name:
                return spec
        raise KeyError(name)

    def default_params(self) -> dict[str, Any]:
        return {spec.name: spec.default for spec in self.params}

    def clean_params(self, raw: Any) -> dict[str, Any]:
        if raw is None:
            raw = {}
        if not isinstance(raw, dict):
            raise ParamError("Parameter müssen ein Objekt sein.")
        unknown = set(raw) - {spec.name for spec in self.params}
        if unknown:
            raise ParamError(f"Unbekannte Parameter: {', '.join(sorted(map(str, unknown)))}.")
        cleaned: dict[str, Any] = {}
        for spec in self.params:
            cleaned[spec.name] = spec.clean(raw[spec.name]) if spec.name in raw else spec.default
        return cleaned


_ICONS = ("error", "warning", "info", "question", "none")
_MSGBOX_VARIANTS = ("ok", "ok_cancel", "yes_no", "yes_no_cancel", "retry_cancel", "abort_retry_ignore")
_TONES = ("beep", "asterisk", "exclamation", "hand", "tv_tone")
_BAR_PRESETS = ("smpte", "hd", "ebu", "pluge", "mono", "rgb", "static")
_POSITIONS = ("top", "center", "bottom")
_CORNERS = ("bottom_right", "top_right", "bottom_left", "top_left", "center")

_ICON_PARAM = ParamSpec("icon", "choice", "error", "Symbol", choices=_ICONS)

_EFFECTS: tuple[EffectSpec, ...] = (
    EffectSpec(
        "error_dialog", "Fehlerdialog", "Dialoge",
        "Zeigt einen Windows-ähnlichen Fehlerdialog im MakePrank-Fenster.",
        "niedrig", "Nur ein Fenster in MakePrank; ein Klick auf die Schaltfläche schließt es.",
        "Fehler", "Ein unerwarteter Fehler ist aufgetreten. (Das ist nur ein Scherz.)",
        params=(_ICON_PARAM, ParamSpec("button_label", "str", "OK", "Schaltflächentext", max_len=24)),
    ),
    EffectSpec(
        "msgbox", "MsgBox", "Dialoge",
        "Dialog mit den klassischen Schaltflächenvarianten (OK, Ja/Nein, Wiederholen/Abbrechen …).",
        "niedrig", "Keine Schaltfläche löst eine Aktion außerhalb von MakePrank aus.",
        "Frage", "Möchtest du fortfahren?",
        params=(
            ParamSpec("variant", "choice", "yes_no", "Variante", choices=_MSGBOX_VARIANTS),
            ParamSpec("icon", "choice", "question", "Symbol", choices=_ICONS),
        ),
    ),
    EffectSpec(
        "toast", "Toast-Benachrichtigung", "Benachrichtigungen",
        "Kleine Benachrichtigung in einem MakePrank-Fenster – keine echte Windows-Benachrichtigung.",
        "niedrig", "Erscheint nur als normales, schließbares Fenster am Bildschirmrand.",
        "Neue Nachricht", "Du hast 1 neue Nachricht.",
        params=(ParamSpec("position", "choice", "bottom_right", "Position", choices=_CORNERS),),
        default_duration=6,
    ),
    EffectSpec(
        "fake_browser", "Browser-Attrappe", "Attrappen",
        "Browserfenster-Attrappe mit frei wählbarer Beispielseite. Öffnet niemals eine echte URL.",
        "niedrig", "Die Adresszeile ist reiner Text; es findet kein Netzwerkzugriff statt.",
        "Beispielseite", "Willkommen auf der Beispielseite!\n\nDies ist nur eine Attrappe.",
        params=(
            ParamSpec("address", "str", "https://beispiel.invalid/", "Angezeigte Adresse", max_len=100),
            ParamSpec("page_title", "str", "Beispielseite", "Seitentitel", max_len=60),
        ),
        default_duration=10,
    ),
    EffectSpec(
        "fake_notepad", "Editor-Attrappe", "Attrappen",
        "Editor-Attrappe mit beliebigem Text. Es wird nie eine echte Datei geschrieben.",
        "niedrig", "Kein Dateizugriff; der Text lebt nur im Fenster.",
        "Editor", "Hallo!\nDieser Text steht nur in der Attrappe.",
        params=(ParamSpec("filename", "str", "Unbenannt.txt", "Angezeigter Dateiname", max_len=40),),
        default_duration=10,
    ),
    EffectSpec(
        "fake_shutdown", "Herunterfahren/Neustart (Simulation)", "Attrappen",
        "Überzeugend aussehende, klar als Simulation gekennzeichnete Szene in einem schließbaren Fenster.",
        "mittel", "Windows wird NIEMALS heruntergefahren oder neu gestartet; das Fenster ist jederzeit schließbar.",
        "Windows wird heruntergefahren", "Bitte warten …",
        params=(
            ParamSpec("mode", "choice", "shutdown", "Modus", choices=("shutdown", "reboot")),
            ParamSpec("countdown", "int", 5, "Countdown (Sekunden)", minimum=1, maximum=30),
        ),
    ),
    EffectSpec(
        "color_bars", "Farbbalken / Testbild", "Visuelle Effekte",
        "Testbild im eigenen Fenster (SMPTE, HD, EBU, PLUGE, Mono, RGB, Rauschen).",
        "niedrig", "Ohne Ton und ohne schnelles Flackern (max. 3 Bildwechsel pro Sekunde).",
        "Testbild", "Bitte nicht verstellen.",
        params=(
            ParamSpec("preset", "choice", "smpte", "Testbild", choices=_BAR_PRESETS),
            ParamSpec("tone", "bool", False, "Testton mit abspielen (Audio!)"),
        ),
    ),
    EffectSpec(
        "screamer", "Screamer-Bild", "Bild & Ton",
        "Kontrastreiches Bild in einem normalen, schließbaren Fenster – kein Vollbild, kein Blitzen.",
        "mittel", "Kann erschrecken. Optionaler Ton ist kurz und leise begrenzt und wird vor dem Start angekündigt.",
        "Überraschung!", "BUH!",
        params=(
            ParamSpec("image", "image", "builtin:ghost", "Bild"),
            ParamSpec("sound", "choice", "none", "Ton (Audio!)", choices=("none",) + _TONES),
        ),
        allows_resource_image=True,
        default_duration=6,
    ),
    EffectSpec(
        "sound_effect", "Soundeffekt", "Bild & Ton",
        "Kurzer System-/TV-ähnlicher Ton (max. 1,5 s, Lautstärke gedeckelt) mit Vorschau und Stummschalter.",
        "mittel", "Audio. Wird vor dem Start angezeigt; der Empfänger kann global stummschalten.",
        "Ton", "Ein kurzer Ton wird abgespielt.",
        params=(
            ParamSpec("tone", "choice", "beep", "Ton", choices=_TONES),
            ParamSpec("volume", "int", 25, "Lautstärke (%)", minimum=1, maximum=MAX_VOLUME_PERCENT),
            ParamSpec("milliseconds", "int", 600, "Länge (ms)", minimum=100, maximum=MAX_TONE_MILLISECONDS),
        ),
        always_audio=True,
        default_duration=4,
    ),
    EffectSpec(
        "window_shake", "Fensterwackeln", "Visuelle Effekte",
        "Das MakePrank-eigene Fenster wackelt kurz. Kein anderes Fenster und nicht der Desktop.",
        "niedrig", "Kleine Bewegung, höchstens 12 Pixel.",
        "Erdbeben!", "Das Fenster wackelt …",
        params=(ParamSpec("intensity", "int", 4, "Stärke", minimum=1, maximum=10),),
        default_duration=5,
    ),
    EffectSpec(
        "text_overlay", "Text-Overlay", "Text & Overlay",
        "Großer Text in einem eigenen MakePrank-Fenster (Farbe, Größe, Position).",
        "niedrig", "Liegt nie über fremden Apps als Overlay, sondern ist ein normales Fenster.",
        "Hinweis", "Hallo Welt!",
        params=(
            ParamSpec("color", "color", "#ffffff", "Textfarbe"),
            ParamSpec("background", "color", "#111827", "Hintergrund"),
            ParamSpec("font_size", "int", 36, "Schriftgröße", minimum=10, maximum=96),
            ParamSpec("position", "choice", "center", "Position", choices=_POSITIONS),
        ),
    ),
    EffectSpec(
        "console_overlay", "Konsole (Attrappe)", "Text & Overlay",
        "Animierte Terminal-Attrappe. Es werden niemals echte Befehle ausgeführt.",
        "niedrig", "Reiner Text; keine Shell.",
        "Terminal", "Initialisiere …\nLade Module …\nFertig.",
        params=(
            ParamSpec("prompt", "str", "C:\\>", "Eingabeaufforderung", max_len=20),
            ParamSpec("line_delay_ms", "int", 300, "Pause pro Zeile (ms)", minimum=50, maximum=1500),
        ),
    ),
    EffectSpec(
        "visual_effect", "Invertieren / Spiegeln", "Visuelle Effekte",
        "Invertierte oder gespiegelte Darstellung – nur auf der Zeichenfläche im eigenen Fenster.",
        "niedrig", "Wechselt höchstens alle 1,5 Sekunden; der Desktop bleibt unverändert.",
        "Visueller Effekt", "Alles steht Kopf.",
        params=(ParamSpec("mode", "choice", "invert", "Modus", choices=("invert", "flip_h", "flip_v")),),
    ),
    EffectSpec(
        "freeze_simulation", "Minimieren/Einfrieren (Simulation)", "Attrappen",
        "Zeigt in einem Mini-Desktop innerhalb des Fensters ein minimiertes oder „eingefrorenes“ Programm.",
        "niedrig", "Blockiert nichts: andere Fenster werden nicht angefasst, MakePrank bleibt bedienbar.",
        "Keine Rückmeldung", "Programm reagiert nicht …",
        params=(ParamSpec("mode", "choice", "freeze", "Modus", choices=("freeze", "minimize")),),
    ),
    EffectSpec(
        "wallpaper_preview", "Wallpaper-Vorschau", "Bild & Ton",
        "Vorschau eines Hintergrunds auf einem Mini-Desktop. Windows-Einstellungen bleiben unverändert.",
        "niedrig", "Keine Systemänderung.",
        "Neuer Hintergrund", "So würde dein Desktop aussehen.",
        params=(
            ParamSpec("color_top", "color", "#1d4ed8", "Farbe oben"),
            ParamSpec("color_bottom", "color", "#7c3aed", "Farbe unten"),
        ),
    ),
    EffectSpec(
        "chat_message", "Chat-Nachricht", "Text & Overlay",
        "Zeigt Chat-Nachrichten in einem MakePrank-Fenster. Schreibt nie in fremde Apps.",
        "niedrig", "Nur Anzeige; keine Eingabe in andere Programme.",
        "Chat", "Hey, bist du da?\nIch habe eine Überraschung für dich.",
        params=(ParamSpec("sender", "str", "Unbekannt", "Absender", max_len=30),),
        default_duration=10,
    ),
)

EFFECTS: dict[str, EffectSpec] = {spec.kind: spec for spec in _EFFECTS}

# Names used by packages in format version 1 (MakePrank 0.1/0.2).
V1_ALIASES = {
    "message": "error_dialog",
    "overlay_text": "text_overlay",
    "console_simulation": "console_overlay",
    "color_bars": "color_bars",
    "fake_browser": "fake_browser",
    "notepad_simulation": "fake_notepad",
    "shutdown_simulation": "fake_shutdown",
    "screamer_simulation": "screamer",
    "sound_effect": "sound_effect",
    "toast_simulation": "toast",
    "wallpaper_simulation": "wallpaper_preview",
    "shake_simulation": "window_shake",
}


def get_effect(kind: Any) -> EffectSpec:
    if not isinstance(kind, str) or kind not in EFFECTS:
        raise ParamError(f"nicht unterstützter Effekt {kind!r}")
    return EFFECTS[kind]


def default_action(kind: str = "error_dialog") -> dict[str, Any]:
    spec = get_effect(kind)
    return {
        "type": kind,
        "title": spec.default_title,
        "text": spec.default_text,
        "duration_seconds": spec.default_duration,
        "params": spec.default_params(),
    }


def action_has_audio(action: dict[str, Any]) -> bool:
    spec = EFFECTS[action["type"]]
    params = action.get("params", {})
    if spec.always_audio:
        return True
    if action["type"] == "color_bars":
        return bool(params.get("tone"))
    if action["type"] == "screamer":
        return params.get("sound", "none") != "none"
    return False


def action_resource_name(action: dict[str, Any]) -> str | None:
    image = action.get("params", {}).get("image")
    if isinstance(image, str) and image.startswith("resource:"):
        return image.split(":", 1)[1]
    return None


DEFAULT_ACTION = default_action("error_dialog")
