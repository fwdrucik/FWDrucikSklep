package pl.fwdrucik.sklep.ui.ekrany

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import pl.fwdrucik.sklep.siec.PozycjaKolejki

/**
 * Studio — jedno miejsce na to, co robi komputer w warsztacie.
 *
 * PO CO OSOBNY EKRAN: stan sześciu silników mieszkał dotąd w dziesięciu
 * kropkach na górnym pasku i w Pomocy. Kropka mówi „coś jest nie tak", ale nie
 * mówi co ani ile się czeka — a przy telefonie w ręku nad stołem liczy się
 * właśnie to drugie. Tu widać silnik po silniku i kolejkę do karty.
 */
@Composable
fun EkranStudia(
    stanForge: String,
    stanComfy: String,
    stanFlow: String,
    stanMeta: String,
    stanCopilot: String,
    stanMuse: String,
    opisWarsztatu: String,
    swiatloWarsztatu: String,
    kolejka: List<PozycjaKolejki>,
    zadaniaWToku: Int,
    modifier: Modifier = Modifier,
) {
    val silniki = listOf(
        Silnik("Forge", "zdjęcia produktowe", stanForge, "karta"),
        Silnik("ComfyUI", "animacje i kadry", stanComfy, "karta"),
        Silnik("Flow / Veo", "ujęcia z dźwiękiem", stanFlow, "chmura"),
        Silnik("Meta AI", "kadry i krótkie filmy", stanMeta, "chmura"),
        Silnik("Copilot", "kadry Designer", stanCopilot, "chmura"),
        Silnik("Muse Code", "opisy produktów", stanMuse, "tekst"),
    )

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PasekWarsztatu(swiatloWarsztatu, opisWarsztatu, zadaniaWToku)
        }

        item {
            Text(
                "Silniki",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // Dwa kafle w rzędzie, zwykłymi Row-ami.
        //
        // PO CO NIE LazyVerticalGrid: siatka wewnątrz LazyColumn przewija się
        // w tej samej osi co rodzic i wymaga wysokości podanej z góry. Sześć
        // kafli to trzy rzędy — Row jest tu prostszy i nie ma jak się zepsuć.
        items(silniki.chunked(2)) { para ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                para.forEach { silnik ->
                    Box(Modifier.weight(1f)) { KafelSilnika(silnik) }
                }
                if (para.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        item {
            Text(
                "Kolejka do karty",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item {
            Text(
                "Kolejność ustala serwer: najpierw najtańsze zlecenia, z karą za " +
                    "przeładowanie silnika i zniżką za czas już przeczekany. Zdjęcia " +
                    "wchodzą przed filmy, ale film czekający kwadrans wyprzedza wszystko.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (kolejka.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                ) {
                    Text(
                        if (zadaniaWToku > 0) "Karta liczy — nic nie czeka w kolejce."
                        else "Kolejka pusta. Karta wolna.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            items(kolejka) { pozycja -> WierszKolejki(pozycja) }
        }

        item { Spacer(Modifier.padding(bottom = 12.dp)) }
    }
}

private data class Silnik(
    val nazwa: String,
    val doCzego: String,
    val stan: String,
    val gdzie: String,
)

private fun barwaStanu(stan: String): Color = when (stan) {
    "dziala", "dostepne", "zielony" -> Color(0xFF43A047)
    "zolty", "w_toku" -> Color(0xFFFFD54F)
    "czerwony", "brak mostu" -> Color(0xFFE5433A)
    else -> Color(0xFF5A6472)
}

private fun slowoStanu(stan: String): String = when (stan) {
    "dziala", "dostepne" -> "gotowy"
    "brak mostu" -> "brak mostu"
    "wylaczony" -> "wyłączony"
    "" -> "nie sprawdzany"
    else -> stan
}

@Composable
private fun KafelSilnika(silnik: Silnik) {
    val barwa = barwaStanu(silnik.stan)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "${silnik.nazwa}: ${slowoStanu(silnik.stan)}"
        },
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(barwa))
                Text(
                    silnik.nazwa,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                silnik.doCzego,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "${slowoStanu(silnik.stan)} · ${silnik.gdzie}",
                style = MaterialTheme.typography.labelSmall,
                color = barwa,
                maxLines = 1,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun PasekWarsztatu(swiatlo: String, opis: String, wToku: Int) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(barwaStanu(swiatlo)))
                Text(
                    "Komputer w warsztacie",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                opis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (wToku > 0) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
                Text(
                    if (wToku == 1) "Liczy jedno zlecenie" else "Liczy $wToku zlecenia",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/**
 * Jedna pozycja kolejki. Koszt z serwera jest w sekundach i mówi wprost, ile
 * to zlecenie waży dla karty — pokazujemy go po ludzku, bo „koszt 420" nic nie
 * znaczy, a „około 7 min" mówi, czy warto stać nad stołem i czekać.
 */
@Composable
private fun WierszKolejki(pozycja: PozycjaKolejki) {
    // Czas liczenia bierzemy z `czasS`, nie z oceny agregatora: ocena spada
    // z czasem czekania i po kwadransie pokazywalaby „0 s" przy zleceniu,
    // ktore i tak liczy sie siedem minut.
    val czas = when {
        pozycja.czasS < 60 -> "~${pozycja.czasS} s"
        else -> "~${pozycja.czasS / 60} min"
    }
    val pierwszy = pozycja.koszt <= 0
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(pozycja.zadanie, style = MaterialTheme.typography.labelLarge)
                Text(
                    pozycja.id,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text(
                    czas,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (pierwszy) {
                    Text(
                        "czeka najdłużej",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}
