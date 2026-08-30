package pl.fwdrucik.sklep.ui.ekrany

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import pl.fwdrucik.sklep.pomoc.Podpowiedz

/**
 * Pole formularza z podpowiedzią schowaną pod znakiem zapytania.
 *
 * Podpowiedź jest zwinięta, bo przy czternastu polach rozwinięte instrukcje
 * zamieniłyby kreator w ścianę tekstu. Ale jest przy każdym polu, więc nie
 * trzeba nigdzie wychodzić, żeby sprawdzić, o co chodzi.
 */
@Composable
fun PoleZPodpowiedzia(
    wartosc: String,
    naZmiane: (String) -> Unit,
    podpowiedz: Podpowiedz,
    modifier: Modifier = Modifier,
    liczbowe: Boolean = false,
    wiersze: Int = 1,
) {
    var pokazPomoc by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        OutlinedTextField(
            value = wartosc,
            onValueChange = naZmiane,
            label = { Text(podpowiedz.pole) },
            supportingText = { Text(podpowiedz.krotko) },
            singleLine = wiersze == 1,
            minLines = wiersze,
            keyboardOptions = if (liczbowe) {
                KeyboardOptions(keyboardType = KeyboardType.Decimal)
            } else {
                KeyboardOptions.Default
            },
            trailingIcon = {
                IconButton(onClick = { pokazPomoc = !pokazPomoc }) {
                    Icon(
                        if (pokazPomoc) Icons.Filled.Close else Icons.Outlined.HelpOutline,
                        contentDescription = if (pokazPomoc) {
                            "Schowaj podpowiedź do pola ${podpowiedz.pole}"
                        } else {
                            "Pokaż podpowiedź do pola ${podpowiedz.pole}"
                        },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(pokazPomoc) {
            KartaPodpowiedzi(podpowiedz)
        }
    }
}

@Composable
fun KartaPodpowiedzi(podpowiedz: Podpowiedz, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(top = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Przykład", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Text(podpowiedz.przyklad, style = MaterialTheme.typography.bodyMedium)
            Text("Po co to", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp))
            Text(podpowiedz.dlaczego, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Wiersz etykieta–wartość, używany w podglądzie zamówienia. */
@Composable
fun WierszDanych(etykieta: String, wartosc: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            etykieta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(0.4f),
        )
        Text(wartosc, style = MaterialTheme.typography.bodyMedium)
    }
}
