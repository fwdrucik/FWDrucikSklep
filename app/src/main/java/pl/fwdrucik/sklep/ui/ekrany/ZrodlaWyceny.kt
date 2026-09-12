package pl.fwdrucik.sklep.ui.ekrany

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import pl.fwdrucik.sklep.narzedzia.bezpiecznyUrlZrodla
import pl.fwdrucik.sklep.siec.OfertaCenowa
import java.util.Locale

/** Wspólne źródła obu trybów. Zwykły tekst; strony otwiera wyłącznie użytkownik. */
@Composable
fun ZrodlaWyceny(oferty: List<OfertaCenowa>, sprawdzono: String, ostrzezenie: String?) {
    val przegladarka = LocalUriHandler.current
    var bladOtwarcia by remember(oferty) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!ostrzezenie.isNullOrBlank()) Text(ostrzezenie, color = MaterialTheme.colorScheme.error)
        if (sprawdzono.isNotBlank()) Text("Sprawdzono: $sprawdzono", style = MaterialTheme.typography.bodySmall)
        if (oferty.isNotEmpty()) Text("Źródła cen w internecie", style = MaterialTheme.typography.titleSmall)
        oferty.forEach { oferta ->
            val adres = remember(oferta.url) { bezpiecznyUrlZrodla(oferta.url) }
            val cena = if (oferta.cena.isFinite() && oferta.cena > 0)
                "%.2f".format(Locale.forLanguageTag("pl-PL"), oferta.cena) + " " + oferta.waluta.ifBlank { "zł" }
                else "Brak poprawnej ceny"
            Text("${oferta.portal} • $cena", style = MaterialTheme.typography.labelLarge)
            Text(oferta.tytul, style = MaterialTheme.typography.bodyMedium)
            if (oferta.fragment.isNotBlank()) Text(oferta.fragment, style = MaterialTheme.typography.bodySmall)
            if (adres != null) {
                TextButton(onClick = {
                    runCatching { przegladarka.openUri(adres) }.onFailure {
                        bladOtwarcia = "Nie udało się otworzyć źródła w przeglądarce."
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(adres, textDecoration = TextDecoration.Underline, style = MaterialTheme.typography.bodySmall)
                }
            } else Text("Brak bezpiecznego odnośnika HTTPS do źródła.", style = MaterialTheme.typography.bodySmall)
        }
        bladOtwarcia?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
