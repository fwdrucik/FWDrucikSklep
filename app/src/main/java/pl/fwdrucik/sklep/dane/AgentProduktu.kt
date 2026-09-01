package pl.fwdrucik.sklep.dane

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.fwdrucik.sklep.siec.Czesc
import pl.fwdrucik.sklep.siec.DaneWlasne
import pl.fwdrucik.sklep.siec.GeminiApi
import pl.fwdrucik.sklep.siec.SerwerWarsztatu
import pl.fwdrucik.sklep.siec.Konfiguracja
import pl.fwdrucik.sklep.siec.OdpowiedzGemini
import pl.fwdrucik.sklep.siec.Schemat
import pl.fwdrucik.sklep.siec.Tresc
import pl.fwdrucik.sklep.siec.ZapytanieGemini
import pl.fwdrucik.sklep.siec.obraz
import pl.fwdrucik.sklep.siec.tekst
import java.io.ByteArrayOutputStream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException

/** Wynik pracy agenta — gotowy do wstawienia w pola kreatora. */
@Serializable
data class SzkicProduktu(
    val nazwa: String = "",
    @SerialName("opis_krotki") val opisKrotki: String = "",
    val opis: String = "",
    val kategoria: String = "inne",
    val jednostka: String = "szt.",
    @SerialName("opis_zdjecia") val opisZdjecia: String = "",
    /** Cena wyłuskana z notatki („…30zł” → „30”). Puste, gdy jej nie podałeś. */
    val cena: String = "",
    /** Fakty, których nie było w notatce ani na zdjęciu — do uzupełnienia ręcznie. */
    @SerialName("do_uzupelnienia") val doUzupelnienia: List<String> = emptyList(),
    /** Waga w gramach jako tekst — agent wypełnia wszystkie pola ogłoszenia, nie tylko opisowe. */
    val waga: String = "",
    @SerialName("czas_realizacji") val czasRealizacji: String = "",
)

/**
 * Poprawka naniesiona przez czlowieka na propozycje agenta.
 *
 * PO CO TO ZBIERAMY: agent pisze poprawnie, ale nie po naszemu — myli kategorie,
 * przesadza w opisie, zaniza czas realizacji. Kazda recznie zmieniona wartosc
 * jest darmowa lekcja: pokazuje, co bylo zle i jak ma byc. Wracaja do promptu
 * przy nastepnym pisaniu, wiec agent uczy sie na zywej robocie tego warsztatu,
 * a nie na ogolnym wyobrazeniu o rzemiosle.
 *
 * Zapisujemy WYLACZNIE pola ogloszenia — zadnych danych klienta.
 */
@Serializable
data class Poprawka(
    val pole: String = "",
    @SerialName("od_agenta") val odAgenta: String = "",
    @SerialName("po_poprawce") val poPoprawce: String = "",
    val kiedy: Long = 0,
)

/**
 * Agent-copywriter.
 *
 * Wejście: notatka pisana w biegu („brelok customizowany z czym chcesz zalany
 * żywicą 30zł”) plus zdjęcie wyrobu. Wyjście: gotowa nazwa, opisy, kategoria,
 * cena i alt zdjęcia.
 *
 * To jest **redakcja, nie wymyślanie**. Notatka jest źródłem faktów, zdjęcie
 * je doprecyzowuje, a agent tylko rozwija skróty i układa zdania. Fakt, którego
 * nie ma w notatce i nie widać na zdjęciu, nie ma prawa pojawić się w opisie —
 * w sklepie z rzeczami fizycznymi zmyślony szczegół wraca jako reklamacja.
 */
class AgentProduktu(
    private val api: GeminiApi,
    private val warsztat: SerwerWarsztatu,
    private val kontekst: Context,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Reguły dla modelu.
     *
     * Sedno: model ma redagować, nie wymyślać. Gdyby dopisał „dąb suszony
     * komorowo”, bo tak zwykle bywa przy takich blatach, wystawiłby sprzedawcę
     * na zarzut wprowadzenia klienta w błąd. Wolno mu rozwinąć skróty z notatki
     * i opisać to, co widać — reszta idzie do uzupełnienia.
     */
    private val instrukcja = """
        Jesteś doświadczonym copywriterem sklepu internetowego F.W. DRUCIK
        (warsztat w Domosławiu pod Winnicą: żywica epoksydowa, druk 3D,
        konstrukcje spawane, frezowanie CNC).

        TWOJE ZADANIE TO REDAKCJA, NIE WYMYŚLANIE.

        Dostajesz dwie rzeczy:
        1. NOTATKĘ od sprzedawcy — pisaną w biegu, skrótami, np.
           "brelok customizowany z czym chcesz zalany żywicą 30zł".
           To jest ŹRÓDŁO FAKTÓW. Wszystko, co tu jest, jest prawdą.
        2. ZDJĘCIE wyrobu — pokazuje kształt, kolor, wykończenie i wielkość
           względem otoczenia. Służy do doprecyzowania tego, co widać.

        Twoja robota: zamienić tę notatkę w porządny, sprzedający opis. Masz
        rozwinąć skróty, ułożyć zdania, dobrać słowa, którymi klient szuka —
        ale każdy fakt w opisie musi pochodzić z notatki albo być widoczny
        na zdjęciu.

        CZEGO NIE WOLNO:
        - Dopisywać gatunku drewna, rodzaju stali, wymiarów, gramatury ani
          technologii, jeśli nie ma ich w notatce i nie widać ich jednoznacznie.
        - Wymyślać historii wykonania, czasu pracy, pochodzenia materiału.
        - Obiecywać właściwości, których nie da się potwierdzić: "wodoodporny",
          "nie blaknie", "wytrzyma lata".
        Wymyślony fakt w sklepie to reklamacja u sprzedawcy. Czego brakuje,
        wypisz jako krótkie pytania w polu "do_uzupelnienia".

        JAK PISAĆ:
        - Po polsku, konkretnie, ciepło ale bez lania wody.
        - Zero słów: unikalny, wyjątkowy, najwyższej jakości, perfekcyjny,
          rewelacyjny, niepowtarzalny. Nic nie znaczą i każdy ich używa.
        - Zwracaj się do klienta na "Ty". Pisz, co dostanie i co z tym zrobi.
        - Jeśli wyrób jest personalizowany, powiedz wprost, co klient wybiera
          i jak to zamawia.

        POLA:
        - nazwa: rzecz + materiał + cecha, po której klient szuka. Do 90 znaków.
          Bez numerów katalogowych.
        - opis_krotki: jedno–dwa zdania, maks. 400 znaków. To trafia do wyniku
          Google i na kafelek w sklepie — musi zachęcać i mówić, co to jest.
        - opis: pełny opis, 2–4 akapity oddzielone pustą linią. Bez nagłówków
          i bez punktorów.
        - kategoria: dokładnie jedna z: zywica, druk3d, spawanie, cnc, inne.
        - jednostka: szt., kpl., mb albo m2.
        - cena: jeśli w notatce jest kwota (np. "30zł", "30 zl", "za 30"),
          wpisz samą liczbę w złotych: "30".
          JEŚLI W NOTATCE NIE MA CENY: oszacuj średnią rynkową cenę w Polsce dla
          podobnego wyrobu rzemieślniczego / personalizowanego na zamówienie / custom
          na podstawie materiału (żywica epoksydowa, stal, druk 3D, drewno, CNC),
          nakładu pracy i stopnia trudności widocznego na zdjęciu. Wpisz realistyczną
          kwotę w pełnych złotych (np. "45", "85", "160", "280"). Nie zostawiaj pustego.
        - waga: szacunkowa waga w gramach (sama liczba), np. "50", "300", "1500".
        - czas_realizacji: szacowany czas wykonania, np. "3-5 dni roboczych", "gotowe od ręki".
        - opis_zdjecia: co widać na zdjęciu, jedno zdanie. To trafia do atrybutu
          alt — czyta go Google Grafika i czytnik ekranu osoby niewidomej.
    """.trimIndent()

    private val schemat = Schemat(
        type = "OBJECT",
        properties = mapOf(
            "nazwa" to Schemat("STRING", description = "Nazwa produktu, do 90 znaków"),
            "opis_krotki" to Schemat("STRING", description = "Do 400 znaków"),
            "opis" to Schemat("STRING", description = "Pełny opis, akapity"),
            "kategoria" to Schemat(
                "STRING",
                enum = listOf("zywica", "druk3d", "spawanie", "cnc", "inne"),
            ),
            "jednostka" to Schemat("STRING", enum = listOf("szt.", "kpl.", "mb", "m2")),
            "opis_zdjecia" to Schemat("STRING", description = "Alt zdjęcia, jedno zdanie"),
            "cena" to Schemat(
                "STRING",
                description = "Sama liczba w złotych (kwota z notatki LUB oszacowana średnia cena rynkowa dla podobnego wyrobu custom/na zamówienie, np. 45, 120, 250).",
            ),
            "waga" to Schemat(
                "STRING",
                description = "Waga w gramach, sama liczba. Puste, jesli nie da sie ocenic.",
            ),
            "czas_realizacji" to Schemat(
                "STRING",
                description = "Ile czekac na wykonanie, np. \"3-5 dni\". Puste przy wyrobie gotowym.",
            ),
            "do_uzupelnienia" to Schemat(
                "ARRAY",
                items = Schemat("STRING"),
                description = "Krótkie pytania o fakty, których nie było w notatce "
                    + "ani nie widać na zdjęciu, a przydałyby się w opisie",
            ),
        ),
        required = listOf("nazwa", "opis_krotki", "opis", "kategoria", "jednostka", "opis_zdjecia"),
    )

    /**
     * Lekcje z poprawek uzytkownika, doklejane do polecenia.
     *
     * Bierzemy ostatnie dziesiec i tylko te, gdzie czlowiek naprawde cos zmienil.
     * Wiecej nie ma sensu: prompt rosnie, a najswiezsze poprawki i tak najlepiej
     * opisuja obecny sposob pracy warsztatu.
     */
    private fun lekcje(poprawki: List<Poprawka>): String {
        if (poprawki.isEmpty()) return ""
        val ostatnie = poprawki.takeLast(10)
        return buildString {
            appendLine()
            appendLine("JAK POPRAWIAL CIE WLASCICIEL (ucz sie z tego, nie powtarzaj bledow):")
            ostatnie.forEach { p ->
                appendLine("- ${p.pole}: napisales \"${p.odAgenta.take(80)}\", " +
                    "poprawil na \"${p.poPoprawce.take(80)}\"")
            }
            appendLine("Trzymaj sie tego stylu i tych wyborow.")
        }
    }

    /** Notatka w rodzaju „brelok customizowany zalany żywicą 30zł” plus zdjęcie → gotowy opis. */
    suspend fun opiszZdjecie(
        klucz: String,
        zdjecie: Uri,
        notatka: String,
        model: String = MODEL_OPISU,
        poprawki: List<Poprawka> = emptyList(),
    ): SzkicProduktu {
        val obraz = wczytajObraz(zdjecie)
        val zapytanie = ZapytanieGemini(
            systemInstruction = Tresc(parts = listOf(Czesc(text = instrukcja + lekcje(poprawki)))),
            contents = listOf(
                Tresc(
                    role = "user",
                    parts = listOf(
                        Czesc(inlineData = obraz),
                        Czesc(
                            text = if (notatka.isBlank()) {
                                "Brak notatki — oprzyj się wyłącznie na zdjęciu. " +
                                    "Czego nie widać, nie zgaduj: wypisz w do_uzupelnienia."
                            } else {
                                "NOTATKA SPRZEDAWCY (źródło faktów):\n$notatka\n\n" +
                                    "Zredaguj z tego opis do sklepu, posiłkując się zdjęciem."
                            }
                        ),
                    ),
                )
            ),
            generationConfig = Konfiguracja(
                temperature = 0.4,
                responseMimeType = "application/json",
                responseSchema = schemat,
            ),
        )

        val odpowiedz = zGemini(model, klucz, zapytanie)
        val tekst = odpowiedz.tekst()
            ?: throw IOException(
                odpowiedz.promptFeedback?.blockReason
                    ?.let { "Model odmówił odpowiedzi ($it). Spróbuj z innym zdjęciem." }
                    ?: "Model nie zwrócił opisu. Spróbuj ponownie."
            )
        return json.decodeFromString(tekst)
    }

    /**
     * Ten sam opis, ale napisany przez Muse Code przez most na komputerze.
     *
     * PO CO DRUGI SILNIK: klucz Gemini potrafi paść — wygasnąć, stracić
     * uprawnienia, wyczerpać limit — i wtedy cały kreator staje, choć w warsztacie
     * stoi włączony komputer z abonamentem Muse. Muse pisze tekst za darmo,
     * bez klucza i bez limitu okna.
     *
     * CZEGO MUSE NIE ZROBI: **nie widzi zdjęcia**. CLI przyjmuje tekst i oddaje
     * tekst. Dlatego ta droga wymaga notatki — bez niej nie ma z czego pisać,
     * a zgadywanie faktów o wyrobie jest tu wprost zakazane (zgadnięty materiał
     * w sklepie to reklamacja). Zdjęcie czyta wyłącznie Gemini.
     *
     * @param adres pełny adres serwera warsztatowego, np. http://192.168.0.166:8770
     */
    suspend fun opiszPrzezMuse(
        adres: String,
        notatka: String,
        poprawki: List<Poprawka> = emptyList(),
        zdjecie: Uri? = null,
    ): SzkicProduktu {
        // Muse POTRAFI ogladac zdjecia — CLI ma do tego `--image`, a most na
        // komputerze przyjmuje plik razem z promptem. Wczesniejsze zalozenie,
        // ze widzi wylacznie tekst, odcinalo darmowa droge do opisu z kadru.
        val obraz = zdjecie?.let { wczytajObrazDoWyslania(it) }
        if (notatka.isBlank() && obraz == null) {
            throw IOException("Napisz notatkę albo wybierz zdjęcie — inaczej nie ma z czego pisać.")
        }

        val polecenie = buildString {
            appendLine(instrukcja)
            append(lekcje(poprawki))
            appendLine()
            appendLine("NOTATKA SPRZEDAWCY (jedyne źródło faktów):")
            appendLine(notatka)
            appendLine()
            appendLine("Nie widzisz zdjęcia. Czego nie ma w notatce, nie zgaduj —")
            appendLine("wypisz w do_uzupelnienia.")
            appendLine()
            // Muse nie ma responseSchema jak Gemini, wiec ksztalt odpowiedzi
            // trzeba wymusic slowem — i tak czy owak przepuszczamy wynik przez
            // wylapywanie nawiasow, bo modele lubia dokleic zdanie od siebie.
            appendLine("Odpowiedz WYŁĄCZNIE obiektem JSON, bez komentarza i bez ```:")
            appendLine("""{"nazwa":"","opis_krotki":"","opis":"","kategoria":"","jednostka":"szt.","opis_zdjecia":"","cena":"","do_uzupelnienia":[]}""")
        }

        val czystyAdres = adres.trim().trimEnd('/')
        val odp = if (obraz == null) {
            warsztat.muse("$czystyAdres/muse", polecenie)
        } else {
            warsztat.museZeZdjeciem(
                "$czystyAdres/muse-obraz",
                okhttp3.MultipartBody.Part.createFormData(
                    "plik", "kadr.jpg",
                    obraz.toRequestBody("image/jpeg".toMediaType()),
                ),
                polecenie.toRequestBody("text/plain".toMediaType()),
            )
        }
        if (!odp.ok || odp.odpowiedz.isBlank()) {
            throw IOException(
                odp.blad.ifBlank { "Most do Muse nie odpowiedział. Czy komputer jest włączony?" }
            )
        }
        return json.decodeFromString(wylusknijJson(odp.odpowiedz))
    }

    suspend fun opiszPrzezCopilota(
        adres: String,
        notatka: String,
        poprawki: List<Poprawka> = emptyList(),
        zdjecie: Uri? = null,
    ): SzkicProduktu {
        // Ze zdjęciem notatka przestaje być obowiązkowa: kadr sam w sobie
        // wystarczy, żeby model miał o czym pisać.
        val obraz = zdjecie?.let { wczytajObrazDoWyslania(it) }
        if (notatka.isBlank() && obraz == null) {
            throw IOException("Napisz notatkę lub podaj słowa kluczowe dla Copilota.")
        }
        val polecenie = buildString {
            appendLine(instrukcja)
            append(lekcje(poprawki))
            appendLine()
            appendLine("NOTATKA SPRZEDAWCY:\n$notatka\n")
            appendLine("Wyszukaj / oszacuj średnią cenę rynkową w PLN dla podobnego wyrobu customowego na zamówienie.")
            appendLine("Odpowiedz WYŁĄCZNIE obiektem JSON bez znaczników markdown:")
            appendLine("""{"nazwa":"","opis_krotki":"","opis":"","kategoria":"","jednostka":"szt.","opis_zdjecia":"","cena":"","waga":"","czas_realizacji":"","do_uzupelnienia":[]}""")
        }
        val czystyAdres = adres.trim().trimEnd('/')
        val odp = if (obraz == null) {
            warsztat.copilot("$czystyAdres/copilot", polecenie)
        } else {
            warsztat.copilotZeZdjeciem(
                "$czystyAdres/copilot-obraz",
                okhttp3.MultipartBody.Part.createFormData(
                    "plik", "kadr.jpg",
                    obraz.toRequestBody("image/jpeg".toMediaType()),
                ),
                polecenie.toRequestBody("text/plain".toMediaType()),
            )
        }
        if (!odp.ok || odp.odpowiedz.isBlank()) {
            throw IOException(odp.blad.ifBlank { "Most do Copilota nie odpowiedział. Czy komputer i przeglądarka są włączone?" })
        }
        return json.decodeFromString(wylusknijJson(odp.odpowiedz))
    }

    /**
     * Poprawia opis, który już jest — bez zdjęcia i bez wymyślania faktów.
     *
     * PO CO OSOBNA DROGA: `opiszZdjecie` pisze od zera i potrzebuje zdjęcia.
     * Tu chodzi o coś innego: tekst już istnieje (własny albo z poprzedniego
     * podejścia agenta) i ma zostać wygładzony — składnia, długość, rytm.
     * Fakty zostają, jakie są: model dostaje wprost zakaz dokładania czegokolwiek,
     * czego nie ma w tekście, bo zmyślony wymiar albo gatunek drewna w sklepie
     * kończy się reklamacją.
     *
     * Działa wszystkimi silnikami: Gemini, Muse lub Copilot.
     */
    suspend fun poprawOpis(
        silnik: String,
        klucz: String,
        adresWarsztatu: String,
        model: String,
        nazwa: String,
        opisKrotki: String,
        opis: String,
        cena: String = "",
        dodatkowe: String = "",
    ): SzkicProduktu {
        val zrodlo = buildString {
            appendLine("Nazwa: " + nazwa.ifBlank { "(brak)" })
            appendLine("Krótki opis: " + opisKrotki.ifBlank { "(brak)" })
            appendLine("Opis: " + opis.ifBlank { "(brak)" })
            if (cena.isNotBlank() && cena != "0") appendLine("Cena obecna: $cena zł")
            if (dodatkowe.isNotBlank()) appendLine("Uwagi: $dodatkowe")
        }
        val polecenie = """
            Popraw poniższy opis produktu do sklepu rzemieślniczego. Zachowaj
            WSZYSTKIE fakty i nie dodawaj żadnych nowych: żadnych wymiarów,
            materiałów, czasów wykonania ani cech, których nie ma w tekście.
            Popraw język, skróć przesadę, ustaw rytm zdań. Krótki opis do 400
            znaków. Jeśli nie ma ceny, oszacuj średnią cenę rynkową w PLN.
            Czego brakuje, wypisz w do_uzupelnienia.

        """.trimIndent() + "\n" + zrodlo

        if (silnik == "muse") {
            if (adresWarsztatu.isBlank()) throw IOException("Brak adresu komputera w ustawieniach.")
            val pelne = polecenie +
                "\n\nOdpowiedz WYLACZNIE obiektem JSON, bez komentarza:\n" +
                """{"nazwa":"","opis_krotki":"","opis":"","kategoria":"","jednostka":"szt.","opis_zdjecia":"","cena":"","waga":"","czas_realizacji":"","do_uzupelnienia":[]}"""
            val odp = warsztat.muse("$adresWarsztatu/muse", pelne)
            if (!odp.ok || odp.odpowiedz.isBlank()) {
                throw IOException(odp.blad.ifBlank { "Most do Muse nie odpowiedział." })
            }
            return json.decodeFromString(wylusknijJson(odp.odpowiedz))
        }

        if (silnik == "copilot") {
            if (adresWarsztatu.isBlank()) throw IOException("Brak adresu komputera w ustawieniach.")
            val pelne = polecenie +
                "\n\nOdpowiedz WYLACZNIE obiektem JSON, bez komentarza:\n" +
                """{"nazwa":"","opis_krotki":"","opis":"","kategoria":"","jednostka":"szt.","opis_zdjecia":"","cena":"","waga":"","czas_realizacji":"","do_uzupelnienia":[]}"""
            val odp = warsztat.copilot("$adresWarsztatu/copilot", pelne)
            if (!odp.ok || odp.odpowiedz.isBlank()) {
                throw IOException(odp.blad.ifBlank { "Most do Copilota nie odpowiedział." })
            }
            return json.decodeFromString(wylusknijJson(odp.odpowiedz))
        }

        if (klucz.isBlank()) throw IOException("Brak klucza Gemini — wybierz Muse albo wpisz klucz.")
        val zapytanie = ZapytanieGemini(
            systemInstruction = Tresc(parts = listOf(Czesc(text = instrukcja))),
            contents = listOf(Tresc(role = "user", parts = listOf(Czesc(text = polecenie)))),
            generationConfig = Konfiguracja(
                temperature = 0.3,
                responseMimeType = "application/json",
                responseSchema = schemat,
            ),
        )
        val odpowiedz = zGemini(model, klucz, zapytanie)
        val tekst = odpowiedz.tekst() ?: throw IOException("Model nie zwrócił poprawionego opisu.")
        return json.decodeFromString(tekst)
    }

    /**
     * Wywołanie Gemini z ponowieniem i modelem zapasowym.
     *
     * PO CO: `503 high demand` i `429` to odpowiedzi CHWILOWE — model działał
     * minutę wcześniej i zadziała minutę później. Bez ponowienia każde takie
     * odbicie kończyło się przejściem na Muse albo komunikatem o awarii, choć
     * wystarczyło poczekać dwie sekundy. Sprawdzone: `gemini-3.7-flash` potrafi
     * oddać 503, gdy `gemini-3.6-flash` w tej samej chwili odpowiada normalnie.
     *
     * Model zapasowy jest z tej samej rodziny, więc opis wychodzi w tym samym
     * stylu. Błędy TRWAŁE (400 zły klucz, 403 brak uprawnień, 404 model
     * wycofany) lecą dalej od razu — czekanie na nie nic nie zmieni.
     */
    private suspend fun zGemini(
        model: String,
        klucz: String,
        zapytanie: ZapytanieGemini,
    ): OdpowiedzGemini {
        val doProbowania: List<String> = listOf(model) + MODELE_ZAPASOWE.filterNot { it == model }
        var ostatni: Exception = IOException("Gemini nie odpowiedział.")

        for ((numer, ktory) in doProbowania.withIndex()) {
            for (proba in 0..1) {
                try {
                    return api.generuj(ktory, klucz, zapytanie)
                } catch (e: HttpException) {
                    ostatni = e
                    // Bledy TRWALE nie maja sensu ponawiac: zly klucz zostanie zly.
                    if (e.code() !in listOf(429, 500, 502, 503, 504)) throw e
                    // Przerwa rosnie z kazdym modelem: przy przeciazeniu
                    // natychmiastowe ponowienie tylko dokłada sie do kolejki.
                    delay(1_500L * (numer + 1))
                } catch (e: Exception) {
                    ostatni = e
                    if (proba == 1) throw e
                    delay(1_000)
                }
            }
        }
        throw ostatni
    }

    /** Wyjmuje obiekt JSON z odpowiedzi, która może mieć wokół siebie zdanie albo ```json. */
    private fun wylusknijJson(tekst: String): String {
        val bez = tekst.replace("```json", "").replace("```", "").trim()
        val od = bez.indexOf('{')
        val do_ = bez.lastIndexOf('}')
        if (od < 0 || do_ <= od) {
            throw IOException("Muse odpowiedział, ale nie obiektem JSON — spróbuj jeszcze raz.")
        }
        return bez.substring(od, do_ + 1)
    }

    /**
     * Poprawione zdjęcie produktowe — czyste tło, wyrównane światło.
     *
     * Model dostaje wyraźny zakaz zmieniania samego wyrobu. Sklep pokazuje
     * rzecz, którą klient dostanie do ręki; „upiększenie”, które dołoży połysk
     * albo zmieni kolor, robi ze zdjęcia produktowego reklamę czegoś innego.
     */
    suspend fun poprawZdjecie(
        klucz: String,
        zdjecie: Uri,
        model: String = MODEL_OBRAZU,
        dodatkowe: String = "",
    ): File {
        val obraz = wczytajObraz(zdjecie)
        // Tlo wymieniamy, wyrobu nie ruszamy.
        //
        // PO CO PASTELE: zdjecie robione przy stole ma za soba warsztat —
        // narzedzia, karton, kabel. Wyciete tlo i lagodny pastelowy gradient
        // stawiaja wyrob na pierwszym planie i wygladaja jak zdjecie ze studia,
        // a nie jak kadr z reklamy stockowej. Dobor barwy zostawiamy modelowi,
        // zeby kolejne zdjecia nie byly identyczne.
        val polecenie = """
            Przygotuj to zdjęcie produktowe do sklepu internetowego:
            - USUŃ całe tło i zastąp je gładkim, delikatnym pastelowym tłem
              (miękki gradient: pudrowy róż, mięta, błękit, kremowy beż —
              dobierz kolor tak, żeby wyrób był na nim dobrze widoczny),
            - ustaw wyrób na środku kadru, cały, w naturalnej perspektywie,
            - dołóż miękki, delikatny cień pod wyrobem, żeby nie wisiał w próżni,
            - wyrównaj oświetlenie, usuń ostre cienie i odbicia od lampy,
            - popraw ostrość i balans bieli.

            NIE WOLNO Ci zmieniać samego wyrobu: kształtu, koloru, faktury,
            wykończenia ani liczby elementów. Nie dodawaj napisów, znaków
            wodnych ani rekwizytów. To ma być ta sama rzecz, tylko ładnie
            pokazana.
        """.trimIndent() +
            (if (dodatkowe.isNotBlank()) "\n\nDODATKOWE POLECENIE: $dodatkowe" else "")

        val zapytanie = ZapytanieGemini(
            contents = listOf(
                Tresc(
                    role = "user",
                    parts = listOf(Czesc(inlineData = obraz), Czesc(text = polecenie)),
                )
            ),
            generationConfig = Konfiguracja(responseModalities = listOf("IMAGE", "TEXT")),
        )

        val odpowiedz = zGemini(model, klucz, zapytanie)
        val wynik = odpowiedz.obraz()
            ?: throw IOException("Model nie zwrócił poprawionego zdjęcia. Spróbuj ponownie.")

        return withContext(Dispatchers.IO) {
            val plik = File.createTempFile("poprawione", ".jpg", kontekst.cacheDir)
            plik.writeBytes(Base64.decode(wynik.data, Base64.DEFAULT))
            plik
        }
    }

    /**
     * Zdjęcie z telefonu na dane dla modelu.
     *
     * Skalujemy do 1536 px dłuższego boku. Zdjęcie z aparatu ma kilkanaście
     * megapikseli, po zakodowaniu w base64 rośnie o kolejną jedną trzecią,
     * a model i tak analizuje je w mniejszej rozdzielczości — pełny plik to
     * tylko dłuższe wysyłanie przez komórkę i większa szansa na przekroczenie
     * limitu zapytania.
     */
    /**
     * Zdjęcie jako bajty JPEG — do wysłania mostowi Muse.
     *
     * Ta sama obróbka co przy Gemini (zmniejszenie do rozsądnego boku), bo
     * powód jest ten sam: kadr z telefonu ma kilkanaście megapikseli, a do
     * opisu wystarcza ułamek tego. Różni się tylko opakowanie — tutaj surowe
     * bajty, tam base64 w JSON-ie.
     */
    private suspend fun wczytajObrazDoWyslania(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val dane = wczytajObraz(uri)
        Base64.decode(dane.data, Base64.NO_WRAP)
    }

    private suspend fun wczytajObraz(uri: Uri): DaneWlasne = withContext(Dispatchers.IO) {
        val pierwotny = kontekst.contentResolver.openInputStream(uri).use { we ->
            requireNotNull(we) { "Nie udało się otworzyć zdjęcia." }
            BitmapFactory.decodeStream(we)
        } ?: throw IOException("Nie udało się odczytać zdjęcia.")

        val dluzszy = maxOf(pierwotny.width, pierwotny.height)
        val mapa = if (dluzszy > MAKS_BOK) {
            val skala = MAKS_BOK.toFloat() / dluzszy
            Bitmap.createScaledBitmap(
                pierwotny,
                (pierwotny.width * skala).toInt(),
                (pierwotny.height * skala).toInt(),
                true,
            )
        } else {
            pierwotny
        }

        try {
            val bufor = ByteArrayOutputStream()
            mapa.compress(Bitmap.CompressFormat.JPEG, 88, bufor)
            DaneWlasne(
                mimeType = "image/jpeg",
                data = Base64.encodeToString(bufor.toByteArray(), Base64.NO_WRAP),
            )
        } finally {
            if (mapa !== pierwotny) {
                mapa.recycle()
            }
            pierwotny.recycle()
        }
    }

    private companion object {
        // Wartosci awaryjne. Normalnie model przychodzi z ustawien — patrz
        // DOMYSLNY_MODEL_OPISU w Ustawienia.kt.
        const val MODEL_OPISU = "gemini-3.7-flash"

        /** Rodzina zapasowa na wypadek chwilowego przeciążenia wybranego modelu. */
        val MODELE_ZAPASOWE = listOf("gemini-3.6-flash", "gemini-3.5-flash", "gemini-3.1-flash-lite")
        const val MODEL_OBRAZU = "gemini-3.1-flash-image"
        const val MAKS_BOK = 1536
    }
}
