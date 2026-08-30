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
import pl.fwdrucik.sklep.siec.Konfiguracja
import pl.fwdrucik.sklep.siec.Schemat
import pl.fwdrucik.sklep.siec.Tresc
import pl.fwdrucik.sklep.siec.ZapytanieGemini
import pl.fwdrucik.sklep.siec.obraz
import pl.fwdrucik.sklep.siec.tekst
import java.io.ByteArrayOutputStream
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
          wpisz samą liczbę w złotych: "30". Jeśli nie ma — zostaw puste.
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
                description = "Sama liczba w złotych, jeśli kwota była w notatce. Inaczej puste.",
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

    /** Notatka w rodzaju „brelok customizowany zalany żywicą 30zł” plus zdjęcie → gotowy opis. */
    suspend fun opiszZdjecie(klucz: String, zdjecie: Uri, notatka: String): SzkicProduktu {
        val obraz = wczytajObraz(zdjecie)
        val zapytanie = ZapytanieGemini(
            systemInstruction = Tresc(parts = listOf(Czesc(text = instrukcja))),
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

        val odpowiedz = api.generuj(MODEL_OPISU, klucz, zapytanie)
        val tekst = odpowiedz.tekst()
            ?: throw IOException(
                odpowiedz.promptFeedback?.blockReason
                    ?.let { "Model odmówił odpowiedzi ($it). Spróbuj z innym zdjęciem." }
                    ?: "Model nie zwrócił opisu. Spróbuj ponownie."
            )
        return json.decodeFromString(tekst)
    }

    /**
     * Poprawione zdjęcie produktowe — czyste tło, wyrównane światło.
     *
     * Model dostaje wyraźny zakaz zmieniania samego wyrobu. Sklep pokazuje
     * rzecz, którą klient dostanie do ręki; „upiększenie”, które dołoży połysk
     * albo zmieni kolor, robi ze zdjęcia produktowego reklamę czegoś innego.
     */
    suspend fun poprawZdjecie(klucz: String, zdjecie: Uri): File {
        val obraz = wczytajObraz(zdjecie)
        val polecenie = """
            Popraw to zdjęcie produktowe do sklepu internetowego:
            - wyczyść tło do jednolitego, neutralnego (ciemna stal albo biel),
            - wyrównaj oświetlenie, usuń ostre cienie i odbicia od lampy,
            - popraw ostrość i balans bieli, wykadruj wyrób na środku.

            NIE WOLNO Ci zmieniać samego wyrobu: kształtu, koloru, faktury,
            wykończenia ani liczby elementów. Nie dodawaj napisów, znaków
            wodnych, rekwizytów ani tła z innego zdjęcia. To ma być ta sama
            rzecz, tylko lepiej sfotografowana.
        """.trimIndent()

        val zapytanie = ZapytanieGemini(
            contents = listOf(
                Tresc(
                    role = "user",
                    parts = listOf(Czesc(inlineData = obraz), Czesc(text = polecenie)),
                )
            ),
            generationConfig = Konfiguracja(responseModalities = listOf("IMAGE", "TEXT")),
        )

        val odpowiedz = api.generuj(MODEL_OBRAZU, klucz, zapytanie)
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

        val bufor = ByteArrayOutputStream()
        mapa.compress(Bitmap.CompressFormat.JPEG, 88, bufor)
        DaneWlasne(
            mimeType = "image/jpeg",
            data = Base64.encodeToString(bufor.toByteArray(), Base64.NO_WRAP),
        )
    }

    private companion object {
        const val MODEL_OPISU = "gemini-2.5-flash"
        const val MODEL_OBRAZU = "gemini-2.5-flash-image"
        const val MAKS_BOK = 1536
    }
}
