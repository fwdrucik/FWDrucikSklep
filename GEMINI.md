# Standardowy Stos Technologiczny dla Android & Kotlin (FW Drucik 3.0+)

Zgodnie z decyzjÄ… uĹĽytkownika z dnia 2026-09-05, we wszystkich pracach zwiÄ…zanych z aplikacjÄ… FW Drucik oraz projektami Kotlin i Android naleĹĽy ZAWSZE stosowaÄ‡ poniĹĽszy stos technologiczny:

1. **JÄ™zyk:** Kotlin 2.0+ (obecnie Kotlin 2.1.0 z nowym kompilatorem K2)
2. **JDK:** Eclipse Adoptium Temurin JDK 21 LTS (`C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` jako `JAVA_HOME`)
3. **NarzÄ™dzia budowania:** Gradle 8.11+ (wywoĹ‚ywane przez globalne polecenie `gradle` lub `./gradlew`)
4. **Android SDK:** `C:\Users\Computer\AppData\Local\Android\Sdk` (oficjalne SDK z Android Studio)
5. **UI:** Jetpack Compose + Material 3 (reaktywne motywy, Canvas dla wykresĂłw/animacji)
6. **Widgety:** Jetpack Glance (dla interaktywnych widgetĂłw pulpitu Android)
7. **Baza danych & Stan:** Room Database + SQLCipher (100% lokalnie, offline, bezpiecznie szyfrowana baza SQLite) + DataStore Preferences
8. **SieÄ‡ & Komunikacja:**
   - Ktor Embedded Server (CIO) â€“ serwowanie lokalnego panelu na PC bez zewnÄ™trznej chmury
   - Ktor Client â€“ obsĹ‚uga API KSeF 2.0 oraz modeli LLM
9. **Lokalne AI & Sensory:**
   - Google LiteRT / MediaPipe GenAI Tasks (.litertlm z akceleracjÄ… GPU/NPU na urzÄ…dzeniu)
   - CameraX + Google ML Kit (OCR faktur, tablic rejestracyjnych, kodĂłw kreskowych)

## Archiwizacja APK — styl nazewnictwa (ZASADA STAŁA od 2026-09-05)

Każdy gotowy APK kopiuj (nie przenoś) do `D:\FWDRUCIK ARCHIWUM\APK_ETAPY_NOWE\<Aplikacja>\etapy\`
w formacie `NazwaAplikacji_NrKolejny_DDMMRR_GGMM_versionCode.apk`:
- `NazwaAplikacji`: `FWDrucik30` | `FWDrucikSklep` | `Antidotum` | `CureDrucik`
- `NrKolejny`: kolejny numer etapu (+1 względem poprzedniego w danym folderze)
- `DDMMRR` / `GGMM`: data i godzina, np. `050926_1355`
- `versionCode`: z `aapt dump badging <apk>` (pole `versionCode=`), nie z nazwy pliku
- Przykład: `FWDrucikSklep_102_050926_1355_26.apk`. Pełna procedura: `D:\FWDRUCIK ARCHIWUM\APK_ETAPY_NOWE\INSTRUKCJA_NAZEWNICTWA.md`