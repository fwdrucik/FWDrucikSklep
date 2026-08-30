# DRUCIK Sklep — aplikacja do prowadzenia sklepu z telefonu

Osobna aplikacja na Androida do obsługi sklepu `fwdrucik.pl` bez siadania do
komputera: dodawanie i edycja produktów, zdjęcia prosto z telefonu, stany
magazynowe, zamówienia i powiadomienie, gdy ktoś kupi.

Aplikacja **nie ma własnej bazy danych**. Wszystko siedzi na serwerze i jedzie
przez `api/sklep.php` — ten sam, z którego korzysta strona. Dzięki temu nie ma
czegoś takiego jak „rozjechany katalog w telefonie i na stronie”.

## Co robi

| Zakładka | Do czego |
|---|---|
| Produkty | Lista wszystkiego razem ze szkicami. Publikacja i ukrywanie jednym dotknięciem, edycja, usuwanie. |
| Magazyn | Same stany. Plus, minus, wpisanie liczby. Na górze widać, czego brakuje i co się kończy. |
| Zamówienia | Kto, co, za ile, pod jaki adres. Zmiana statusu od „nowe” do „zakończone”. |
| Pomoc | Instrukcja: jak wystawić produkt, jakie zdjęcia robić, co znaczy każde pole. |

Kreator produktu ma podpowiedź przy **każdym** polu — pod znakiem zapytania po
prawej stronie. Podpowiedź mówi, co wpisać, daje przykład i tłumaczy, po co to
pole jest. Te same treści zebrane razem są w zakładce Pomoc.

## Agent Gemini w kreatorze

Kreator zaczyna się od zdjęcia, bo tak wygląda praca w warsztacie: wyrób leży
na stole, robisz zdjęcie, opis powstaje z tego, co widać.

1. **Zdjęcie** — z aparatu albo galerii (Photo Picker, bez proszenia o dostęp
   do całej galerii).
2. **Notatka** — dwa słowa: „fioletowy brelok”, „deska dębowa z żywicą”.
3. **Opisz zdjęciem** — Gemini widzi zdjęcie, czyta notatkę i zwraca nazwę,
   krótki opis, pełny opis, kategorię, jednostkę i opis alternatywny zdjęcia.
4. **Popraw zdjęcie** — czyste tło, wyrównane światło, kadr na środku.

### Dwie rzeczy wbudowane celowo

**Agent nie zgaduje faktów.** Ma zakaz wymyślania gatunku drewna, wymiarów,
rodzaju stali i technologii, jeśli nie widać ich jednoznacznie. Czego nie da
się ustalić ze zdjęcia, wypisuje w osobnej ramce „tego nie widać na zdjęciu —
uzupełnij sam”. Sklep sprzedaje rzeczy fizyczne; zgadnięty materiał w opisie
to gotowa reklamacja.

**Poprawianie zdjęcia nie zmienia wyrobu.** Model ma zakaz ruszania kształtu,
koloru, faktury i liczby elementów — wolno mu poprawić tło, światło i kadr.
Zdjęcie produktowe pokazuje rzecz, którą klient dostanie do ręki.

Agent uzupełnia tylko puste pola — tego, co wpiszesz ręcznie, nie nadpisuje.

### Klucz API

Wpisuje się raz w zakładce **Pomoc**. Zostaje w `DataStore` na telefonie:
nie ma go w kodzie, w repozytorium ani w kopii zapasowej. Klucz w repozytorium
to klucz spalony — wystarczy raz wypchnąć projekt na GitHuba.

Gemini leci **osobnym klientem HTTP**, bez ciasteczek. Klient sklepowy nosi
token sesji administratora fwdrucik.pl i wysłanie go pod adres Google byłoby
wyciekiem danych logowania.

Bez klucza kreator działa normalnie — opis piszesz sam.

Modele: `gemini-2.5-flash` do opisu (z `responseSchema`, więc odpowiedź jest
strukturą, a nie tekstem do wróżenia), `gemini-2.5-flash-image` do zdjęcia.
Zdjęcie jest skalowane do 1536 px dłuższego boku przed wysłaniem.

### Animacja produktowa — czego brakuje

**Nie jest zrobiona i nie udaję, że jest.** Flow (Google Labs) nie ma
publicznego API — to aplikacja w przeglądarce. Animację z API robi się modelem
**Veo** przez Gemini API, a to wymaga:

- płatnego poziomu dostępu (rozliczenia włączone w Google Cloud),
- obsługi operacji długotrwałej: zlecenie zwraca `operation`, wynik odbiera się
  odpytywaniem co kilkanaście sekund,
- limitu długości i rozdzielczości ustalonego pod koszt jednej generacji.

Wymaganie do zapamiętania przy wdrażaniu: **na animacjach nie umieszczamy
żadnych napisów poza logo F.W. DRUCIK.**

Kiedy będzie dostęp do API, dopisanie tego to jeden plik obok `AgentProduktu.kt`.

## Czego potrzeba

- Konto **administratora** na fwdrucik.pl (zwykłe konto klienta aplikacja
  odrzuci przy logowaniu — i tak odbiłoby się o `fw_wymagaj_admina()`).
- Android 8.0 (API 26) albo nowszy.
- Internet. Bez niego nie da się nic zmienić.

## Budowanie

Na tym komputerze narzędzia leżą poza `PATH`, więc trzeba wskazać JDK ręcznie:

```bash
cd /c/Users/Computer/FWDrucikSklep && JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug
```

Gotowy plik: `app/build/outputs/apk/debug/app-debug.apk`.

Wgranie na podłączony telefon:

```bash
/c/Users/Computer/android-build/android-sdk/platform-tools/adb.exe install -r "C:/Users/Computer/FWDrucikSklep/app/build/outputs/apk/debug/app-debug.apk"
```

### Wersje

`compileSdk 34`, AGP 8.7.3, Kotlin 2.1.0 — dokładnie to, co jest zainstalowane
na tym komputerze (w `android-sdk/platforms` leży wyłącznie `android-34`).
Podniesienie do nowszego SDK wymaga najpierw pobrania platformy przez
`sdkmanager`, potem zmiany dwóch liczb w `app/build.gradle.kts`.

### Adres serwera

Siedzi w `app/build.gradle.kts` jako `ADRES_API`. Do testów na innym serwerze
zmienia się tam jedną linię, a nie w kodzie.

## Powiadomienia o zamówieniach

`ObserwatorZamowien` (WorkManager) sprawdza sklep co 15 minut, także gdy
aplikacja jest zamknięta. Krócej się nie da — Android nie uruchamia pracy
cyklicznej częściej niż raz na kwadrans.

Zapamiętywany jest **największy widziany numer zamówienia**, a nie ich liczba.
Liczba potrafi zmaleć po usunięciu zamówienia i wtedy kolejne nowe nie
podniosłoby jej ponad zapamiętany stan — powiadomienie by nie przyszło.

Pierwsze uruchomienie po instalacji tylko zapisuje stan, bez powiadamiania.
Inaczej na powitanie przyszłaby informacja o wszystkich zamówieniach sprzed
instalacji.

**Gdy powiadomienia nie przychodzą:** w ustawieniach telefonu sprawdź zgodę na
powiadomienia i czy aplikacja nie została objęta oszczędzaniem baterii. Na
telefonach Xiaomi, Huawei i Samsunga trzeba to zwykle ustawić ręcznie.

## Bezpieczeństwo

Ciasteczko sesji administratora jest wyłączone z kopii zapasowej i z
przenoszenia na nowy telefon (`res/xml/reguly_kopii.xml`). Po zmianie telefonu
logujesz się raz ręcznie. To celowe — token administratora sklepu nie ma prawa
wyjechać z urządzenia w kopii w chmurze.

## Uwaga o zwrotach

Puste pole **stanu magazynowego** znaczy „wykonywane na zamówienie”. To nie jest
kosmetyka: taki wyrób nie podlega zwrotowi w 14 dni (art. 38 pkt 3 ustawy o
prawach konsumenta), sklep sam napisze to klientowi na stronie produktu i
zadeklaruje odpowiednio w danych dla Google. Wpisanie tam liczby zmienia zasady
zwrotu na standardowe 14 dni. Kreator ostrzega o tym w momencie, w którym
zostawiasz pole puste.

## Zanim klienci cokolwiek zobaczą

Katalog jest publiczny dopiero po ustawieniu `sklep_otwarty` w konfiguracji
serwera. Do tego czasu `/sklep/` pokazuje stronę o przebudowie, a Ty możesz
spokojnie wprowadzać produkty przez aplikację. Szczegóły: `api/PLATNOSCI.md`,
punkt 6.

## Struktura

```
app/src/main/java/pl/fwdrucik/sklep/
  SklepAplikacja.kt      — zależności, kanał powiadomień, planowanie pracy w tle
  GlownaAktywnosc.kt     — zakładki i przełączanie ekranów
  dane/Modele.kt         — odpowiedniki tabel z api/_sklep.php
  dane/Repozytorium.kt   — wywołania API i tłumaczenie błędów na polski
  dane/AgentProduktu.kt  — opis produktu ze zdjęcia i poprawianie zdjęcia
  dane/Ustawienia.kt     — klucz Gemini w DataStore
  siec/GeminiApi.kt      — Gemini: generateContent, schemat odpowiedzi
  siec/SklepApi.kt       — punkty api/sklep.php i api/auth.php
  siec/Sesja.kt          — ciasteczko sesji i nagłówek CSRF
  pomoc/Podpowiedzi.kt   — treści podpowiedzi kreatora
  praca/ObserwatorZamowien.kt — sprawdzanie zamówień w tle
  ui/                    — ekrany
```

Bez Hilta i bez Room świadomie: jeden moduł, jedno repozytorium, dane i tak
mieszkają na serwerze. Dołożenie ich wydłużyłoby kompilację i dorzuciło drugą
tabelę zgodności wersji, nie usuwając ani jednej linii kodu.
