# Mapa funkcji aplikacji FWDrucikSklep — etap 106

Audyt źródeł: 11.09.2026. Zakres: aplikacja Android, dokumentacja i testy w `C:/Users/Computer/FWDrucikSklep`. Bez zmian serwera, wdrożeń, urządzenia, archiwizacji APK i operacji Git commit/push.

## Korekta VC34 — 11.09.2026

Aktualizacja istniejącego ekranu, bez usuwania funkcji wymienionych niżej. Szczegóły implementacji i weryfikacji: [raport VC34](RAPORT_VC34.md).

- Cztery sekcje można otwierać niezależnie. Błąd opisu i pusta nazwa nie blokują wejścia do mediów ani ręcznej ceny. W trakcie jednej operacji przyciski generowania pozostają zablokowane; zmiana sekcji nie zleca kolejnej pracy.
- Automatyczny opis: zatwierdzona polityka serwera ChatGPT → komputer, bez automatycznego OpenRouter. Media auto: Flow. Jawny model nadal pochodzi z katalogu; nie jest podmieniany przez aplikację.
- Odczyt samego zdjęcia wymaga deklaracji `auto_tekst: {model,dostepny,vision}` dla auto albo dostępnego jawnego modelu z vision. Brak deklaracji oznacza brak potwierdzenia. Dowolny inny model vision na liście nie upoważnia do wysyłki. Przy zdjęciu i słowach bez vision wysyłane są wszystkie wpisane fakty, bez zdjęcia, z ostrzeżeniem.
- Przed zleceniem UI podaje odbiorcę zdjęcia/słów i informuje o limitach konta lub kosztach; nie obiecuje bezpłatności API/CLI.
- Pionowo 9:16, poziomo 16:9 i kwadrat 1:1 są dostępne również w Prostym. Ten sam wybór jest w Zaawansowanym i w kopii roboczej.
- Propozycja opisu oraz obraz/film oczekujące na akceptację zapisują się w kopii. Błąd nie nadpisuje wpisanych danych, ręcznej ceny ani wcześniejszych rezultatów. Dotychczasowy limit 24 godzin pozostaje.
- POST generacji nie jest ponawiany automatycznie: wyłączono retry, przekierowania i powtórkę 503; usunięto drugi POST przez LAN/Tailscale. Odczyt stanu GET może zmienić adres. Przy niepewnej odpowiedzi trzeba najpierw sprawdzić zadanie w warsztacie; ponowne naciśnięcie może ponownie zużyć limit.
- Błędy AI to stałe komunikaty po polsku, m.in. brak środków/uprawnień, limit, brak vision, timeout. Log zawiera tylko rodzaj operacji, status HTTP i klasę błędu — bez ciała odpowiedzi, promptu, URL, sekretów i stosu wyjątków.
- Historia odróżnia jawny model z katalogu od „Automatycznie — wybór serwera”; nie przypisuje chmurowego wyniku do Forge/karty bez dowodu.
- Nie dodano parowania ani żadnych kluczy usług. Logowanie, ustawienia, zaawansowane integracje, ceny z pomiaru, Allegro INACTIVE i potwierdzenia publikacji zachowano.

Poniższa inwentaryzacja i starsze wyniki testów opisują etap 106; zmiany VC34 powyżej mają pierwszeństwo w kwestiach polityki auto, formatu i niezależności kroków.

## Nowy przebieg dla początkującej osoby

Domyślnie otwiera się **Prosty**. Przełącznik **Prosty / Zaawansowany** jest nad kreatorem, dostępny również przy edycji istniejącego produktu. Oba widoki korzystają z tych samych pól i tej samej kopii roboczej.

1. **Pokaż wyrób i powiedz, co to jest.** Aparat lub galeria, kilka słów z dowolną pisownią albo przycisk „Powiedz zamiast pisać”. Materiał i wymiary są opcjonalne, po rozwinięciu szczegółów. Można przygotować opis bez zdjęcia.
2. **Przygotuj i sprawdź opis.** Domyślny model `auto`; przycisk wyboru otwiera katalog warsztatu. Propozycja pojawia się w osobnym oknie, z brakującymi faktami, ostrzeżeniem, modelem i źródłem. „Użyj tego opisu” wstawia ją do edytowalnych pól; „Zostaw mój tekst” zachowuje dotychczasową treść. Cena, waga, stan, dostawa i materiał nie są uzupełniane odpowiedzią z nowego endpointu.
3. **Zdjęcie i krótki film — opcjonalnie.** Dwa duże przyciski: „Popraw tło i światło” oraz „Zrób krótki film ze zdjęcia”. Każdy zleca jedną czynność dopiero po naciśnięciu; następny krok niczego nie generuje. Tło/światło używają dotychczasowego `zdjecie-produktowe` z poleceniem ochrony wyrobu, film — `animacja-szybka` z referencją i ochroną kształtu/koloru/faktury. Wynik czeka na akceptację lub odrzucenie. Akceptacja w Prostym nie wysyła materiałów do istniejącego produktu: zachowuje je w kopii. Domyślny format 16:9; Pionowo i Kwadrat są dostępne w obu trybach. Pełny ciąg pozostaje w Zaawansowanym. Wybór modeli obrazu/wideo jest małym rozwijanym ustawieniem; koszty i niedostępność widać także przy schowanym wyborze. Postęp z warsztatu oraz błędy są widoczne w kroku.
4. **Cena i podgląd.** Cena w złotych (przecinek lub kropka), podgląd ogłoszenia i filmu, dostępność, przejście do pełnych ustawień. Obok ceny jest opcjonalne „Sprawdź ceny podobnych wyrobów”: istniejący `/wycena`, wynik tylko potwierdzonych ofert; bez ofert — powód błędu i ręczna cena, nigdy kwota z estymacji do zastosowania. Cena zmienia się dopiero po „Użyj tej ceny w sklepie”. Nowe badanie usuwa poprzedni wynik; Prosty nie pokazuje wyniku innego produktu lub innej nazwy. „Przygotuj prywatny szkic na Allegro” rozwija obowiązkowe pola i duży przycisk INACTIVE. Pokazuje tytuł, cenę, czytelny wybór kategorii Allegro, stan sztuk oraz ograniczenie zdjęcia. „Znajdź pasującą kategorię” wyszukuje po nazwie wyrobu; użytkownik wybiera nazwę i ścieżkę z listy. Ręczny numer jest schowany pod „Wpisz ID ręcznie (opcjonalnie)”. Utworzenie szkicu wymaga osobnego potwierdzenia wysyłki danych i utworzenia wyłącznie prywatnego szkicu. Nie zgaduje kategorii/ilości ani nie dolicza prowizji. Istniejące powiązanie blokuje tworzenie duplikatu w Prostym. „Pokaż produkt w sklepie” jest niezależne, wymaga dodatniej ceny i nazwy oraz potwierdzenia publikacji. „Zachowaj na telefonie i wróć” czeka na zapis kopii; nie publikuje.

Wybór modelu tekstowego także jest zwijany. Wejście na ekran może odczytać katalog/status i pobrać istniejące zdjęcie; nie uruchamia generowania, wyceny, tworzenia oferty ani zakupu. Stara kopia z krokiem 3 zachowuje dane, lecz pokaże teraz opcjonalne media — można od razu przejść dalej do ceny.

### Końcowa korekta wycen po ustaleniu ograniczenia Allegro

Main zgłosił prawidłowe uwierzytelnienie konta, ale brak uprawnienia dostawcy do `/offers/listing` (403 AccessDenied). Nie sprawdzano tego niezależnie z aplikacji ani nie obchodzono ograniczenia. Nowa odpowiedź `/wycena` bez ofert: `ok=false`, `zrodlo=brak`, `liczba_ofert=0`, pola liczbowe zerowe, `blad` z wyjaśnieniem. Aplikacja wyświetla powód i prosi o ręczną cenę; nie tworzy kwoty zastępczej.

Dotyczy to **obu trybów**, także starszego serwera zwracającego `ok=true,zrodlo=estymacja`: brak przycisków zastosowania szacunku, brak automatycznego wstawiania jakiejkolwiek wyceny. Akceptowany pomiar wymaga `ok=true`, źródła dokładnie `allegro-api` lub `allegro-listing`, dodatniej liczby ofert i dodatniej skończonej kwoty. Stare sugestie z kopii bez potwierdzenia pomiaru nie mogą być stosowane. Ręczne `cena` i `allegroCena` pozostają nietknięte.

Starszy `AgentProduktu` ma zakaz szacowania cen w instrukcji, schemacie strukturalnym i osobnych promptach integracji. Dodatkowa ochrona po stronie Kotlin usuwa wygenerowaną cenę ze szkicu: może ją zastąpić wyłącznie ceną ręczną albo jednoznaczną kwotą w notatce użytkownika. Wymiary, czas, koszt przesyłki i kilka niejednoznacznych kwot nie są automatycznie ceną wyrobu. Parser awaryjny nie pobiera ceny z tekstu odpowiedzi modelu. Ręczna cena ma pierwszeństwo również przy akceptacji starego szkicu w kreatorze.

Wspomniane w mapie rozróżnienie pomiaru/szacunku oznacza teraz odmowę stosowania szacunku, nie propozycję kwoty do zastosowania. Porównywanie cen nadal jest zachowaną funkcją, ale potrzebuje faktycznych ofert i uprawnień po stronie dostawcy.

### Ograniczenia szkicu Allegro i przekazanie do main

- Istniejący endpoint `POST /allegro/szkic` przyjmuje `tytul`, `kategoria_id`, `cena_pln`, `opis_tekst`, `zdjecia`, `stan_sztuk`. Prosty przekazuje rzeczywisty numer kategorii Allegro, nie nazwę kategorii sklepu. Użytkownik wybiera go z wyników `GET /allegro/kategorie?fraza=...`; puste, błędne lub stare wyniki nie wybierają kategorii automatycznie. Ręczny numer jest opcjonalnym rozwinięciem. ID, nazwa i ścieżka wybranej kategorii są zachowane w kopii, również po przełączeniu trybu; starszy JSON nadal się wczytuje. Ręczna zmiana ID czyści poprzednią nazwę/ścieżkę, żeby nie opisywała innej kategorii.
- Pole `zdjecia` dotychczas obsługuje URL, nie lokalny multipart. Prosty używa istniejącego zdjęcia serwerowego; jasno informuje, że nowy lokalny obraz/film nie trafi tą drogą. Dla nowego produktu przekazuje puste zdjęcia zgodnie z istniejącym opcjonalnym polem API. Serwer może odrzucić niekompletny szkic. Aplikacja nie publikuje pomocniczo produktu ani nie omija walidacji serwera.
- Sukces wymaga `ok=true`, `status=INACTIVE` oraz ID lub URL. Sam HTTP 200, `ok=false`, brak statusu lub inny status nie wyświetlają sukcesu. Nie ma automatycznego ponawiania; przy niepewnej odpowiedzi komunikat prosi o sprawdzenie szkiców na Allegro. Callback zachowuje istniejącą sygnaturę; ID bez URL może posłużyć do odnośnika oferty.
- Aktywacja sprzedaży, dodatkowe parametry kategorii i faktyczna integracja wymagają obsługi po stronie main/Allegro. Nie wykonano prawdziwego utworzenia szkicu w ramach tych prac.
- [Audyt sekretów i metoda następnego versionCode](AUDYT_SEKRETOW_106.md) obejmuje konfigurację Gradle, BuildConfig, zasoby i źródła. Bez budowania APK.

Dyktowanie używa dostępnej w Androidzie aplikacji obsługującej `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`, z językiem `pl-PL`. Nie dodano mikrofonu działającego w tle ani klucza dostawcy. Brak rozpoznawania, anulowanie, pusty wynik i błąd uruchomienia mają komunikat oraz możliwość pisania ręcznie. Rozpoznawanie może potrzebować internetu; aplikacja nie deklaruje trybu offline.

Krótkie polecenia, pełne etykiety i komunikaty z następnym krokiem przygotowano według wskazówek `design:ux-copy`.

## Zachowana mapa funkcji

Inwentaryzacja pochodzi z `GlownaAktywnosc`, `KreatorAktywnosc`, ekranów, `ModelSklepu`, `Repozytorium`, `SklepApi`, ustawień i usług. „Zachowana” oznacza obecność ścieżki w kodzie i interfejsie, nie potwierdzenie aktualnego działania produkcyjnej usługi.

| Obszar | Zachowane funkcje | Dostęp / źródła |
|---|---|---|
| Sesja | Logowanie, sesja i CSRF, wylogowanie, obsługa błędów | Ekran logowania; `siec/Sesja.kt`, `SklepApi.kt` |
| Nawigacja | Produkty, Magazyn, Zamówienia, Studio, Pomoc; odświeżanie; osobny kreator | `GlownaAktywnosc.kt`, `KreatorAktywnosc.kt` |
| Produkty | Lista i miniatury, filtrowanie po statusie, ceny, stan, edycja, dodawanie, usuwanie z potwierdzeniem | `ui/ekrany/Produkty.kt` |
| Status produktu | Szkic na serwerze, opublikowany, ukryty; szybka zmiana z listy | Lista oraz Zaawansowany → Podgląd, status i galeria |
| Kopie robocze | Lista niedokończonych produktów, dokończenie, usunięcie, autosave, odtworzenie pól | Lista produktów, `Modele.kt`, `Ustawienia.kt`, `ModelSklepu.kt` |
| Zdjęcie wejściowe | Aparat, galeria systemowa, trwała kopia prywatna, pobranie zdjęcia istniejącego produktu | Prosty → krok 1; Zaawansowany → Zdjęcie |
| Opis | Opis ze zdjęcia lub notatki, redakcja istniejącego tekstu, kategoria, nazwa, krótki i długi opis, braki do uzupełnienia | Prosty → krok 2; Zaawansowany → Sprawdź i popraw |
| Korekty | Ręczna edycja, cofnięcie poprzedniej poprawki opisu, zapis porównań do historii korekt | Zaawansowany → Sprawdź i popraw; `AgentProduktu.kt`, `ModelSklepu.kt` |
| Poprzednie integracje opisów | Zachowana opcjonalna droga Gemini / Muse / Meta / Copilot, również poprzedni agent z historią korekt | Zaawansowany → Sprawdź i popraw → Poprzednie połączenia opisów; dostępność z dotychczasowych kontrolek, lista Gemini pobierana z konta w Pomocy, koszt nie jest przedstawiany jako zawsze zerowy |
| Cena | Cena podstawowa, promocyjna, przeliczanie złotych i groszy | Prosty → krok 4; Zaawansowany → Cena i dostępność |
| Dostępność | Stan liczbowy; `0` oznacza brak, puste pole oznacza wykonanie na zamówienie; jednostka, waga, czas realizacji, kolejność | Zaawansowany → Cena i dostępność |
| Tło | Wymiana tła, polecenie ochrony kształtu i koloru wyrobu, dodatkowa prośba | Prosty → krok 3 jednym przyciskiem ze światłem; Zaawansowany → Zdjęcie → Tło |
| Światło | Poprawa oświetlenia, ostrości i kontrastu bez zmiany wyrobu | Prosty → krok 3 z tłem; Zaawansowany → Zdjęcie → Upiększanie |
| Film | Zlecenie animacji/obrotu produktu, zdjęcie jako odniesienie, format 16:9 / 9:16 / 1:1 | Prosty → krok 3 ze wspólnym wyborem formatu; Zaawansowany → Wideo z pełnymi ustawieniami |
| Cały ciąg | Tło → światło → animacja; postęp, wynik po każdym kroku; zachowanie kadru, jeśli późniejszy krok zawiedzie | Zaawansowany → Przygotuj tło, światło i film |
| Wybór modeli | Tekst, obraz, wideo; dostępność i powód niedostępności, koszt, droga, `vision`, odświeżanie, `auto` | Nowy `ui/ekrany/ModeleAi.kt`; model obrazu i model filmu wybierane osobno dla całego ciągu |
| Akceptacja obrazu | Ustawienie jako główne, dodanie do galerii, odrzucenie wyniku | Dotychczasowe okno gotowego kadru |
| Porównanie | Oryginał / wersja AI, zastąpienie głównego zdjęcia, dodanie do galerii | `ui/ekrany/Podglady.kt` |
| Galeria produktu | Wiele zdjęć, wybór okładki, usunięcie kadru, dodanie kolejnych, opisy alternatywne, zdjęcia serwerowe | Zaawansowany → Podgląd, status i galeria |
| Materiały ruchome | Podgląd MP4/GIF, zatrzymanie w kreatorze, dodanie do ogłoszenia, materiał przed zdjęciami | Dotychczasowe okno animacji, `Podglady.kt`, `Repozytorium.kt` |
| Eksport materiałów | Otwórz, udostępnij, zapis do galerii Androida | `Podglady.kt`, `GaleriaZapis.kt`, `ZapisNaTelefonie.kt` |
| Podgląd oferty | Zdjęcie lub film, nazwa, cena, krótki i długi opis, kategoria, status | Prosty → krok 4; Zaawansowany → Podgląd |
| Zapis/publikacja | Zapis produktu, zdjęcia głównego, galerii i animacji, niezależna publikacja/status | Przyciski w obu trybach; `ModelSklepu.zapiszZeZdjeciami` |
| Allegro — import | Import po adresie aukcji, pobranie danych, synchronizacja danych oferty ze sklepem | Lista produktów i Zaawansowany → Integracja z Allegro |
| Allegro — pola | Adres, ID, cena, status, kategoria/parametry utrzymywane w modelu; otwarcie aukcji | Zaawansowany → Integracja z Allegro |
| Allegro — kategorie | Wyszukanie po nazwie, nazwa i pełna ścieżka, jawny wybór, błędy/brak wyników, opcjonalne ręczne ID | Prosty → krok 4 → prywatny szkic; bez automatycznego wyboru lub zapisu oferty |
| Allegro — wycena | Badanie cen, sugestia sklepu i Allegro, zakres, lista ofert, rozróżnienie pomiaru i szacunku, ostrzeżenie, zastosowanie kwot | Prosty → krok 4: opcjonalne porównanie; pełne wyniki nadal w Zaawansowanym; `WycenaRynkowaTest.kt` |
| Allegro — szkic | Prywatny szkic `INACTIVE`, formatowanie tytułu i opisu, cena z buforem w starym pełnym procesie, otwarcie szkicu | Prosty → krok 4 z wymaganymi polami i potwierdzeniem; Zaawansowany → pełny proces i Integracja z Allegro |
| Magazyn | Podsumowanie zapasów, wpisywanie/zmiana stanu, rozróżnienie braku i produkcji na zamówienie | Zakładka Magazyn |
| Zamówienia | Lista, szczegóły pozycji/klienta/adresu, numer przesyłki, wszystkie dotychczasowe statusy | Zakładka Zamówienia |
| Studio | Stan warsztatu/silników, światło i opis, kolejka, liczba zadań, odpytywanie i pobieranie wyniku | Zakładka Studio; `SerwerWarsztatu.kt`, `ModelSklepu.kt` |
| Sieć warsztatu | Konfigurowalny adres, istniejące przełączanie sieci lokalnej/zdalnej, postęp i ponowne połączenie podczas pobierania wyniku | Pomoc i repozytorium; nie zmieniano infrastruktury |
| Pomoc i ustawienia | Podpowiedzi pól, instrukcja zdjęć, sprawdzanie API/Gemini/chmury, zapis adresu, poprzednie ustawienia integracji | Zakładka Pomoc; `Instrukcja.kt`, `Ustawienia.kt` |
| Chmura | Konfiguracja, synchronizacja produktów/zamówień i sprawdzanie połączenia | `MostChmury.kt`, `ModelSklepu.kt` |
| Powiadomienia | WorkManager, FCM, kanał powiadomień, otwarcie zamówień | `praca/ObserwatorZamowien.kt`, `UslugaPowiadomien.kt` |
| Widget | Godzina, data, informacja o nowych zamówieniach, otwarcie aplikacji | `praca/WidgetSklepu.kt`, zasoby widgetu |

## Kontrakt z warsztatem

- `GET {workshopBase}/allegro/kategorie?fraza=...`: obcięta fraza długości 2–100 znaków; `ok=true,kategorie:[{id,nazwa,sciezka}]`, maksymalnie 10 wyników. `sciezka` jest tekstem z nazwami połączonymi ` > `, łącznie z nazwą końcowej kategorii. Pusta lista oznacza brak wyników, a nie kategorię domyślną. Błędy HTTP mogą zawierać `ok=false,kategorie:[],blad`; istniejące repozytorium zachowuje wyjaśnienie. Tolerowane jest także `ok=false` z HTTP 200. Lista pomija niekompletne pozycje i duplikaty. Na wejściu w ekran nie ma zapytania; wysyła je tylko przycisk. Podczas odczytu i po nim nie dochodzi do zapisu na Allegro. Przepływ: `KategorieAllegro` UI → `Kreator` → `KreatorAktywnosc` → `ModelSklepu` → `Repozytorium` → `KategorieWarsztatu` / `SerwerWarsztatu`.
- Odczyt integracyjny `GET http://100.84.198.20:8770/allegro/kategorie?fraza=miska` zwrócił HTTP 404 w dwóch próbach przed końcowym przekazaniem. Main osobno zgłosił poprawne wyniki wyszukiwarki i trwające podłączanie głównego API; ta sesja nie potwierdza jeszcze działania trasy pod wskazanym adresem. Nie modyfikowano serwera ani nie wysyłano żądań generowania tekstu.

- `GET {workshopBase}/ai/modele`: `ok`, `modele`, `domyslne`; model: `id`, `nazwa`, `rodzaje`, `droga`, `koszt`, `dostepny`, `powod`, dodatkowe `vision`. Nieznane pola JSON tolerowane. Nieobecna dostępność oznacza `false`, nie domniemane działanie. `koszt` jest tekstem zgodnie z ustalonym kontraktem.
- Identyfikatory są traktowane jako nieprzezroczyste teksty z serwera. Nazw ani dostępności konkretnych modeli nie wpisano w nowy kod UI. Przykładowe identyfikatory występują tylko w testach lokalnych.
- `auto` jest osobną opcją i domyślnym wyborem we wszystkich nowych polach. `domyslne` jest odczytywane, ale nie nadpisuje zapisanego wyboru użytkownika. Automatyczna redakcja tekstu stosuje zatwierdzoną politykę ChatGPT → komputer. Media auto korzystają z Flow; UI nie przedstawia usług jako zawsze bezpłatnych ani nielimitowanych.
- Lista filtruje rodzaj zadania, a niedostępne modele pokazuje z nieaktywnym wyborem i wyjaśnieniem. Zniknięcie wybranego modelu blokuje konkretny wybór; nie podmienia go na inny. Przed wysłaniem wybranego ID ViewModel ponownie pobiera katalog i sprawdza dostępność/rodzaj.
- `POST {base}/ai/opis`, multipart: opcjonalny `plik`, `notatka`, `nazwa`, `material`, `wymiary`, `model`. Własna pisownia notatki trafia bez poprawiania do serwera. Korekta języka i ograniczenie do znanych faktów należą do serwera. Historia lekcji dawnego agenta pozostaje lokalnie; nie jest dołączana jako fakty do nowego endpointu.
- Odpowiedź: `ok`, `nazwa`, `opis_krotki`, `opis`, opcjonalna `kategoria`, lista `do_uzupelnienia`, `model`, `zrodlo`, opcjonalne `ostrzezenie` i `blad`. Pusty lub błędny wynik nie jest stosowany. Braki `material` i `wymiary` są wyświetlane jako pytania po polsku.
- Konkretny model tekstowy z `vision=false` otrzymuje tylko wpisane fakty. Jeśli nie ma faktów, aplikacja prosi o kilka słów lub wybór modelu odczytującego zdjęcia. Gdy zdjęcie pominięto, do propozycji dodawana jest wyraźna informacja, że model go nie oglądał. Możliwości auto deklaruje serwer w `auto_tekst`; aplikacja nie zgaduje ich na podstawie innych modeli.
- `POST {base}/zlec`: zachowane `zadanie`, `opis`, `proporcje`, `plik`; nowe opcjonalne `model`. Nowy kreator przekazuje dokładne ID albo `auto`. Dawne wywołanie bez modelu nadal pomija tę część multipart. Rodzaj operacji nadal określa `zadanie` (`zdjecie-produktowe` / `animacja`); konkretny dostawca wynika z pola `model` po stronie serwera.
- Przepływ modelu: `ModeleAi` → stan `Kreator`/`KopiaRobocza` → callback `KreatorAktywnosc` → `ModelSklepu` → `Repozytorium` → `SerwerWarsztatu`. Ciąg wysyła model obrazu w pierwszych dwóch zleceniach, model filmu w trzecim.
- Błąd katalogu nie tworzy zastępczej listy fikcyjnych modeli. Nadal można pisać ręcznie, zachować kopię, odświeżyć listę lub jawnie spróbować `auto`. Stary serwer bez `/ai/opis` wymaga aktualizacji do używania nowego asystenta.
- Nowy kod nie zawiera kluczy usług, pól na wklejanie sekretów ani kodu logowania żądań z sekretami. Wcześniejsze ustawienia integracji pozostały dla kompatybilności; nie nadpisują nowych wyborów z katalogu.
- Dawne połączenia opisów są dostępne osobno w Zaawansowanym. Pozostawiono ich istniejący kod uwierzytelniania, konfigurację i historię korekt. To jawnie wybrana ścieżka zgodności; aplikacja nie przechodzi do niej automatycznie po błędzie katalogowego modelu. W katalogowym przepływie `mcp:assistant` może być niedostępny z podanym przez serwer powodem; nie odblokowujemy go na podstawie innych kontrolek.

## Naprawione problemy kopii i HTML

- Kreator czeka na wczytanie kopii. Pola kopii mają pierwszeństwo przed wersją z serwera, również wtedy, gdy użytkownik celowo wyczyścił opis, promocję albo odnośnik. Usunięto efekt, który po każdej zmianie kopii ponownie wypełniał puste pola.
- Nowe fakty, krok, tryb, trzy wybory modeli, pytania i informacja o źródle są serializowane w kopii. Domyślne wartości pozwalają wczytać starszy JSON.
- `content://` jest zachowywane jako pełny URI do czasu kopiowania; nie jest obcinane do samej ścieżki. Kopiowanie obsługuje ścieżkę pliku i prywatne pliki aplikacji. Galeria i animacja przechodzą ten sam mechanizm zabezpieczania co okładka. Pliki tymczasowe zachowują rozszerzenie obrazu/filmu.
- Oczekujący zapis kopii może dokończyć się po zamknięciu Activity, a jawne wyjście czeka na zapis. Trwały zapis ma opóźnienie 500 ms; nagłe zabicie procesu w tym krótkim oknie nadal wymaga sprawdzenia na urządzeniu.
- Kopia jest usuwana dopiero po wysłaniu wszystkich mediów. Przy częściowym sukcesie zachowuje niewysłane materiały pod ID już utworzonego produktu, a kreator przechodzi do tego ID. Ponowienie nie zaczyna od nowego produktu o ID `0`.
- Formatter Allegro escapuje nazwę, opis krótki i opis długi; tylko on tworzy HTML i podziały wierszy. Nie dopisuje już twierdzeń o Allegro Smart, kraju produkcji ani „100% autorskim rękodziele”. Edytor nadal traktuje wpis jako zwykły tekst; wklejony HTML nie jest wykonywany.
- Nieudane wczytanie istniejącego produktu pokazuje ponowienie i powrót, zamiast otwierać pusty formularz nowego produktu. Błędy zapisu zwalniają stan ładowania. Usunięcie kopii po zapisie jest oczekiwane przed zamknięciem ekranu; sprzątanie okładki ograniczono do własnego katalogu kopii i plików niewspółdzielonych.
- Android Lint ujawnił wcześniejsze użycie `windowLightNavigationBar` od API 27 w ogólnym motywie przy minSdk 26. Przeniesiono ten atrybut do `values-v27`, z zachowaniem wspólnego ciemnego motywu.

## Zmienione pliki

Wszystkie poniższe ścieżki są względem `C:/Users/Computer/FWDrucikSklep/`:

```text
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/pl/fwdrucik/sklep/KreatorAktywnosc.kt
app/src/main/java/pl/fwdrucik/sklep/dane/Modele.kt
app/src/main/java/pl/fwdrucik/sklep/dane/AgentProduktu.kt
app/src/main/java/pl/fwdrucik/sklep/dane/AsystentReguly.kt (nowy)
app/src/main/java/pl/fwdrucik/sklep/dane/Polecenia.kt
app/src/main/java/pl/fwdrucik/sklep/dane/Repozytorium.kt
app/src/main/java/pl/fwdrucik/sklep/narzedzia/AllegroFormat.kt
app/src/main/java/pl/fwdrucik/sklep/siec/AiKontrakt.kt (nowy)
app/src/main/java/pl/fwdrucik/sklep/siec/KategorieAllegro.kt (nowy)
app/src/main/java/pl/fwdrucik/sklep/siec/SerwerWarsztatu.kt
app/src/main/java/pl/fwdrucik/sklep/ui/ModelSklepu.kt
app/src/main/java/pl/fwdrucik/sklep/ui/ekrany/AsystentSklepu.kt (nowy)
app/src/main/java/pl/fwdrucik/sklep/ui/ekrany/KategorieAllegro.kt (nowy)
app/src/main/java/pl/fwdrucik/sklep/ui/ekrany/ModeleAi.kt (nowy)
app/src/main/java/pl/fwdrucik/sklep/ui/ekrany/Instrukcja.kt
app/src/main/java/pl/fwdrucik/sklep/ui/ekrany/Kreator.kt
app/src/main/java/pl/fwdrucik/sklep/ui/ekrany/Podglady.kt
app/src/main/res/values/themes.xml
app/src/main/res/values-v27/themes.xml (nowy)
app/src/test/java/pl/fwdrucik/sklep/AiKontraktTest.kt (nowy)
app/src/test/java/pl/fwdrucik/sklep/AsystentProstyTest.kt (nowy)
app/src/test/java/pl/fwdrucik/sklep/KategorieAllegroTest.kt (nowy)
app/src/test/java/pl/fwdrucik/sklep/WycenaRynkowaTest.kt
app/src/test/java/pl/fwdrucik/sklep/SzkicIHtmlTest.kt (nowy)
docs/MAPA_FUNKCJI_106.md (nowy)
docs/AUDYT_SEKRETOW_106.md (nowy)
```

## Sprawdzenie i granice weryfikacji

Uruchomiono lokalny Gradle wrapper 8.11.1 na zainstalowanym Temurin JDK 21.0.12; dwa workery, pamięć Gradle 1536 MB, kompilator Kotlin w procesie Gradle. Nie uruchamiano assemble, instalacji APK, emulatora ani urządzenia. MockWebServer uruchamia wyłącznie atrapę HTTP na loopback.

Końcowa walidacja po dodaniu czytelnego wyboru kategorii, rozszerzeniu Prostego i zablokowaniu fikcyjnych wycen zakończyła się powodzeniem (`BUILD SUCCESSFUL in 2m 27s`): kompilacja Kotlin aplikacji i testów, 57 testów bez błędów/pominięć oraz Android Lint bez błędów blokujących. Lint pozostawia 76 ostrzeżeń i 3 informacje, m.in. o zależnościach, zasobach, wersjach SDK i istniejących uprawnieniach. Nie wyłączano reguł ani nie tworzono baseline. Początkowy przebieg Lint wykrył opisany wyżej błąd API 27; końcowy przebieg sprawdził już poprawiony motyw. Testy regresji przed poprawkami wykazały utratę numeru oraz nazwy/ścieżki kategorii, brak ograniczenia frazy do 2–100 znaków i listy do 10 wyników, uznawanie błędnego/pustego listingu za pomiar oraz nieobsłużony zapis „49,90zł” bez spacji. Wszystkie te przypadki przechodzą w końcowym przebiegu.

Dokładne końcowe polecenie w PowerShell, z katalogu aplikacji:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' '-Pkotlin.compiler.execution.strategy=in-process'
```

Wcześniej osobno przeszło także `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin` z identycznymi flagami. `git -c core.safecrlf=false diff --check` zakończyło się kodem 0.

| Zestaw | Liczba testów | Wynik |
|---|---:|---|
| `AiKontraktTest` (nowy) | 14 | 0 błędów |
| `AsystentProstyTest` (nowy) | 15 | 0 błędów |
| `KategorieAllegroTest` (nowy) | 8 | 0 błędów |
| `SzkicIHtmlTest` (nowy) | 7 | 0 błędów |
| `AllegroIntegracjaTest` (istniejący) | 5 | 0 błędów |
| `WycenaRynkowaTest` (rozszerzony) | 8 | 0 błędów |

Raporty Gradle znajdują się standardowo w `app/build/reports/tests/testDebugUnitTest/` i `app/build/reports/lint-results-debug.html`; nie kopiowano ich do archiwum ani nie przygotowywano APK. Źródła są gotowe do dalszych testów urządzeniowych prowadzonych przez main.

Ograniczenia wymagające testu urządzeniowego/integracyjnego poza tym zakresem:

- dostępność i jakość systemowego dyktowania, obrót/restart/ubicie procesu, duża czcionka i czytnik ekranu;
- aparat, systemowy Photo Picker, uprawnienia, pamięć telefonu, odtwarzanie i udostępnianie mediów;
- realne katalogi, koszty i działanie modeli zapewniane przez warsztat, poprawność gramatyczna i fakty w faktycznych odpowiedziach;
- dostępność trasy kategorii pod skonfigurowanym adresem warsztatu po podłączeniu głównego API przez main;
- wysyłka produktu z częściowym błędem materiałów oraz ponowienie w rzeczywistej sieci;
- dotychczasowy limit kopii: 24 godziny i maksymalnie 15 wpisów w DataStore. Nie zmieniano go w tym etapie; tryb prosty informuje o czasie.

Manualna lista regresji: otworzyć starą kopię → wyczyścić opis → zamknąć → odtworzyć; przełączyć oba tryby po wpisaniu pól Allegro i dodaniu filmu; dyktować/anulować przy braku rozpoznawania; wybrać model niedostępny/właściwego i niewłaściwego rodzaju; sprawdzić `vision=false`; odrzucić propozycję opisu; zatwierdzić opis bez nadpisania ceny/wagi; sprawdzić kolejność tło–światło–film i oba ID modeli; zapisać galerię; sprawdzić częściowy upload; przejść przez wszystkie pięć zakładek. W nowym Prostym dodatkowo: pominąć media bez zlecenia, uruchomić każde z dwóch zleceń osobno, odrzucić i przyjąć wyniki na nowym/istniejącym produkcie, sprawdzić brak wysyłki po samej akceptacji, przetestować nieudane ponowne badanie cen i zmianę nazwy, zachować cenę przed „Użyj tej ceny”, anulować potwierdzenie Allegro i publikacji sklepowej. Utworzenie realnego szkicu dopiero po osobnej zgodzie; potwierdzić INACTIVE po stronie serwera. Ta lista nie została wykonana na urządzeniu w tej sesji.

Regresja kategorii do wykonania przez main: wyszukać po domyślnej nazwie wyrobu → przeczytać nazwy i ścieżki → jawnie wybrać jedną kategorię; sprawdzić, że samo wyszukanie nie zmienia starego ID. Pusta lista i błąd mają zachować dotychczasowy wybór i ręczne ceny. Zmiana frazy ukrywa poprzednie wyniki. Nazwa, ścieżka i ID mają przetrwać zamknięcie kopii oraz zmianę trybu. Ręczna zmiana ID w dowolnym trybie ma wyczyścić starą etykietę. Ten scenariusz nie wymaga utworzenia oferty na Allegro.
