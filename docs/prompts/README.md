# MGO Manager - Entwicklungsdokumentation

## Projektübersicht

**MGO Manager** ist eine Android-App zur Verwaltung von Monopoly Go Account-Backups mit Root-Zugriff. Die App ermöglicht das Erstellen, Wiederherstellen und Verwalten von vollständigen Spielstand-Backups.

## Dokumentationsstruktur

### 📋 CLAUDE.md
Die zentrale Spezifikationsdatei mit allen technischen Details:
- Vollständige Feature-Beschreibung
- Technische Architektur (MVVM + Hilt + Room + Compose)
- Datenmodelle und Entities
- File-System-Operationen
- UI-Spezifikationen
- Testing-Anforderungen

### 📂 docs/prompts/

Die Entwicklung ist in **7 strukturierte Phasen** aufgeteilt:

#### **Phase 1: Projekt-Setup & Basis-Architektur**
`Initial_Entwicklungsprompt_1.md` (939 Zeilen)
- Gradle-Konfiguration mit allen Dependencies
- Hilt Dependency Injection Setup
- Root-Zugriff Implementation (libsu)
- Permission-Management (Android 9-15)
- Theme und Basis-UI
- MainActivity mit Root-Check

#### **Phase 2: Datenmodell & Datenbank**
`Initial_Entwicklungsprompt_2.md` (1274 Zeilen)
- Room Database mit Entities (Account, Log)
- DAOs für alle CRUD-Operationen
- Repository Pattern Implementation
- DataStore für App-Einstellungen
- Session-Management
- Data Model Conversions

#### **Phase 3: Backup-Logik**
`Initial_Entwicklungsprompt_3.md` (450 Zeilen)
- ID-Extraktion aus XML (User ID, GAID, Device Token, App Set ID)
- SSAID-Extraktion per Regex
- File-Operations mit Root
- Permission-Reading
- Backup Use Case Implementation
- Error-Handling

#### **Phase 4: Restore-Logik**
`Initial_Entwicklungsprompt_4.md` (230 Zeilen)
- Backup-Validierung
- File-Wiederherstellung mit Root
- Permission-Restauration
- Game Process Management (Force-Stop, Launch)
- Timestamp-Updates
- Restore Use Case Implementation

#### **Phase 5: UI - Home Screen & Navigation**
`Initial_Entwicklungsprompt_5.md` (541 Zeilen)
- Navigation Graph Setup
- Home Screen mit Statistics Cards
- Account Grid (2 Spalten)
- Floating Action Button
- Home ViewModel
- Composable Components

#### **Phase 6: UI - Detail Screen & Dialoge**
`Initial_Entwicklungsprompt_6.md` (530 Zeilen)
- Detail Screen mit allen Account-Daten
- Backup-Dialog (2-Step Flow)
- Restore-Confirmation
- Delete-Confirmation
- Edit-Dialog
- Detail ViewModel

#### **Phase 7: Settings & Logging**
`Initial_Entwicklungsprompt_7.md` (483 Zeilen)
- Settings Screen mit DataStore-Integration
- Log Screen mit Session-Gruppierung
- Log-Management (letzte 5 Sessions)
- Root-Status Display
- Final Testing Checklist

## Verwendung der Prompts

### Workflow

1. **Lies CLAUDE.md vollständig** um das Gesamtprojekt zu verstehen
2. **Arbeite sequenziell durch die Prompts** (1→7)
3. **Schließe jede Phase vollständig ab** bevor du zur nächsten gehst
4. **Teste nach jeder Phase** gemäß den Testkriterien
5. **Prüfe die Abschlusskriterien** am Ende jeder Phase

### Wichtige Hinweise

- **Jeder Prompt ist selbstständig**: Enthält alle notwendigen Code-Beispiele
- **Ausführliche Troubleshooting-Sektionen**: Hilfe bei häufigen Problemen
- **Klare Abschlusskriterien**: Checkliste für Phasenabschluss
- **Nahtlose Übergänge**: Jeder Prompt verweist auf den nächsten

### Technologie-Stack

- **Sprache**: Kotlin
- **Architektur**: MVVM
- **DI**: Hilt
- **Database**: Room
- **UI**: Jetpack Compose + Material3
- **Settings**: DataStore
- **Root**: libsu (TopJohnWu)
- **Min SDK**: 28 (Android 9)
- **Target SDK**: 34 (Android 14)

## Entwicklungszeit (Schätzung)

- **Phase 1**: 2-3 Stunden (Setup + Dependencies)
- **Phase 2**: 3-4 Stunden (Database + Models)
- **Phase 3**: 4-5 Stunden (Backup-Logik + Root)
- **Phase 4**: 2-3 Stunden (Restore-Logik)
- **Phase 5**: 3-4 Stunden (Home UI)
- **Phase 6**: 3-4 Stunden (Detail UI + Dialoge)
- **Phase 7**: 2-3 Stunden (Settings + Logs)

**Gesamt**: ~20-26 Stunden Entwicklungszeit

## Key Features

✅ Root-basiertes Backup-System  
✅ Automatische ID-Extraktion (User ID, GAID, Device Token, App Set ID, SSAID)  
✅ File-Permission Management  
✅ Facebook-Login Credentials Storage  
✅ Sus-Level Tracking (0, 3, 7, Permanent)  
✅ Error-Flagging  
✅ Statistics Dashboard  
✅ Complete Restore Functionality  
✅ Session-based Logging (letzte 5 Sessions)  
✅ Material3 Design mit Purple Theme  
✅ Android 9-15 Kompatibilität  

## Testing

Jede Phase enthält:
- **Manual Tests**: Schritt-für-Schritt Anleitung
- **Unit Tests**: Wo sinnvoll (Data Models, Conversions)
- **Integration Tests**: Database, Repository
- **Checklist**: Vollständige Abnahmekriterien

## Support & Troubleshooting

Jeder Prompt enthält eine dedizierte **Troubleshooting-Sektion** mit:
- Häufige Fehler und deren Lösungen
- Gradle-Probleme
- Hilt-Injection-Fehler
- Root-Zugriffs-Probleme
- Permission-Probleme
- Compose-Preview-Probleme

## Lizenz & Autor

**Entwickelt von**: babix (KI-Agent-Prompt-Entwickler)  
**Projekt-Typ**: Persönliches Tool für Monopoly Go Account-Management  
**Entwicklungs-Methode**: Strukturierte Phasen-basierte Entwicklung

---

**Version**: 1.0  
**Letzte Aktualisierung**: Januar 2025  
**Status**: Produktionsbereit für Entwicklung
