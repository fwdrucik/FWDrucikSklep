package pl.fwdrucik.sklep.ui.ekrany

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.fwdrucik.sklep.dane.Statusy
import pl.fwdrucik.sklep.dane.Zamowienie
import pl.fwdrucik.sklep.dane.groszeNaZlote

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EkranZamowien(
    zamowienia: List<Zamowienie>,
    naZmianeStatusu: (Int, String) -> Unit,
) {
    if (zamowienia.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Brak zamówień", style = MaterialTheme.typography.titleMedium)
            Text(
                "Gdy ktoś złoży zamówienie, dostaniesz powiadomienie — aplikacja " +
                    "sprawdza sklep co kwadrans, także gdy jest zamknięta.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(zamowienia, key = { it.id }) { zamowienie ->
            KartaZamowienia(zamowienie, naZmianeStatusu)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KartaZamowienia(
    zamowienie: Zamowienie,
    naZmianeStatusu: (Int, String) -> Unit,
) {
    var rozwiniete by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        zamowienie.numer.ifBlank { "Zamówienie ${zamowienie.id}" },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        zamowienie.utworzone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    groszeNaZlote(zamowienie.sumaGr),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Text(
                Statusy.opisZamowienia(zamowienie.status),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )

            TextButton(onClick = { rozwiniete = !rozwiniete }) {
                Text(if (rozwiniete) "Zwiń" else "Pokaż szczegóły")
            }

            if (rozwiniete) {
                Text("Pozycje", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 6.dp))
                zamowienie.pozycje.forEach { pozycja ->
                    WierszDanych(
                        "${pozycja.ilosc} × ${pozycja.nazwa}",
                        groszeNaZlote(pozycja.cenaGr * pozycja.ilosc),
                    )
                }

                Text("Klient", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp))
                WierszDanych("Konto", zamowienie.nick)
                zamowienie.adres.forEach { (klucz, wartosc) ->
                    if (wartosc.isNotBlank()) WierszDanych(klucz, wartosc)
                }
                if (zamowienie.dostawa.isNotBlank()) {
                    WierszDanych("Dostawa", zamowienie.dostawa)
                }
                if (zamowienie.platnosc.isNotBlank()) {
                    WierszDanych("Zapłata", zamowienie.platnosc)
                }

                Text("Zmień status", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Statusy.zamowienia.forEach { status ->
                        FilterChip(
                            selected = zamowienie.status == status,
                            onClick = { naZmianeStatusu(zamowienie.id, status) },
                            label = { Text(Statusy.opisZamowienia(status), maxLines = 1) },
                        )
                    }
                }
            }
        }
    }
}
