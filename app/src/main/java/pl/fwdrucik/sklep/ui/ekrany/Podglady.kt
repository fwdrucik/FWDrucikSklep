package pl.fwdrucik.sklep.ui.ekrany

import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import pl.fwdrucik.sklep.ui.Czynnosc
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Podglady wynikow pracy agenta i komputera.
 *
 * PO CO OSOBNY PLIK: dotad robota konczyla sie napisem „gotowe" i sciezka do
 * pliku. Z punktu widzenia czlowieka przy stole to jest to samo, co „nie
 * dziala" — wynik istnial, ale nie bylo jak go zobaczyc. Kazda czynnosc ma
 * teraz swoj podglad: zdjecie przed i po, animacja do odtworzenia na miejscu,
 * ogloszenie w takiej postaci, w jakiej zobaczy je klient.
 */

/** Animacja z odtwarzaczem — na miejscu, bez szukania pliku w pamieci telefonu. */
@Composable
fun PodgladAnimacji(
    plik: Uri,
    modifier: Modifier = Modifier,
    /**
     * Co zrobic po akceptacji materialu. `null` znaczy, ze nie ma gdzie go
     * dodac — produkt nie zostal jeszcze zapisany, wiec nie ma numeru,
     * do ktorego serwer moglby plik przypiac.
     */
    naDodajDoOgloszenia: (() -> Unit)? = null,
) {
    val kontekst = LocalContext.current
    val sciezka = plik.toString().lowercase()
    val jestGif = sciezka.endsWith(".gif") || sciezka.contains("image/gif")
    val typMime = if (jestGif) "image/gif" else "video/mp4"

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(if (jestGif) "Animacja GIF gotowa" else "Animacja wideo gotowa", style = MaterialTheme.typography.titleSmall)

            if (jestGif) {
                AsyncImage(
                    model = plik,
                    contentDescription = "Animacja GIF produktu",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .padding(top = 8.dp),
                )
            } else {
                var proporcjaFilmu by androidx.compose.runtime.remember(plik) {
                    androidx.compose.runtime.mutableStateOf(16f / 9f)
                }

                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                            setVideoURI(plik)
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
                    update = { it.setVideoURI(plik) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .aspectRatio(proporcjaFilmu),
                )
            }

            var gdzieZapisane by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { otworzZewnetrznie(kontekst, plik, typMime) }) {
                    Text("Otwórz")
                }
                TextButton(onClick = { udostepnij(kontekst, plik, typMime) }) {
                    Text("Wyślij dalej")
                }
                TextButton(onClick = {
                    gdzieZapisane = plik.path?.let {
                        pl.fwdrucik.sklep.dane.ZapisNaTelefonie.zapisz(kontekst, File(it))
                    } ?: null
                }) { Text("Zapisz w telefonie") }
            }

            if (naDodajDoOgloszenia != null) {
                androidx.compose.material3.Button(
                    onClick = naDodajDoOgloszenia,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) { Text("Dodaj do ogłoszenia") }
            }

            gdzieZapisane?.let {
                Text(
                    "Zapisane: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Text(
                if (naDodajDoOgloszenia != null)
                    "Materiał ruchomy staje w galerii PRZED zdjęciami — to jego " +
                        "widzi klient jako pierwszy."
                else
                    "Zapisz najpierw produkt — dopiero zapisany ma numer, do " +
                        "którego sklep przypina pliki.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Porownanie dwoch zdjec: oryginalnego i poprawionego przez AI.
 *
 * PO CO OSOBNY PODGLAD: modele AI maja tendencje do „upiekszania" przez wymyslanie
 * rzeczy od nowa. Poprawa ma wygladzic tlo, usunac paprochy ze stolu i wyrownac
 * swiatlo, a NIE zmieniac wyrobu. Roznice widac dopiero na dwoch kadrach
 * jednoczesnie — i tylko wtedy da sie zlapac moment, w ktorym model zaczal
 * poprawiac sam produkt.
 */
@Composable
fun PodgladZdjeciaPrzedPo(
    przed: Uri,
    po: Uri,
    naZastapGlowne: () -> Unit,
    naDodajDoGalerii: () -> Unit,
    naPrzywrocOryginal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Zdjęcie: przed i po", style = MaterialTheme.typography.titleSmall)
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    AsyncImage(
                        model = przed,
                        contentDescription = "Zdjęcie przed poprawką",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                    )
                    Text("oryginał", style = MaterialTheme.typography.labelSmall)
                }
                Column(Modifier.weight(1f)) {
                    AsyncImage(
                        model = po,
                        contentDescription = "Zdjęcie po poprawce",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                    )
                    Text("wersja AI", style = MaterialTheme.typography.labelSmall)
                }
            }
            val kontekst = LocalContext.current
            var gdzieZapisane by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.Button(
                    onClick = naZastapGlowne,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Zastąp główne")
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = naDodajDoGalerii,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Dodaj do galerii")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = naPrzywrocOryginal) { Text("Wróć do oryginału") }
                TextButton(onClick = {
                    gdzieZapisane = po.path?.let {
                        pl.fwdrucik.sklep.dane.ZapisNaTelefonie.zapisz(kontekst, File(it))
                    }
                }) { Text("Zapisz w telefonie") }
            }

            gdzieZapisane?.let {
                Text(
                    "Zapisane: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Ogloszenie takie, jakie zobaczy klient.
 *
 * PO CO: pola w kreatorze sa listą, a sklep pokazuje calosc naraz — i dopiero
 * wtedy widac, ze nazwa jest za dluga, cena pusta, a krotki opis konczy sie
 * w polowie zdania. Lepiej zobaczyc to tutaj niz na stronie.
 */
@Composable
fun PodgladOgloszenia(
    zdjecie: Uri?,
    nazwa: String,
    cena: String,
    opisKrotki: String,
    opis: String,
    kategoria: String,
    status: String,
    animacja: Uri? = null,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Tak zobaczy to klient", style = MaterialTheme.typography.titleSmall)

            val wyswietlanyPlik = animacja ?: zdjecie
            if (wyswietlanyPlik != null) {
                val sciezka = wyswietlanyPlik.toString().lowercase()
                val jestWideo = sciezka.endsWith(".mp4") || sciezka.contains("video/mp4")
                if (jestWideo) {
                    var proporcjaFilmu by androidx.compose.runtime.remember(wyswietlanyPlik) {
                        androidx.compose.runtime.mutableStateOf(16f / 9f)
                    }
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                                setVideoURI(wyswietlanyPlik)
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
                        update = { it.setVideoURI(wyswietlanyPlik) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .aspectRatio(proporcjaFilmu),
                    )
                } else {
                    AsyncImage(
                        model = wyswietlanyPlik,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .padding(top = 8.dp),
                    )
                }
                if (animacja != null && zdjecie != null && animacja != zdjecie) {
                    Text(
                        "Podgląd animacji produktu (ruchomy materiał w sklepie wyświetla się jako pierwszy).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                Text(
                    "Bez zdjęcia — w sklepie taka pozycja wygląda na pustą.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Text(
                nazwa.ifBlank { "(bez nazwy)" },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                if (cena.isBlank() || cena == "0") "cena nieustawiona" else "$cena zł",
                style = MaterialTheme.typography.titleSmall,
                color = if (cena.isBlank() || cena == "0") {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            if (opisKrotki.isNotBlank()) {
                Text(
                    opisKrotki,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (opis.isNotBlank()) {
                Text(
                    opis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(
                "kategoria: ${kategoria.ifBlank { "inne" }} · ${
                    pl.fwdrucik.sklep.dane.Statusy.nazwaProduktu(status)
                }",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Dziennik: co agent zrobil, czym i z jakim skutkiem. */
@Composable
fun DziennikCzynnosci(
    czynnosci: List<Czynnosc>,
    modifier: Modifier = Modifier,
) {
    val kontekst = LocalContext.current
    val godzina = SimpleDateFormat("HH:mm", Locale("pl"))

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Co robił agent", style = MaterialTheme.typography.titleSmall)
            if (czynnosci.isEmpty()) {
                Text(
                    "Jeszcze nic. Tu wyląduje każde zlecenie: opis, poprawka zdjęcia, animacja.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            czynnosci.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (c.plik.isNotBlank() && c.plik.endsWith(".mp4").not()) {
                        AsyncImage(
                            model = File(c.plik),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(40.dp).padding(end = 8.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${godzina.format(Date(c.czas))} · ${c.co}",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            (if (c.udana) "" else "nie udało się: ") + c.wynik + " · " + c.silnik,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (c.udana) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (c.plik.isNotBlank()) {
                        TextButton(onClick = {
                            val typ = if (c.plik.endsWith(".mp4")) "video/mp4" else "image/*"
                            otworzZewnetrznie(kontekst, Uri.fromFile(File(c.plik)), typ)
                        }) { Text("Pokaż") }
                        TextButton(onClick = {
                            pl.fwdrucik.sklep.dane.ZapisNaTelefonie.zapisz(kontekst, File(c.plik))
                        }) { Text("Zapisz") }
                    }
                }
            }
        }
    }
}

// Pliki oddajemy przez FileProvider, a nie surowym `file://`. Od Androida 7
// przekazanie `file://` innej aplikacji konczy sie `FileUriExposedException`
// — i to samo wywrocilo kiedys aparat w kreatorze.
private fun adresDoDzielenia(kontekst: android.content.Context, plik: Uri): Uri =
    if (plik.scheme == "file") {
        FileProvider.getUriForFile(
            kontekst,
            "${kontekst.packageName}.pliki",
            File(requireNotNull(plik.path)),
        )
    } else {
        plik
    }

private fun otworzZewnetrznie(kontekst: android.content.Context, plik: Uri, typ: String) {
    val intencja = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(adresDoDzielenia(kontekst, plik), typ)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { kontekst.startActivity(intencja) }
}

private fun udostepnij(kontekst: android.content.Context, plik: Uri, typ: String) {
    val intencja = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_STREAM, adresDoDzielenia(kontekst, plik))
        setType(typ)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { kontekst.startActivity(Intent.createChooser(intencja, "Wyślij animację")) }
}

/**
 * Pytanie o silnik, model i dodatkowe polecenie — zadawane w chwili kliknięcia.
 *
 * PO CO TAK: to, czym najlepiej zrobić robotę, zmienia się w ciągu dnia. Limit
 * Gemini pada w środku partii zdjęć, komputer w warsztacie bywa wyłączony,
 * a Veo kosztuje punkty, których czasem szkoda na próbny kadr. Ustawienie
 * w Pomocy zostaje domyślne, ale ostatnie słowo pada tutaj, przy konkretnym
 * wyrobie.
 *
 * Trzy rzeczy w jednym okienku, bo to jedna decyzja: czym, jakim modelem
 * i z jakim dodatkowym poleceniem. Rozbicie tego na trzy ekrany zamieniłoby
 * jedno kliknięcie w wędrówkę.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun WyborSilnika(
    tytul: String,
    opcje: List<TrzyOpcje>,
    modele: List<String>,
    modelDomyslny: String,
    naWykonaj: (silnik: String, model: String, dodatkowe: String, proporcje: String) -> Unit,
    naAnuluj: () -> Unit,
    /**
     * Czy pytac o ksztalt kadru.
     *
     * Ma sens tylko tam, gdzie cos powstaje jako obraz albo film. Przy pisaniu
     * opisu ten wybor bylby pytaniem bez znaczenia — a kazde takie pytanie
     * uczy, ze okienko mozna klikac bez czytania.
     */
    pokazProporcje: Boolean = false,
) {
    var silnik by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(opcje.firstOrNull { it.dostepny }?.klucz ?: "auto") }
    var model by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(modelDomyslny) }
    var dodatkowe by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var proporcje by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("16:9") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = naAnuluj,
        title = { Text(tytul) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Text("Czym zrobić", style = MaterialTheme.typography.labelLarge)
                opcje.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = silnik == o.klucz,
                            onClick = { silnik = o.klucz },
                            enabled = o.dostepny,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                o.nazwa,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (o.dostepny) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (o.dostepny) o.opis else o.opis + " — teraz niedostępne",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Model pokazujemy tylko przy Gemini: Muse i komputer nie maja
                // czego wybierac, a pusta lista tylko myli.
                if (silnik == "gemini" && modele.isNotEmpty()) {
                    Text(
                        "Model",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        modele.forEach { m ->
                            androidx.compose.material3.FilterChip(
                                selected = m == model,
                                onClick = { model = m },
                                label = { Text(m, maxLines = 1) },
                            )
                        }
                    }
                }

                // Ksztalt kadru. Zna go tylko Meta AI — pozostale silniki maja
                // format zaszyty (Forge oddaje kwadrat, Veo swoje 16:9), wiec
                // przy nich chowamy wybor zamiast obiecywac cos, czego nie bedzie.
                // Ksztalt pokazujemy przy Mecie i przy „auto".
                //
                // Przy „auto" dlatego, ze agregator moze skonczyc wlasnie na
                // Mecie — a wtedy wybor ksztaltu zadziala. Przy pozostalych
                // silnikach wartosc przeleci bez skutku, o czym mowi zdanie
                // pod chipami.
                if (pokazProporcje && (silnik == "meta" || silnik == "auto")) {
                    Text(
                        "Ksztalt kadru",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            "16:9" to "16:9 poziomo",
                            "9:16" to "9:16 pionowo",
                            "1:1" to "1:1 kwadrat",
                        ).forEach { (klucz, etykieta) ->
                            androidx.compose.material3.FilterChip(
                                selected = klucz == proporcje,
                                onClick = { proporcje = klucz },
                                label = { Text(etykieta, maxLines = 1) },
                            )
                        }
                    }
                    if (silnik == "auto") {
                        Text(
                            "Kształt uszanuje tylko Meta AI. Jeśli agregator wybierze " +
                                "Forge albo Flow, kadr wróci kwadratowy.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                androidx.compose.material3.OutlinedTextField(
                    value = dodatkowe,
                    onValueChange = { dodatkowe = it },
                    label = { Text("Dodatkowe polecenie (nieobowiązkowe)") },
                    placeholder = { Text("np. tło w pastelowym różu, iskry wokół wyrobu") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { naWykonaj(silnik, model, dodatkowe.trim(), proporcje) },
                enabled = opcje.firstOrNull { it.klucz == silnik }?.dostepny ?: false,
            ) { Text("Wykonaj") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = naAnuluj) { Text("Anuluj") }
        },
    )
}

/** Jedna droga do wyboru: czym, jak się nazywa, co potrafi i czy teraz żyje. */
data class TrzyOpcje(
    val klucz: String,
    val nazwa: String,
    val opis: String,
    val dostepny: Boolean,
)
