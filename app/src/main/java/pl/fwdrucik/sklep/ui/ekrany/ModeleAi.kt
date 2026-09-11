package pl.fwdrucik.sklep.ui.ekrany

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.fwdrucik.sklep.siec.KatalogAi
import pl.fwdrucik.sklep.siec.kosztPoPolsku

@Composable
fun WyborModeluAi(
    rodzaj: String,
    wybrany: String,
    katalog: KatalogAi,
    wToku: Boolean,
    blad: String?,
    naOdswiez: () -> Unit,
    naWybierz: (String) -> Unit,
) {
    var otwarte by rememberSaveable { mutableStateOf(false) }
    val etykieta = when (rodzaj) { "tekst" -> "Opis"; "obraz" -> "Zdjęcie"; else -> "Film" }
    val pozycja = katalog.dla(rodzaj).firstOrNull { it.id == wybrany }
    OutlinedButton(onClick = { otwarte = true; naOdswiez() }, modifier = Modifier.fillMaxWidth()) {
        Text("$etykieta: " + if (wybrany == "auto") "Automatycznie" else pozycja?.nazwa ?: "Model wymaga sprawdzenia")
    }
    Text(
        if (wybrany == "auto") if (rodzaj == "tekst") "Automatycznie: bezpłatna lub lokalna droga. Dostępność sprawdza warsztat."
            else "Automatycznie: domyślna droga warsztatu. Koszt zależy od usługi."
        else pozycja?.let { "Koszt: ${kosztPoPolsku(it.koszt)}. " + if (it.dostepny) "Dostępny." else "Niedostępny: ${it.powod}" }
            ?: "Ten model nie jest już na liście. Wybierz inny lub Automatycznie.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (blad != null) Text(blad, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    if (otwarte) AlertDialog(
        onDismissRequest = { otwarte = false },
        title = { Text("Wybierz model: ${etykieta.lowercase()}") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text("Lista i koszty pochodzą z warsztatu. Płatne API lub abonament mogą wymagać opłat.")
                WierszModelu("Automatycznie", if (rodzaj == "tekst") "Bezpłatna lub lokalna droga; gdy brak dostępnej — zobaczysz błąd."
                    else "Domyślna droga warsztatu. Nie oznacza, że każda usługa jest bezpłatna.",
                    wybrany == "auto", true) { naWybierz("auto"); otwarte = false }
                if (wToku) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Sprawdzam dostępność…")
                }
                if (blad != null) Text(blad, color = MaterialTheme.colorScheme.error)
                if (!wToku && katalog.dla(rodzaj).isEmpty()) Text("Brak modeli tego rodzaju na aktualnej liście.")
                katalog.dla(rodzaj).forEach { model ->
                    WierszModelu(
                        model.nazwa.ifBlank { model.id },
                        (if (model.dostepny) "Dostępny. " else "Niedostępny. ") +
                            "Koszt: ${kosztPoPolsku(model.koszt)}. Droga: ${model.droga.ifBlank { "nie podano" }}. " +
                            (if (rodzaj == "tekst") "Odczyt zdjęcia: ${if (model.vision) "tak" else "nie, opis tylko ze słów"}. " else "") + model.powod,
                        wybrany == model.id, katalog.ok && model.dostepny && !wToku,
                    ) { naWybierz(model.id); otwarte = false }
                }
            }
        },
        confirmButton = { TextButton(onClick = naOdswiez, enabled = !wToku) { Text("Odśwież listę") } },
        dismissButton = { TextButton(onClick = { otwarte = false }) { Text("Wróć") } },
    )
}

@Composable
private fun WierszModelu(nazwa: String, opis: String, wybrany: Boolean, dostepny: Boolean, naKlik: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = wybrany, onClick = naKlik, enabled = dostepny)
        Column(Modifier.weight(1f)) {
            TextButton(onClick = naKlik, enabled = dostepny, contentPadding = PaddingValues(0.dp)) {
                Text(nazwa, style = MaterialTheme.typography.titleSmall)
            }
            Text(opis, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun DialogZadaniaAi(
    zadanie: String,
    katalog: KatalogAi,
    wToku: Boolean,
    blad: String?,
    modelTekstu: String,
    modelObrazu: String,
    modelWideo: String,
    naModel: (String, String) -> Unit,
    naOdswiez: () -> Unit,
    naAnuluj: () -> Unit,
    naWykonaj: (String, String) -> Unit,
) {
    var dodatkowe by rememberSaveable { mutableStateOf("") }
    var proporcje by rememberSaveable { mutableStateOf("16:9") }
    val rodzaje = when (zadanie) {
        "opis", "poprawa-opisu" -> listOf("tekst")
        "auto-ciag" -> listOf("obraz", "wideo")
        "animacja" -> listOf("wideo")
        else -> listOf("obraz")
    }
    fun model(rodzaj: String) = when (rodzaj) { "tekst" -> modelTekstu; "obraz" -> modelObrazu; else -> modelWideo }
    AlertDialog(
        onDismissRequest = naAnuluj,
        title = { Text(when (zadanie) {
            "opis" -> "Ułóż opis po polsku"
            "poprawa-opisu" -> "Popraw pisownię i zdania"
            "auto-ciag" -> "Tło, światło i film"
            "tlo" -> "Zmień tło zdjęcia"
            "upiekszanie" -> "Popraw światło i ostrość"
            else -> "Przygotuj film z wyrobem"
        }) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rodzaje.forEach { rodzaj ->
                    WyborModeluAi(rodzaj, model(rodzaj), katalog, wToku, blad, naOdswiez) { naModel(rodzaj, it) }
                }
                OutlinedTextField(dodatkowe, { dodatkowe = it }, label = { Text("Dodatkowa prośba (możesz pominąć)") })
                if ("tekst" !in rodzaje) {
                    Text("Kształt zdjęcia lub filmu")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("16:9" to "Poziomo", "9:16" to "Pionowo", "1:1" to "Kwadrat").forEach { (id, nazwa) ->
                            FilterChip(proporcje == id, { proporcje = id }, label = { Text(nazwa) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { naWykonaj(dodatkowe, proporcje) }, enabled = !wToku && rodzaje.all { katalog.moznaWybrac(model(it), it) }) {
                Text("Wykonaj")
            }
        },
        dismissButton = { TextButton(onClick = naAnuluj) { Text("Anuluj") } },
    )
}
