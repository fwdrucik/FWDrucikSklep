package pl.fwdrucik.sklep.ui.ekrany

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import pl.fwdrucik.sklep.dane.KopiaRobocza
import pl.fwdrucik.sklep.dane.zloteNaGrosze
import pl.fwdrucik.sklep.dane.brakiSzkicuAllegro
import pl.fwdrucik.sklep.narzedzia.AllegroFormat
import pl.fwdrucik.sklep.siec.KatalogAi
import pl.fwdrucik.sklep.siec.kosztPoPolsku
import pl.fwdrucik.sklep.siec.pytanieOBrakujacyFakt

/** Te same pola co w zaawansowanym kreatorze, pokazywane po jednym kroku. */
@Composable
fun AsystentSklepu(
    dane: KopiaRobocza,
    zdjecie: Uri?,
    animacja: Uri?,
    zajety: Boolean,
    katalog: KatalogAi,
    katalogWToku: Boolean,
    bladKatalogu: String?,
    naZmien: (KopiaRobocza) -> Unit,
    naAparat: () -> Unit,
    naGalerie: () -> Unit,
    naOpis: () -> Unit,
    naOdswiezModele: () -> Unit,
    naZachowaj: () -> Unit,
    naPublikuj: () -> Unit,
    naZaawansowane: () -> Unit,
    naPoprawTloISwiatlo: () -> Unit,
    naAnimuj: () -> Unit,
    postep: String?,
    bladOperacji: String?,
    naZbadajCeny: (() -> Unit)?,
    badanieCen: Boolean,
    sugestiaCeny: Double?,
    wycenaZmierzona: Boolean,
    ostrzezenieWyceny: String?,
    bladWyceny: String?,
    naSzkicAllegro: (() -> Unit)?,
    tworzenieSzkicu: Boolean,
    komunikatAllegro: String?,
    maZdjecieNaSerwerze: Boolean,
    wyszukiwanieKategorii: pl.fwdrucik.sklep.ui.StanWyszukiwaniaKategorii,
    naSzukajKategorii: ((String) -> Unit)?,
) {
    val context = LocalContext.current
    var uwaga by rememberSaveable { mutableStateOf("") }
    var faktyRozwiniete by rememberSaveable { mutableStateOf(false) }
    var potwierdzPublikacje by remember { mutableStateOf(false) }
    var ustawieniaTekstu by rememberSaveable { mutableStateOf(false) }
    var ustawieniaMediow by rememberSaveable { mutableStateOf(false) }
    var allegroRozwiniete by rememberSaveable { mutableStateOf(false) }
    var potwierdzAllegro by remember { mutableStateOf(false) }
    var widocznyBlad by rememberSaveable { mutableStateOf<String?>(null) }
    var ostatniPostep by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(bladOperacji, zajety) {
        if (!bladOperacji.isNullOrBlank()) widocznyBlad = bladOperacji
        else if (zajety) widocznyBlad = null
    }
    LaunchedEffect(postep, zajety) {
        if (!zajety) ostatniPostep = null
        else if (!postep.isNullOrBlank()) ostatniPostep = postep
    }
    val krok = dane.krokAsystenta.coerceIn(1, 4)
    val brakiAllegro = dane.brakiSzkicuAllegro()
    val glosIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Powiedz kilka słów o wyrobie")
        }
    }
    val moznaDyktowac = remember { glosIntent.resolveActivity(context.packageManager) != null }
    val dyktowanie = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { wynik ->
        val slowa = wynik.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        if (wynik.resultCode == Activity.RESULT_OK && slowa.isNotBlank()) {
            naZmien(dane.copy(notatka = listOf(dane.notatka, slowa).filter { it.isNotBlank() }.joinToString(" ")))
            uwaga = "Dodano Twoje słowa. Możesz je poprawić."
        } else uwaga = "Nie zapisano głosu. Spróbuj ponownie lub wpisz kilka słów."
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Dodaj wyrób krok po kroku", style = MaterialTheme.typography.titleLarge)
        Text("Nie musisz pisać poprawnie. Kilka słów wystarczy.")
        Text("Krok $krok z 4", style = MaterialTheme.typography.labelLarge)
        if (zajety) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(ostatniPostep ?: "Pracuję… Film może potrwać kilka minut. Poczekaj na wynik.")
        }
        widocznyBlad?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when (krok) {
            1 -> {
                Text("1. Pokaż wyrób i powiedz, co to jest", style = MaterialTheme.typography.titleMedium)
                zdjecie?.let { AsyncImage(it, "Wybrane zdjęcie wyrobu", Modifier.fillMaxWidth().heightIn(max = 240.dp)) }
                Button(onClick = naAparat, enabled = !zajety, modifier = Modifier.fillMaxWidth()) { Text("Zrób zdjęcie") }
                OutlinedButton(onClick = naGalerie, enabled = !zajety, modifier = Modifier.fillMaxWidth()) { Text("Wybierz zdjęcie z telefonu") }
                OutlinedTextField(dane.notatka, { naZmien(dane.copy(notatka = it)) },
                    modifier = Modifier.fillMaxWidth(), enabled = !zajety, minLines = 2,
                    label = { Text("Co to jest?") }, placeholder = { Text("np. miska niebieska na klucze") },
                    supportingText = { Text("Możesz pisać z błędami. Opis możesz też zrobić bez zdjęcia.") })
                OutlinedButton(onClick = {
                    runCatching { dyktowanie.launch(glosIntent) }.onFailure {
                        uwaga = "Dyktowanie nie uruchomiło się. Wpisz kilka słów w polu powyżej."
                    }
                }, enabled = moznaDyktowac && !zajety, modifier = Modifier.fillMaxWidth()) { Text("Powiedz zamiast pisać") }
                Text(if (moznaDyktowac) "Dyktuje aplikacja w telefonie. Może potrzebować internetu."
                    else "Telefon nie ma dostępnego dyktowania. Możesz użyć klawiatury.", style = MaterialTheme.typography.bodySmall)
                if (uwaga.isNotBlank()) Text(uwaga)
                TextButton(onClick = { faktyRozwiniete = !faktyRozwiniete }) { Text(if (faktyRozwiniete) "Schowaj szczegóły" else "Dodaj materiał lub wymiary (opcjonalnie)") }
                if (faktyRozwiniete) {
                    OutlinedTextField(dane.material, { naZmien(dane.copy(material = it)) }, label = { Text("Z czego jest?") }, enabled = !zajety, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(dane.wymiary, { naZmien(dane.copy(wymiary = it)) }, label = { Text("Jakie ma wymiary?") }, supportingText = { Text("Dopisz jednostkę, np. 20 cm. Jeśli nie wiesz, zostaw puste.") }, enabled = !zajety, modifier = Modifier.fillMaxWidth())
                }
                Button(onClick = { naZmien(dane.copy(krokAsystenta = 2)) }, enabled = !zajety, modifier = Modifier.fillMaxWidth()) { Text("Dalej: opis") }
            }
            2 -> {
                Text("2. Przygotuj i sprawdź opis", style = MaterialTheme.typography.titleMedium)
                Text("Asystent poprawi pisownię i ułoży zdania. Nieznane fakty powinien zostawić do uzupełnienia.")
                TextButton(onClick = { ustawieniaTekstu = !ustawieniaTekstu }, enabled = !zajety) {
                    Text(if (ustawieniaTekstu) "Schowaj wybór modelu" else "Wybór modelu — zaawansowane")
                }
                if (ustawieniaTekstu) WyborModeluAi("tekst", dane.modelTekstuAi, katalog, katalogWToku, bladKatalogu, naOdswiezModele) { naZmien(dane.copy(modelTekstuAi = it)) }
                else Text(podsumowanieModelu("tekst", dane.modelTekstuAi, katalog), style = MaterialTheme.typography.bodySmall)
                Button(onClick = naOpis, enabled = !zajety && !katalogWToku && katalog.moznaWybrac(dane.modelTekstuAi, "tekst") &&
                    (zdjecie != null || listOf(dane.notatka, dane.nazwa, dane.material, dane.wymiary).any { it.isNotBlank() }), modifier = Modifier.fillMaxWidth()) {
                    Text(if (zajety) "Układam opis…" else "Ułóż opis z moich słów")
                }
                OutlinedTextField(dane.nazwa, { naZmien(dane.copy(nazwa = it)) }, label = { Text("Nazwa wyrobu") }, enabled = !zajety, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dane.opisKrotki, { naZmien(dane.copy(opisKrotki = it)) }, label = { Text("Opis w jednym zdaniu") }, enabled = !zajety, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dane.opis, { naZmien(dane.copy(opis = it)) }, label = { Text("Opis wyrobu") }, minLines = 3, enabled = !zajety, modifier = Modifier.fillMaxWidth())
                dane.doUzupelnienia.forEach { Text("Do sprawdzenia: ${pytanieOBrakujacyFakt(it)}") }
                if (dane.zrodloOpisu.isNotBlank()) Text(dane.zrodloOpisu, style = MaterialTheme.typography.bodySmall)
                if (dane.ostrzezenieOpisu.isNotBlank()) Text(dane.ostrzezenieOpisu, color = MaterialTheme.colorScheme.error)
                Button(onClick = { naZmien(dane.copy(krokAsystenta = 3)) }, enabled = !zajety && dane.nazwa.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Dalej: zdjęcie i krótki film") }
            }
            3 -> {
                Text("3. Zdjęcie i krótki film", style = MaterialTheme.typography.titleMedium)
                Text("Opcjonalnie. Możesz zostawić swoje zdjęcie i pominąć film.")
                zdjecie?.let { AsyncImage(it, "Zdjęcie używane do poprawy i filmu", Modifier.fillMaxWidth().heightIn(max = 240.dp)) }
                if (zdjecie == null) Text("Najpierw dodaj zdjęcie w kroku 1 albo pomiń ten krok.")
                Button(onClick = naPoprawTloISwiatlo, enabled = !zajety && zdjecie != null && !katalogWToku && katalog.moznaWybrac(dane.modelObrazuAi, "obraz"),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Popraw tło i światło") }
                Text(podsumowanieModelu("obraz", dane.modelObrazuAi, katalog), style = MaterialTheme.typography.bodySmall)
                Button(onClick = naAnimuj, enabled = !zajety && zdjecie != null && !katalogWToku && katalog.moznaWybrac(dane.modelWideoAi, "wideo"),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Zrób krótki film ze zdjęcia") }
                Text(podsumowanieModelu("wideo", dane.modelWideoAi, katalog), style = MaterialTheme.typography.bodySmall)
                Text("Każdy przycisk zleca tylko jedną czynność. Sprawdź wynik i zdecyduj, czy go użyć. AI może zmienić szczegóły — odrzuć wynik, jeśli nie zgadza się z wyrobem.")
                if (animacja != null) Text("Film jest zachowany w kopii. Obejrzysz go w następnym kroku.")
                TextButton(onClick = { ustawieniaMediow = !ustawieniaMediow }, enabled = !zajety) {
                    Text(if (ustawieniaMediow) "Schowaj wybór modeli" else "Wybór modeli — zaawansowane")
                }
                if (ustawieniaMediow) {
                    WyborModeluAi("obraz", dane.modelObrazuAi, katalog, katalogWToku, bladKatalogu, naOdswiezModele) { naZmien(dane.copy(modelObrazuAi = it)) }
                    WyborModeluAi("wideo", dane.modelWideoAi, katalog, katalogWToku, bladKatalogu, naOdswiezModele) { naZmien(dane.copy(modelWideoAi = it)) }
                }
                Button(onClick = { naZmien(dane.copy(krokAsystenta = 4)) }, enabled = !zajety, modifier = Modifier.fillMaxWidth()) { Text("Dalej: cena i podgląd (bez zlecania AI)") }
            }
            4 -> {
                Text("4. Wpisz cenę i sprawdź całość", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(dane.cena, { naZmien(dane.copy(cena = it)) }, label = { Text("Cena w złotych") }, placeholder = { Text("np. 49,90") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !zajety, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { naZbadajCeny?.invoke() }, enabled = !zajety && naZbadajCeny != null && dane.nazwa.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(if (badanieCen) "Sprawdzam ceny…" else "Sprawdź ceny podobnych wyrobów")
                }
                Text("Opcjonalnie. Porównanie nie zmienia Twojej ceny.", style = MaterialTheme.typography.bodySmall)
                if (!bladWyceny.isNullOrBlank()) Text(bladWyceny, color = MaterialTheme.colorScheme.error)
                if (!badanieCen && wycenaZmierzona && sugestiaCeny != null && sugestiaCeny.isFinite() && sugestiaCeny > 0) {
                    val kwota = "%.2f".format(java.util.Locale.US, sugestiaCeny)
                    Text("Propozycja na podstawie ofert: ${kwota.replace('.', ',')} zł. To ceny ofert, nie potwierdzonych sprzedaży.")
                    if (!ostrzezenieWyceny.isNullOrBlank()) Text(ostrzezenieWyceny, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { naZmien(dane.copy(cena = kwota)) }, enabled = !zajety) { Text("Użyj tej ceny w sklepie") }
                }
                Text("Zdjęcie, opis i cena muszą zgadzać się z tym, co sprzedajesz.")
                PodgladOgloszenia(zdjecie, dane.nazwa, dane.cena, dane.opisKrotki, dane.opis, dane.kategoria, dane.status, animacja)
                Text(if (dane.stan.isBlank()) "Dostępność: wykonywane na zamówienie." else "Stan: ${dane.stan} ${dane.jednostka}")
                TextButton(onClick = naZaawansowane, enabled = !zajety) { Text("Wszystkie ustawienia dostępności i dostawy") }
                OutlinedButton(onClick = { allegroRozwiniete = !allegroRozwiniete }, enabled = !zajety,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Przygotuj prywatny szkic na Allegro") }
                Text("Opcjonalnie. Nie publikuje oferty i niczego nie kupuje.", style = MaterialTheme.typography.bodySmall)
                if (allegroRozwiniete) {
                    Text("Szkic na Allegro — niewidoczny dla kupujących (INACTIVE)", style = MaterialTheme.typography.titleMedium)
                    Text("Tytuł: ${AllegroFormat.oczyscTytul(dane.nazwa)}")
                    OutlinedTextField(dane.allegroCena, { naZmien(dane.copy(allegroCena = it)) }, label = { Text("Cena na Allegro w zł") },
                        supportingText = { Text("Puste pole = cena ze sklepu. Prowizja nie jest tu doliczana automatycznie.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !zajety, modifier = Modifier.fillMaxWidth())
                    WyborKategoriiAllegro(dane, wyszukiwanieKategorii, zajety, naSzukajKategorii, naZmien)
                    OutlinedTextField(dane.stan, { naZmien(dane.copy(stan = it)) }, label = { Text("Ile sztuk jest do sprzedania?") },
                        supportingText = { Text("To także stan produktu w sklepie. Nie zgaduj liczby.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !zajety, modifier = Modifier.fillMaxWidth())
                    Text(if (maZdjecieNaSerwerze) "Szkic pobierze zapisane zdjęcie ze sklepu. Nowe zdjęcie i film z telefonu nie są tu wysyłane."
                        else "Zdjęcie z telefonu nie ma jeszcze adresu dla Allegro. Ten szkic nie otrzyma zdjęcia; uzupełnisz je później na Allegro. Serwer może wymagać dodatkowych danych.")
                    brakiAllegro.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (dane.allegroId.isNotBlank() || dane.allegroUrl.isNotBlank()) {
                        Text("Ten produkt ma już powiązanie z Allegro. Sprawdź je w Zaawansowanym; nie tworzę drugiego szkicu.")
                    }
                    Button(onClick = { potwierdzAllegro = true }, enabled = !zajety && naSzkicAllegro != null && brakiAllegro.isEmpty() && dane.allegroId.isBlank() && dane.allegroUrl.isBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (tworzenieSzkicu) "Tworzę prywatny szkic…" else "Utwórz prywatny szkic (INACTIVE)") }
                    if (!komunikatAllegro.isNullOrBlank()) Text(komunikatAllegro)
                }
                Button(onClick = { potwierdzPublikacje = true }, enabled = !zajety && dane.nazwa.isNotBlank() && (zloteNaGrosze(dane.cena) ?: 0) > 0,
                    modifier = Modifier.fillMaxWidth()) { Text("Pokaż produkt w sklepie") }
            }
        }
        if (krok > 1) OutlinedButton(onClick = { naZmien(dane.copy(krokAsystenta = krok - 1)) }, enabled = !zajety, modifier = Modifier.fillMaxWidth()) { Text("Wróć do poprzedniego kroku") }
        OutlinedButton(onClick = naZachowaj, enabled = !zajety, modifier = Modifier.fillMaxWidth()) { Text("Zachowaj na telefonie i wróć") }
        Text("Kopia robocza zapisuje się na telefonie. Dotychczasowy limit kopii to 24 godziny — dokończ ją przed upływem tego czasu.", style = MaterialTheme.typography.bodySmall)
    }
    if (potwierdzAllegro) AlertDialog(
        onDismissRequest = { potwierdzAllegro = false },
        title = { Text("Utworzyć prywatny szkic na Allegro?") },
        text = { Text("„${AllegroFormat.oczyscTytul(dane.nazwa)}”, ${dane.allegroCena.ifBlank { dane.cena }} zł, ${dane.stan} szt., kategoria: ${dane.allegroKategoriaNazwa.ifBlank { "zapisana wcześniej lub wpisana ręcznie" }}. Wyślemy dane do Allegro. Szkic ma pozostać niewidoczny (INACTIVE). Nie uruchomimy sprzedaży. Zdjęcia: ${if (maZdjecieNaSerwerze) "zapisane zdjęcie ze sklepu" else "brak — do uzupełnienia"}.") },
        confirmButton = { Button(onClick = { potwierdzAllegro = false; naSzkicAllegro?.invoke() }, enabled = !zajety && brakiAllegro.isEmpty()) { Text("Tak, utwórz tylko szkic") } },
        dismissButton = { TextButton(onClick = { potwierdzAllegro = false }) { Text("Jeszcze sprawdzę") } },
    )
    if (potwierdzPublikacje) AlertDialog(
        onDismissRequest = { potwierdzPublikacje = false },
        title = { Text("Pokazać produkt klientom?") },
        text = { Text("„${dane.nazwa}”, ${dane.cena} zł. Po zapisaniu produkt będzie widoczny w sklepie. Sprawdź zdjęcie, opis, cenę i dostępność.") },
        confirmButton = { Button(onClick = { potwierdzPublikacje = false; naPublikuj() }, enabled = !zajety) { Text("Tak, opublikuj") } },
        dismissButton = { TextButton(onClick = { potwierdzPublikacje = false }) { Text("Jeszcze sprawdzę") } },
    )
}

private fun podsumowanieModelu(rodzaj: String, id: String, katalog: KatalogAi): String {
    if (id == "auto") return if (rodzaj == "tekst") "Automatycznie — bezpłatna lub lokalna droga warsztatu."
        else "Automatycznie — domyślna droga warsztatu. Może korzystać z abonamentu lub płatnej usługi."
    val model = katalog.dla(rodzaj).firstOrNull { it.id == id }
        ?: return "Wybrany model wymaga sprawdzenia. Rozwiń wybór modeli."
    return "${model.nazwa}: ${kosztPoPolsku(model.koszt)}. " + if (katalog.ok && model.dostepny) "Dostępny."
        else "Niedostępny: ${model.powod}. Rozwiń wybór modeli."
}
