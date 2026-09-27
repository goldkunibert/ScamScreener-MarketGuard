# Spielerinformationen im HUD – Planungsnotiz

## Ziel

Nach dem Port von Tango's HudLib auf die von MarketGuard unterstützten Minecraft-Versionen soll MarketGuard die Bibliothek als **Jar-in-Jar** mitliefern. Spieler brauchen dann keine separate HudLib-Datei.

MarketGuard soll in passenden SkyBlock-Situationen eine kontextbezogene Spielerkarte anzeigen können:

- beim Öffnen einer Spielerprofil-Ansicht;
- bei einem Trade mit einem anderen Spieler;
- optional schon in einer BIN-Auktion für den erkannten Verkäufer.

Die Karte zeigt ausschließlich Informationen zum aktuell erkannten Zielspieler. Sie verschwindet bei Kontextwechsel oder wenn keine eindeutige Spieleridentität vorliegt.

## Ist-Zustand

- HudLib kommt über Modrinth Maven (`maven.modrinth:dynamic-hudlib`) und wird als Jar-in-Jar in MarketGuard eingebettet. Spieler benötigen keine separate HudLib-Datei.
- MarketGuard erkennt Spielerprofile über `<Spielername>'s Profile` und Trades über `You                  <Spielername>`. Die bekannten Titel und die BIN-Verkäufer-Lore sind zentral in `HypixelScreens` gehalten.
- In einer BIN-Auktion wird die Karte ausschließlich geöffnet, wenn in Slot 13 eine eindeutige Lore-Zeile `Seller: <Minecraft-Name>` steht.
- Die MarketGuard-API stellt zusätzlich `QUERY /api/v1/players` bereit. Sie liefert Spieler- und Profildaten pro Eintrag; ohne `profileId` wählt sie das von Hypixel als `selected` markierte Profil.
- MarketGuard nutzt einen asynchronen QUERY-Client mit 60-Sekunden-Cache, dessen Schlüssel nach der ersten Auflösung UUID plus Profil-ID ist. Bei installiertem ScamScreener wird die UUID lokal gegen dessen Blacklist geprüft.
- Tango's HudLib rendert Widgets auch über Container-Screens, kann sie verschieben, aktivieren/deaktivieren, persistent speichern sowie benannte Layout-Snapshots laden und speichern.

## Gewünschter Ablauf

```text
Profil- oder Trade-Kontext erkannt
        -> Zielspielername und UUID bestimmen
        -> lokalen Cache prüfen
        -> Spielerzusammenfassung asynchron von der MarketGuard-API laden
        -> HudLib rendert die Spielerkarte mit Daten-, Lade- oder Nicht-verfügbar-Status
        -> bei Kontextwechsel Karte ausblenden
```

Die UUID ist die maßgebliche Identität. Ein Name darf nur als Anzeige oder als einmaliger Auflösungsweg dienen; er darf nicht als Cache-Schlüssel oder für Scam-Signale verwendet werden.

Anfragen dürfen weder den Render-Thread noch Interaktionen blockieren. Cache-Dauer, `fetchedAt`, Datenquelle und ein möglicher veralteter Status sind sichtbar.

## Empfohlene Standardkarte

Der erste Release soll eine einzelne, kompakte Karte sein. Damit nutzt MarketGuard die vorhandene HudLib direkt und braucht keinen eigenen visuellen HUD-Designer.

**Preset `Trade Check` (Standard):**

- Spielername und optional SkyBlock-Profilname
- ScamScreener-Status: `kein lokaler Treffer`, `lokaler Blacklist-Treffer`, `nicht verfügbar` oder `unbekannt`
- Bank und Purse, falls verfügbar
- kurze Zusammenfassungen für Rüstung, Equipment und das aktive Pet
- Datenzustand: lädt, aktuell, veraltet oder nicht verfügbar

Die Karte soll bewusst keine lange Item-Liste und keinen pauschalen „Scam-Score“ zeigen. Eine Warnung muss eine konkrete, erklärbare Ursache haben, zum Beispiel einen lokalen Blacklist-Treffer. Ohne belegbare Ursache bleibt der Status neutral. Das aktive Pet stammt aus Hypixels eindeutigem `active`-Flag. Eine Hauptwaffe wird nicht erfunden: Das öffentliche Profil enthält keinen aktuell gehaltenen Slot und meldet daher `activeWeapon` als nicht verfügbar.

## Datenfelder und Grenzen

| Gruppe | Sinnvolle Felder | Bedingung |
| --- | --- | --- |
| Identität | Minecraft-Name, UUID, gewähltes SkyBlock-Profil | Immer mit UUID als Primärschlüssel |
| Wirtschaft | geschätztes Networth, Purse, Bank, Zeitpunkt und Datenquelle | Nur falls ein verlässlicher Upstream diese Daten liefert; Werte immer als Schätzung kennzeichnen |
| Ausrüstung | Rüstungsteile, Pet, Hauptwaffe und deren Kurzname | Nur aus öffentlichen oder vom Spieler freigegebenen Profildaten |
| Progress | SkyBlock-Level, Magical Power, Dungeon-/Slayer-Kurzwerte | Optional; erst hinzufügen, wenn der Nutzen für Trade-Entscheidungen klar ist |
| ScamScreener | lokaler Blacklist-Treffer, klar definierte serverseitige Sicherheitswarnungen | Keine Rohmeldungen, Trainingsfälle, Chatverläufe oder personenbezogenen internen Daten ausgeben |
| Datenqualität | Quelle, `fetchedAt`, `stale`, Teilfehler | Immer für remote geladene Werte mitgeben |

`Wealth` und Ausrüstung sind keine Garantie für Vertrauenswürdigkeit. Sie sind Kontext für eine Entscheidung, nicht der Auslöser für einen automatischen Trade-Block.

## API-Vorschlag

Die MarketGuard-API stellt die klar abgegrenzte Batch-Ressource bereit:

```http
QUERY /api/v1/players
```

Der Request enthält ein bis zehn `players`-Einträge mit `player` (Minecraft-Name oder UUID) und optional `profileId`. Ohne Profil-ID wählt der Server das ausgewählte Hypixel-Profil. MarketGuard fragt keine Drittanbieter direkt aus dem Spiel ab und legt keinen externen API-Schlüssel im Mod-JAR ab. Der Server übernimmt Upstream-Abfragen, Normalisierung, Caching, Rate-Limits und das Kennzeichnen unvollständiger Daten.

Antwortform:

```json
{
  "status": "ok",
  "players": [
    {
      "status": "ok",
      "uuid": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      "name": "Pankraz01",
      "firstJoin": 1587483921000,
      "profile": {
        "id": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        "name": "Apple",
        "selected": true,
        "wealth": { "bank": 125000000.0, "purse": 4250000.5, "equipment": [], "armor": [] },
        "skills": { "farming": { "level": 60, "xp": 111234567.0 } }
      },
      "unavailableFields": []
    }
  ]
}
```

Fehlende Daten sind `null`, leere Listen oder ein klarer Teilstatus – nie erfundene Standardwerte. Vor der Umsetzung wird entschieden, welche Datenquelle für jedes Feld autoritativ ist und wie deren Aktualität, Datenschutz und Nutzungsbedingungen eingehalten werden. Der Endpunkt braucht außerdem eine begrenzte Antwortgröße, UUID-basierte Server-Caches und ein Rate-Limit gegen massenhafte Profilabfragen.

## HUD, Layouts und Designs

HudLib ist die passende Grundlage für das **Verschieben, Ein-/Ausblenden und Speichern** der Spielerkarte. Die `Trade Check`-Karte ist als HudLib-Widget registriert, verwendet `HudContent.visible(false)` außerhalb eines aktiven Ziels und kann über den MarketGuard-Hotkey `F8` auch bei geöffneten Container-Screens platziert werden.

Bis Profil- und Trade-Screens erkannt werden, kann sie manuell getestet werden:

```text
/marketguard playerhud <Spielername-oder-UUID> [Profil-UUID]
/marketguard playerhud clear
```

Ohne Profil-UUID zeigt die Karte das serverseitig ausgewählte Profil.

Für die gewünschten Designs werden vier feste Inhalte vorgesehen:

| Preset | Inhalt | Zweck |
| --- | --- | --- |
| `Trade Check` | Identität, ScamScreener-Status, Vermögen, Gear-Kurzfassung | Standard beim Trade |
| `Compact` | Name, ein Sicherheitsstatus, Vermögen | wenig Bildschirmfläche |
| `Profile` | alle freigegebenen Gruppen mit Datenquellen/Zeitpunkten | Spielerprofil-Ansicht |
| `All` | alle verfügbaren Player-API-Daten einschließlich UUID | vollständige Ansicht |

Eigene Designs bedeuten zunächst: Spieler wählt ein Preset, aktiviert/deaktiviert Informationsgruppen und verschiebt die Karte mit dem HudLib-Editor. Das vermeidet einen zweiten, eigenen Drag-and-drop-Designer in MarketGuard.

Wenn Spieler **mehrere benannte, speicherbare Layouts** erstellen und zwischen ihnen wechseln sollen, braucht HudLib anschließend eine gezielte Erweiterung: Layout-Snapshots müssen für alle registrierten Widgets Position und Aktivierung unter einem Namen speichern und anwenden können. Erst dann sind Aktionen wie „Trade Check speichern“, „Profil-Layout laden“ oder „auf Standard zurücksetzen“ sinnvoll. Diese Erweiterung gehört in HudLib, nicht als paralleler Layout-Editor in MarketGuard.

## Umsetzung in Etappen

1. Erledigt: HudLib über Modrinth Maven als Jar-in-Jar eingebunden. Fabric API bleibt eine externe Pflicht-Abhängigkeit.
2. Erledigt: QUERY-Contract, Rate-Limit, Teilfehlerdarstellung, asynchroner Abruf, UUID-basierter 60-Sekunden-Cache sowie API-Datenqualität (`source`, `fetchedAt`, `stale`).
3. Erledigt: `Trade Check`-Karte, manuelle Teststeuerung und neutrale ScamScreener-Anzeige.
4. Erledigt im Code: Profile, reale Trade-Titel und konservative BIN-Verkäufer-Erkennung; der Ingame-Gegencheck mit dem neu installierten Dev-JAR steht noch aus.
5. Erledigt: feste Presets (`trade`, `compact`, `profile`, `all`) sowie HudLib-Snapshots über Befehle und die Player-HUD-Layoutverwaltung. Gespeicherte Layouts können dort ausgewählt, bearbeitet, aktualisiert, gelöscht, geteilt und aus der Zwischenablage importiert werden.

## Nicht Teil des ersten Releases

- keine automatische Überwachung aller Spieler in der Lobby;
- kein globaler, undurchsichtiger Scam-Score;
- keine Weitergabe von ScamScreener-Rohdaten, Chatinhalten oder Trainingsfällen;
- keine direkte Drittanbieter-Abfrage oder geheimen API-Schlüssel im Client;
- kein eigener MarketGuard-HUD-Designer neben HudLib.

## Jar-in-Jar-Einbindung

MarketGuard bezieht HudLib als `maven.modrinth:dynamic-hudlib:1.2.0+<Minecraft-Version>` und bettet sie mit Loom (`include`) unter `META-INF/jars/` ein. Für jedes Stonecutter-Ziel muss die passende HudLib-Version auf Modrinth veröffentlicht sein, sonst schlägt die Dependency-Auflösung dieses Ziels fehl. Fabric API bleibt eine externe Pflicht-Abhängigkeit und wird nicht eingebettet.
