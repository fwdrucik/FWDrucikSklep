# DRUCIK Sklep (Android)

## Wspólna pamięć (czytaj to najpierw)

Cały ekosystem FW DRUCIK dzieli jedną pamięć — vault Obsidian `D:\Obsidian\FWDRUCIK`.
Punkt wejścia: **`00-Meta/HUB-Agentow.md`** (przez serwer MCP `obsidian` albo wprost
z dysku). Nowe ustalenia zapisuj tam, nie tylko w rozmowie.

## Ten projekt

Aplikacja `pl.fwdrucik.sklep` — mobilny klient sklepu. **Nie ma własnej bazy.**
Wszystko bierze z backendu PHP strony: `api/sklep.php` i `api/auth.php`
(źródła w `D:\strona www fwdrucik\api`). Modele w `dane/Modele.kt` celowo nazywają
pola tak samo jak PHP — zmiana po jednej stronie wymaga zmiany po drugiej.

## Budowanie

JDK 21 i Gradle są zainstalowane w systemie i skonfigurowane w `PATH`.
- **JAVA_HOME:** `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`
- **ANDROID_HOME:** `C:\Users\Computer\AppData\Local\Android\Sdk`
- **Gradle:** 8.11.1 (wywoływalne przez `gradle.cmd` lub `./gradlew`)

```bash
gradle.cmd :app:assembleDebug
# lub dla release:
gradle.cmd :app:assembleRelease
```

Gotowe APK kopiuj (nie przenoś) do `D:\FWDRUCIK ARCHIWUM\APK_ETAPY_NOWE\FWDrucikSklep\etapy\`
w formacie `FWDrucikSklep_NrKolejny_DDMMRR_GGMM_versionCode.apk` (ZASADA STAŁA od 2026-09-05,
wspólna dla FWDrucik30 / FWDrucikSklep / Antidotum / CureDrucik — pełna procedura:
`D:\FWDRUCIK ARCHIWUM\APK_ETAPY_NOWE\INSTRUKCJA_NAZEWNICTWA.md`).
`versionCode` bierz z `aapt dump badging <apk>`, `NrKolejny` to +1 względem poprzedniego w folderze.
