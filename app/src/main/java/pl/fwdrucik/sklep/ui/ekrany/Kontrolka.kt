package pl.fwdrucik.sklep.ui.ekrany

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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


/**
 * Kontrolka usługi — ten sam język kolorów co lampka warsztatu.
 *
 *   zielony  — sprawdzone i działa
 *   żółty    — właśnie sprawdzam
 *   czerwony — odpowiedziało, ale źle: zły klucz, reguły, brak sesji
 *   szary    — nie sprawdzane albo nie skonfigurowane
 *
 * PO CO OSOBNA KONTROLKA NA KAŻDĄ USŁUGĘ: sklep wisi na czterech niezależnych
 * rzeczach (API sklepu, Gemini, wspólna baza, komputer w warsztacie) i każda
 * potrafi paść osobno. Bez tego rozbicia objaw jest zawsze ten sam — „nie
 * działa" — a ruch do wykonania zupełnie inny.
 */
@Composable
fun KontrolkaUslugi(
    nazwa: String,
    kolor: String,
    opis: String,
    modifier: Modifier = Modifier,
    naSprawdzenie: (() -> Unit)? = null,
) {
    val barwa = when (kolor) {
        "zielony" -> Color(0xFF43A047)
        "zolty" -> Color(0xFFFFD54F)
        "czerwony" -> Color(0xFFE5433A)
        else -> Color(0xFF5A6472)
    }

    Row(
        modifier = modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(12.dp).clip(CircleShape).background(barwa)
        )
        androidx.compose.foundation.layout.Column(
            Modifier
                .padding(start = 8.dp)
                .weight(1f)
                .semantics {
                    // Czytnik ekranu nie widzi koloru — stan musi paść słowem.
                    contentDescription = "$nazwa: $opis"
                },
        ) {
            Text(nazwa, style = MaterialTheme.typography.labelLarge)
            Text(
                opis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (naSprawdzenie != null) {
            androidx.compose.material3.TextButton(
                onClick = naSprawdzenie,
                enabled = kolor != "zolty",
            ) { Text(if (kolor == "zolty") "Sprawdzam..." else "Sprawdź") }
        }
    }
}

/**
 * Zegar z datą w pasku — ten sam, co na pulpicie aplikacji warsztatowej.
 *
 * PO CO W SKLEPIE: przy wystawianiu wyrobu liczy się godzina. Zamówienia mają
 * terminy, kurier ma odbiór do konkretnej godziny, a telefon leży ekranem
 * w dół albo obok na stole. Zegar w pasku oszczędza wyjścia z aplikacji —
 * a data mówi, czy „na jutro" znaczy jeszcze dziś, czy już po północy.
 *
 * Odświeża się co sekundę tylko wtedy, gdy ekran z nim jest widoczny:
 * `LaunchedEffect` gaśnie razem z kompozycją, więc nic nie chodzi w tle.
 */
@Composable
fun ZegarZData(modifier: Modifier = Modifier) {
    var teraz by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(java.util.Date())
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            teraz = java.util.Date()
            kotlinx.coroutines.delay(1_000)
        }
    }

    val godzina = androidx.compose.runtime.remember {
        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale("pl"))
    }
    val data = androidx.compose.runtime.remember {
        java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale("pl"))
    }

    androidx.compose.foundation.layout.Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.End,
    ) {
        Text(
            godzina.format(teraz),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
        Text(
            data.format(teraz).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Pasek miniaturowych kropek stanu dla wszystkich silników i połączeń.
 * Umieszczany na górnym pasku aplikacji.
 */
@Composable
fun PasekKropekStanu(
    stanApi: String,
    stanGemini: String,
    stanChmury: String,
    stanWarsztatu: String,
    stanMuse: String,
    stanForge: String,
    stanComfy: String,
    stanFlow: String,
    stanMeta: String,
    stanCopilot: String = "",
    modifier: Modifier = Modifier,
    naKlik: () -> Unit = {},
) {
    fun doKoloru(stan: String): Color = when (stan) {
        "zielony", "dziala", "dostepne" -> Color(0xFF43A047)
        "zolty", "w_toku" -> Color(0xFFFFD54F)
        "czerwony", "brak mostu" -> Color(0xFFE5433A)
        else -> Color(0xFF5A6472)
    }

    val kropki = listOf(
        "API Sklepu" to doKoloru(stanApi),
        "Firebase Baza" to doKoloru(stanChmury),
        "Google Gemini" to doKoloru(stanGemini),
        "Serwer PC" to doKoloru(stanWarsztatu),
        "Muse Code" to doKoloru(stanMuse),
        "Forge SDXL" to doKoloru(stanForge),
        "ComfyUI Wan" to doKoloru(stanComfy),
        "Google Flow" to doKoloru(stanFlow),
        "Meta AI" to doKoloru(stanMeta),
        "Copilot" to doKoloru(stanCopilot),
    )

    Row(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .clickable(onClick = naKlik)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp),
    ) {
        kropki.forEach { (nazwa, kolor) ->
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(kolor)
                    .semantics { contentDescription = "$nazwa: $kolor" }
            )
        }
    }
}

