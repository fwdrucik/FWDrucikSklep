package pl.fwdrucik.sklep.ui.ekrany

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.fwdrucik.sklep.dane.KopiaRobocza
import pl.fwdrucik.sklep.dane.wybierzKategorieAllegro
import pl.fwdrucik.sklep.ui.StanWyszukiwaniaKategorii

@Composable
fun WyborKategoriiAllegro(
    dane: KopiaRobocza,
    wyniki: StanWyszukiwaniaKategorii,
    zajety: Boolean,
    naSzukaj: ((String) -> Unit)?,
    naZmien: (KopiaRobocza) -> Unit,
) {
    var fraza by rememberSaveable(dane.nazwa) { mutableStateOf(dane.nazwa) }
    var reczneId by rememberSaveable { mutableStateOf(false) }
    val wynikDlaFrazy = wyniki.fraza == fraza.trim()
    val poprawnaFraza = fraza.trim().length in 2..100
    Text("Kategoria na Allegro", style = MaterialTheme.typography.titleMedium)
    if (!dane.allegroKategoria.isNullOrBlank()) {
        Text(if (dane.allegroKategoriaNazwa.isNotBlank()) "Wybrana: ${dane.allegroKategoriaNazwa}"
            else "Kategoria jest zapisana. Nie zmienimy jej bez Twojego wyboru.")
        if (dane.allegroKategoriaSciezka.isNotBlank()) Text(dane.allegroKategoriaSciezka, style = MaterialTheme.typography.bodySmall)
    } else Text("Znajdź kategorię i wybierz tę, która najlepiej opisuje wyrób.")
    OutlinedTextField(fraza, { fraza = it }, label = { Text("Czego szukamy?") },
        supportingText = { Text(if (fraza.isNotBlank() && !poprawnaFraza) "Wpisz od 2 do 100 znaków, np. „miska”."
            else "Na początek nazwa wyrobu. Możesz ją skrócić, np. do „miska”.") },
        isError = fraza.isNotBlank() && !poprawnaFraza,
        enabled = !zajety, modifier = Modifier.fillMaxWidth())
    OutlinedButton(onClick = { naSzukaj?.invoke(fraza) }, enabled = !zajety && !wyniki.wToku && poprawnaFraza && naSzukaj != null,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(if (wyniki.wToku) "Szukam kategorii…" else "Znajdź pasującą kategorię")
    }
    if (naSzukaj == null) Text("Wyszukiwanie jest teraz niedostępne. Zachowaj kopię i spróbuj później.")
    if (wyniki.wToku) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (wynikDlaFrazy && !wyniki.wToku && wyniki.sprawdzono) {
        if (wyniki.blad != null) Text(wyniki.blad, color = MaterialTheme.colorScheme.error)
        else if (wyniki.kategorie.isEmpty()) Text("Nie znaleziono kategorii. Wpisz krótszą nazwę wyrobu i spróbuj ponownie.")
        else {
            Text("Wybierz jedną kategorię. Samo wyszukanie niczego nie zmienia.")
            Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                wyniki.kategorie.forEach { kategoria ->
                    OutlinedButton(onClick = {
                        naZmien(dane.wybierzKategorieAllegro(kategoria))
                    }, enabled = !zajety, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Column(Modifier.fillMaxWidth()) {
                            Text((if (dane.allegroKategoria == kategoria.id) "Wybrana: " else "Wybierz: ") + kategoria.nazwa)
                            if (kategoria.sciezka.isNotBlank()) Text(kategoria.sciezka, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    TextButton(onClick = { reczneId = !reczneId }, enabled = !zajety) {
        Text(if (reczneId) "Schowaj numer kategorii" else "Wpisz ID ręcznie (opcjonalnie)")
    }
    if (reczneId) OutlinedTextField(dane.allegroKategoria.orEmpty(), {
        naZmien(dane.copy(allegroKategoria = it, allegroKategoriaNazwa = "", allegroKategoriaSciezka = ""))
    }, label = { Text("Numer kategorii Allegro (ID)") },
        supportingText = { Text("Użyj tylko wtedy, gdy znasz właściwy numer. Nazwa kategorii sklepu nie jest numerem Allegro.") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !zajety, modifier = Modifier.fillMaxWidth())
}
