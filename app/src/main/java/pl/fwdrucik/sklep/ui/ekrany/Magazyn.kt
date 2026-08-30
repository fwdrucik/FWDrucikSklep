package pl.fwdrucik.sklep.ui.ekrany

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.fwdrucik.sklep.dane.Produkt

/**
 * Magazyn — jedno miejsce na poprawienie stanów po dniu w warsztacie.
 *
 * Osobno od kreatora, bo zmiana stanu to najczęstsza czynność w całej
 * aplikacji, a przechodzenie za każdym razem przez formularz z czternastoma
 * polami byłoby karą za sprzedaż.
 */
@Composable
fun EkranMagazynu(
    produkty: List<Produkt>,
    naZmianeStanu: (Produkt, Int?) -> Unit,
) {
    val naStanie = produkty.filter { !it.naZamowienie }
    val naZamowienie = produkty.filter { it.naZamowienie }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Podsumowanie(naStanie)
        }

        items(naStanie, key = { it.id }) { produkt ->
            WierszMagazynu(produkt, naZmianeStanu)
        }

        if (naZamowienie.isNotEmpty()) {
            item {
                Text(
                    "Wykonywane na zamówienie",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
                )
                Text(
                    "Te wyroby nie mają stanu magazynowego. Dotknij „Wpisz stan”, " +
                        "jeśli zaczynasz je trzymać gotowe na półce.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(naZamowienie, key = { it.id }) { produkt ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            produkt.nazwa,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { naZmianeStanu(produkt, 1) }) {
                            Text("Wpisz stan")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Podsumowanie(naStanie: List<Produkt>) {
    val brakuje = naStanie.count { it.brakNaStanie }
    val konczySie = naStanie.count { it.malyStan }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Stan warsztatu", style = MaterialTheme.typography.titleSmall)
            Text(
                when {
                    brakuje == 0 && konczySie == 0 ->
                        "Wszystko, co trzymasz na półce, jest dostępne."
                    brakuje > 0 && konczySie > 0 ->
                        "$brakuje bez stanu, $konczySie na wyczerpaniu."
                    brakuje > 0 -> "$brakuje pozycji pokazuje się jako niedostępne."
                    else -> "$konczySie pozycji się kończy — sklep pokazuje „Zostały…”."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (brakuje > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun WierszMagazynu(produkt: Produkt, naZmianeStanu: (Produkt, Int?) -> Unit) {
    val stan = produkt.stan ?: 0

    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    produkt.nazwa,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        produkt.brakNaStanie -> "Klient widzi: chwilowo niedostępny"
                        produkt.malyStan -> "Klient widzi: Zostały $stan ${produkt.jednostka}"
                        else -> "Klient widzi: na stanie"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        produkt.brakNaStanie -> MaterialTheme.colorScheme.error
                        produkt.malyStan -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            IconButton(
                onClick = { naZmianeStanu(produkt, (stan - 1).coerceAtLeast(0)) },
                enabled = stan > 0,
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Zmniejsz stan o jeden")
            }
            OutlinedTextField(
                value = stan.toString(),
                onValueChange = { nowy ->
                    nowy.toIntOrNull()?.let { naZmianeStanu(produkt, it.coerceAtLeast(0)) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(88.dp),
            )
            IconButton(onClick = { naZmianeStanu(produkt, stan + 1) }) {
                Icon(Icons.Filled.Add, contentDescription = "Zwiększ stan o jeden")
            }
        }
    }
}
