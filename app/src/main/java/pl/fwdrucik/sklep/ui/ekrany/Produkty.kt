package pl.fwdrucik.sklep.ui.ekrany

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import pl.fwdrucik.sklep.BuildConfig
import pl.fwdrucik.sklep.dane.KopiaRobocza
import pl.fwdrucik.sklep.dane.Produkt
import pl.fwdrucik.sklep.dane.Statusy
import pl.fwdrucik.sklep.dane.groszeNaZlote

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedTextField

@Composable
fun EkranProduktow(
    produkty: List<Produkt>,
    naEdycje: (Int) -> Unit,
    naStatus: (Int, String) -> Unit,
    naUsun: (Int) -> Unit,
    kopie: List<KopiaRobocza> = emptyList(),
    naOtworzKopie: (Int) -> Unit = {},
    naUsunKopie: (Int) -> Unit = {},
    naImportujAllegro: ((String) -> Unit)? = null,
) {
    var filtr by remember { mutableStateOf("wszystkie") }
    var doUsuniecia by remember { mutableStateOf<Produkt?>(null) }
    var pokazImportAllegro by remember { mutableStateOf(false) }

    val widoczne = when (filtr) {
        "wszystkie" -> produkty
        else -> produkty.filter { it.status == filtr }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("wszystkie" to "Wszystkie").plus(
                    Statusy.produktu.map { it to Statusy.opisProduktu(it) }
                ).forEach { (klucz, etykieta) ->
                    FilterChip(
                        selected = filtr == klucz,
                        onClick = { filtr = klucz },
                        label = { Text(etykieta, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            if (naImportujAllegro != null) {
                AssistChip(
                    onClick = { pokazImportAllegro = true },
                    label = { Text("⚡ Import Allegro", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }

        if (pokazImportAllegro) {
            var urlAllegro by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { pokazImportAllegro = false },
                title = { Text("Importuj aukcję z Allegro") },
                text = {
                    Column {
                        Text(
                            "Wklej link do wystawionej aukcji Allegro. Sklep pobierze ustandaryzowany opis, tytuł (max 75 zn.), cenę oraz zdjęcia i opublikuje produkt na fwdrucik.pl.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(12.dp))
                        OutlinedTextField(
                            value = urlAllegro,
                            onValueChange = { urlAllegro = it },
                            label = { Text("Adres URL aukcji Allegro") },
                            placeholder = { Text("https://allegro.pl/oferta/...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (urlAllegro.isNotBlank()) {
                                naImportujAllegro?.invoke(urlAllegro.trim())
                                pokazImportAllegro = false
                            }
                        },
                        enabled = urlAllegro.isNotBlank() && urlAllegro.startsWith("http")
                    ) {
                        Text("Pobierz i opublikuj")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pokazImportAllegro = false }) {
                        Text("Anuluj")
                    }
                }
            )
        }

        if (widoczne.isEmpty() && kopie.isEmpty()) {
            PustaLista(filtr)
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Kopie robocze na gorze i tylko przy filtrze „wszystkie" albo
                // „szkic": to jeszcze nie sa produkty — serwer o nich nie wie,
                // wiec nie mieszaja sie do listy opublikowanych.
                if (kopie.isNotEmpty() && filtr in listOf("wszystkie", "szkic")) {
                    items(kopie, key = { "kopia-" + it.id }) { kopia ->
                        KartaKopii(
                            kopia = kopia,
                            naOtworz = { naOtworzKopie(kopia.id) },
                            naUsun = { naUsunKopie(kopia.id) },
                        )
                    }
                }

                items(widoczne, key = { it.id }) { produkt ->
                    KartaProduktu(
                        produkt = produkt,
                        naEdycje = { naEdycje(produkt.id) },
                        naStatus = { naStatus(produkt.id, it) },
                        naUsun = { doUsuniecia = produkt },
                    )
                }
            }
        }
    }

    // Usunięcie kasuje produkt razem ze zdjęciami i jest nieodwracalne —
    // dlatego pytamy, i pytamy nazwą produktu, a nie samym "czy na pewno".
    doUsuniecia?.let { produkt ->
        AlertDialog(
            onDismissRequest = { doUsuniecia = null },
            title = { Text("Usunąć produkt?") },
            text = {
                Text(
                    "„${produkt.nazwa}” zniknie ze sklepu razem ze wszystkimi zdjęciami. " +
                        "Tego nie da się cofnąć. Jeśli chcesz go tylko zdjąć ze sprzedaży, " +
                        "zamknij to okno i ustaw status na „ukryty”."
                )
            },
            confirmButton = {
                TextButton(onClick = { naUsun(produkt.id); doUsuniecia = null }) {
                    Text("Usuń bezpowrotnie")
                }
            },
            dismissButton = {
                TextButton(onClick = { doUsuniecia = null }) { Text("Zostaw") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KartaProduktu(
    produkt: Produkt,
    naEdycje: () -> Unit,
    naStatus: (String) -> Unit,
    naUsun: () -> Unit,
) {
    Card(onClick = naEdycje, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                produkt.nazwa,
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (produkt.obrazy.isNotEmpty()) {
                    AsyncImage(
                        model = BuildConfig.ADRES_API + produkt.obrazy.first().src,
                        contentDescription = produkt.obrazy.first().alt.ifBlank { produkt.nazwa },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp),
                    )
                } else {
                    Text("bez\nzdjęcia", style = MaterialTheme.typography.labelSmall)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        groszeNaZlote(produkt.cenaObowiazujaca) + " / " + produkt.jednostka,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        opisStanu(produkt),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (produkt.brakNaStanie || produkt.malyStan) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Trzy stany w kolku zamiast przelacznika na dwa.
                // Poprzednio z „ukryty" wracalo sie tylko do publikacji —
                // nie bylo jak powiedziec „wracam do roboty nad tym".
                val nastepny = Statusy.nastepnyProduktu(produkt.status)
                AssistChip(
                    onClick = { naStatus(nastepny) },
                    label = {
                        Text("${Statusy.nazwaProduktu(produkt.status)} → ${Statusy.nazwaProduktu(nastepny)}")
                    },
                )
                if (!produkt.allegroUrl.isNullOrBlank()) {
                    AssistChip(
                        onClick = {},
                        label = { Text("🟠 Allegro") },
                    )
                } else {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(Statusy.opisProduktu(produkt.status)) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = naEdycje) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edytuj ${produkt.nazwa}")
                }
                IconButton(onClick = naUsun) {
                    Icon(Icons.Filled.Delete, contentDescription = "Usuń ${produkt.nazwa}")
                }
            }
        }
    }
}

private fun opisStanu(produkt: Produkt): String = when {
    produkt.naZamowienie -> "Na zamówienie"
    produkt.brakNaStanie -> "Chwilowo niedostępny"
    else -> "Na stanie: ${produkt.stan} ${produkt.jednostka}"
}

@Composable
private fun PustaLista(filtr: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (filtr == "wszystkie") "Katalog jest pusty" else "Nic w tym stanie",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (filtr == "wszystkie") {
                "Dotknij plusa na dole, żeby dodać pierwszy produkt. " +
                    "Kreator prowadzi przez wszystkie pola i tłumaczy, po co każde jest."
            } else {
                "Zmień filtr u góry, żeby zobaczyć pozostałe produkty."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * Kopia robocza na liscie produktow — widoczna, ale wyraznie inna niz produkt.
 *
 * PO CO TU JEST: dotad niedokonczony szkic zylo wylacznie w kreatorze i o jego
 * istnieniu mowil dopiero baner po ponownym wejsciu. Tutaj widac go od razu,
 * razem z reszta roboty. Serwer o nim nie wie i klient go nie zobaczy —
 * dlatego etykieta mowi wprost „prywatna".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KartaKopii(kopia: KopiaRobocza, naOtworz: () -> Unit, naUsun: () -> Unit) {
    Card(onClick = naOtworz, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                kopia.nazwa.ifBlank { "Kopia robocza bez nazwy" },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "Kopia robocza — prywatna, tylko na tym telefonie" +
                    if (kopia.zapisano > 0) {
                        " · " + java.text.SimpleDateFormat("HH:mm", java.util.Locale("pl"))
                            .format(java.util.Date(kopia.zapisano))
                    } else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (kopia.zdjecie.isNotBlank()) {
                    AsyncImage(
                        model = java.io.File(kopia.zdjecie),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp),
                    )
                }
                FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = naOtworz) { Text("Dokończ") }
                    TextButton(onClick = naUsun) { Text("Usuń") }
                }
            }
        }
    }
}
