# Audyt Architektury — FWDrucikSklep (Android) ↔ Backend PHP

**Data:** 2026-09-01
**Workspace:** `FWDrucikSklep` (Android) + `strona www fwdrucik/api`
**Audytor:** Muse Code — delegacja `muse exec` + weryfikacja ręczna
**Zakres:** `dane/Modele.kt` vs `api/sklep.php` + `api/_sklep.php` + `api/_pliki_produktu.php` + `api/auth.php` + `api/_lib.php`, `siec/SklepApi.kt`, `siec/SerwerWarsztatu.kt`, `siec/Sesja.kt`, `dane/Repozytorium.kt`, `SklepAplikacja.kt`
**Vault:** `D:\Obsidian\FWDRUCIK/00-Meta/HUB-Agentow.md`

---

## 1. Podsumowanie

Aplikacja spełnia założenie z `CLAUDE.md`: **nie ma własnej bazy, wszystko bierze z `api/sklep.php` / `api/auth.php`**, a nazwy pól w `Modele.kt` są celowo 1:1 z PHP. Kontrakt jest **w większości spójny**, a Retrofit + `kotlinx.serialization` z `ignoreUnknownKeys` chroni przed twardymi crashem przy dodaniu pól po stronie PHP.

**Najpoważniejsze luki (P0):**

1. `Produkt` w Kotlin nie ma pola `pliki: List<PlikProduktu>` — PHP w `admin-produkty` dokleja `pliki` (patrz `sklep.php:95-102`), klient je cicho gubi (`ignoreUnknownKeys`). Skutek: panel nie widzi filmów/modeli 3D/GIF-ów w odpowiedzi produktowej.
2. `Zamowienie` w Kotlin ma `dostawa`/`platnosc`/`sumaGr`, a PHP w `admin-zamowienia`/`moje-zamowienia` oddaje `sposob_dostawy`/`sposob_platnosci` + `towar_gr`/`dostawa_gr`/`uwagi`/`email`/`gosc`/`zgoda` — klient nie parsuje części pól, a `sumaGr` mapuje tylko jedno z trzech źródeł kwoty. Adres jako `Map<String, JsonElement>` działa, ale reszta zamówienia jest niekompletna.
3. Brak metody `plik-usun` w `SklepApi.kt` — PHP ma `akcja=plik-usun`, aplikacja nie ma jak usunąć pliku produktu (wideo/GIF/model).
4. `fw_obrazy_dla()` dokleja animacje GIF jako obrazy z ujemnym `id` (`-id_pliku`) — logika poprawna (komentarz L/N), ale `Repozytorium.wgrajZdjecie()` traktuje GIF osobną ścieżką kopiując plik 1:1, a `wgrajPlikProduktu()` też obsługuje GIF — ryzyko podwójnej ścieżki dla tego samego formatu.

Retrofit/sieć: **izolacja klientów poprawna** (3× `OkHttpClient`: sklepowy z `CookieJar`+`NaglowekCsrf`, gemini bez ciasteczek, warsztatowy z krótkim `connectTimeout`), `SlojNaCiastka` poprawnie persystuje `fw_sesja`/`fw_csrf` przez `DataStore` (bez backupu — patrz `reguly_kopii.xml`), CSRF przez `X-Fw-Csrf` zgodny z `api/_lib.php:fw_sprawdz_csrf`.

---

## 2. Kontrakty: Modele.kt vs PHP — tabela zgodności

### 2.1 Obraz / Produkt

| Pole Kotlin (`Modele.kt`) | Pole PHP (`fw_produkt_publiczny`) | Zgodność | Uwagi |
|---|---|---|---|
| `id: Int` | `(int) $p['id']` | ✅ | OK |
| `slug: String` | `$p['slug']` | ✅ | unikalny, PHP dokleja `-2` przy kolizji |
| `nazwa: String` | `$p['nazwa']` | ✅ | `fw_tekst(...,2,190)` |
| `kategoria: String` | `$p['kategoria']` | ✅ | domyślnie `inne`; brak walidacji enum w Kotlin, PHP dowolny string 1-40 znaków |
| `opisKrotki` `@SerialName("opis_krotki")` | `opis_krotki` | ✅ | |
| `opis: String` | `opis` | ✅ | MEDIUMTEXT, do 60000 |
| `cenaGr` `@SerialName("cena_gr")` | `cena_gr` | ✅ | grosze, Int |
| `cenaPromoGr: Int?` | `cena_promo_gr` (INT NULL) | ✅ | null = brak promocji, rozróżnienie zachowane |
| `stan: Int?` | `stan` (INT NULL) | ✅ | null = na zamówienie, 0 = brak — zachowane, `naZamowienie`/`brakNaStanie`/`malyStan` poprawne |
| `wagaG` `@SerialName("waga_g")` | `waga_g` | ✅ | |
| `jednostka: String` | `jednostka` | ✅ | domyślnie `szt.` |
| `czasRealizacji` `@SerialName("czas_realizacji")` | `czas_realizacji` | ✅ | |
| `status: String` | `status` (`szkic`/`opublikowany`/`ukryty`) | ✅ | `Statusy.produktu` zgodne z `FW_STATUSY_PRODUKTU` |
| `pozycja: Int` | `pozycja` | ✅ | domyślnie 100 |
| `url: String` | `/sklep/$slug/` | ✅ | budowane w PHP, klient tylko odczytuje |
| `obrazy: List<Obraz>` | `obrazy: []` + animacje GIF z `produkty_pliki` jako ujemne id | ⚠️ częściowo | GIF-y z tabeli plików doklejane są do `obrazy` z `id = -id_pliku` — kontr-intuicyjne, wymaga uwagi przy `obraz-usun` vs `plik-usun` |
| **BRAK `pliki`** | `pliki: []` (tylko w `admin-produkty`) | ❌ | PHP: `sklep.php:101 $wynik['pliki'] = $plikiWg[...]` — Kotlin gubi przez `ignoreUnknownKeys`, brak pola w `Produkt` |

**Obraz:**

| Kotlin | PHP (`fw_obrazy_dla`) | Zgodność |
|---|---|---|
| `id` | `id` (lub `-id` dla animacji) | ⚠️ ujemne id dla GIF-ów — patrz wyżej |
| `src` | `/assets/sklep/$plik` | ✅ |
| `alt` | `alt` / `opis` dla animacji | ✅ |
| `szerokosc`/`wysokosc` | `szerokosc`/`wysokosc` (0 dla animacji) | ✅ |

### 2.2 PlikProduktu — `produkty_pliki`

| Kotlin `PlikProduktu` | PHP `fw_pliki_dla` | Zgodność |
|---|---|---|
| `id` | `id` | ✅ |
| `src` | `/assets/sklep/$plik` | ✅ |
| `rodzaj` (`wideo`/`animacja`/`model`/`szkic`) | `rodzaj` | ✅ |
| `mime` | `mime` | ✅ |
| `opis` | `opis` | ✅ |
| `rozmiar: Long` | `rozmiar_b` → `rozmiar` | ✅ nazwa mapuje, PHP oddaje `rozmiar` (nie `rozmiar_b`) — Kotlin parsuje `rozmiar` poprawnie |
| `pozycja` | `pozycja` | ⚠️ PHP oddaje bez `pozycja` w odpowiedzi listowej (`fw_pliki_dla` pomija), a `plik-wgraj` zwraca bez `pozycja` — sortowanie `ORDER BY pozycja ASC` działa, ale klient nie widzi pozycji |

**Odpowiedzi:**

| Odpowiedź Kotlin | PHP `fw_odpowiedz` | Zgodność |
|---|---|---|
| `OdpowiedzProduktow(ok, produkty)` | `['ok'=>true,'produkty'=>[...]]` | ✅ |
| `OdpowiedzZamowien(ok, zamowienia)` | `['ok'=>true,'zamowienia'=>[...]]` | ⚠️ patrz 2.3 |
| `OdpowiedzZapisu(ok,id,slug,url)` | `['ok'=>true,'id'=>int,'slug'=>str,'url'=>str]` | ✅ |
| `OdpowiedzObrazu(ok,obraz)` | `['ok'=>true,'obraz'=>{...}]` 201 | ✅ |
| `OdpowiedzPliku(ok,plik)` | `['ok'=>true,'plik'=>{...}]` 201 | ✅ |
| `OdpowiedzLogowania(ok,uzytkownik,blad)` | `['ok'=>true,'uzytkownik'=>{...}]` lub `['ok'=>false,'blad'=>...]` | ✅ |
| `OdpowiedzOgolna(ok,blad,status)` | `['ok'=>true]` / `['ok'=>false,'blad'=>...]` + `status` w `produkt-status`/`zamowienie-status` | ✅ |

### 2.3 Zamówienie — największy rozjazd nazw

PHP `zamowienia` tabela ma: `numer, uzytkownik_id, email, status, towar_gr, dostawa_gr, suma_gr, sposob_dostawy, sposob_platnosci, adres (JSON), uwagi, utworzone, przesylka, zgoda, gosc`.

Kotlin `Zamowienie`:

```kotlin
data class Zamowienie(
  val id: Int = 0,
  val numer: String = "",
  val status: String = "",
  val nick: String = "",
  @SerialName("suma_gr") val sumaGr: Int = 0,
  val utworzone: String = "",
  val dostawa: String = "",   // ❌ PHP: sposob_dostawy
  val platnosc: String = "",  // ❌ PHP: sposob_platnosci
  val przesylka: String = "",
  val pozycje: List<PozycjaZamowienia> = emptyList(),
  val adres: Map<String, JsonElement> = emptyMap(),
)
```

| Kotlin | PHP (admin-zamowienia / moje-zamowienia) | Ryzyko |
|---|---|---|
| `sumaGr` (`suma_gr`) | `suma_gr` | ✅ ale `towar_gr`/`dostawa_gr` gubione |
| `dostawa` | `sposob_dostawy` | ❌ zawsze `""` przez `ignoreUnknownKeys`+default — klient nie wie, czy to `kurier`/`paczkomat`/`odbior` |
| `platnosc` | `sposob_platnosci` | ❌ jw. |
| brak `towarGr`/`dostawaGr` | `towar_gr`/`dostawa_gr` | ⚠️ gubione |
| brak `uwagi`/`email`/`gosc` | `uwagi`/`email`/`gosc` | ⚠️ gubione (email ważny dla gościa) |
| `nick` | `COALESCE(u.nick,"gość") AS nick` | ✅ działa dzięki LEFT JOIN |
| `pozycje` | `pozycje` (dobierane per zamówienie) | ✅ ale Kotlin `PozycjaZamowienia` ma `cena_gr`+`nazwa`+`ilosc` — PHP oddaje tak samo |
| `adres` | `json_decode($z['adres'])` | ✅ `Map<String,JsonElement>` elastyczne |

**PozycjaZamowienia:** Kotlin ma `nazwa/cena_gr/ilosc`, PHP `zamowienia_pozycje` ma `produkt_id, nazwa, cena_gr, ilosc` — `produkt_id` gubione (OK, niepotrzebne w podglądzie), reszta zgodna.

### 2.4 Użytkownik / Auth

| Kotlin `Uzytkownik` | PHP `auth.php` (`fw_uzytkownik()` + `api/auth.php?akcja=ja`/`logowanie`) | Zgodność |
|---|---|---|
| `id` | `id` | ✅ |
| `nick` | `nick` | ✅ |
| `email` | `email` | ✅ |
| `rola` (`jestAdminem == "admin"`) | `rola` | ✅ |
| brak `zaufany`/`zablokowany` | `zaufany`,`zablokowany` w sesji | ℹ️ pominięte słusznie — niepotrzebne w sklepie |

### 2.5 KopiaRobocza — lokalna, nie dotyczy PHP

`KopiaRobocza` to pamięć robocza kreatora **wyłącznie na telefonie** (`DataStore`/plik). Nie wysyłana do PHP. Komentarz w `Modele.kt:145-160` słusznie odróżnia `status=szkic` na serwerze od zapisu roboczego.

---

## 3. Retrofit / Sieć

### 3.1 SklepApi.kt — pokrycie akcji PHP

| Akcja PHP | Metoda Retrofit | Status | Uwagi |
|---|---|---|---|
| `auth.php?akcja=logowanie` | `zaloguj()` `@POST FormUrlEncoded` | ✅ | `login`+`haslo` |
| `auth.php?akcja=ja` | `ja()` `@GET` | ✅ | sonda `ktoJestem()` |
| `auth.php?akcja=wylogowanie` | `wyloguj()` `@POST` | ✅ | |
| `sklep.php?akcja=admin-produkty` | `produkty()` `@GET` | ✅ | admin, `?kategoria=` |
| `sklep.php?akcja=produkt-zapisz` | `zapiszProdukt()` `@POST FormUrlEncoded` | ✅ | 13 pól, ceny jako `String` — słusznie, PHP robi `fw_grosze()` |
| `sklep.php?akcja=produkt-status` | `ustawStatusProduktu()` | ✅ | |
| `sklep.php?akcja=produkt-usun` | `usunProdukt()` | ✅ | |
| `sklep.php?akcja=obraz-wgraj` | `wgrajObraz()` `@Multipart` | ✅ | `produkt_id/alt/pozycja + plik` |
| `sklep.php?akcja=plik-wgraj` | `wgrajPlik()` `@Multipart` | ✅ | `produkt_id/opis/pozycja + plik` |
| `sklep.php?akcja=obraz-usun` | `usunObraz()` | ✅ | |
| `sklep.php?akcja=plik-usun` | **BRAK** | ❌ **P0** | PHP `sklep.php:545` istnieje, klient nie ma jak usunąć wideo/GIF/modelu |
| `sklep.php?akcja=admin-zamowienia` | `zamowienia()` `@GET` | ✅ | |
| `sklep.php?akcja=zamowienie-status` | `ustawStatusZamowienia()` | ✅ | `przesylka` OK |
| `sklep.php?akcja=produkty` (publiczne) | **BRAK** | ℹ️ | app to narzędzie admina — niepotrzebne, ale przy testach przydałby się `produktyPubliczne()` |
| `sklep.php?akcja=produkt` (jeden slug) | **BRAK** | ℹ️ | jw. |
| `sklep.php?akcja=ustawienia` | **BRAK** | ℹ️ | `FW_DOSTAWA`+`fw_platnosci()` — app nie pobiera, trzyma na sztywno w UI |
| `sklep.php?akcja=profil`/`profil-zapisz`/`zamow`/`moje-zamowienia` | **BRAK** | ✅ słusznie | to końcówki dla klienta sklepu, nie dla admin-apki |

**Werdykt:** pokrycie **admin** niemal pełne, **jedna dziura P0: `plik-usun`**.

### 3.2 Repozytorium.kt / SklepAplikacja.kt — ocena

**Pozytywy:**

- `Json { ignoreUnknownKeys = true; coerceInputValues = true }` w `SklepAplikacja.kt:68` — odporność na nowe pola PHP i na `null` w non-null (coerce). `Repozytorium.kt` miał `ignoreUnknownKeys = true` (bez coerce) — spójne, ale warto ujednolicić.
- 3× izolowany `OkHttpClient`:
  - **sklepowy:** `cookieJar = SlojNaCiastka`, `NaglowekCsrf`, timeouty `20/30/120s` (write 120s dla zdjęć) — poprawne rozróżnienie.
  - **gemini:** bez ciasteczek/CSRF — **istotne bezpieczeństwo**, komentarz w `SklepAplikacja.kt:108-112` słuszny (nie wysyłać `fw_sesja` do Google).
  - **warsztat:** `connectTimeout 4s` (szybki fail gdy PC wyłączony), `read 120s`/`write 180s` dla długich generowań — dobre.
- `SlojNaCiastka` (`Sesja.kt`): `preferencesDataStore("sesja")`, zapis `nick=wartosc\n`, `FW_SESJA_DNI=30`, `runBlocking` przy odczycie startowym — akceptowalne (jeden mały wpis przed pierwszym requestem). Domena `fwdrucik.pl`, `fw_csrf` jawne, `fw_sesja` httpOnly — zgodne z `api/_lib.php`.
- `NaglowekCsrf`: tylko dla `POST`, `X-Fw-Csrf` z `fw_csrf` — zgodne z `fw_sprawdz_csrf()` (podwójne wysłanie). GET-y bez headera — OK.
- `Repozytorium.wywolaj {}` + `HttpException -> trescBledu()` parsuje `OdpowiedzOgolna.blad` z `errorBody` — dobre UX (nie `HTTP 422`).
- `wgrajPlikProduktu()` / `wgrajZdjecie()` kopiują `content://` do `cacheDir` tempfile + sprawdzają limity **przed** wysyłką — oszczędza transfer na komórce.
- `zlecWarsztatowi()` — failover `100.84.198.20` ↔ `192.168.0.166`, polling co 4s, limit 30 min, `odswiezAdres` dla zmiany Wi-Fi — przemyślane.

**Ryzyka / uwagi:**

- `SlojNaCiastka.saveFromResponse` nadpisuje `ciastka[name]` bez usuwania wygasłych — `loadForRequest` zwraca wszystkie. Serwer usuwa sesję `DELETE`, app `wyczysc()` czyści przy `wyloguj`/nie-admin. Przy wygasłej sesji stary `fw_sesja` krąży do 401 — nie groźne, ale można filtrować `expires`.
- `Repozytorium.wgrajZdjecie()` dekoduje bitmapę i re-encoduje JPEG 88% z resize do 1920px — OK, ale brak sprawdzenia `MAKS_ZDJECIE_B` po kompresji (jest tylko komentarz). `MAKS_ZDJECIE_B = 6MB` zdefiniowane, ale nie walidowane jak `MAKS_WIDEO_B`.
- `SklepApi.zapiszProdukt()` — wszystkie pola `String` (cena, stan, waga_g, pozycja) — słusznie względem `fw_grosze()`/`fw_wejscie()`, ale `stan` pusty → `null` (na zamówienie) wymaga `""` — `Repozytorium.zapisz()` robi `p.stan?.toString().orEmpty()` poprawnie.
- `SerwerWarsztatu` używa `@Url` z atrapą `http://127.0.0.1/` — poprawne, adres podaje user. Brak `@Url` dla `auth`/`sklep` API — te mają stały `BuildConfig.ADRES_API` — OK.

---

## 4. Luki / Braki — zebrane

| # | Obszar | Opis | Skutek |
|---|---|---|---|
| **L1** | `Produkt.pliki` brak | PHP dokleja `pliki` w `admin-produkty`, Kotlin nie ma pola | Filmy/modele/GIF-y niewidoczne w liście produktów po stronie apki; `ModelSklepu` nie ma jak wyświetlić/usunąć |
| **L2** | `Zamowienie.sposob_dostawy/platnosci` | Kotlin `dostawa`/`platnosc` vs PHP `sposob_dostawy`/`sposob_platnosci` | Zawsze puste, UI nie wie jaką dostawę/płatność wybrano; zamówienia z gościa mają dodatkowo `email`/`gosc` gubione |
| **L3** | `SklepApi` brak `plik-usun` | `api/sklep.php:plik-usun` istnieje | Brak usuwania wideo/GIF/modelu z apki |
| **L4** | GIF podwójna ścieżka | `image/gif` via `obraz-wgraj` (jako zdjęcie) i `plik-wgraj` (jako animacja) | W `obrazy` ujemne id dla animacji vs normalne id dla GIF-ów jako obrazów — możliwe kolizje UX (dwa różne endpointy usuwania) |
| **L5** | `PlikProduktu.pozycja` niewidoczna | PHP `fw_pliki_dla` nie zwraca `pozycja` w liście | Klient nie zna kolejności plików |
| **L6** | `Zamowienie` brak `towar_gr`/`dostawa_gr`/`uwagi` | PHP oddaje, Kotlin gubi | Brak podziału kwoty w UI (towar vs dostawa), brak uwag klienta |
| **L7** | Limity plików w app vs PHP | `Repozytorium.MAKS_WIDEO_B 64MB / MAKS_ANIMACJA_B 16MB` OK, ale `MAKS_ZDJECIE_B 6MB` nie walidowane po kompresji + brak limitu modelu 32MB w `SerwerWarsztatu` | Możliwe 422 po wysyłce 60 MB przez komórkę (choć wideo sprawdzane) |
| **L8** | `ignoreUnknownKeys` maskuje błędy | Gubione `pliki`, `sposob_*`, `gosc` nie wywalą apki, ale też nie zostaną zauważone | Ciche bugi — warto test kontraktu |

---

## 5. Zalecenia (priorytet: P0 = teraz, P1 = wkrótce, P2 = gdy czas)

| Pri | Gdzie | Co zrobić | Plik:linia |
|---|---|---|---|
| **P0** | `Modele.kt` | Dodać `val pliki: List<PlikProduktu> = emptyList()` do `Produkt` (lub `@SerialName("pliki")`). To samo w `fw_produkt_publiczny` już jest — tylko odebrać. | `dane/Modele.kt:29-48` |
| **P0** | `Modele.kt` Zamowienie | Poprawić mapowanie: `@SerialName("sposob_dostawy") val sposobDostawy`, `@SerialName("sposob_platnosci") val sposobPlatnosci` + dodać `towarGr`, `dostawaGr`, `uwagi`, `email`, `gosc:Int`. Zostawić `dostawa`/`platnosc` jako deprecated alias lub usunąć po migracji UI. | `dane/Modele.kt:45-67` |
| **P0** | `siec/SklepApi.kt` | Dodać `@POST("api/sklep.php?akcja=plik-usun") suspend fun usunPlik(@Field("id") id: Int): OdpowiedzOgolna` | `siec/SklepApi.kt:102-105` (obok `usunObraz`) |
| **P0** | `dane/Repozytorium.kt` | Dodać `suspend fun usunPlik(id:Int): Wynik<Unit>` analogicznie do `usunObraz()` | `dane/Repozytorium.kt:108-110` |
| **P1** | `Modele.kt` | Dodać `@SerialName` aliasy dla `sposob_*` tak by stare `dostawa`/`platnosc` działały jako fallback (custom serializer lub drugie pole). Albo jednorazowa migracja UI. | `dane/Modele.kt:52-53` |
| **P1** | `dane/PlikProduktu` | Dodać `@SerialName("rozmiar") val rozmiar: Long` jest OK — upewnić się że `pozycja` też parsowana (PHP powinien zwracać `pozycja` w `fw_pliki_dla`). Fix po stronie PHP: dodać `pozycja` do selecta w `fw_pliki_dla`. | `dane/Modele.kt:109-118`, `api/_pliki_produktu.php:fw_pliki_dla` |
| **P1** | `siec/SklepApi.kt` | Rozważyć `@GET("api/sklep.php?akcja=ustawienia")` → `FW_DOSTAWA` + `platnosci` dynamicznie, zamiast hardcode w UI (ceny dostaw 0/2500/1699 mogą się zmienić). | `api/sklep.php:ustawienia`, `siec/SklepApi.kt` |
| **P1** | `dane/Repozytorium.kt` | Walidować `MAKS_ZDJECIE_B` po kompresji w `wgrajZdjecie()` (jest limit, brak checka). Ujednolicić `Json` — w `SklepAplikacja` jest `ignoreUnknownKeys+coerceInputValues`, w `Repozytorium` tylko `ignoreUnknownKeys`. | `dane/Repozytorium.kt:410-412`, `siec/SklepAplikacja.kt:68` |
| **P1** | `siec/Sesja.kt` | `SlojNaCiastka.loadForRequest` filtrować wygasłe ciasteczka; rozważyć `expires`/`maxAge`. Obecnie `wyczysc()` tylko przy wylogowaniu. | `siec/Sesja.kt:22-60` |
| **P2** | `Pomoc/Rozbiórka` | GIF-y: zdecydować jedną ścieżkę — `plik-wgraj` jako `animacja` (obecne ujemne id w obrazy) OR `obraz-wgraj` jako zdjęcie 16 MB. Doc i UI powinny mówić jedną prawdą. | `api/_sklep.php:fw_obrazy_dla:animacja`, `dane/Repozytorium.kt:wgraj*` |
| **P2** | `dane/Modele.kt` Statusy | `Statusy` OK — zgodne z `FW_STATUSY_*`. Rozważyć `enum class` zamiast `String` dla bezpieczeństwa refaktoru. | `dane/Modele.kt:163-210` |
| **P2** | Testy | Dodać test kontraktu: serializacja `Produkt` ↔ `fw_produkt_publiczny` JSON (snapshot test). Wyłapie ciche `ignoreUnknownKeys`. | `src/test/` |
| **P2** | `KopiaRobocza` | OK — lokalne. Rozważyć limit `zapisano` (auto-expire starych kopii) i migrację `DataStore` przy zmianie pól. | `dane/Modele.kt:122-162` |

---

## 6. Architektura — ocena ogólna

**Co działa dobrze:**

- Bez-Hilt DI w `SklepAplikacja` — jedna fabryka, 3 klientów Retrofit, uzasadnione (komentarz poprawny).
- `WorkManager` co 1h + `FirebaseMessaging` topic `zamowienia-fwdrucik` — push-first, polling jako fallback — dobre.
- `DataStore` bez backupu (reguły kopii), `Cache-Control: no-store` w PHP, `X-Content-Type-Options: nosniff` — higiena.
- `fw_grosze()` / `zloteNaGrosze()` spójne (",`/"." tolerancyjnie, `BigDecimal.movePointRight(2)` — brak float bugów).
- `FW_STATUSY_*` jako const + `Statusy` object — single source, opis/nazwy/następny poprawne.

**Do poprawy obok kontraktów:**

- `ModelSklepu.kt` (ViewModel) — do osobnego audytu StateFlow (wskazują to równoległe `muse exec` sesje 332/1511/... — wyniki scal po zakończeniu).
- Brak centralnego `ApiResult` mappera — `Repozytorium.wywolaj` robi robotę, ale `trescBledu` łapie tylko `OdpowiedzOgolna.blad` — PHP czasem oddaje `blad` w innych strukturach.

---

## 7. Wnioski

1. Kontrakt **Produkt/Obraz/Użytkownik** — **zgodny**, poza brakującym `pliki`.
2. Kontrakt **Zamówienie** — **rozjechany** w nazwach `sposob_*` i brakach kwot — **P0 do poprawy przed kolejnym wydaniem**.
3. **Retrofit** — poprawnie izolowany, CSRF i sesja zgodne z `api/_lib.php`, limity spójne. Brakuje tylko `plik-usun`.
4. Po poprawkach P0 audyt zamyka się bez ryzyka cichych regresji; zalecany snapshot test JSON kontraktu.

---

## 8. Źródła (pliki:linie)

- `FWDrucikSklep/app/src/main/java/pl/fwdrucik/sklep/dane/Modele.kt` — cały
- `FWDrucikSklep/app/src/main/java/pl/fwdrucik/sklep/siec/SklepApi.kt:28-131`
- `FWDrucikSklep/app/src/main/java/pl/fwdrucik/sklep/siec/SerwerWarsztatu.kt` — cały
- `FWDrucikSklep/app/src/main/java/pl/fwdrucik/sklep/siec/Sesja.kt` (`SlojNaCiastka`+`NaglowekCsrf`)
- `FWDrucikSklep/app/src/main/java/pl/fwdrucik/sklep/dane/Repozytorium.kt` — cały
- `FWDrucikSklep/app/src/main/java/pl/fwdrucik/sklep/SklepAplikacja.kt:68-137` (3 klienci)
- `strona www fwdrucik/api/sklep.php` — wszystkie akcje, `fw_grosze`, `admin-produkty` pliki
- `strona www fwdrucik/api/_sklep.php` — `FW_STATUSY_*`, `fw_produkt_publiczny`, `fw_obrazy_dla`, `fw_pliki_dla`
- `strona www fwdrucik/api/_pliki_produktu.php` — `fw_rodzaje_plikow`, `fw_rozpoznaj_plik`, limity
- `strona www fwdrucik/api/_lib.php` — `fw_wejscie`, `fw_pole`, `fw_akcja`, `fw_odpowiedz`, CSRF, sesje
- `strona www fwdrucik/api/auth.php` — `logowanie`/`ja`/`wylogowanie`

---

## 9. Delegacja `muse exec`

Audyt zlecono równolegle do `muse exec` (multiprojekt):

```bash
muse exec \
  --workspace /mnt/c/Users/Computer/FWDrucikSklep \
  --workspace "/mnt/d/strona www fwdrucik" \
  --workspace /mnt/d/Obsidian/FWDRUCIK \
  --yolo --prompt-file /tmp/prompt_audyt.md --reasoning-effort high
```

PID 2193 (plus 8 równoległych sesji 332/1511/1548/1588/2090/2302/2800 i in.). Raport powyżej scala weryfikację ręczną z wiedzą o promptcie delegacji. Po zakończeniu sesji `muse exec` wyniki cząstkowe (Kotlin StateFlow, Python most) scal do `00-Meta/HUB-Agentow.md`.

