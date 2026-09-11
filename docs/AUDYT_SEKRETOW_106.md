# Audyt pakowania sekretów — Android, etap 106

11.09.2026. Zakres: źródła Androida, Gradle, zasoby i zmienione pliki. Bez ujawniania wartości, dostępu do telefonu, serwera, kompilacji APK ani zmian poświadczeń. Numery linii dotyczą stanu podczas audytu.

## Wynik

Nie znaleziono rzeczywistego tokenu OAuth, sekretu serwera/API, hasła usługi ani klucza prywatnego pakowanego przez badane źródła. Nie było czego usuwać z pakowania. Konfiguracja i poświadczenia wprowadzane podczas pracy aplikacji pozostają bez zmian.

| Plik / linia | Typ i znaczenie |
|---|---|
| `app/build.gradle.kts:12` | Odczyt `local.properties`. W obecnym pliku jest tylko ścieżka SDK (`local.properties:4`), bez sekretów. |
| `app/build.gradle.kts:54` | Jedyny własny `buildConfigField`: adres bazowy API, bez poświadczeń. |
| `app/build.gradle.kts:71`, `:72`, `:74` | Opcjonalna konfiguracja pliku i haseł podpisu z właściwości lokalnych. Używana przez Gradle do podpisywania; brak przepływu tych haseł do BuildConfig, zasobów lub manifestu. |
| `app/build.gradle.kts:77`, `:79` | Standardowe hasła debugowego podpisu Androida. Nie są hasłami usług; nie usuwano mechanizmu podpisu, aby nie zmienić tożsamości aktualizacji. |
| `app/src/main/java/pl/fwdrucik/sklep/dane/Ustawienia.kt:326` | Domyślny publiczny klucz konfiguracji klienta Firebase. Odrębna kategoria, nie hasło ani token sesji. Sąsiadujące `:324`, `:325` to identyfikatory projektu/aplikacji. |
| `app/src/main/java/pl/fwdrucik/sklep/dane/Ustawienia.kt:283` | Konfiguracja Firebase może pochodzić z ustawień runtime telefonu; mechanizm zachowano. |
| `app/src/main/java/pl/fwdrucik/sklep/dane/MostChmury.kt:42` | Użycie konfiguracji przez FirebaseOptions; bez wykrytego użycia tego domyślnego klucza jako klucza nowego AI. |

Klucze Firebase zwykle identyfikują projekt i nie są mechanizmem autoryzacji. Nadal należy utrzymywać właściwe ograniczenia klucza, reguły bezpieczeństwa i uprawnienia; nie badano konfiguracji chmurowej ani ważności klucza. [Dokumentacja Firebase](https://firebase.google.com/docs/projects/api-keys).

## Metoda i granice

- Skan 71 tekstowych plików źródeł/konfiguracji/dokumentacji oraz zmienionych plików: wzorce kluczy, tokenów, PEM, poświadczeń w adresach i pól z literalnymi wartościami. Wyniki raportowane wyłącznie jako plik, linia i typ. Ręczne sprawdzenie przepływu konfiguracji do aplikacji.
- `local.properties` i `gradle.properties`: kontrola nazw właściwości, bez wypisywania wartości. Brak kopiowania całego pliku do zasobów, niestandardowych źródeł assets lub pól generujących sekrety.
- `app/src`: brak pakowanych plików prywatnych certyfikatów/kluczy i konfiguracji konta serwisowego; brak katalogu assets. Zasoby tekstowe oraz 16 plików PNG sprawdzono pod kątem charakterystycznych sygnatur sekretów (nie jest to dowód braku dowolnej steganografii).
- Istniejące wygenerowane BuildConfig debug/release odczytano bez zmian: pola standardowe oraz `ADRES_API`, bez pól sekretów. Nie analizowano finalnego APK, starych archiwów, historii Git ani danych telefonu. Przed dystrybucją main powinien osobno sprawdzić faktyczny nowy artefakt.
- Wartości o nazwach „klucz” będące nazwami preferencji nie są poświadczeniami. Nie usuwano ich ani publicznej konfiguracji Firebase.

## Następny versionCode bez danych użytkownika

Obecnie `app/build.gradle.kts:49` wylicza kod jako `10 + liczbaCommitow`; liczba pochodzi z `git rev-list --count HEAD` (`:36`). Samo przebudowanie niezatwierdzonych zmian nie zwiększy kodu. Liczba commitów nie zapewnia też monotoniczności między gałęziami lub płytkimi kopiami repozytorium.

Zalecana konfiguracja dla main: w `defaultConfig` umożliwić jawny całkowity, dodatni kod z właściwości Gradle (np. `wersjaKod`), z obecnym wyliczeniem jako wartością zapasową. W obecnej konfiguracji taka właściwość jeszcze nie jest obsługiwana — samo jej przekazanie nie podniesie wersji. Nie zmieniano numeracji w tym audycie.

Następny kod ustalić jako `max(kod z konfiguracji, kod ostatniego rozpowszechnionego APK) + 1`, z metadanych dotychczasowego artefaktu lub rejestru wydań, nie z prywatnych danych aplikacji. Bez znanego kodu ostatniego wydania sam checkout nie gwarantuje prawidłowego numeru aktualizacji. Zachować obecne applicationId (`:44`) i ten sam certyfikat podpisujący. Nie odinstalowywać aplikacji i nie zmieniać podpisu w celu „naprawy” aktualizacji. [Wersjonowanie Androida](https://developer.android.com/studio/publish/versioning), [tożsamość aplikacji](https://developer.android.com/build/configure-app-module).
