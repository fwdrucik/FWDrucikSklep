package pl.fwdrucik.sklep.ui.ekrany

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.fwdrucik.sklep.R
import pl.fwdrucik.sklep.pomoc.Podpowiedzi
import pl.fwdrucik.sklep.dane.TrojkaFirebase
import pl.fwdrucik.sklep.ui.StanUslugi

/**
 * Instrukcja obsługi i Centrum Połączeń w aplikacji.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EkranInstrukcji(
    kluczGemini: String,
    adresWarsztatu: String,
    swiatloWarsztatu: String,
    opisWarsztatu: String,
    chmura: TrojkaFirebase,
    museWarsztatu: String,
    stanForge: String = "",
    stanComfy: String = "",
    stanFlow: String = "",
    stanMeta: String = "",
    stanCopilot: String = "",
    silnikOpisu: String,
    silnikZdjecia: String,
    silnikAnimacji: String,
    naZapiszSilnik: (String, String) -> Unit,
    modelOpisu: String,
    modelObrazu: String,
    modeleTekstowe: List<String>,
    modeleObrazowe: List<String>,
    naZapiszModele: (String, String) -> Unit,
    stanApi: StanUslugi,
    stanGemini: StanUslugi,
    stanChmury: StanUslugi,
    naZapiszKlucz: (String) -> Unit,
    naZapiszAdres: (String) -> Unit,
    naZapiszChmure: (String, String, String) -> Unit,
    naSprawdzApi: () -> Unit,
    naSprawdzGemini: () -> Unit,
    naSprawdzChmure: () -> Unit,
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
        // Logo & Nagłówek
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_fwdrucik),
                        contentDescription = "FW Drucik Logo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(85.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Text(
                        "Centrum Pomocy i Statusu",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "Podgląd wszystkich 10 dróg generowania, stanu połączeń oraz wskazówki.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        // Stan wszystkich silników i połączeń
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Stan silników i połączeń", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Kolory kropki w pasku na górze odpowiadają poniższym usługom:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )

                    KontrolkaUslugi(
                        nazwa = "1. API fwdrucik.pl",
                        kolor = stanApi.kolor,
                        opis = stanApi.opis,
                        naSprawdzenie = naSprawdzApi,
                    )
                    KontrolkaUslugi(
                        nazwa = "2. Wspólna Baza (Firebase)",
                        kolor = stanChmury.kolor,
                        opis = stanChmury.opis,
                        naSprawdzenie = naSprawdzChmure,
                    )
                    KontrolkaUslugi(
                        nazwa = "3. Google Gemini (Chmura)",
                        kolor = stanGemini.kolor,
                        opis = stanGemini.opis,
                        naSprawdzenie = naSprawdzGemini,
                    )
                    KontrolkaWarsztatu(
                        swiatlo = swiatloWarsztatu,
                        opis = "4. Serwer PC: $opisWarsztatu",
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    KontrolkaUslugi(
                        nazwa = "5. Muse Code CLI (WSL)",
                        kolor = when (museWarsztatu) {
                            "dostepne" -> "zielony"
                            "brak mostu" -> "czerwony"
                            else -> "brak"
                        },
                        opis = when (museWarsztatu) {
                            "dostepne" -> "Gotowy — opisy produktów bez klucza i opłat."
                            "brak mostu" -> "Brak mostu do WSL lub sesja wygasła."
                            else -> "Serwer PC nie odpowiada."
                        },
                    )
                    KontrolkaUslugi(
                        nazwa = "6. Forge (SDXL / rembg)",
                        kolor = if (stanForge == "dziala") "zielony" else "brak",
                        opis = if (stanForge == "dziala") "Gotowy — wycinanie tła i poprawa światła lokalnie." else "Wygaszony (uruchomi się automatycznie).",
                    )
                    KontrolkaUslugi(
                        nazwa = "7. ComfyUI (Wan 2.2 Wideo)",
                        kolor = if (stanComfy == "dziala") "zielony" else "brak",
                        opis = if (stanComfy == "dziala") "Gotowy — animacje 5s na karcie RTX." else "Wygaszony dla oszczędności VRAM.",
                    )
                    KontrolkaUslugi(
                        nazwa = "8. Google Flow (Nano / Veo)",
                        kolor = if (stanFlow == "dziala") "zielony" else "brak",
                        opis = if (stanFlow == "dziala") "Połączony z sesją Chrome." else "Wygaszony.",
                    )
                    KontrolkaUslugi(
                        nazwa = "9. Meta AI (Obraz / Animacje)",
                        kolor = if (stanMeta == "dziala") "zielony" else "brak",
                        opis = if (stanMeta == "dziala") "Gotowy — formaty 16:9, 9:16, 1:1." else "Wygaszony.",
                    )
                    KontrolkaUslugi(
                        nazwa = "10. Microsoft Copilot (DALL-E 3 / GPT-4o)",
                        kolor = if (stanCopilot == "dziala") "zielony" else "brak",
                        opis = if (stanCopilot == "dziala") "Gotowy — DALL-E 3, opisy i Suno Audio." else "Wygaszony.",
                    )
                }
            }
        }

        // Czym pracować
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Poprzednie ustawienia silników", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Zachowane ustawienia starszych połączeń. Nowy asystent i obróbka mediów korzystają z katalogu warsztatu; wybór modelu zmienisz w kreatorze. Poprzednie opisy są dostępne w trybie Zaawansowanym.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )

                    WyborSilnika(
                        tytul = "Domyślny agent opisów",
                        wybrany = silnikOpisu.takeIf { it != "auto" } ?: "gemini",
                        opcje = listOf(
                            "gemini" to "Google Gemini",
                            "meta" to "Meta AI",
                            "copilot" to "Copilot",
                            "muse" to "Muse Code",
                        ),
                        naWybor = { naZapiszSilnik("opis", it) },
                    )
                    WyborSilnika(
                        tytul = "Domyślny agent zdjęć",
                        wybrany = silnikZdjecia.takeIf { it != "auto" } ?: "meta",
                        opcje = listOf(
                            "meta" to "Meta AI",
                            "forge" to "RTX 5070 (Rembg)",
                            "flow" to "Google Flow",
                            "copilot" to "Copilot",
                            "gemini" to "Google Gemini",
                        ),
                        naWybor = { naZapiszSilnik("zdjecie", it) },
                    )
                    WyborSilnika(
                        tytul = "Domyślny agent animacji",
                        wybrany = silnikAnimacji.takeIf { it != "auto" } ?: "meta",
                        opcje = listOf(
                            "meta" to "Meta AI (GIF 360°)",
                            "flow" to "Google Flow (Veo)",
                            "gemini" to "Google Gemini (Veo)",
                            "comfy" to "RTX 5070 (ComfyUI)",
                        ),
                        naWybor = { naZapiszSilnik("animacja", it) },
                    )
                }
            }
        }

        // Konfiguracja adresu serwera warsztatowego
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Adres serwera warsztatowego", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Komputer w warsztacie wykonuje zadania na karcie RTX (Forge, ComfyUI, Muse, Flow, Meta AI).",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    OutlinedTextField(
                        value = adres,
                        onValueChange = { adres = it },
                        label = { Text("Adres serwera (np. http://192.168.1.100:8770)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                    Button(
                        onClick = { naZapiszAdres(adres) },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Zapisz adres") }

                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TextButton(
                            onClick = { adres = pl.fwdrucik.sklep.dane.DOMYSLNY_ADRES_WARSZTATU },
                        ) { Text("Adres domowy (Wi-Fi)") }
                        TextButton(
                            onClick = { adres = pl.fwdrucik.sklep.dane.ADRES_ZDALNY_WARSZTATU },
                        ) { Text("Adres zdalny (Tailscale)") }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Wspolna baza z panelem", style = MaterialTheme.typography.titleSmall)
                    KontrolkaUslugi(
                        nazwa = "Firebase",
                        kolor = stanChmury.kolor,
                        opis = stanChmury.opis,
                        naSprawdzenie = naSprawdzChmure,
                    )
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
                    KontrolkaUslugi(
                        nazwa = "Gemini",
                        kolor = stanGemini.kolor,
                        opis = stanGemini.opis,
                        naSprawdzenie = naSprawdzGemini,
                    )

                    // Wybor modelu. Lista pojawia sie po sprawdzeniu klucza, bo
                    // wtedy i tak pobieramy ja z konta — nazwy u Google zmieniaja
                    // sie co kilka tygodni i wpisane w kod szybko sie rozjezdzaja.
                    Text(
                        "Model opisow: $modelOpisu",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    if (modeleTekstowe.isEmpty()) {
                        Text(
                            "Dotknij przycisku Sprawdz wyzej, zeby pobrac liste modeli z konta.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            modeleTekstowe.forEach { m ->
                                FilterChip(
                                    selected = m == modelOpisu,
                                    onClick = { naZapiszModele(m, modelObrazu) },
                                    label = { Text(m, maxLines = 1) },
                                )
                            }
                        }
                    }

                    Text(
                        "Model poprawiania zdjec: $modelObrazu",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    if (modeleObrazowe.isNotEmpty()) {
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            modeleObrazowe.forEach { m ->
                                FilterChip(
                                    selected = m == modelObrazu,
                                    onClick = { naZapiszModele(modelOpisu, m) },
                                    label = { Text(m, maxLines = 1) },
                                )
                            }
                        }
                    }
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

/** Jeden wiersz wyboru silnika: tytul i dwa chipy. */
@Composable
private fun WyborSilnika(
    tytul: String,
    wybrany: String,
    opcje: List<Pair<String, String>>,
    naWybor: (String) -> Unit,
) {
    Text(
        tytul,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        opcje.forEach { (klucz, etykieta) ->
            FilterChip(
                selected = wybrany == klucz,
                onClick = { naWybor(klucz) },
                label = { Text(etykieta, maxLines = 2) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
