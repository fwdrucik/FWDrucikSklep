package pl.fwdrucik.sklep.ui.ekrany

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import android.widget.Toast
import android.widget.VideoView
import android.widget.MediaController
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import pl.fwdrucik.sklep.BuildConfig
import pl.fwdrucik.sklep.dane.KopiaRobocza
import pl.fwdrucik.sklep.dane.Polecenia
import pl.fwdrucik.sklep.dane.Produkt
import pl.fwdrucik.sklep.dane.Statusy
import pl.fwdrucik.sklep.dane.SzkicProduktu
import pl.fwdrucik.sklep.dane.zloteNaGrosze
import pl.fwdrucik.sklep.pomoc.Aparat
import pl.fwdrucik.sklep.pomoc.Podpowiedz
import pl.fwdrucik.sklep.pomoc.Podpowiedzi
import pl.fwdrucik.sklep.ui.groszeNaPole

/**
 * Kreator produktu — zaczyna się od zdjęcia.
 *
 * Taka jest kolejność pracy w warsztacie: wyrób leży na stole, robisz zdjęcie,
 * a opis powstaje z tego, co widać. Formularz z czternastoma pustymi polami na
 * początku to najprostszy sposób, żeby produkt nigdy nie trafił do sklepu.
 *
 * Zdjęcie wybrane przed pierwszym zapisem jest trzymane lokalnie i wysyłane na
 * serwer dopiero po zapisaniu produktu — serwer przypina pliki do istniejącego
 * wyrobu. Ta kolejność jest ukryta w modelu, nie obciąża użytkownika.
 */
@Composable
fun EkranKreatora(
    istniejacy: Produkt?,
    agentPracuje: Boolean,
    maKluczGemini: Boolean,
    naOpiszZeZdjecia: (Uri, String, WyborRoboty, (SzkicProduktu) -> Unit) -> Unit,
    naPoprawZdjecie: (Uri, WyborRoboty, (Uri) -> Unit) -> Unit,
    naPoprawOpis: (String, String, String, String, WyborRoboty, (SzkicProduktu) -> Unit) -> Unit =
        { _, _, _, _, _, _ -> },
    modeleTekstowe: List<String> = emptyList(),
    modeleObrazowe: List<String> = emptyList(),
    modelOpisu: String = "",
    modelObrazu: String = "",
    museDostepny: Boolean = false,
    forgeDziala: Boolean = false,
    comfyDziala: Boolean = false,
    flowDziala: Boolean = false,
    metaDziala: Boolean = false,
    copilotDziala: Boolean = false,
    silnikAnimacji: String = "comfy",
    czynnosci: List<pl.fwdrucik.sklep.ui.Czynnosc> = emptyList(),
    naZapisz: (Produkt, String, String, Uri?, List<Uri>, Uri?, String) -> Unit,
    warsztatGotowy: Boolean,
    naZlecWarsztatowi: (String, Uri, String, String, (Uri) -> Unit) -> Unit,
    naWgrajZdjecie: (Int, Uri, String) -> Unit,
    /** Film albo animacja do ogloszenia: produkt, plik, podpis. */
    naWgrajPlik: (Int, Uri, String) -> Unit = { _, _, _ -> },
    /** Caly ciag: zdjecie, co to jest, ksztalt, dopisek, silnik, wynik zdjecia, wynik animacji. */
    naCiagAuto: (Uri, String, String, String, String, (Uri) -> Unit, (Uri) -> Unit) -> Unit =
        { _, _, _, _, _, _, _ -> },
    naUsunZdjecie: (Int) -> Unit,
    /** Sciaga zdjecie produktu ze sklepu na telefon, zeby dalo sie na nim pracowac. */
    naPobierzZdjecieProduktu: (String, (Uri) -> Unit) -> Unit = { _, _ -> },
    naWyjscie: () -> Unit,
    kopia: KopiaRobocza? = null,
    naZapiszKopie: (KopiaRobocza) -> Unit = {},
    naOdrzucKopie: (Int) -> Unit = {},
    naImportujAllegro: ((String) -> Unit)? = null,
    naSynchronizujAllegro: ((String, String, String, String, String, String, String) -> Unit)? = null,
    badanieCenyWToku: Boolean = false,
    sugerowanaCenaRynkowa: Double? = null,
    sugerowanaCenaAllegro: Double? = null,
    minCenaRynkowa: Double? = null,
    maxCenaRynkowa: Double? = null,
    /** Czy kwoty pochodzÄ… ze zmierzonych ofert Allegro, czy z tabeli awaryjnej. */
    wycenaZmierzona: Boolean = false,
    /** TreĹ›Ä‡ ostrzeĹĽenia, gdy kwota jest szacunkiem. */
    ostrzezenieWyceny: String? = null,
    ofertyRynkowe: List<pl.fwdrucik.sklep.siec.OfertaCenowa> = emptyList(),
    naZbadajCeneRynkowa: ((String, String, (Double, Double, Double, Double) -> Unit) -> Unit)? = null,
    naUtworzSzkicAllegro: ((String, String, Double, String, String, Int, (Boolean, String?) -> Unit) -> Unit)? = null,
) {
    val p = istniejacy ?: Produkt()

    var nazwa by rememberSaveable(p.id) {
        mutableStateOf(if (p.nazwa.isNotBlank()) p.nazwa else kopia?.nazwa.orEmpty())
    }
    var kategoria by rememberSaveable(p.id) {
        mutableStateOf(if (p.kategoria.isNotBlank() && p.kategoria != "inne") p.kategoria else kopia?.kategoria?.ifBlank { "inne" } ?: "inne")
    }
    var opisKrotki by rememberSaveable(p.id) {
        mutableStateOf(if (p.opisKrotki.isNotBlank()) p.opisKrotki else kopia?.opisKrotki.orEmpty())
    }
    var opis by rememberSaveable(p.id) {
        mutableStateOf(if (p.opis.isNotBlank()) p.opis else kopia?.opis.orEmpty())
    }
    var cena by rememberSaveable(p.id) {
        mutableStateOf(if (p.cenaGr > 0) groszeNaPole(p.cenaGr) else kopia?.cena.orEmpty())
    }
    var cenaPromo by rememberSaveable(p.id) {
        mutableStateOf(p.cenaPromoGr?.let(::groszeNaPole) ?: kopia?.cenaPromo.orEmpty())
    }
    var stan by rememberSaveable(p.id) {
        mutableStateOf(p.stan?.toString() ?: kopia?.stan.orEmpty())
    }
    var jednostka by rememberSaveable(p.id) {
        mutableStateOf(if (p.jednostka.isNotBlank()) p.jednostka else kopia?.jednostka?.ifBlank { "szt." } ?: "szt.")
    }
    var waga by rememberSaveable(p.id) {
        mutableStateOf(if (p.wagaG > 0) p.wagaG.toString() else kopia?.waga.orEmpty())
    }
    var czas by rememberSaveable(p.id) {
        mutableStateOf(if (p.czasRealizacji.isNotBlank()) p.czasRealizacji else kopia?.czas.orEmpty())
    }
    var status by rememberSaveable(p.id) {
        mutableStateOf(if (p.status.isNotBlank()) p.status else kopia?.status?.ifBlank { "szkic" } ?: "szkic")
    }
    var pozycja by rememberSaveable(p.id) {
        mutableStateOf(if (p.pozycja > 0) p.pozycja.toString() else kopia?.pozycja?.ifBlank { "100" } ?: "100")
    }
    var notatka by rememberSaveable(p.id) {
        mutableStateOf(kopia?.notatka.orEmpty())
    }
    var opisZdjecia by rememberSaveable(p.id) {
        mutableStateOf(kopia?.opisZdjecia.orEmpty())
    }
    var allegroUrl by rememberSaveable(p.id) {
        mutableStateOf(p.allegroUrl ?: kopia?.allegroUrl.orEmpty())
    }
    var allegroCena by rememberSaveable(p.id) {
        mutableStateOf(p.allegroCenaGr?.let(::groszeNaPole) ?: kopia?.allegroCena.orEmpty())
    }
    var allegroId by rememberSaveable(p.id) {
        mutableStateOf(p.allegroId ?: kopia?.allegroId.orEmpty())
    }
    var allegroStatus by rememberSaveable(p.id) {
        mutableStateOf(if (p.allegroStatus.isNotBlank() && p.allegroStatus != "brak") p.allegroStatus else kopia?.allegroStatus?.ifBlank { "brak" } ?: "brak")
    }

    var sugerowanaRynkowaStr by rememberSaveable(p.id) {
        mutableStateOf(kopia?.sugerowanaCenaRynkowa.orEmpty())
    }
    var sugerowanaAllegroStr by rememberSaveable(p.id) {
        mutableStateOf(kopia?.sugerowanaCenaAllegro.orEmpty())
    }
    var zakresCenStr by rememberSaveable(p.id) {
        mutableStateOf(kopia?.zakresCen.orEmpty())
    }
    var ostatnieWcisniecieWstecz by rememberSaveable { mutableLongStateOf(0L) }
    var tworzenieSzkicuWToku by remember { mutableStateOf(false) }

    LaunchedEffect(sugerowanaCenaRynkowa, sugerowanaCenaAllegro, minCenaRynkowa, maxCenaRynkowa) {
        if (sugerowanaCenaRynkowa != null && sugerowanaCenaRynkowa > 0) {
            sugerowanaRynkowaStr = "%.2f".format(java.util.Locale.US, sugerowanaCenaRynkowa)
        }
        if (sugerowanaCenaAllegro != null && sugerowanaCenaAllegro > 0) {
            sugerowanaAllegroStr = "%.2f".format(java.util.Locale.US, sugerowanaCenaAllegro)
        }
        if (minCenaRynkowa != null && maxCenaRynkowa != null && minCenaRynkowa > 0) {
            zakresCenStr = "%.0f - %.0f zł".format(java.util.Locale.US, minCenaRynkowa, maxCenaRynkowa)
        }
    }

    var lokalneZdjecie by remember(p.id) {
        mutableStateOf<Uri?>(
            kopia?.zdjecie?.takeIf { it.isNotBlank() && java.io.File(it).exists() }?.let { Uri.fromFile(java.io.File(it)) }
        )
    }
    var zdjecieDlaProduktu by remember { mutableIntStateOf(p.id) }

    LaunchedEffect(p.id) {
        val bylNowy = zdjecieDlaProduktu == 0 && p.id > 0
        if (p.id != zdjecieDlaProduktu && !bylNowy) {
            lokalneZdjecie = null
        }
        zdjecieDlaProduktu = p.id
    }
    val dodatkoweKadry = remember(p.id) {
        mutableStateListOf<Uri>().apply {
            kopia?.dodatkoweKadry?.forEach { path ->
                if (path.isNotBlank() && java.io.File(path).exists()) {
                    add(Uri.fromFile(java.io.File(path)))
                }
            }
        }
    }
    var animacja by remember(p.id) {
        mutableStateOf<Uri?>(
            kopia?.animacja?.takeIf { it.isNotBlank() && java.io.File(it).exists() }?.let { Uri.fromFile(java.io.File(it)) }
        )
    }
    var zdjecieOryginalne by remember(p.id) { mutableStateOf<Uri?>(null) }
    var opisPrzedPoprawka by remember(p.id) { mutableStateOf<Triple<String, String, String>?>(null) }
    var pokazOgloszenie by remember(p.id) { mutableStateOf(false) }
    var otwarta by rememberSaveable { mutableIntStateOf(kopia?.sekcjaOtwarta?.takeIf { it in 1..5 } ?: 1) }

    // Swiezo policzony kadr i film — czekaja na decyzje czlowieka.
    //
    // PO CO: dotad wynik wskakiwal na miejsce zdjecia glownego sam. Przy dobrym
    // kadrze to wygoda, przy nieudanym strata: oryginal znikal z pola widzenia,
    // a jedyna droga powrotu byla przez „przed/po". Teraz kazdy wynik zatrzymuje
    // sie na pytaniu — glowne, kolejne w galerii, albo kosz.
    var swiezyKadr by remember(p.id) { mutableStateOf<Uri?>(null) }
    var swiezaAnimacja by remember(p.id) { mutableStateOf<Uri?>(null) }

    // Zdjecie glowne wczytujemy same, przy wejsciu w ekran.
    //
    // PO CO: dotad kreator otwarty na gotowym produkcie albo na niedokonczonej
    // kopii startowal z pustym miejscem na zdjecie i trzeba bylo wskazac je
    // z galerii jeszcze raz — mimo ze jedno lezalo w sklepie, a drugie na
    // telefonie. Tekstu z kopii nadal NIE przywracamy sami: podmiana wpisanych
    // pol pod rekami byłaby gorsza niz utrata kopii. Ze zdjeciem jest inaczej,
    // bo puste miejsce nie ma czego nadpisac.
    LaunchedEffect(p.id, kopia?.zdjecie, p.obrazy.firstOrNull()?.src) {
        if (lokalneZdjecie != null) return@LaunchedEffect

        val zKopii = kopia?.zdjecie.orEmpty()
        if (zKopii.isNotBlank() && java.io.File(zKopii).exists()) {
            lokalneZdjecie = Uri.fromFile(java.io.File(zKopii))
            return@LaunchedEffect
        }

        val zeSklepu = p.obrazy.firstOrNull()?.src
        if (!zeSklepu.isNullOrBlank()) {
            val pelny = if (zeSklepu.startsWith("http")) zeSklepu
            else BuildConfig.ADRES_API + zeSklepu
            naPobierzZdjecieProduktu(pelny) { plik -> lokalneZdjecie = plik }
        }
    }
    // Ktore zadanie czeka na wybor silnika. Pytamy w chwili klikniecia, bo to,
    // co akurat zyje i ile zostalo limitu, zmienia sie w ciagu dnia.
    var pytanieOSilnik by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    // Wstecz NIE przerywa roboty agenta.
    //
    // Podwójne wciśnięcie WSTECZ chroni przed przypadkowym wyjściem i utratą szkicu
    androidx.activity.compose.BackHandler(enabled = true) {
        val teraz = System.currentTimeMillis()
        if (agentPracuje || badanieCenyWToku || tworzenieSzkicuWToku) {
            Toast.makeText(
                context,
                "Trwa wykonywanie zadania w tle — poczekaj na ukończenie.",
                Toast.LENGTH_SHORT
            ).show()
        } else if (teraz - ostatnieWcisniecieWstecz < 2000L) {
            naWyjscie()
        } else {
            ostatnieWcisniecieWstecz = teraz
            Toast.makeText(
                context,
                "Naciśnij WSTECZ ponownie w ciągu 2s, aby opuścić kreator",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    var doUzupelnienia by remember(p.id) { mutableStateOf<List<String>>(emptyList()) }

    // Pierwszy krok drogi „Zdjecie": co ma sie z nim stac. Silnik wybiera sie
    // dopiero potem, bo dopiero wtedy wiadomo, ktore silniki w ogole umieja
    // to zadanie.
    var pytanieOZadaniuZdjecia by remember { mutableStateOf(false) }

    val wybierzDoAgenta = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        // Kopiujemy do pamieci aplikacji OD RAZU, nie dopiero przy zapisie kopii
        // roboczej. Adres `content://` z galerii jest wazny tylko dopoki zyje
        // uprawnienie nadane przy wyborze — po powrocie z aparatu, po obroceniu
        // telefonu albo po ubiciu aplikacji przez system wskazuje w prozne
        // i zdjecie „znika".
        if (uri != null) {
            val zachowane = pl.fwdrucik.sklep.narzedzia.GaleriaZapis.zabezpieczLokalnie(context, uri, p.id, "glowne")
            lokalneZdjecie = if (zachowane != null) Uri.fromFile(zachowane) else (zachowajLokalnie(context, uri) ?: uri)
        }
    }

    // Automatyczne przywracanie stanu szkicu z kopii roboczej
    LaunchedEffect(kopia) {
        if (kopia != null && !kopia.pusta) {
            if (nazwa.isBlank() && kopia.nazwa.isNotBlank()) nazwa = kopia.nazwa
            if (opisKrotki.isBlank() && kopia.opisKrotki.isNotBlank()) opisKrotki = kopia.opisKrotki
            if (opis.isBlank() && kopia.opis.isNotBlank()) opis = kopia.opis
            if (kategoria.isBlank() && kopia.kategoria.isNotBlank()) kategoria = kopia.kategoria
            if (cena.isBlank() && kopia.cena.isNotBlank()) cena = kopia.cena
            if (cenaPromo.isBlank() && kopia.cenaPromo.isNotBlank()) cenaPromo = kopia.cenaPromo
            if (stan.isBlank() && kopia.stan.isNotBlank()) stan = kopia.stan
            if (jednostka.isBlank() && kopia.jednostka.isNotBlank()) jednostka = kopia.jednostka
            if (waga.isBlank() && kopia.waga.isNotBlank()) waga = kopia.waga
            if (czas.isBlank() && kopia.czas.isNotBlank()) czas = kopia.czas
            if (notatka.isBlank() && kopia.notatka.isNotBlank()) notatka = kopia.notatka
            if (opisZdjecia.isBlank() && kopia.opisZdjecia.isNotBlank()) opisZdjecia = kopia.opisZdjecia
            if (lokalneZdjecie == null && kopia.zdjecie.isNotBlank() && java.io.File(kopia.zdjecie).exists()) {
                lokalneZdjecie = Uri.fromFile(java.io.File(kopia.zdjecie))
            }
            if (dodatkoweKadry.isEmpty() && kopia.dodatkoweKadry.isNotEmpty()) {
                kopia.dodatkoweKadry.forEach { path ->
                    if (java.io.File(path).exists()) {
                        dodatkoweKadry.add(Uri.fromFile(java.io.File(path)))
                    }
                }
            }
            if (animacja == null && kopia.animacja.isNotBlank() && java.io.File(kopia.animacja).exists()) {
                animacja = Uri.fromFile(java.io.File(kopia.animacja))
            }
            if (allegroUrl.isBlank() && kopia.allegroUrl.isNotBlank()) allegroUrl = kopia.allegroUrl
            if (allegroCena.isBlank() && kopia.allegroCena.isNotBlank()) allegroCena = kopia.allegroCena
            if (allegroId.isBlank() && kopia.allegroId.isNotBlank()) allegroId = kopia.allegroId
            if (allegroStatus == "brak" && kopia.allegroStatus.isNotBlank() && kopia.allegroStatus != "brak") allegroStatus = kopia.allegroStatus
            if (sugerowanaRynkowaStr.isBlank() && kopia.sugerowanaCenaRynkowa.isNotBlank()) sugerowanaRynkowaStr = kopia.sugerowanaCenaRynkowa
            if (sugerowanaAllegroStr.isBlank() && kopia.sugerowanaCenaAllegro.isNotBlank()) sugerowanaAllegroStr = kopia.sugerowanaCenaAllegro
            if (zakresCenStr.isBlank() && kopia.zakresCen.isNotBlank()) zakresCenStr = kopia.zakresCen
        }
    }

    // Pamiec robocza: kazda zmiana pola odklada sie sama, bez przycisku.
    val biezaca = KopiaRobocza(
        id = p.id,
        nazwa = nazwa, kategoria = kategoria, opisKrotki = opisKrotki, opis = opis,
        cena = cena, cenaPromo = cenaPromo, stan = stan, jednostka = jednostka,
        waga = waga, czas = czas, status = status, pozycja = pozycja,
        notatka = notatka, opisZdjecia = opisZdjecia,
        zdjecie = lokalneZdjecie?.path ?: lokalneZdjecie?.toString().orEmpty(),
        dodatkoweKadry = dodatkoweKadry.mapNotNull { it.path ?: it.toString() },
        animacja = animacja?.path ?: animacja?.toString().orEmpty(),
        sekcjaOtwarta = otwarta,
        allegroUrl = allegroUrl,
        allegroCena = allegroCena,
        allegroId = allegroId,
        allegroStatus = allegroStatus,
        sugerowanaCenaRynkowa = sugerowanaRynkowaStr,
        sugerowanaCenaAllegro = sugerowanaAllegroStr,
        zakresCen = zakresCenStr,
    )
    LaunchedEffect(biezaca) { naZapiszKopie(biezaca) }

    var kopiaOdrzucona by remember(p.id) { mutableStateOf(false) }
    val mozliwePrzywrocenie = false

    var adresZAparatu by remember { mutableStateOf<Uri?>(null) }
    val zrobZdjecie = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { udane: Boolean ->
        if (udane) {
            val zrobione = adresZAparatu
            if (zrobione != null) {
                val bezpieczny = pl.fwdrucik.sklep.narzedzia.GaleriaZapis.zabezpieczLokalnie(context, zrobione, p.id, "aparat")
                lokalneZdjecie = if (bezpieczny != null) Uri.fromFile(bezpieczny) else (zachowajLokalnie(context, zrobione) ?: zrobione)
            }
        }
    }

    val wybierzWieleKolejnych = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 8)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            if (p.id > 0) {
                uris.forEach { u ->
                    naWgrajZdjecie(p.id, u, opisZdjecia.ifBlank { nazwa })
                }
            } else {
                uris.forEach { u ->
                    val bezpieczny = pl.fwdrucik.sklep.narzedzia.GaleriaZapis.zabezpieczLokalnie(context, u, p.id, "dodatkowe")
                    val zachowane = if (bezpieczny != null) Uri.fromFile(bezpieczny) else (zachowajLokalnie(context, u) ?: u)
                    if (lokalneZdjecie == null) {
                        lokalneZdjecie = zachowane
                    } else {
                        dodatkoweKadry.add(zachowane)
                    }
                }
            }
        }
    }


    // Co zrobic z gotowym szkicem od agenta. Jedno miejsce, bo wolaja to
    // dwie drogi: opis ze zdjecia i poprawa napisanego opisu.
    val zastosujSzkic: (SzkicProduktu) -> Unit = { szkic ->
        opisPrzedPoprawka = Triple(nazwa, opisKrotki, opis)
        if (szkic.nazwa.isNotBlank()) nazwa = szkic.nazwa
        if (szkic.opisKrotki.isNotBlank()) opisKrotki = szkic.opisKrotki
        if (szkic.opis.isNotBlank()) opis = szkic.opis
        if (szkic.kategoria.isNotBlank() && szkic.kategoria != "inne") kategoria = szkic.kategoria
        if (szkic.opisZdjecia.isNotBlank()) opisZdjecia = szkic.opisZdjecia
        if (szkic.cena.isNotBlank()) cena = szkic.cena
        if (szkic.waga.isNotBlank()) waga = szkic.waga
        if (szkic.czasRealizacji.isNotBlank()) czas = szkic.czasRealizacji
        if (szkic.jednostka.isNotBlank()) jednostka = szkic.jednostka
        doUzupelnienia = szkic.doUzupelnienia
    }

    val nadpiszSzkicem: (SzkicProduktu) -> Unit = { szkic ->
        opisPrzedPoprawka = Triple(nazwa, opisKrotki, opis)
        if (szkic.nazwa.isNotBlank()) nazwa = szkic.nazwa
        if (szkic.opisKrotki.isNotBlank()) opisKrotki = szkic.opisKrotki
        if (szkic.opis.isNotBlank()) opis = szkic.opis
        if (szkic.kategoria.isNotBlank() && szkic.kategoria != "inne") kategoria = szkic.kategoria
        if (szkic.cena.isNotBlank()) cena = szkic.cena
        if (szkic.waga.isNotBlank()) waga = szkic.waga
        if (szkic.czasRealizacji.isNotBlank()) czas = szkic.czasRealizacji
        if (szkic.doUzupelnienia.isNotEmpty()) doUzupelnienia = szkic.doUzupelnienia
    }


    // Pierwszy krok: co zrobic ze zdjeciem.
    //
    // PO CO OSOBNE PYTANIE: „popraw zdjecie" znaczylo dotad trzy rozne rzeczy
    // naraz i kazda potrzebuje innego polecenia dla modelu. Rozdzielone, dostaja
    // wlasne prompty i wlasna liste silnikow — nikt nie wybierze karty graficznej
    // do wygladzenia swiatla ani Muse do animacji.
    if (pytanieOZadaniuZdjecia) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pytanieOZadaniuZdjecia = false },
            title = { Text("Co zrobić ze zdjęciem?") },
            text = {
                Column {
                    listOf(
                        Triple(
                            "animacja", "Animacja",
                            "Wyrób obraca się wokół własnej osi. Film albo GIF, " +
                                "zależnie od silnika.",
                        ),
                        Triple(
                            "tlo", "Tło",
                            "Wycina tło i stawia wyrób na pastelu z delikatnym " +
                                "efektem w głębi.",
                        ),
                        Triple(
                            "upiekszanie", "Upiększanie",
                            "Poprawa światła, ostrości i kontrastu. Wyrób zostaje " +
                                "dokładnie taki, jaki jest.",
                        ),
                        Triple(
                            "auto-ciag", "Wszystko po kolei",
                            "Tło, potem światło, na końcu animacja. Silnik do każdego " +
                                "kroku dobiera agregator — Ty tylko czekasz.",
                        ),
                    ).forEach { (klucz, tytul, opis) ->
                        androidx.compose.material3.TextButton(
                            onClick = {
                                pytanieOZadaniuZdjecia = false
                                pytanieOSilnik = klucz
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(tytul, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    opis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { pytanieOZadaniuZdjecia = false }
                ) { Text("Anuluj") }
            },
        )
    }

    // Okienko wyboru silnika. Pytamy przy kazdym zleceniu, bo odpowiedz zalezy
    // od tego, co akurat zyje: limit Gemini potrafi paść w srodku partii zdjec,
    // a komputer w warsztacie bywa wylaczony.
    pytanieOSilnik?.let { zadanie ->
        val opcje = when (zadanie) {
            "opis", "poprawa-opisu" -> listOf(
                TrzyOpcje(
                    "meta", "Agent Meta AI (Llama 3 Vision)",
                    "Generowanie opisu rzemiosła i marketingu z Meta AI.",
                    true,
                ),
                TrzyOpcje(
                    "copilot", "Agent Microsoft Copilot (GPT-4o)",
                    "Opis handlowy i wycena rynkowa z Microsoft Copilot.",
                    true,
                ),
                TrzyOpcje(
                    "gemini", "Agent Google Gemini (3.6 Flash / Pro)",
                    "Oficjalne modele Google: 3.6 Flash dla szybkości, Pro dla redakcji.",
                    true,
                ),
                TrzyOpcje(
                    "muse", "Agent Muse Code (Lokalny PC CLI)",
                    "Lokalny agent redakcyjny na Twoim komputerze bez opłat i limitów.",
                    true,
                ),
            )
            "auto-ciag" -> listOf(
                TrzyOpcje(
                    "meta", "Agent Meta AI (Pełny ciąg)",
                    "Kadr studyjny + animacja obrotowa 360° GIF przez Meta AI.",
                    true,
                ),
                TrzyOpcje(
                    "flow", "Agent Google Flow (Pełny ciąg)",
                    "Kadr studyjny + ujęcie Veo 3.1 & Omni z dźwiękiem.",
                    true,
                ),
                TrzyOpcje(
                    "gemini", "Agent Google Gemini (Pełny ciąg)",
                    "Wymiana tła Imagen 3.0 + animacja Veo w chmurze.",
                    true,
                ),
                TrzyOpcje(
                    "forge", "Agent Karta RTX 5070 (Pełny ciąg)",
                    "Wycięcie tła maską (rembg 1.3s) + rendering i animacja lokalnie.",
                    true,
                ),
                TrzyOpcje(
                    "copilot", "Agent Microsoft Copilot (Pełny ciąg)",
                    "Kadr DALL-E 3 + animacja przez Copilota.",
                    true,
                ),
            )
            "tlo" -> listOf(
                TrzyOpcje(
                    "meta", "Agent Meta AI (Przeglądarka)",
                    "Wymiana tła i stylizacja kadru w Meta AI (16:9, 9:16, 1:1).",
                    true,
                ),
                TrzyOpcje(
                    "flow", "Agent Google Flow (Przeglądarka)",
                    "Generowanie i stylizacja kadru przez Google Flow.",
                    true,
                ),
                TrzyOpcje(
                    "copilot", "Agent Microsoft Copilot (DALL-E 3)",
                    "Stylizacja tła i kadru przez Copilota w przeglądarce.",
                    true,
                ),
                TrzyOpcje(
                    "forge", "Agent Karta RTX 5070 (Rembg)",
                    "Wycięcie tła maską (1.3s) i pastelowe tło studyjne na PC.",
                    true,
                ),
                TrzyOpcje(
                    "gemini", "Agent Google Gemini (Imagen 3)",
                    "Generowanie i stylizacja tła przez Imagen 3.0 w chmurze.",
                    true,
                ),
            )
            "upiekszanie" -> listOf(
                TrzyOpcje(
                    "meta", "Agent Meta AI (Przeglądarka)",
                    "Poprawa oświetlenia, ostrości i nastroju przez Meta AI.",
                    true,
                ),
                TrzyOpcje(
                    "flow", "Agent Google Flow (Przeglądarka)",
                    "Poprawa jakości ujęcia przez Google Flow.",
                    true,
                ),
                TrzyOpcje(
                    "copilot", "Agent Microsoft Copilot (Przeglądarka)",
                    "Poprawa stylu i światła przez Copilota.",
                    true,
                ),
                TrzyOpcje(
                    "forge", "Agent Karta RTX 5070 (PC)",
                    "Obróbka światła i kontrastu na lokalnej karcie graficznej.",
                    true,
                ),
                TrzyOpcje(
                    "gemini", "Agent Google Gemini (Imagen 3)",
                    "Poprawa detali i oświetlenia przez chmurę Google.",
                    true,
                ),
            )
            else -> listOf(
                TrzyOpcje(
                    "meta", "Agent Meta AI (Turntable 360° GIF)",
                    "Natywny obrót 360° GIF w przeglądarce Meta AI (format 9:16 lub 16:9).",
                    true,
                ),
                TrzyOpcje(
                    "flow", "Agent Google Flow (Veo 3.1 & Omni)",
                    "Generowanie wideo Veo 3.1 przez Agenta Google Flow (720p 8s).",
                    true,
                ),
                TrzyOpcje(
                    "gemini", "Agent Google Gemini (Veo Cloud)",
                    "Generowanie wideo przez oficjalny model Google Veo.",
                    true,
                ),
                TrzyOpcje(
                    "comfy", "Agent Karta RTX 5070 (ComfyUI)",
                    "Lokalna animacja na karcie graficznej PC.",
                    true,
                ),
            )
        }

        WyborSilnika(
            tytul = when (zadanie) {
                "opis" -> "Czym napisać opis?"
                "poprawa-opisu" -> "Czym poprawić opis?"
                "auto-ciag" -> "Cały ciąg — tło, światło, animacja"
                "tlo" -> "Czym wymienić tło?"
                "upiekszanie" -> "Czym poprawić światło i ostrość?"
                else -> "Czym zrobić animację?"
            },
            opcje = opcje,
            modele = if (zadanie in listOf("tlo", "upiekszanie", "auto-ciag")) modeleObrazowe
                     else modeleTekstowe,
            modelDomyslny = if (zadanie in listOf("tlo", "upiekszanie", "auto-ciag")) modelObrazu
                            else modelOpisu,
            naAnuluj = { pytanieOSilnik = null },
            // Ksztalt kadru ma sens przy zdjeciu i przy animacji; przy opisie
            // nie ma czego ksztaltowac.
            pokazProporcje = zadanie in listOf("tlo", "upiekszanie", "animacja", "auto-ciag"),
            naWykonaj = { silnik, model, dodatkowe, proporcje ->
                pytanieOSilnik = null
                when (zadanie) {
                    "opis" -> naOpiszZeZdjecia(
                        lokalneZdjecie!!,
                        listOf(notatka, dodatkowe).filter { it.isNotBlank() }.joinToString(". "),
                        WyborRoboty(silnik, model),
                        zastosujSzkic,
                    )
                    "poprawa-opisu" -> {
                        opisPrzedPoprawka = Triple(nazwa, opisKrotki, opis)
                        naPoprawOpis(nazwa, opisKrotki, opis, cena, WyborRoboty(silnik, model, dodatkowe), nadpiszSzkicem)
                    }
                    "auto-ciag" -> naCiagAuto(
                        lokalneZdjecie!!,
                        notatka.ifBlank { nazwa },
                        proporcje,
                        dodatkowe,
                        silnik,
                        { poprawione ->
                            // Kazdy krok oddaje kadr od razu, wiec widac postep,
                            // a nie tylko kolo na ekranie. Oryginal zapamietujemy
                            // raz — przy pierwszym podmienieniu.
                            if (zdjecieOryginalne == null) zdjecieOryginalne = lokalneZdjecie
                            lokalneZdjecie = poprawione
                        },
                        { film -> swiezaAnimacja = film },
                    )
                    "tlo", "upiekszanie" -> naPoprawZdjecie(
                        lokalneZdjecie!!,
                        WyborRoboty(
                            silnik, model,
                            // Domyslne polecenie zadania + to, co dopisal czlowiek.
                            // Kolejnosc nie jest obojetna: dopisek na koncu wazy
                            // wiecej i moze poprawic to, co stoi wczesniej.
                            listOf(
                                if (zadanie == "tlo")
                                    polecenieTla(notatka.ifBlank { nazwa }.ifBlank { "handmade craft object" })
                                else
                                    polecenieUpiekszania(notatka.ifBlank { nazwa }.ifBlank { "handmade craft object" }),
                                dodatkowe,
                            ).filter { it.isNotBlank() }.joinToString(", "),
                            proporcje,
                        ),
                    ) { poprawione -> swiezyKadr = poprawione }
                    else -> {
                        // „auto" rozstrzyga model po stanie serwera; tutaj tylko
                        // tlumaczymy wybor na nazwe zadania, ktora rozumie serwer.
                        val zadanieSerwera = when (silnik) {
                            "flow" -> "animacja-flow"
                            "gemini" -> "animacja-gemini"
                            "meta" -> "animacja-meta"
                            "comfy" -> "animacja"
                            else -> when (silnikAnimacji) {
                                "flow" -> "animacja-flow"
                                "gemini" -> "animacja-gemini"
                                "meta" -> "animacja-meta"
                                else -> "animacja"
                            }
                        }
                        // Zdjecie wyrobu idzie jako kadr odniesienia — Veo animuje
                        // TEN przedmiot, a nie wyobrazenie o nim. Opis mowi, co ma
                        // sie dziac; dodatkowe polecenie doklejamy na koncu.
                        // Bez nazwy i bez notatki prompt szedl jako
                        // „product turntable: , slow smooth..." — z pusta
                        // dziura w miejscu przedmiotu. Model dostawal wtedy
                        // samo zdjecie i zdanie o obrocie czegokolwiek.
                        // Podstawiamy neutralny rzeczownik zamiast pustki.
                        val coAnimujemy = notatka.ifBlank { nazwa }
                            .ifBlank { "handmade craft object from the photo" }
                        naZlecWarsztatowi(
                            zadanieSerwera,
                            lokalneZdjecie!!,
                            listOf(
                                poleceniObrotu(coAnimujemy),
                                dodatkowe,
                            ).filter { it.isNotBlank() }.joinToString(", "),
                            proporcje,
                        ) { wynik -> swiezaAnimacja = wynik }
                    }
                }
            },
        )
    }

    // Pytanie o swiezo policzony kadr. Stoi POZA Column, jak pozostale dialogi:
    // ma byc widoczne niezaleznie od przewiniecia i od tego, ktora sekcja jest
    // rozwinieta.
    swiezyKadr?.let { kadr ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Gotowy kadr — co z nim zrobić?") },
            text = {
                Column {
                    AsyncImage(
                        model = kadr,
                        contentDescription = "Świeżo policzony kadr",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                    )
                    Text(
                        "Główne zdjęcie idzie na pierwszą pozycję w ogłoszeniu i to " +
                            "z niego liczą się dalsze rzeczy — animacja, poprawa światła.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // Poprzednie glowne nie ginie: ladzie w oryginale, wiec
                    // porownanie „przed/po" dalej dziala.
                    if (lokalneZdjecie != null) zdjecieOryginalne = lokalneZdjecie
                    lokalneZdjecie = kadr
                    swiezyKadr = null
                }) { Text("Ustaw jako główne") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        if (p.id > 0) {
                            naWgrajZdjecie(p.id, kadr, opisZdjecia.ifBlank { nazwa })
                        } else {
                            dodatkoweKadry.add(kadr)
                        }
                        swiezyKadr = null
                    }) { Text("Do galerii") }
                    TextButton(onClick = { swiezyKadr = null }) { Text("Usuń") }
                }
            },
        )
    }

    swiezaAnimacja?.let { film ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Gotowy materiał ruchomy — co z nim zrobić?") },
            text = {
                Column {
                    val sciezka = film.toString().lowercase()
                    val jestWideo = sciezka.endsWith(".mp4") || sciezka.contains("video/mp4")
                    if (jestWideo) {
                        var proporcjaFilmu by androidx.compose.runtime.remember(film) {
                            androidx.compose.runtime.mutableStateOf(16f / 9f)
                        }
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                                    setVideoURI(film)
                                    setOnPreparedListener { odtwarzacz ->
                                        odtwarzacz.isLooping = true
                                        if (odtwarzacz.videoWidth > 0 && odtwarzacz.videoHeight > 0) {
                                            proporcjaFilmu =
                                                odtwarzacz.videoWidth.toFloat() / odtwarzacz.videoHeight
                                        }
                                        start()
                                    }
                                }
                            },
                            update = { it.setVideoURI(film) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .aspectRatio(proporcjaFilmu),
                        )
                    } else {
                        AsyncImage(
                            model = film,
                            contentDescription = "Gotowa animacja GIF",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                        )
                    }
                    Text(
                        "Materiał w ogłoszeniu staje przed zdjęciami i to on rusza się " +
                            "na stronie produktu. Zostawiony w kreatorze posłuży do " +
                            "dalszej roboty, ale nie trafi jeszcze do sklepu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    animacja = film
                    // Do sklepu material idzie tylko przy zapisanym produkcie:
                    // serwer przypina pliki do istniejacego wyrobu.
                    if (p.id > 0) naWgrajPlik(p.id, film, nazwa)
                    swiezaAnimacja = null
                }) { Text(if (p.id > 0) "Dodaj do ogłoszenia" else "Zatrzymaj") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        animacja = film
                        swiezaAnimacja = null
                    }) { Text("Zostaw w kreatorze") }
                    TextButton(onClick = { swiezaAnimacja = null }) { Text("Usuń") }
                }
            },
        )
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text(
            if (p.id == 0) "Nowy produkt" else "Edycja produktu",
            style = MaterialTheme.typography.headlineSmall,
        )

        // =====================================================================
        // SZYBKI ASYSTENT DLA ŻONY (KROK PO KROKU 1-KLIK)
        // =====================================================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            ),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✨", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "Szybki Asystent (Krok po kroku)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Od zdjęcia na stole do prywatnego szkicu na Allegro",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // KROK 1: Zdjęcie
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (lokalneZdjecie != null) "✅ Krok 1: Zdjęcie wybrane" else "📸 Krok 1: Wczytaj zdjęcie",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = if (lokalneZdjecie != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (lokalneZdjecie == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val plik = Aparat.nowePlikDoZdjecia(context)
                                val adres = Aparat.adresDlaAparatu(context, plik)
                                adresZAparatu = adres
                                zrobZdjecie.launch(adres)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Aparat")
                        }
                        OutlinedButton(
                            onClick = {
                                wybierzDoAgenta.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Galeria")
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // KROK 2: Opis & Wycena w internecie
                Text(
                    "🔍 Krok 2: Opis i badanie cen w internecie",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val frazaDoSzukania = nazwa.ifBlank { notatka.take(50).ifBlank { "rękodzieło artystyczne" } }
                            naZbadajCeneRynkowa?.invoke(frazaDoSzukania, kategoria) { sug, sugAllegro, minC, maxC ->
                                if (cena.isBlank()) cena = "%.2f".format(java.util.Locale.US, sug)
                                if (allegroCena.isBlank()) allegroCena = "%.2f".format(java.util.Locale.US, sugAllegro)
                            }
                        },
                        enabled = !badanieCenyWToku && (nazwa.isNotBlank() || notatka.isNotBlank() || lokalneZdjecie != null),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (badanieCenyWToku) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text("Badam rynek...", style = MaterialTheme.typography.labelSmall)
                        } else {
                            // Nazwa mowi, co naprawde sie dzieje: OLX i Erli nigdy nie
                        // byly odpytywane, a obiecywala je etykieta przycisku.
                        Text("🔍 Sprawdź ceny na Allegro", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }

                    OutlinedButton(
                        onClick = { pytanieOSilnik = "opis" },
                        enabled = lokalneZdjecie != null && !agentPracuje,
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Napisz opis", style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Wyniki badania cen rynkowych
                if (sugerowanaRynkowaStr.isNotBlank() || sugerowanaCenaRynkowa != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                if (wycenaZmierzona) "📊 Ceny zmierzone na Allegro:"
                                else "⚠️ SZACUNEK — to nie są ceny z rynku:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                color = if (wycenaZmierzona) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                            )
                            // Szacunek potrafi rozminac sie z rynkiem trzykrotnie,
                            // a wyglada na ekranie tak samo jak zmierzona cena.
                            // Powod podajemy wprost, zeby dalo sie zdecydowac,
                            // czy sprawdzic cene recznie.
                            if (!wycenaZmierzona && !ostrzezenieWyceny.isNullOrBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    ostrzezenieWyceny,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            val sRyn = sugerowanaRynkowaStr.ifBlank { sugerowanaCenaRynkowa?.let { "%.2f".format(java.util.Locale.US, it) }.orEmpty() }
                            val sAll = sugerowanaAllegroStr.ifBlank { sugerowanaCenaAllegro?.let { "%.2f".format(java.util.Locale.US, it) }.orEmpty() }
                            Text(
                                (if (wycenaZmierzona) "• Mediana ofert: " else "• Szacowana cena: ") +
                                        "$sRyn zł" +
                                        (if (zakresCenStr.isNotBlank()) " (zakres: $zakresCenStr)" else ""),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "• Sugerowana na Allegro (+12% prowizji): $sAll zł",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (sRyn.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { cena = sRyn },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Ustaw do sklepu ($sRyn zł)", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                }
                                if (sAll.isNotBlank()) {
                                    Button(
                                        onClick = { allegroCena = sAll },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Ustaw na Allegro ($sAll zł)", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // KROK 3: Tło i Światło
                Text("🎨 Krok 3: Tło i światło studyjne", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { pytanieOZadaniuZdjecia = true },
                        enabled = lokalneZdjecie != null && !agentPracuje,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PhotoFilter, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("🌸 Pastelowe studio", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = { pytanieOSilnik = "auto-ciag" },
                        enabled = lokalneZdjecie != null && !agentPracuje,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Wszystko na raz (AI)", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // KROK 4: Animacja 360°
                Text(
                    if (animacja != null) "✅ Krok 4: Animacja 360° gotowa" else "🔄 Krok 4: Animacja obrotu 360°",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = if (animacja != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (animacja == null) {
                    OutlinedButton(
                        onClick = { pytanieOSilnik = "animacja" },
                        enabled = lokalneZdjecie != null && !agentPracuje,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    ) {
                        Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Utwórz obrót 360° (Google Flow / Meta AI)")
                    }
                }

                Spacer(Modifier.height(12.dp))

                // KROK 5: Prywatny Szkic na Allegro (REST API)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f))
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🟠", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Krok 5: Utwórz prywatny szkic na Allegro",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Text(
                            "Status INACTIVE — oferta jest w 100% prywatna, niewidoczna dla kupujących, dopóki sama jej nie sprawdzisz.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                        )

                        if (allegroId.isNotBlank() && allegroUrl.isNotBlank()) {
                            Text(
                                "✅ Szkic podpięty (ID: $allegroId)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        runCatching {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(allegroUrl.trim()))
                                            context.startActivity(intent)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Otwórz na Allegro ↗", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        } else {
                            val tytulGotowy = pl.fwdrucik.sklep.narzedzia.AllegroFormat.oczyscTytul(nazwa.ifBlank { notatka })
                            if (tytulGotowy.isNotBlank()) {
                                Text(
                                    "Tytuł oferty: „$tytulGotowy” (${tytulGotowy.length}/75 znaków)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    val tytulOczyszczony = pl.fwdrucik.sklep.narzedzia.AllegroFormat.oczyscTytul(nazwa.ifBlank { notatka })
                                    val cenaKwota = (zloteNaGrosze(allegroCena.ifBlank { cena }) ?: 0) / 100.0
                                    val htmlOpis = pl.fwdrucik.sklep.narzedzia.AllegroFormat.zbudujOpisHtml(nazwa, opisKrotki, opis)
                                    val urlZdjecia = p.obrazy.firstOrNull()?.src?.let {
                                        if (it.startsWith("http")) it else BuildConfig.ADRES_API + it
                                    }.orEmpty()

                                    tworzenieSzkicuWToku = true
                                    naUtworzSzkicAllegro?.invoke(
                                        tytulOczyszczony,
                                        kategoria,
                                        cenaKwota,
                                        htmlOpis,
                                        urlZdjecia,
                                        stan.toIntOrNull() ?: 1
                                    ) { sukces, zwroconyUrl ->
                                        tworzenieSzkicuWToku = false
                                        if (sukces) {
                                            allegroStatus = "szkic"
                                            if (zwroconyUrl != null) {
                                                allegroUrl = zwroconyUrl
                                                val wyciagnieteId = zwroconyUrl.substringAfterLast("/")
                                                if (wyciagnieteId.isNotBlank() && wyciagnieteId.all { it.isDigit() }) {
                                                    allegroId = wyciagnieteId
                                                }
                                            }
                                            Toast.makeText(context, "Prywatny szkic na Allegro utworzony!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Błąd tworzenia szkicu Allegro — sprawdź komputer w warsztacie", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = !tworzenieSzkicuWToku && (nazwa.isNotBlank() || notatka.isNotBlank()) && (cena.isNotBlank() || allegroCena.isNotBlank()),
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                if (tworzenieSzkicuWToku) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onTertiary)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Tworzę szkic...", style = MaterialTheme.typography.labelSmall)
                                } else {
                                    Text("🛒 Utwórz prywatny szkic na Allegro (INACTIVE)")
                                }
                            }
                        }
                    }
                }
            }
        }


        // Baner pamieci roboczej. Nic nie przywracamy sami: podmiana pol pod
        // rekami uzytkownika bylaby gorsza niz utrata kopii. Decyzja nalezy
        // do czlowieka, a odrzucenie kasuje kopie na dobre.
        if (mozliwePrzywrocenie && kopia != null) {
            Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Niedokończona robota", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Z tego kreatora został zapis" +
                            (if (kopia.zapisano > 0) " z " + java.text.SimpleDateFormat("HH:mm, d MMM", java.util.Locale("pl")).format(java.util.Date(kopia.zapisano)) else "") +
                            (if (kopia.nazwa.isNotBlank()) ": „" + kopia.nazwa + "”" else "") + ".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            nazwa = kopia.nazwa
                            kategoria = kopia.kategoria
                            opisKrotki = kopia.opisKrotki
                            opis = kopia.opis
                            cena = kopia.cena
                            cenaPromo = kopia.cenaPromo
                            stan = kopia.stan
                            jednostka = kopia.jednostka
                            waga = kopia.waga
                            czas = kopia.czas
                            status = kopia.status
                            pozycja = kopia.pozycja
                            notatka = kopia.notatka
                            opisZdjecia = kopia.opisZdjecia
                            if (kopia.zdjecie.isNotBlank()) {
                                lokalneZdjecie = Uri.fromFile(java.io.File(kopia.zdjecie))
                            }
                            kopiaOdrzucona = true
                        }) { Text("Przywróć") }
                        TextButton(onClick = {
                            kopiaOdrzucona = true
                            naOdrzucKopie(p.id)
                        }) { Text("Odrzuć") }
                    }
                    Text(
                        if (kopia.zdjecie.isNotBlank()) "Zdjęcie też jest w kopii."
                        else "Zdjęcia nie ma w kopii — wybierz je jeszcze raz.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Zdjecie glowne stoi PONAD sekcjami i widac je zawsze.
        //
        // PO CO: to jest jedyna rzecz, na ktora patrzy sie przy kazdym kroku —
        // przy pisaniu opisu, przy cenie i przy podgladzie ogloszenia. Schowane
        // w zwinietej sekcji znaczyloby, ze przy wpisywaniu ceny nie wiadomo,
        // czego ta cena dotyczy.
        lokalneZdjecie?.let { uri ->
            Box(Modifier.padding(top = 12.dp)) {
                val modelObrazu = remember(uri) {
                    val s = uri.toString()
                    val p = uri.path.orEmpty()
                    if (uri.scheme == null || uri.scheme == "file") {
                        val sciezka = if (p.isNotBlank()) p else s.removePrefix("file://")
                        java.io.File(sciezka)
                    } else {
                        uri
                    }
                }
                AsyncImage(
                    model = modelObrazu,
                    contentDescription = "Zdjęcie główne produktu",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                )
                IconButton(
                    onClick = { lokalneZdjecie = null },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Odrzuć to zdjęcie")
                }
            }
        }

        // ------------------------------------------------- krok 1: zdjęcie
        NaglowekSekcji(
            numer = 1,
            tytul = "Zacznij od zdjęcia",
            podpis = if (lokalneZdjecie != null) "zdjęcie wybrane" else "brak zdjęcia",
            otwarta = otwarta == 1,
            naKlik = { otwarta = if (otwarta == 1) 0 else 1 },
        )
        if (otwarta == 1) {

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = {
                    val plik = Aparat.nowePlikDoZdjecia(context)
                    val adres = Aparat.adresDlaAparatu(context, plik)
                    adresZAparatu = adres
                    zrobZdjecie.launch(adres)
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Text("  Aparat")
            }
            OutlinedButton(
                onClick = {
                    wybierzDoAgenta.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                Text("  Galeria")
            }
        }

        if (lokalneZdjecie != null) {
            PoleZPodpowiedzia(
                notatka,
                { notatka = it },
                Podpowiedzi.notatkaDlaAgenta,
            )

            // Główny przycisk automatyzacji Antigravity
            Button(
                onClick = { pytanieOSilnik = "auto-ciag" },
                enabled = lokalneZdjecie != null && !agentPracuje,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                if (agentPracuje) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("🤖 Antigravity pracuje...")
                } else {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("🤖 Antigravity — Stwórz wszystko z kadru", style = MaterialTheme.typography.titleSmall)
                }
            }

            // Pojedyncze akcje narzędziowe
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(
                    onClick = { pytanieOSilnik = "opis" },
                    enabled = lokalneZdjecie != null && !agentPracuje,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 38.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Opis", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = { pytanieOZadaniuZdjecia = true },
                    enabled = lokalneZdjecie != null && !agentPracuje,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 38.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Filled.PhotoFilter, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Tło / Kadr", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = {
                        if (lokalneZdjecie != null) {
                            pytanieOSilnik = "animacja"
                        }
                    },
                    enabled = lokalneZdjecie != null && !agentPracuje,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 38.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Wideo", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
                }
            }

            if (dodatkoweKadry.isNotEmpty()) {
                Text(
                    "Warianty wygenerowane i kadry (kliknij, aby przenieść na górę do obróbki):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                ) {
                    items(dodatkoweKadry) { kadr ->
                        Card(
                            onClick = {
                                val temp = lokalneZdjecie
                                lokalneZdjecie = kadr
                                dodatkoweKadry.remove(kadr)
                                if (temp != null) {
                                    dodatkoweKadry.add(0, temp)
                                }
                            },
                            modifier = Modifier.width(96.dp),
                        ) {
                            Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                AsyncImage(
                                    model = kadr,
                                    contentDescription = "Wariant kadru",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(88.dp),
                                )
                                Text(
                                    "⬆️ Na górę",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }


            // Skrotow do warsztatu juz tu nie ma.
            //
            // Robily to samo, co „Zdjecie", tylko z silnikiem i ksztaltem
            // wybranym za czlowieka — czyli dwa ukryte ustawienia domyslne,
            // o ktorych nikt nie wiedzial i ktore rozjezdzaly sie z tym,
            // co stalo w Pomocy. Zostaje jedna droga: CO, potem CZYM.
            if (warsztatGotowy) {
                Text(
                    "Komputer w warsztacie odpowiada — Forge, karta i Meta AI są " +
                        "w wyborze pod przyciskiem „Zdjęcie”. Animacja potrafi zająć " +
                        "kilka minut.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            val przed = zdjecieOryginalne
            val po = lokalneZdjecie
            if (przed != null && po != null && przed != po) {
                PodgladZdjeciaPrzedPo(
                    przed = przed,
                    po = po,
                    naZastapGlowne = {
                        // Wersja AI zostaje nowym zdjeciem glownym
                        zdjecieOryginalne = null
                    },
                    naDodajDoGalerii = {
                        // Przywracamy oryginal jako glowne, a kadr AI trafia do galerii jako kolejne zdjecie
                        lokalneZdjecie = przed
                        dodatkoweKadry.add(po)
                        zdjecieOryginalne = null
                    },
                    naPrzywrocOryginal = {
                        lokalneZdjecie = przed
                        zdjecieOryginalne = null
                    },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            animacja?.let { plik ->
                PodgladAnimacji(
                    plik,
                    Modifier.padding(top = 10.dp),
                    // Dopiero zapisany produkt ma numer, do ktorego serwer
                    // przypnie plik — przy nowym szkicu przycisku nie ma.
                    naDodajDoOgloszenia = if (p.id != 0) {
                        { naWgrajPlik(p.id, plik, nazwa.ifBlank { "Animacja produktu" }) }
                    } else null,
                )
            }

            if (!maKluczGemini) {
                Text(
                    "Oba przyciski potrzebują klucza Gemini — wpiszesz go w zakładce Pomoc. " +
                        "Bez niego kreator działa normalnie, tylko opis piszesz sam.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }


        }

        // ------------------------------------------------- krok 2: opis
        NaglowekSekcji(
            numer = 2,
            tytul = "Sprawdź i popraw",
            podpis = if (nazwa.isBlank()) "nazwa pusta" else nazwa,
            otwarta = otwarta == 2,
            naKlik = { otwarta = if (otwarta == 2) 0 else 2 },
        )
        if (otwarta == 2) {

        PoleZPodpowiedzia(nazwa, { nazwa = it }, Podpowiedzi.nazwa)
        val limitAllegro = 75
        val dlugoscNazwy = nazwa.length
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (dlugoscNazwy > limitAllegro) "⚠️ Przekroczono limit Allegro (max $limitAllegro znaków)" else "Limit tytułu Allegro: $dlugoscNazwy / $limitAllegro",
                style = MaterialTheme.typography.labelSmall,
                color = if (dlugoscNazwy > limitAllegro) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (dlugoscNazwy > limitAllegro) {
                TextButton(
                    onClick = { nazwa = nazwa.take(limitAllegro).trim() },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text("Przytnij do 75", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        PoleZPodpowiedzia(kategoria, { kategoria = it.lowercase().trim() }, Podpowiedzi.kategoria)
        PoleZPodpowiedzia(opisKrotki, { opisKrotki = it }, Podpowiedzi.opisKrotki, wiersze = 3)
        PoleZPodpowiedzia(opis, { opis = it }, Podpowiedzi.opis, wiersze = 6)
        PoleZPodpowiedzia(opisZdjecia, { opisZdjecia = it }, Podpowiedzi.opisZdjecia)

        // Poprawianie tekstu, ktory juz jest — inna robota niz pisanie od zera
        // ze zdjecia. Idzie silnikiem wybranym w Pomocy: Muse nie widzi zdjecia,
        // ale do wygladzania zdan wystarcza i nie zjada limitu Gemini.
        OutlinedButton(
            onClick = { pytanieOSilnik = "poprawa-opisu" },
            enabled = !agentPracuje,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text("Popraw napisany opis") }

        opisPrzedPoprawka?.let { (staraNazwa, staryKrotki, staryOpis) ->
            TextButton(onClick = {
                nazwa = staraNazwa
                opisKrotki = staryKrotki
                opis = staryOpis
                opisPrzedPoprawka = null
            }) { Text("Cofnij poprawki opisu") }
        }

        }

        // ------------------------------------------------- krok 3: handel
        NaglowekSekcji(
            numer = 3,
            tytul = "Cena i dostępność",
            podpis = if (cena.isBlank()) "cena pusta" else "$cena zł",
            otwarta = otwarta == 3,
            naKlik = { otwarta = if (otwarta == 3) 0 else 3 },
        )
        if (otwarta == 3) {

        PoleZPodpowiedzia(cena, { cena = it }, Podpowiedzi.cena, liczbowe = true)
        PoleZPodpowiedzia(cenaPromo, { cenaPromo = it }, Podpowiedzi.cenaPromo, liczbowe = true)
        PoleZPodpowiedzia(stan, { stan = it }, Podpowiedzi.stan, liczbowe = true)

        // Puste pole stanu to decyzja z konsekwencjami prawnymi, więc mówimy o
        // niej wprost w momencie, w którym zapada — a nie dopiero w regulaminie.
        if (stan.isBlank()) {
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(
                    "Stan pusty: sklep pokaże „wykonywane na zamówienie”. " +
                        "Taki wyrób nie podlega zwrotowi w 14 dni (art. 38 ustawy " +
                        "o prawach konsumenta) i sklep napisze to klientowi na stronie produktu.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        PoleZPodpowiedzia(jednostka, { jednostka = it }, Podpowiedzi.jednostka)
        PoleZPodpowiedzia(waga, { waga = it }, Podpowiedzi.waga, liczbowe = true)
        PoleZPodpowiedzia(czas, { czas = it }, Podpowiedzi.czasRealizacji)
        PoleZPodpowiedzia(pozycja, { pozycja = it }, Podpowiedzi.pozycja, liczbowe = true)


        }

        // ------------------------------------------------- krok 4: allegro
        NaglowekSekcji(
            numer = 4,
            tytul = "Integracja z Allegro",
            podpis = when {
                allegroUrl.isNotBlank() -> "Aukcja podpięta"
                allegroId.isNotBlank() -> "ID: $allegroId"
                allegroStatus != "brak" -> "Status: $allegroStatus"
                else -> "brak aukcji"
            },
            otwarta = otwarta == 4,
            naKlik = { otwarta = if (otwarta == 4) 0 else 4 },
        )
        if (otwarta == 4) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "🟠 Przekierowanie do Allegro",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "Wpisanie linku do aukcji sprawi, że klienci na stronie fwdrucik.pl po kliknięciu „Kup” zostaną bezpośrednio przeniesieni do Twojej oferty z obsługą Allegro Smart i Allegro Protect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            PoleZPodpowiedzia(allegroUrl, { allegroUrl = it }, Podpowiedzi.allegroUrl)

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1.2f)) {
                    PoleZPodpowiedzia(allegroCena, { allegroCena = it }, Podpowiedzi.allegroCena, liczbowe = true)
                }
                OutlinedButton(
                    onClick = {
                        val c = zloteNaGrosze(cena)
                        if (c != null && c > 0) {
                            val zProwizja = (c * 1.12).toInt()
                            allegroCena = groszeNaPole(zProwizja)
                        }
                    },
                    enabled = cena.isNotBlank(),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("+12% prowizji")
                }
            }

            PoleZPodpowiedzia(
                allegroId,
                { allegroId = it },
                Podpowiedz(
                    pole = "ID oferty Allegro",
                    krotko = "Numer oferty z Allegro (np. 15423891023).",
                    przyklad = "15423891023",
                    dlaczego = "Pozwala agentowi i serwerowi MCP synchronizować stany i ceny bezpośrednio przez API Allegro."
                ),
                liczbowe = true
            )

            Text(
                "Status oferty na Allegro",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("brak" to "Brak", "szkic" to "Szkic", "aktywna" to "Aktywna", "zakonczona" to "Koniec").forEach { (klucz, etykieta) ->
                    FilterChip(
                        selected = allegroStatus == klucz,
                        onClick = { allegroStatus = klucz },
                        label = { Text(etykieta, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Button(
                onClick = {
                    val tytulOczyszczony = pl.fwdrucik.sklep.narzedzia.AllegroFormat.oczyscTytul(nazwa.ifBlank { notatka })
                    val cenaKwota = (zloteNaGrosze(allegroCena.ifBlank { cena }) ?: 0) / 100.0
                    val htmlOpis = pl.fwdrucik.sklep.narzedzia.AllegroFormat.zbudujOpisHtml(nazwa, opisKrotki, opis)
                    val urlZdjecia = p.obrazy.firstOrNull()?.src?.let {
                        if (it.startsWith("http")) it else BuildConfig.ADRES_API + it
                    }.orEmpty()

                    tworzenieSzkicuWToku = true
                    naUtworzSzkicAllegro?.invoke(
                        tytulOczyszczony,
                        kategoria,
                        cenaKwota,
                        htmlOpis,
                        urlZdjecia,
                        stan.toIntOrNull() ?: 1
                    ) { sukces, zwroconyUrl ->
                        tworzenieSzkicuWToku = false
                        if (sukces) {
                            allegroStatus = "szkic"
                            if (zwroconyUrl != null) {
                                allegroUrl = zwroconyUrl
                                val wyciagnieteId = zwroconyUrl.substringAfterLast("/")
                                if (wyciagnieteId.isNotBlank() && wyciagnieteId.all { it.isDigit() }) {
                                    allegroId = wyciagnieteId
                                }
                            }
                            Toast.makeText(context, "Prywatny szkic na Allegro utworzony!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Błąd tworzenia szkicu Allegro — sprawdź połączenie", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                enabled = !tworzenieSzkicuWToku && (nazwa.isNotBlank() || notatka.isNotBlank()) && (cena.isNotBlank() || allegroCena.isNotBlank())
            ) {
                if (tworzenieSzkicuWToku) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onTertiary)
                    Spacer(Modifier.width(6.dp))
                    Text("Tworzę prywatny szkic...", style = MaterialTheme.typography.labelMedium)
                } else {
                    Text("🛒 Utwórz prywatny szkic na Allegro (REST API)", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (allegroUrl.isNotBlank() && allegroUrl.startsWith("http")) {
                Button(
                    onClick = {
                        naImportujAllegro?.invoke(allegroUrl.trim())
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    ),
                    enabled = !agentPracuje
                ) {
                    Text("⚡ Pobierz ustandaryzowane dane z aukcji i opublikuj")
                }

                OutlinedButton(
                    onClick = {
                        naSynchronizujAllegro?.invoke(
                            allegroUrl.trim(),
                            allegroId.trim(),
                            nazwa.trim(),
                            allegroCena.ifBlank { cena }.trim(),
                            kategoria,
                            opis.trim(),
                            opisKrotki.trim()
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    enabled = nazwa.isNotBlank() && !agentPracuje
                ) {
                    Text("🚀 Wyślij format aukcji na fwdrucik.pl")
                }

                OutlinedButton(
                    onClick = {
                        runCatching {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(allegroUrl.trim()))
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Text("Otwórz aukcję na Allegro ↗")
                }
            }
        }

        // ------------------------------------------------- krok 5: podgląd
        NaglowekSekcji(
            numer = 5,
            tytul = "Podgląd, status i galeria",
            podpis = Statusy.nazwaProduktu(status),
            otwarta = otwarta == 5,
            naKlik = { otwarta = if (otwarta == 5) 0 else 5 },
        )
        if (otwarta == 5) {

        OutlinedButton(
            onClick = { pokazOgloszenie = !pokazOgloszenie },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text(if (pokazOgloszenie) "Schowaj podgląd ogłoszenia" else "Podgląd ogłoszenia") }

        if (pokazOgloszenie) {
            PodgladOgloszenia(
                zdjecie = lokalneZdjecie ?: p.obrazy.firstOrNull()?.let {
                    Uri.parse(if (it.src.startsWith("http")) it.src else (BuildConfig.ADRES_API + it.src))
                },
                animacja = animacja,
                nazwa = nazwa,
                cena = cena,
                opisKrotki = opisKrotki,
                opis = opis,
                kategoria = kategoria,
                status = status,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Text("Status", style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Statusy.produktu.forEach { s ->
                FilterChip(
                    selected = status == s,
                    onClick = { status = s },
                    label = { Text(Statusy.nazwaProduktu(s), maxLines = 1) },
                )
            }
        }
        Text(
            Statusy.opisProduktu(status),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        KartaPodpowiedzi(Podpowiedzi.status)

        // ------------------------------------------- galeria produktu i materiały ruchome
        Text(
            "Materiały i galeria w sklepie",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp)
        )

        // 1. Materiał ruchomy (GIF / Wideo)
        animacja?.let { animUri ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val jestWideo = animUri.toString().lowercase().endsWith(".mp4")
                    AsyncImage(
                        model = animUri,
                        contentDescription = "Animacja produktu",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(72.dp),
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(
                            if (jestWideo) "Wideo obrotowe (MP4)" else "Animacja 360° (GIF)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "⭐ Pozycja 0 — ruchomy materiał widoczny jako pierwszy w sklepie",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { animacja = null }) {
                        Icon(Icons.Filled.Close, contentDescription = "Usuń animację")
                    }
                }
            }
        }

        // 2. Zdjęcia w roboczym szkicu (Główne i Dodatkowe)
        if (lokalneZdjecie != null || dodatkoweKadry.isNotEmpty() || p.obrazy.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Zdjęcie główne (lokalne)
                lokalneZdjecie?.let { mainUri ->
                    item {
                        Card(Modifier.width(130.dp)) {
                            Column(Modifier.padding(6.dp)) {
                                Box {
                                    AsyncImage(
                                        model = mainUri,
                                        contentDescription = "Zdjęcie główne",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(118.dp),
                                    )
                                    IconButton(
                                        onClick = {
                                            val nastepne = if (dodatkoweKadry.isNotEmpty()) dodatkoweKadry.removeAt(0) else null
                                            lokalneZdjecie = nastepne
                                        },
                                        modifier = Modifier.align(Alignment.TopEnd),
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Usuń", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    label = { Text("⭐ Okładka (Główne)", maxLines = 1) },
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Dodatkowe zdjęcia w szkicu
                items(dodatkoweKadry) { kadr ->
                    Card(Modifier.width(130.dp)) {
                        Column(Modifier.padding(6.dp)) {
                            Box {
                                AsyncImage(
                                    model = kadr,
                                    contentDescription = "Kolejne zdjęcie",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(118.dp),
                                )
                                IconButton(
                                    onClick = { dodatkoweKadry.remove(kadr) },
                                    modifier = Modifier.align(Alignment.TopEnd),
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Usuń", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            TextButton(
                                onClick = {
                                    val staryGlowne = lokalneZdjecie
                                    lokalneZdjecie = kadr
                                    dodatkoweKadry.remove(kadr)
                                    if (staryGlowne != null) {
                                        dodatkoweKadry.add(0, staryGlowne)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                            ) {
                                Text("Ustaw okładkę", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Istniejące zdjęcia na serwerze.
                //
                // `distinctBy` NIE JEST OZDOBNIKIEM: serwer doklada do tej listy
                // animacje z osobnej tabeli, a obie numeracje zaczynaly sie od 1.
                // Powtorzony klucz w LazyRow to nie zle ulozona siatka, tylko
                // natychmiastowy wyjatek („Key 1 was already used") i aplikacja
                // padala przy samym wejsciu w edycje produktu. Serwer jest juz
                // poprawiony, ale telefon nie ma prawa umierac przez dane,
                // ktore przyszly z sieci — na starszym hostingu ta lista wciaz
                // moze przyjsc z duplikatem.
                items(p.obrazy.distinctBy { it.id }, key = { it.id }) { obraz ->
                    Card(Modifier.width(130.dp)) {
                        Column(Modifier.padding(6.dp)) {
                            Box {
                                AsyncImage(
                                    model = BuildConfig.ADRES_API + obraz.src,
                                    contentDescription = obraz.alt.ifBlank { "Zdjęcie produktu" },
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(118.dp),
                                )
                                // Ujemny identyfikator znaczy „to nie jest zdjecie,
                                // tylko animacja z tabeli plikow". Usuwa sie ja
                                // inna koncowka, wiec krzyzyk tu nie ma sensu —
                                // przycisk, ktory na pewno nie zadziala, jest
                                // gorszy niz jego brak.
                                if (obraz.id > 0) {
                                    IconButton(
                                        onClick = { naUsunZdjecie(obraz.id) },
                                        modifier = Modifier.align(Alignment.TopEnd),
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Usuń", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            Text(
                                if (obraz.id < 0) "Animacja" else "Na serwerze",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = {
                wybierzWieleKolejnych.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Icon(Icons.Filled.AddAPhoto, contentDescription = null)
            Text("  Dodaj kolejne zdjęcia do galerii")
        }

        }

        // Dziennik i lista brakow ZOSTAJA poza sekcjami.
        //
        // PO CO: dziennik pokazuje, ze agent wlasnie pracuje, a zlecic go mozna
        // z dwoch roznych sekcji — schowany w jednej z nich znaczylby, ze po
        // kliknieciu „Popraw napisany opis" przez kilkadziesiat sekund nie widac
        // nic. Lista brakow wskazuje pola z sekcji 2 i 3, wiec zwiniete razem
        // z sekcja 1 bylaby nie do odczytania w momencie, w ktorym jest potrzebna.
        if (czynnosci.isNotEmpty()) {
            DziennikCzynnosci(czynnosci, Modifier.padding(top = 16.dp))
        }

        if (doUzupelnienia.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Tego nie widać na zdjęciu — uzupełnij sam",
                        style = MaterialTheme.typography.titleSmall)
                    doUzupelnienia.forEach { pytanie ->
                        Text(
                            "• $pytanie",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        // ------------------------------------------------- przyciski na dole
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(
                onClick = naWyjscie,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                modifier = Modifier.weight(0.7f),
            ) {
                Text("Wróć", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
            }
            Button(
                onClick = {
                    val nazwaAwaryjna = nazwa.trim().ifBlank {
                        notatka.trim().take(40).ifBlank { "Wyrób bez nazwy" }
                    }
                    val zebrany = p.copy(
                        nazwa = nazwaAwaryjna,
                        kategoria = kategoria.ifBlank { "inne" },
                        opisKrotki = opisKrotki.trim(),
                        opis = opis.trim(),
                        stan = stan.trim().toIntOrNull(),
                        wagaG = waga.trim().toIntOrNull() ?: 0,
                        jednostka = jednostka.ifBlank { "szt." },
                        czasRealizacji = czas.trim(),
                        status = "opublikowany",
                        pozycja = pozycja.trim().toIntOrNull() ?: 100,
                        allegroUrl = allegroUrl.trim().ifBlank { null },
                        allegroCenaGr = zloteNaGrosze(allegroCena),
                        allegroId = allegroId.trim().ifBlank { null },
                        allegroStatus = allegroStatus,
                    )
                    naZapisz(
                        zebrany,
                        cena.trim().ifBlank { "0" },
                        cenaPromo.trim(),
                        lokalneZdjecie,
                        dodatkoweKadry.toList(),
                        animacja,
                        opisZdjecia.ifBlank { nazwaAwaryjna },
                    )
                },
                enabled = !agentPracuje,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                modifier = Modifier.weight(1.3f),
            ) {
                Text("Opublikuj", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
            }
            FilledTonalButton(
                onClick = {
                    val zebrany = p.copy(
                        nazwa = nazwa.trim(),
                        kategoria = kategoria.ifBlank { "inne" },
                        opisKrotki = opisKrotki.trim(),
                        opis = opis.trim(),
                        stan = stan.trim().toIntOrNull(),
                        wagaG = waga.trim().toIntOrNull() ?: 0,
                        jednostka = jednostka.ifBlank { "szt." },
                        czasRealizacji = czas.trim(),
                        status = status,
                        pozycja = pozycja.trim().toIntOrNull() ?: 100,
                        allegroUrl = allegroUrl.trim().ifBlank { null },
                        allegroCenaGr = zloteNaGrosze(allegroCena),
                        allegroId = allegroId.trim().ifBlank { null },
                        allegroStatus = allegroStatus,
                    )
                    naZapisz(
                        zebrany,
                        cena.trim(),
                        cenaPromo.trim(),
                        lokalneZdjecie,
                        dodatkoweKadry.toList(),
                        animacja,
                        opisZdjecia.ifBlank { nazwa.trim() },
                    )
                },
                enabled = nazwa.isNotBlank() && cena.isNotBlank() && !agentPracuje,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                modifier = Modifier.weight(0.8f),
            ) {
                Text("Zapisz", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * Kopiuje wskazane zdjęcie do pamięci aplikacji i oddaje adres do tej kopii.
 *
 * PO CO: adres `content://` z galerii albo z aparatu żyje tak długo, jak
 * nadane przy wyborze uprawnienie. Wystarczy powrót z innej aplikacji, obrót
 * telefonu w złym momencie albo ubicie procesu przez system — i kreator ma
 * adres, pod którym nic nie ma. Z punktu widzenia człowieka wygląda to tak,
 * że aplikacja „zgubiła zdjęcie".
 *
 * Kopia leży w katalogu prywatnym, więc nie zaśmieca galerii; sprzątamy ją
 * razem z kopią roboczą produktu.
 */
private fun zachowajLokalnie(context: android.content.Context, zrodlo: Uri): Uri? = runCatching {
    val katalog = java.io.File(context.filesDir, "kadry").apply { mkdirs() }
    val cel = java.io.File(katalog, "kadr_" + System.currentTimeMillis() + ".jpg")
    context.contentResolver.openInputStream(zrodlo)!!.use { we ->
        cel.outputStream().use { wy -> we.copyTo(wy) }
    }
    Uri.fromFile(cel)
}.getOrNull()


/**
 * Czym, jakim modelem i z jakim dodatkowym poleceniem ma być zrobiona robota.
 *
 * Trzy wartości chodzą razem przez cały przepływ, więc jedna paczka zamiast
 * trzech parametrów doklejanych do każdej sygnatury.
 */
data class WyborRoboty(
    val silnik: String,
    val model: String = "",
    val dodatkowe: String = "",
    /**
     * Ksztalt kadru: 16:9, 9:16 albo 1:1.
     *
     * Znaczy cos tylko dla Meta AI — pozostale silniki maja format zaszyty,
     * wiec wartosc przelatuje przez nie bez skutku. Domyslnie kadr poziomy,
     * bo tak wyglada zdjecie na stronie produktu.
     */
    val proporcje: String = "16:9",
)

/**
 * Polecenie animacji: obrót wyrobu jak na obrotowym stoliku.
 *
 * PO CO TAK: klient ogląda wyrób, a nie film o wyrobie. Powolny obrót wokół
 * własnej osi pokazuje kształt ze wszystkich stron w osiem sekund i nie wymaga
 * od widza niczego. Do tego drobna ozdoba w tle — iskry albo delikatne światła —
 * dobierana losowo, żeby kolejne animacje nie wyglądały jak jedna, powielona.
 */
/**
 * Ozdoby tla — jedna lista dla animacji i dla wymiany tla.
 *
 * PO CO LOSOWO: przy stalym tle kolejne wyroby w sklepie wygladaja jak jedno
 * zdjecie powielone dwadziescia razy. Losowa ozdoba daje roznorodnosc, ktorej
 * nikt nie musi wymyslac przy kazdym produkcie.
 *
 * Kazda pozycja jest DELIKATNA i ZA przedmiotem. To ma robic nastroj, a nie
 * przyciagac wzrok — kupuje sie wyrob, nie tlo.
 */
private val OZDOBY_TLA = listOf(
    "delikatne iskry unoszące się w tle",
    "miękkie rozbłyski światła jak małe fajerwerki w oddali",
    "drobne złote gwiazdki opadające powoli",
    "łagodne smugi światła przesuwające się za przedmiotem",
    "subtelny brokatowy pył w powietrzu",
    "spokojne wzory fraktalne pulsujące w głębi tła",
    "ciepłe bokeh, rozmyte światełka w tle",
)

/** Pastele, na ktorych wyrob nie ginie i nie kloci sie kolorem. */
private val PASTELE = listOf(
    "pastelowa mięta", "pastelowy błękit", "ciepła kość słoniowa",
    "pastelowy piaskowy beż", "bardzo jasny pastelowy róż",
    "pastelowa szałwia",
)

/**
 * Wymiana tla: wyrob zostaje, tlo znika.
 *
 * Zdanie „zachowaj przedmiot bez zmian" nie jest grzecznosciowe — bez niego
 * modele obrazowe „poprawiaja" ksztalt i kolor wyrobu, a w sklepie zdjecie,
 * ktore nie zgadza sie z tym, co przyjdzie w paczce, konczy sie reklamacja.
 */
private fun polecenieTla(coTo: String): String = pl.fwdrucik.sklep.dane.Polecenia.tlo(coTo)

@Suppress("unused")
private fun polecenieTlaStare(coTo: String): String =
    "usuń tło całkowicie i zastąp je gładkim pastelowym gradientem studyjnym " +
        "(" + PASTELE.random() + "), " + OZDOBY_TLA.random() + " delikatnie w tle, " +
        "przedmiot (" + coTo + ") zachowaj DOKŁADNIE bez zmian: ten sam kształt, " +
        "kolor, faktura i napisy, wyśrodkowany i cały widoczny, miękki cień " +
        "kontaktowy pod spodem, ostre krawędzie, bez tekstu i bez znaku wodnego"

/**
 * Upiekszanie: samo swiatlo i ostrosc, nic wiecej.
 *
 * Tu NIE zmieniamy tla — od tego jest osobne zadanie. Slowa „światło",
 * „ostrość" i „kontrast" sa tez sygnalem dla serwera warsztatowego: po nich
 * poznaje, ze ma dolozyc Forge do wycietego kadru zamiast oddac sam wycinek.
 */
private fun polecenieUpiekszania(coTo: String): String =
    pl.fwdrucik.sklep.dane.Polecenia.upieksz(coTo)

@Suppress("unused")
private fun polecenieUpiekszaniaStare(coTo: String): String =
    "popraw światło, ostrość i kontrast tego zdjęcia (" + coTo + "), " +
        "wyrównaj ekspozycję i balans bieli, wydobądź fakturę materiału, " +
        "usuń szum i odblaski, zachowaj tło i kompozycję bez zmian, " +
        "nie zmieniaj kształtu ani koloru przedmiotu, bez tekstu i znaku wodnego"

private fun poleceniObrotu(coTo: String): String {
    val ozdoby = OZDOBY_TLA
    return "product turntable: " + coTo +
        ", slow smooth 360 degree rotation of the object around its vertical axis, " +
        "object centered and fully visible, soft pastel studio background, " +
        "gentle studio lighting, shallow depth of field, " + ozdoby.random() +
        ", 8 seconds, no text, no captions, no watermark"
}


/**
 * Naglowek jednej zwijanej sekcji kreatora.
 *
 * Podpis po prawej mowi, co w tej sekcji juz jest — dzieki temu przy zwinietej
 * harmonijce widac stan calego produktu bez rozwijania czegokolwiek.
 */
@Composable
private fun NaglowekSekcji(
    numer: Int,
    tytul: String,
    podpis: String,
    otwarta: Boolean,
    naKlik: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clickable(onClick = naKlik),
        colors = CardDefaults.cardColors(
            containerColor = if (otwarta) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "$numer. $tytul",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (otwarta) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    podpis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Icon(
                if (otwarta) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (otwarta) "Zwiń sekcję" else "Rozwiń sekcję",
            )
        }
    }
}
