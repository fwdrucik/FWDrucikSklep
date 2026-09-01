package pl.fwdrucik.sklep.ui.ekrany

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import android.widget.Toast
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
import pl.fwdrucik.sklep.dane.Produkt
import pl.fwdrucik.sklep.dane.Statusy
import pl.fwdrucik.sklep.dane.SzkicProduktu
import pl.fwdrucik.sklep.pomoc.Aparat
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
    /** Caly ciag: zdjecie, co to jest, ksztalt, dopisek, wynik zdjecia, wynik animacji. */
    naCiagAuto: (Uri, String, String, String, (Uri) -> Unit, (Uri) -> Unit) -> Unit =
        { _, _, _, _, _, _ -> },
    naUsunZdjecie: (Int) -> Unit,
    /** Sciaga zdjecie produktu ze sklepu na telefon, zeby dalo sie na nim pracowac. */
    naPobierzZdjecieProduktu: (String, (Uri) -> Unit) -> Unit = { _, _ -> },
    naWyjscie: () -> Unit,
    kopia: KopiaRobocza? = null,
    naZapiszKopie: (KopiaRobocza) -> Unit = {},
    naOdrzucKopie: (Int) -> Unit = {},
) {
    val p = istniejacy ?: Produkt()

    var nazwa by rememberSaveable(p.id) { mutableStateOf(p.nazwa) }
    var kategoria by rememberSaveable(p.id) { mutableStateOf(p.kategoria) }
    var opisKrotki by rememberSaveable(p.id) { mutableStateOf(p.opisKrotki) }
    var opis by rememberSaveable(p.id) { mutableStateOf(p.opis) }
    var cena by rememberSaveable(p.id) { mutableStateOf(if (p.cenaGr > 0) groszeNaPole(p.cenaGr) else "") }
    var cenaPromo by rememberSaveable(p.id) {
        mutableStateOf(p.cenaPromoGr?.let(::groszeNaPole).orEmpty())
    }
    var stan by rememberSaveable(p.id) { mutableStateOf(p.stan?.toString().orEmpty()) }
    var jednostka by rememberSaveable(p.id) { mutableStateOf(p.jednostka) }
    var waga by rememberSaveable(p.id) { mutableStateOf(if (p.wagaG > 0) p.wagaG.toString() else "") }
    var czas by rememberSaveable(p.id) { mutableStateOf(p.czasRealizacji) }
    var status by rememberSaveable(p.id) { mutableStateOf(p.status) }
    var pozycja by rememberSaveable(p.id) { mutableStateOf(p.pozycja.toString()) }
    var opisZdjecia by rememberSaveable(p.id) { mutableStateOf("") }

    // Zdjęcie i notatka do agenta — stan wyłącznie tego ekranu.
    // Zdjecie glowne NIE jest kluczowane po `p.id`.
    //
    // PO CO: po pierwszym zapisie nowego produktu `istniejacy` zmienia sie
    // z `null` na produkt z nadanym numerem, wiec `p.id` skacze z 0 na N.
    // Kazde `remember(p.id)` leci wtedy od nowa — i wybrane zdjecie znikalo
    // z ekranu w srodku pracy, mimo ze nikt go nie usuwal. Zamiast klucza
    // pilnujemy jawnie, dla ktorego produktu to zdjecie jest, i czyscimy je
    // tylko przy przejsciu na INNY produkt.
    var lokalneZdjecie by remember { mutableStateOf<Uri?>(null) }
    var zdjecieDlaProduktu by remember { mutableIntStateOf(p.id) }

    LaunchedEffect(p.id) {
        val bylNowy = zdjecieDlaProduktu == 0 && p.id > 0
        if (p.id != zdjecieDlaProduktu && !bylNowy) {
            // Naprawde inny produkt — stare zdjecie nie ma tu czego szukac.
            lokalneZdjecie = null
        }
        zdjecieDlaProduktu = p.id
    }
    val dodatkoweKadry = remember(p.id) { mutableStateListOf<Uri>() }
    var animacja by remember(p.id) { mutableStateOf<Uri?>(null) }
    // Oryginal trzymamy osobno: poprawka ma czyscic tlo, a nie zmieniac wyrobu,
    // i tylko porownanie dwoch kadrow pozwala to zlapac.
    var zdjecieOryginalne by remember(p.id) { mutableStateOf<Uri?>(null) }
    var opisPrzedPoprawka by remember(p.id) { mutableStateOf<Triple<String, String, String>?>(null) }
    var pokazOgloszenie by remember(p.id) { mutableStateOf(false) }

    // Ktora sekcja kreatora jest rozwinieta. 0 = wszystkie zwiniete.
    //
    // PO CO: kreator byl jedna scianą pol na kilka ekranow przewijania, a przy
    // telefonie w reku nad stolem szuka sie w nim jednej rzeczy naraz. Harmonijka
    // pokazuje jedna sekcje, a naglowki mowia skrotem, co juz jest wypelnione.
    // Stan pol NIE siedzi w sekcjach — wszystkie zmienne, launchery aparatu,
    // dialogi i BackHandler zostaja na poziomie ekranu, wiec zwiniecie sekcji
    // niczego nie kasuje.
    var otwarta by rememberSaveable { mutableIntStateOf(1) }

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
    // PO CO: pojedyncze dotkniecie „wstecz" zamykalo kreator w trakcie liczenia
    // animacji — a to sa minuty pracy karty albo punkty wydane w Veo. Robota
    // szla dalej w tle, tylko wynik nie mial juz gdzie wrocic.
    androidx.activity.compose.BackHandler(enabled = true) {
        if (agentPracuje) {
            Toast.makeText(
                context,
                "Trwa generowanie materiału — poczekaj na ukończenie zadania.",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            naWyjscie()
        }
    }
    var notatka by rememberSaveable(p.id) { mutableStateOf("") }
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
                TrzyOpcje("auto", "Automatycznie", "Wybierz za mnie to, co teraz zyje", true),
                TrzyOpcje(
                    "gemini", "Gemini (chmura)",
                    if (zadanie == "opis") "Widzi zdjecie — najlepszy do opisu z kadru"
                    else "Szybki, ale liczy sie do limitu",
                    maKluczGemini,
                ),
                TrzyOpcje(
                    "muse", "Muse na komputerze",
                    "Bez limitu i za darmo. Widzi zdjęcie — komputer podaje mu kadr.",
                    museDostepny,
                ),
                TrzyOpcje(
                    "copilot", "Microsoft Copilot (GPT-4o)",
                    "Opisy i cechy wyrobu za darmo przez konto Microsoft.",
                    copilotDziala,
                ),
            )
            // Caly ciag prowadzi agregator, wiec nie ma tu czego wybierac.
            // Pokazujemy jedna pozycje zamiast chowac liste: czlowiek widzi
            // wtedy, ze decyzja zostala podjeta, a nie ze o nia zapomniano.
            "auto-ciag" -> listOf(
                TrzyOpcje(
                    "auto", "Agregator wybiera",
                    "Do każdego z trzech kroków bierze to, co akurat żyje " +
                        "i jest najszybsze.",
                    true,
                ),
            )
            // Wycinanie tla: Forge pierwszy, bo robi to MASKA, a nie model —
            // tlo znika zawsze, a wyrob zostaje nietkniety. Reszta dorysowuje
            // tlo od nowa, co bywa ladniejsze, ale bywa tez innym przedmiotem.
            "tlo" -> listOf(
                TrzyOpcje("auto", "Automatycznie", "Wybierz za mnie to, co teraz żyje", true),
                TrzyOpcje(
                    "forge", "Komputer w warsztacie",
                    "Wycina tło maską i stawia wyrób na pastelu. Pewne i za darmo.",
                    forgeDziala,
                ),
                TrzyOpcje(
                    "flow", "Flow (Nano Banana)",
                    "Najszybszy — kadr w kilkanaście sekund, zero punktów. " +
                        "Oddaje zawsze kwadrat.",
                    flowDziala,
                ),
                TrzyOpcje(
                    "meta", "Meta AI",
                    "Około minuty, za to jedyny, który da zadany kształt: " +
                        "16:9 na stronę, 9:16 pod rolkę.",
                    metaDziala,
                ),
                TrzyOpcje(
                    "copilot", "Copilot (DALL-E 3 / Designer)",
                    "Stylizacja tła i kadru za darmo.",
                    copilotDziala,
                ),
                TrzyOpcje(
                    "gemini", "Gemini (chmura)",
                    "Szybkie, ale limit obrazów kończy się pierwszy.",
                    maKluczGemini,
                ),
            )
            // Upiekszanie: tu chodzi o swiatlo i ostrosc, wiec Forge idzie
            // z niskim denoise, a modele chmurowe dostaja polecenie wprost
            // zakazujace zmiany wyrobu.
            "upiekszanie" -> listOf(
                TrzyOpcje("auto", "Automatycznie", "Wybierz za mnie to, co teraz żyje", true),
                TrzyOpcje(
                    "gemini", "Gemini (chmura)",
                    "Najlepiej trzyma się oryginału. Limit obrazów kończy się pierwszy.",
                    maKluczGemini,
                ),
                TrzyOpcje(
                    "forge", "Komputer w warsztacie",
                    "Poprawia światło na własnej karcie, bez limitu i za darmo.",
                    forgeDziala,
                ),
                TrzyOpcje(
                    "flow", "Flow (Nano Banana)",
                    "Kilkanaście sekund, zero punktów. Oddaje kwadrat.",
                    flowDziala,
                ),
                TrzyOpcje(
                    "meta", "Meta AI",
                    "Około minuty, kształt kadru do wyboru.",
                    metaDziala,
                ),
                TrzyOpcje(
                    "copilot", "Microsoft Copilot (Designer)",
                    "Poprawa stylu i światła DALL-E 3.",
                    copilotDziala,
                ),
            )
            else -> listOf(
                TrzyOpcje("auto", "Automatycznie", "Wybierz za mnie to, co teraz zyje", true),
                TrzyOpcje("comfy", "Karta w warsztacie", "5 sekund, 0 zl, bez dzwieku", comfyDziala),
                TrzyOpcje("flow", "Veo przez Flow", "8 sekund z dzwiekiem, 20 punktow z abonamentu", flowDziala),
                TrzyOpcje(
                    "meta", "Meta AI",
                    "5 sekund ze zdjecia, 0 zl, ksztalt do wyboru. Bez dzwieku.",
                    metaDziala,
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
                            "comfy" -> "animacja"
                            "meta" -> "animacja-meta"
                            else -> when (silnikAnimacji) {
                                "flow" -> "animacja-flow"
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
                Text(
                    "Materiał w ogłoszeniu staje przed zdjęciami i to on rusza się " +
                        "na stronie produktu. Zostawiony w kreatorze posłuży do " +
                        "dalszej roboty, ale nie trafi jeszcze do sklepu.",
                    style = MaterialTheme.typography.bodySmall,
                )
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
                AsyncImage(
                    model = uri,
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

            // DWA PRZYCISKI, RESZTA W PYTANIACH.
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { pytanieOSilnik = "opis" },
                    enabled = lokalneZdjecie != null && !agentPracuje,
                    modifier = Modifier.weight(1f),
                ) {
                    if (agentPracuje) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    }
                    Text("  Opis")
                }
                Button(
                    onClick = { pytanieOZadaniuZdjecia = true },
                    enabled = lokalneZdjecie != null && !agentPracuje,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Zdjęcie")
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

        NaglowekSekcji(
            numer = 4,
            tytul = "Podgląd, status i galeria",
            podpis = Statusy.nazwaProduktu(status),
            otwarta = otwarta == 4,
            naKlik = { otwarta = if (otwarta == 4) 0 else 4 },
        )
        if (otwarta == 4) {

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

        // Przyciski zapisu ZOSTAJA poza sekcjami — muszą być pod ręką niezależnie
        // od tego, co jest rozwinięte. Zwinięty zapis to zgubiona robota.
        Row(
            Modifier.fillMaxWidth().padding(top = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(onClick = naWyjscie, modifier = Modifier.weight(1f)) {
                Text("Wróć")
            }
            OutlinedButton(
                onClick = {
                    val nazwaAwaryjna = nazwa.trim().ifBlank {
                        notatka.trim().take(40).ifBlank { "Wyrob bez nazwy" }
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
                modifier = Modifier.weight(1f),
            ) {
                Text("Opublikuj")
            }
            Button(
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
                modifier = Modifier.weight(1f),
            ) {
                Text("Zapisz")
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
