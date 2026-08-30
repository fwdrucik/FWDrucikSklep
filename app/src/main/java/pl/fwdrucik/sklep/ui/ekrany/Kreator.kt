package pl.fwdrucik.sklep.ui.ekrany

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import pl.fwdrucik.sklep.BuildConfig
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
    naOpiszZeZdjecia: (Uri, String, (SzkicProduktu) -> Unit) -> Unit,
    naPoprawZdjecie: (Uri, (Uri) -> Unit) -> Unit,
    naZapisz: (Produkt, String, String, Uri?, String) -> Unit,
    warsztatGotowy: Boolean,
    naZlecWarsztatowi: (String, Uri, String, (Uri) -> Unit) -> Unit,
    naWgrajZdjecie: (Int, Uri, String) -> Unit,
    naUsunZdjecie: (Int) -> Unit,
    naWyjscie: () -> Unit,
) {
    val p = istniejacy ?: Produkt()

    var nazwa by remember(p.id) { mutableStateOf(p.nazwa) }
    var kategoria by remember(p.id) { mutableStateOf(p.kategoria) }
    var opisKrotki by remember(p.id) { mutableStateOf(p.opisKrotki) }
    var opis by remember(p.id) { mutableStateOf(p.opis) }
    var cena by remember(p.id) { mutableStateOf(if (p.cenaGr > 0) groszeNaPole(p.cenaGr) else "") }
    var cenaPromo by remember(p.id) {
        mutableStateOf(p.cenaPromoGr?.let(::groszeNaPole).orEmpty())
    }
    var stan by remember(p.id) { mutableStateOf(p.stan?.toString().orEmpty()) }
    var jednostka by remember(p.id) { mutableStateOf(p.jednostka) }
    var waga by remember(p.id) { mutableStateOf(if (p.wagaG > 0) p.wagaG.toString() else "") }
    var czas by remember(p.id) { mutableStateOf(p.czasRealizacji) }
    var status by remember(p.id) { mutableStateOf(p.status) }
    var pozycja by remember(p.id) { mutableStateOf(p.pozycja.toString()) }
    var opisZdjecia by remember(p.id) { mutableStateOf("") }

    // Zdjęcie i notatka do agenta — stan wyłącznie tego ekranu.
    var lokalneZdjecie by remember(p.id) { mutableStateOf<Uri?>(null) }
    var notatka by remember(p.id) { mutableStateOf("") }
    var doUzupelnienia by remember(p.id) { mutableStateOf<List<String>>(emptyList()) }
    var animacja by remember(p.id) { mutableStateOf<Uri?>(null) }

    // Photo Picker nie wymaga zgody na dostęp do galerii — użytkownik wskazuje
    // jedno zdjęcie i tylko ono trafia do aplikacji.
    val wybierzDoAgenta = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> if (uri != null) lokalneZdjecie = uri }

    // Aparat. `TakePicture` zapisuje pod wskazany adres i oddaje samo
    // „udało się", więc adres trzeba zapamiętać przed uruchomieniem.
    // Kontrakt nie wymaga zgody CAMERA, bo zdjęcie robi aplikacja aparatu,
    // a nie my — jedno pytanie mniej przy pierwszym uruchomieniu.
    val context = LocalContext.current
    var adresZAparatu by remember { mutableStateOf<Uri?>(null) }
    val zrobZdjecie = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { udane: Boolean ->
        if (udane) lokalneZdjecie = adresZAparatu
    }

    val wybierzKolejne = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && p.id > 0) {
            naWgrajZdjecie(p.id, uri, opisZdjecia.ifBlank { nazwa })
            opisZdjecia = ""
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text(
            if (p.id == 0) "Nowy produkt" else "Edycja produktu",
            style = MaterialTheme.typography.headlineSmall,
        )

        // ------------------------------------------------- krok 1: zdjęcie
        Text(
            "1. Zacznij od zdjęcia",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
        )

        lokalneZdjecie?.let { uri ->
            Box {
                AsyncImage(
                    model = uri,
                    contentDescription = "Wybrane zdjęcie produktu",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().size(220.dp),
                )
                IconButton(
                    onClick = { lokalneZdjecie = null },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Odrzuć to zdjęcie")
                }
            }
        }

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

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        naOpiszZeZdjecia(lokalneZdjecie!!, notatka) { szkic ->
                            // Nie nadpisujemy tego, co już wpisałeś ręcznie —
                            // agent uzupełnia puste pola, a nie kasuje Twoją pracę.
                            if (nazwa.isBlank()) nazwa = szkic.nazwa
                            if (opisKrotki.isBlank()) opisKrotki = szkic.opisKrotki
                            if (opis.isBlank()) opis = szkic.opis
                            if (kategoria.isBlank() || kategoria == "inne") {
                                kategoria = szkic.kategoria
                            }
                            if (opisZdjecia.isBlank()) opisZdjecia = szkic.opisZdjecia
                            if (cena.isBlank() && szkic.cena.isNotBlank()) cena = szkic.cena
                            jednostka = szkic.jednostka
                            doUzupelnienia = szkic.doUzupelnienia
                        }
                    },
                    enabled = !agentPracuje,
                    modifier = Modifier.weight(1f),
                ) {
                    if (agentPracuje) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    }
                    Text("  Zredaguj opis")
                }
                OutlinedButton(
                    onClick = {
                        naPoprawZdjecie(lokalneZdjecie!!) { poprawione ->
                            lokalneZdjecie = poprawione
                        }
                    },
                    enabled = !agentPracuje,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Popraw zdjęcie")
                }
            }

            // Robota na komputerze w warsztacie: wlasna karta, za darmo,
            // bez limitow. Pokazujemy tylko wtedy, gdy komputer odpowiada —
            // przycisk, ktory na pewno nie zadziala, jest gorszy niz jego brak.
            if (warsztatGotowy) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            naZlecWarsztatowi(
                                "zdjecie-produktowe",
                                lokalneZdjecie!!,
                                notatka.ifBlank { nazwa },
                            ) { wynik -> lokalneZdjecie = wynik }
                        },
                        enabled = !agentPracuje,
                        modifier = Modifier.weight(1f),
                    ) { Text("Popraw na kompie") }

                    OutlinedButton(
                        onClick = {
                            naZlecWarsztatowi(
                                "animacja",
                                lokalneZdjecie!!,
                                notatka.ifBlank { nazwa },
                            ) { wynik -> animacja = wynik }
                        },
                        enabled = !agentPracuje,
                        modifier = Modifier.weight(1f),
                    ) { Text("Zrób animację") }
                }
                Text(
                    "Liczy karta w warsztacie — bez limitów i bez opłat. " +
                        "Animacja potrafi zająć kilka minut.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            animacja?.let { plik ->
                Card(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Animacja gotowa", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Plik leży w pamięci telefonu. Sklep przyjmuje na razie " +
                                "tylko zdjęcia — animację wrzuć na Instagram albo YouTube.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            plik.path ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
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

        // Agent mówi wprost, czego nie dało się ustalić ze zdjęcia. To jest
        // ważniejsze niż sam opis: sklep sprzedaje rzeczy fizyczne, a zgadnięty
        // materiał albo wymiar to gotowa reklamacja.
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

        // ------------------------------------------------- krok 2: opis
        Text(
            "2. Sprawdź i popraw",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 2.dp),
        )

        PoleZPodpowiedzia(nazwa, { nazwa = it }, Podpowiedzi.nazwa)
        PoleZPodpowiedzia(kategoria, { kategoria = it.lowercase().trim() }, Podpowiedzi.kategoria)
        PoleZPodpowiedzia(opisKrotki, { opisKrotki = it }, Podpowiedzi.opisKrotki, wiersze = 3)
        PoleZPodpowiedzia(opis, { opis = it }, Podpowiedzi.opis, wiersze = 6)
        PoleZPodpowiedzia(opisZdjecia, { opisZdjecia = it }, Podpowiedzi.opisZdjecia)

        // ------------------------------------------------- krok 3: handel
        Text(
            "3. Cena i dostępność",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
        )

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

        Text("Status", style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Statusy.produktu.forEach { s ->
                FilterChip(
                    selected = status == s,
                    onClick = { status = s },
                    label = { Text(Statusy.opisProduktu(s), maxLines = 1) },
                )
            }
        }
        KartaPodpowiedzi(Podpowiedzi.status)

        // ------------------------------------------- zdjęcia już na serwerze
        if (p.id > 0) {
            Text("Zdjęcia w sklepie", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp))

            if (p.obrazy.isNotEmpty()) {
                LazyRow(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(p.obrazy, key = { it.id }) { obraz ->
                        Box {
                            AsyncImage(
                                model = BuildConfig.ADRES_API + obraz.src,
                                contentDescription = obraz.alt.ifBlank { "Zdjęcie produktu" },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(110.dp),
                            )
                            IconButton(
                                onClick = { naUsunZdjecie(obraz.id) },
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Usuń to zdjęcie")
                            }
                        }
                    }
                }
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("Pierwsze zdjęcie na liście jest okładką") },
                )
            }

            OutlinedButton(
                onClick = {
                    wybierzKolejne.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) {
                Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                Text("  Dodaj kolejne zdjęcie")
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(onClick = naWyjscie, modifier = Modifier.weight(1f)) {
                Text("Wróć")
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
