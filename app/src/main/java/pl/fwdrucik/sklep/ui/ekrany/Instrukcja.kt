package pl.fwdrucik.sklep.ui.ekrany

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import pl.fwdrucik.sklep.pomoc.Podpowiedzi
import pl.fwdrucik.sklep.dane.TrojkaFirebase

/**
 * Instrukcja obsługi w aplikacji.
 *
 * Te same podpowiedzi co w kreatorze, tylko zebrane w jednym miejscu i do
 * przeczytania na spokojnie — na przykład zanim zrobi się zdjęcia całej partii.
 */
@Composable
fun EkranInstrukcji(
    kluczGemini: String,
    adresWarsztatu: String,
    swiatloWarsztatu: String,
    opisWarsztatu: String,
    chmura: TrojkaFirebase,
    naZapiszKlucz: (String) -> Unit,
    naZapiszAdres: (String) -> Unit,
    naZapiszChmure: (String, String, String) -> Unit,
) {
    var klucz by remember(kluczGemini) { mutableStateOf(kluczGemini) }
    var adres by remember(adresWarsztatu) { mutableStateOf(adresWarsztatu) }
    var fbProject by remember(chmura.projectId) { mutableStateOf(chmura.projectId) }
    var fbApp by remember(chmura.appId) { mutableStateOf(chmura.appId) }
    var fbKlucz by remember(chmura.apiKey) { mutableStateOf(chmura.apiKey) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Jak wystawić produkt", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Kolejność, która działa: zapisz szkic z nazwą i ceną, zrób zdjęcia, " +
                    "wróć i uzupełnij opis, na końcu opublikuj. Produkt opublikowany " +
                    "bez zdjęcia trafia do Google jako pusta strona i tak już zostaje " +
                    "na kilka tygodni.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Serwer warsztatowy", style = MaterialTheme.typography.titleSmall)
                    KontrolkaWarsztatu(
                        swiatlo = swiatloWarsztatu,
                        opis = opisWarsztatu,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "Komputer w warsztacie robi animacje i poprawia zdjecia na wlasnej " +
                            "karcie — za darmo i bez limitow. Musi byc wlaczony, a telefon " +
                            "w tej samej sieci.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = adres,
                        onValueChange = { adres = it },
                        label = { Text("Adres serwera") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                    Button(
                        onClick = { naZapiszAdres(adres) },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Zapisz adres") }
                    Text(
                        "Zielona kropka — gotowe, policzy sie od razu. Zolta — cos wstaje " +
                            "albo liczy. Czerwona — karta zimna, pierwsze zlecenie potrwa " +
                            "okolo dwoch minut. Szara — komputer wylaczony.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Wspolna baza z panelem", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Zamowienia przepisuja sie do chmury, zeby panel warsztatowy je widzial - " +
                            "bez tego trzeba wchodzic tutaj i odswiezac. Trzy wartosci z konsoli " +
                            "Firebase, te same co w panelu. Puste pola = przenoszenie wylaczone.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = fbProject,
                        onValueChange = { fbProject = it },
                        label = { Text("Project ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                    OutlinedTextField(
                        value = fbApp,
                        onValueChange = { fbApp = it },
                        label = { Text("App ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = fbKlucz,
                        onValueChange = { fbKlucz = it },
                        label = { Text("Klucz API") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Button(
                        onClick = { naZapiszChmure(fbProject, fbApp, fbKlucz) },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Zapisz polaczenie") }
                    Text(
                        "Do chmury ida same naglowki: numer, kwota, data i liczba pozycji. " +
                            "Adres klienta i pozycje zostaja na serwerze - panel ma wiedziec, " +
                            "ze cos przyszlo i za ile, a nie znac adresu domowego klienta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Klucz Gemini", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Bez niego kreator działa normalnie — tylko opis piszesz sam. " +
                            "Z kluczem robisz zdjęcie, wpisujesz dwa słowa i dostajesz " +
                            "gotową nazwę, opis i kategorię.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    OutlinedTextField(
                        value = klucz,
                        onValueChange = { klucz = it },
                        label = { Text("Klucz API") },
                        singleLine = true,
                        // Klucz to sekret — nie ma prawa świecić na ekranie
                        // w warsztacie, gdzie za plecami stoi klient.
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                    Row {
                        Button(
                            onClick = { naZapiszKlucz(klucz) },
                            modifier = Modifier.padding(top = 8.dp),
                        ) { Text("Zapisz klucz") }
                        if (kluczGemini.isNotBlank()) {
                            TextButton(
                                onClick = { klucz = ""; naZapiszKlucz("") },
                                modifier = Modifier.padding(top = 8.dp, start = 8.dp),
                            ) { Text("Usuń") }
                        }
                    }
                    Text(
                        "Klucz zakładasz raz w Google AI Studio i wpisujesz tutaj. " +
                            "Zostaje na telefonie — nie ma go w kodzie aplikacji ani " +
                            "w kopii zapasowej. Po zmianie telefonu wpisujesz od nowa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Zdjęcia — cztery ujęcia, które sprzedają",
                        style = MaterialTheme.typography.titleSmall)
                    listOf(
                        "Całość na czystym tle, w świetle dziennym — to okładka.",
                        "Detal roboty: łączenie, spoina, słoje, faktura żywicy.",
                        "Wyrób w użyciu albo we wnętrzu — pokazuje skalę i zastosowanie.",
                        "Coś znanego obok dla skali: kubek, dłoń, linijka.",
                    ).forEachIndexed { i, tekst ->
                        Text(
                            "${i + 1}. $tekst",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Text(
                        "Serwer przyjmuje pliki do 6 MB. Zdjęcia z telefonu bywają " +
                            "większe — zmniejsz je w galerii przed wysłaniem.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        item {
            Text("Pola po kolei", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp))
        }

        items(Podpowiedzi.wszystkie) { podpowiedz ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(podpowiedz.pole, style = MaterialTheme.typography.titleSmall)
                    Text(
                        podpowiedz.krotko,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        "Przykład: ${podpowiedz.przyklad}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        podpowiedz.dlaczego,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Powiadomienia o zamówieniach",
                        style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Aplikacja sprawdza sklep co kwadrans, także zamknięta. " +
                            "Krótszego odstępu Android nie pozwala ustawić pracy w tle. " +
                            "Jeśli powiadomienia nie przychodzą, sprawdź w ustawieniach " +
                            "telefonu, czy aplikacja ma zgodę na powiadomienia i czy nie " +
                            "jest objęta oszczędzaniem baterii.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Zanim sklep ruszy", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Katalog jest widoczny dla klientów dopiero, gdy na serwerze " +
                            "ustawisz „sklep_otwarty” w konfiguracji. Do tego czasu " +
                            "strona /sklep/ pokazuje informację o przebudowie, a Ty " +
                            "możesz spokojnie wprowadzać produkty. Szczegóły w " +
                            "api/PLATNOSCI.md, punkt 6.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
