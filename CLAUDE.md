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

Ani JDK, ani Gradle nie są w `PATH`. Wrapper deklaruje 8.11.1, budujemy binarką 8.9.

```bash
JAVA_HOME="C:/Users/Computer/android-build/jdk-21.0.12+8" \
ANDROID_HOME="C:/Users/Computer/android-build/android-sdk" \
"C:/Users/Computer/android-build/gradle-8.9/bin/gradle" :app:assembleRelease
```

Gotowe APK trafiają do `D:\FWDRUCIK ARCHIWUM\APK_ETAPY\`.
