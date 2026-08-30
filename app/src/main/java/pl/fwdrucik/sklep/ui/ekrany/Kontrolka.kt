package pl.fwdrucik.sklep.ui.ekrany

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Kontrolka serwera warsztatowego — jak lampka na maszynie.
 *
 *   czerwony — nic nie chodzi, karta zimna; zlecenie ruszy, ale pierwsze
 *              potrwa około dwóch minut na rozruch backendu
 *   żółty    — backend wstaje albo coś właśnie liczy
 *   zielony  — gotowe, policzy się od razu
 *   szary    — komputer wyłączony albo telefon poza siecią domową
 *
 * Po co to w ogóle: różnica między zielonym a czerwonym to dwie minuty
 * czekania. Lepiej zobaczyć ją, zanim się stanie nad stołem z telefonem
 * w ręku i wyrobem w drugiej.
 */
@Composable
fun KontrolkaWarsztatu(
    swiatlo: String,
    opis: String,
    modifier: Modifier = Modifier,
    naDotkniecie: () -> Unit = {},
) {
    val kolor = when (swiatlo) {
        "zielony" -> Color(0xFF43A047)
        "zolty" -> Color(0xFFFFD54F)
        "czerwony" -> Color(0xFFE5433A)
        else -> Color(0xFF5A6472)
    }

    val nazwaStanu = when (swiatlo) {
        "zielony" -> "gotowy"
        "zolty" -> "pracuje"
        "czerwony" -> "model niezaładowany"
        else -> "niedostępny"
    }

    Row(
        modifier = modifier
            .clickable(onClick = naDotkniecie)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics {
                // Czytnik ekranu nie widzi koloru — musi dostać stan słowem.
                contentDescription = "Serwer warsztatowy: $nazwaStanu. $opis"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(12.dp).clip(CircleShape).background(kolor)
        )
        Text(
            text = opis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
