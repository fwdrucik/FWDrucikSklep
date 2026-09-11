# Shop VC34 — niezależne kroki i bezpieczne AI

Zakres: tylko `C:/Users/Computer/FWDrucikSklep`; bez zmian serwera, commit/push, instalacji na telefonie, ofert Allegro i wysyłki zdjęć użytkownika. Patch przygotowany przez apply_patch w workspace i zastosowany z eskalacją. Stan początkowy Git: czysty HEAD `7380c3b`.

## Wynik i przebieg UI

Prosty pozostaje tym samym kreatorem, z czterema niezależnymi sekcjami: Zdjęcie i słowa → Opis → Zdjęcie i film → Cena i podgląd. Można wybrać dowolną sekcję bez nazwy i udanego opisu. Przyciski generowania wymagają odpowiednich danych i czekają na zakończenie jednej operacji; samo przejście niczego nie generuje.

Opis używa `auto` domyślnie. Backend deklaruje zatwierdzoną drogę dla zdjęcia w `auto_tekst: {model,dostepny,vision}`. Brak deklaracji, modelu, dostępności lub vision nie upoważnia do wysyłki samego zdjęcia. Inny dostępny model vision (np. OpenRouter) nie odblokowuje auto. Jawny wybór pozostaje jawny. Przy zdjęciu i faktach bez vision zdjęcie jest pomijane, wszystkie fakty pozostają, a wynik ma ostrzeżenie. ViewModel ponownie sprawdza katalog przed wysłaniem zdjęcia.

Potwierdzono odczytem GET rzeczywiste JSON lokalnego API: `ok=true`, `auto_tekst.model=chatgpt:gpt-5.5`, `dostepny=true`, `vision=true`, wszystkie `domyslne=auto`. Nie wykonywano rzeczywistego POST opisu ani generacji. Odrębny test syntetycznego zdjęcia po stronie main nie jest przedstawiany jako test wykonany z APK.

UI informuje o wysłaniu zdjęcia/słów do wybranego dostawcy i limitach lub kosztach. Auto tekst: ChatGPT → komputer, bez automatycznego OpenRouter. Media auto: Flow, operacje `zdjecie-produktowe` i `animacja-szybka`. Konkretny katalogowy ID oraz `model=auto` docierają do repozytorium i multipart. Nie dodano parowania ani żadnych kluczy API.

Format Pionowo 9:16 / Poziomo 16:9 / Kwadrat 1:1 jest wspólny dla obu trybów i trwałej kopii. Opis, zdjęcie oraz film oczekujące na decyzję użytkownika są zachowywane. Błąd nie nadpisuje wcześniejszych danych i cen.

POST generacji jest wykonywany raz: wyłączono retry połączenia i przekierowania, ciało POST oznaczono one-shot także dla odpowiedzi 503 Retry-After: 0. Usunięto ponowne zlecenie przez drugi adres LAN/Tailscale. GET stanu może nadal zmienić adres. Po niepewnej odpowiedzi trzeba sprawdzić zadanie przed ręcznym ponowieniem, które może drugi raz zużyć limit.

Błędy HTTP i `ok=false` mapują wyłącznie do własnych komunikatów: brak środków/dostępu, limit, logowanie, zajęty asystent, brak odczytu zdjęć, nieprawidłowe dane, timeout/połączenie. Znane polskie odpowiedzi backendu klasyfikowane są przed ogólnym 503/422. Log ma tylko whitelist operacji/statusu/klasy — nie zawiera odpowiedzi serwera, promptu, URL, sekretów ani stosu wyjątków.

## Zachowane funkcje

Pełna inwentaryzacja: [MAPA_FUNKCJI_106.md](MAPA_FUNKCJI_106.md), z aktualizacją VC34.

Zachowano logowanie/wylogowanie i adres warsztatu, produkty/statusy/kopie, magazyn, zamówienia/przesyłki, Studio/kolejkę, Pomoc/ustawienia, widget i powiadomienia. W kreatorze pozostają aparat/galeria/dyktowanie, redakcja opisu, starsze jawne integracje, tło/światło/film/cały ciąg, oryginał–wynik, akceptacja/odrzucenie, galerie/eksport i podgląd. Ceny ręczne nie są nadpisywane, wycena nadal wymaga faktycznych ofert. Allegro zachowuje import, czytelne kategorie, ręczne ID, prywatny szkic INACTIVE oraz obowiązkowe potwierdzenia. Zaawansowany nadal udostępnia wszystkie ustawienia. „Zachowane” oznacza obecność ścieżek w kodzie, nie sprawdzenie wszystkich usług produkcyjnych.

## Zmienione pliki

Ścieżki względem `C:/Users/Computer/FWDrucikSklep/`:

- `app/build.gradle.kts`
- `app/src/main/java/pl/fwdrucik/sklep/SklepAplikacja.kt`
- `app/src/main/java/pl/fwdrucik/sklep/dane/AsystentReguly.kt`
- `app/src/main/java/pl/fwdrucik/sklep/dane/Modele.kt`
- `app/src/main/java/pl/fwdrucik/sklep/dane/Repozytorium.kt`
- `app/src/main/java/pl/fwdrucik/sklep/siec/AiKontrakt.kt`
- `app/src/main/java/pl/fwdrucik/sklep/siec/BledyAi.kt` (nowy)
- `app/src/main/java/pl/fwdrucik/sklep/siec/KlientWarsztatu.kt` (nowy)
- `app/src/main/java/pl/fwdrucik/sklep/ui/ModelSklepu.kt`
- `app/src/main/java/pl/fwdrucik/sklep/ui/ekrany/AsystentSklepu.kt`
- `app/src/main/java/pl/fwdrucik/sklep/ui/ekrany/Kreator.kt`
- `app/src/main/java/pl/fwdrucik/sklep/ui/ekrany/ModeleAi.kt`
- `app/src/test/java/pl/fwdrucik/sklep/AiKontraktTest.kt`
- `app/src/test/java/pl/fwdrucik/sklep/AsystentChmuraTest.kt` (nowy)
- `app/src/test/java/pl/fwdrucik/sklep/AiBledyTest.kt` (nowy)
- `app/src/test/java/pl/fwdrucik/sklep/GeneracjaRazTest.kt` (nowy)
- `docs/MAPA_FUNKCJI_106.md`
- `docs/RAPORT_VC34.md` (ten raport)

## TDD i sprawdzenie

RED: pierwszy zestaw 15 testów — 12 błędów; następne dwie regresje nawigacji/diagnostyki — 2 błędy. Test klienta ujawnił włączony retry i drugi POST po HTTP 503. Rzeczywisty kontrakt `auto_tekst`: 13 testów, 2 błędy przed poprawką (brak odczytu deklaracji i błędne zaufanie do wiersza auto). Cztery rzeczywiste polskie odpowiedzi backendu: RED przed poprawką klasyfikacji.

GREEN końcowy: **89 testów, 0 błędów, 0 pominięć**. Kompilacja Kotlin, testy, Lint Debug/Vital Release i podpisany Release APK: **BUILD SUCCESSFUL in 2m 27s**. Lint: 0 błędów, 76 ostrzeżeń i 3 informacje (bez nowych reguł wyciszających). `git -c core.safecrlf=false diff --check`: kod 0.

Nowe zestawy: AsystentChmuraTest 13, AiBledyTest 10, GeneracjaRazTest 3. Pozostałe: AiKontraktTest 14, AllegroIntegracjaTest 5, AsystentProstyTest 15, KategorieAllegroTest 8, StanWarsztatuTest 6, SzkicIHtmlTest 7, WycenaRynkowaTest 8.

Raporty: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/lint-results-debug.html`.

Dokładne końcowe polecenie (Temurin JDK21, wrapper Gradle8.11.1, 2 workers, bez równoległych wywołań assemble):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=768m' '-Pkotlin.compiler.execution.strategy=in-process' -PreleaseVersionCode=34
```

Pierwszy przebieg z 512 MB metaspace zgłosił ostrzeżenie o pamięci, lecz zakończył się poprawnie; końcowy ma 768 MB. Nie wyłączano Lint ani nie dodano baseline.

## APK i następna aktualizacja

Plik: `C:/Users/Computer/FWDrucikSklep/app/build/outputs/apk/release/app-release.apk`

SHA-256: `F99C2F0F14624FD4A9F2B105E9ED887959D14C7DFCD16CCF4B6F60257830EC30`

Odczyt aapt: `pl.fwdrucik.sklep`, `versionCode=34`, `versionName=1.23+7380c3b`. `apksigner verify`: kod 0. Numer odczytano z APK, nie z nazwy pliku.

Przegląd nowych źródeł/BuildConfig i zmian Gradle: nie dodano literalnych kluczy, OAuth, tokenów ani pakowania sekretów z local.properties. Dotychczasowa konfiguracja podpisu nie jest pakowanym hasłem usługi; nie zmieniono jej. Publiczne identyfikatory Firebase i poprzedni audyt opisano osobno w `AUDYT_SEKRETOW_106.md`; nie przedstawiamy ich automatycznie jako hasła.

Nie zmieniano applicationId ani konfiguracji podpisu. Jawne `-PreleaseVersionCode=N` ma pierwszeństwo; bez niego minimum 34 lub `10 + liczbaCommitow`, jeśli większe. Następne wydanie musi otrzymać N większe od ostatnio wydanego numeru i ten sam certyfikat. Liczba commitów nie gwarantuje wzrostu po zmianie gałęzi lub ponownym buildzie bez commita. Nie wymaga to odczytu danych użytkownika.

## Granice

- Bez interakcji z fizycznym Samsungiem, commit/push, archiwizacji, ofert, zakupów, parowania i restartu backendu.
- Nie wysłano oryginalnych zdjęć ani promptów do dostawcy. Zewnętrzne działanie modelu i jakość generacji pozostają w gestii main/zgody użytkownika.
- Zapis kopii ma dotychczasowe opóźnienie 500 ms, limit 24 h i 15 kopii; nagłe ubicie procesu w tym oknie nadal może przerwać ostatni zapis.
- Nie dodano automatycznego ponawiania ani wznawiania generacji po utracie odpowiedzi. Zadanie serwerowe mogło trwać dalej; najpierw należy sprawdzić Studio/warsztat.
- Etykieta historii auto nie zgaduje faktycznego dostawcy, gdy odpowiedź mediów go nie podaje.
- Kopia i wyniki jednostkowe nie stanowią dowodu działania aparatu, dyktowania czy fizycznego telefonu.

## Emulator (tylko emulator-5554)

Aktualizacja instalacji z zachowaniem danych, bez czyszczenia aplikacji. Sprawdzono pusty kreator: sekcja Opis → Zdjęcie i film działa bez nazwy/udanego opisu; brak zdjęcia prawidłowo wyłącza generowanie. Wybrano Pionowo, dodano wyłącznie lokalną syntetyczną notatkę `test_UI_bez_wysylki` do potwierdzonego pustego pola i uruchomiono Shop ponownie: przywrócił krok 3 oraz Pionowo. Po aktualizacji do finalnego APK format nadal zachowany. Przełącznik Zaawansowany otwiera pełny proces; powrót zachowuje wybór.

Nie naciskano przycisku generowania, wysyłki, publikacji, wyceny ani Allegro. Nie wybierano i nie wysyłano zdjęcia. Nie wykonywano komend kierowanych do fizycznego telefonu. W emulatorze pozostawiono wyłącznie sztuczny szkic testowy.

Zrzuty w `app/build/reports/vc34/`: `emulator-opis.png`, `emulator-media.png`, `emulator-media-final.png`, `emulator-zaawansowany.png`. Pierwsze dwa przedstawiają tę samą wersję UI przed ostatnią zmianą mapowania błędów; końcowe dwa po aktualizacji do finalnego hasha APK. Zrzuty zawierają interfejs, bez fotografii użytkownika.

Krótkie etykiety, rozdzielenie czynności i komunikaty z następnym krokiem opracowano zgodnie z design:ux-copy. Nie przeprowadzono nowego przeprojektowania pozostałych ekranów.
