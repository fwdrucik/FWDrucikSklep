package pl.fwdrucik.sklep.ui.ekrany

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import pl.fwdrucik.sklep.dane.Produkt
import pl.fwdrucik.sklep.dane.Statusy
import pl.fwdrucik.sklep.dane.groszeNaZlote

@Composable
fun EkranProduktow(
    produkty: List<Produkt>,
    naEdycje: (Int) -> Unit,
    naStatus: (Int, String) -> Unit,
    naUsun: (Int) -> Unit,
) {
    var filtr by remember { mutableStateOf("wszystkie") }
    var doUsuniecia by remember { mutableStateOf<Produkt?>(null) }

    val widoczne = when (filtr) {
        "wszystkie" -> produkty
        else -> produkty.filter { it.status == filtr }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("wszystkie" to "Wszystkie") .plus(
                Statusy.produktu.map { it to Statusy.opisProduktu(it) }
            ).forEach { (klucz, etykieta) ->
                FilterChip(
                    selected = filtr == klucz,
                    onClick = { filtr = klucz },
                    label = { Text(etykieta, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }

        if (widoczne.isEmpty()) {
            PustaLista(filtr)
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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

@Composable
private fun KartaProduktu(
    produkt: Produkt,
    naEdycje: () -> Unit,
    naStatus: (String) -> Unit,
    naUsun: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (produkt.obrazy.isNotEmpty()) {
                AsyncImage(
                    model = BuildConfig.ADRES_API + produkt.obrazy.first().src,
                    contentDescription = produkt.obrazy.first().alt.ifBlank { produkt.nazwa },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp),
                )
            } else {
                Column(
                    Modifier.size(64.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("bez\nzdjęcia", style = MaterialTheme.typography.labelSmall)
                }
            }

            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    produkt.nazwa,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
                Row(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val nastepny = when (produkt.status) {
                        "opublikowany" -> "ukryty"
                        else -> "opublikowany"
                    }
                    AssistChip(
                        onClick = { naStatus(nastepny) },
                        label = {
                            Text(
                                if (nastepny == "opublikowany") "Opublikuj" else "Ukryj",
                                maxLines = 1,
                            )
                        },
                    )
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(Statusy.opisProduktu(produkt.status), maxLines = 1) },
                    )
                }
            }

            IconButton(onClick = naEdycje) {
                Icon(Icons.Filled.Edit, contentDescription = "Edytuj ${produkt.nazwa}")
            }
            IconButton(onClick = naUsun) {
                Icon(Icons.Filled.Delete, contentDescription = "Usuń ${produkt.nazwa}")
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
