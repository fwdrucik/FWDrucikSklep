package pl.fwdrucik.sklep.dane

import pl.fwdrucik.sklep.narzedzia.AllegroFormat
import pl.fwdrucik.sklep.siec.OdpowiedzSzkicuAllegro
import pl.fwdrucik.sklep.siec.KategoriaAllegro

/** Wywoływane tylko po jawnym naciśnięciu pozycji z listy. */
fun KopiaRobocza.wybierzKategorieAllegro(kategoria: KategoriaAllegro): KopiaRobocza {
    require(kategoria.id.matches(Regex("[0-9]+")) && kategoria.nazwa.isNotBlank())
    return copy(allegroKategoria = kategoria.id, allegroKategoriaNazwa = kategoria.nazwa,
        allegroKategoriaSciezka = kategoria.sciezka)
}

/** Cena z pola ma pierwszeństwo. Nie korzystamy z ceny wygenerowanej przez AI. */
fun cenaTylkoOdUzytkownika(cenaReczna: String, notatka: String): String {
    if (cenaReczna.isNotBlank()) return cenaReczna
    // Nie wyciągaj kosztu przesyłki ani jednej z kilku kwot jako ceny wyrobu.
    if (Regex("(?i)wysył|wysyl|dostaw|przesył|przesyl|kurier|prowiz").containsMatchIn(notatka)) return ""
    val liczba = "(?:[0-9]{1,3}(?:[ \\u00a0][0-9]{3})+|[0-9]+)(?:[.,][0-9]{1,2})?"
    val kwoty = Regex("(?i)(?<![0-9.,-])($liczba)\\s*(?:zł|zl|pln)(?![\\p{L}\\p{N}])").findAll(notatka)
        .map { it.groupValues[1].replace(" ", "").replace("\u00a0", "").replace(',', '.') }.toList()
    if (kwoty.size == 1) return kwoty.single()
    if (kwoty.isNotEmpty()) return ""
    return Regex("(?i)\\b(?:cena|za)\\s*:?\\s*($liczba)\\s*$").find(notatka)?.groupValues?.get(1)
        ?.replace(" ", "")?.replace("\u00a0", "")?.replace(',', '.').orEmpty()
}

fun SzkicProduktu.zCenaPotwierdzona(notatka: String, cenaReczna: String = ""): SzkicProduktu {
    val potwierdzona = cenaTylkoOdUzytkownika(cenaReczna, notatka)
    val pytanie = "Wpisz cenę wyrobu — nie ustalamy jej na podstawie zgadywania."
    val pozostalePytania = doUzupelnienia.filterNot { it == pytanie }
    return copy(cena = potwierdzona, doUzupelnienia = if (potwierdzona.isBlank())
        (pozostalePytania + pytanie).distinct() else pozostalePytania)
}

/** Walidacja przed potwierdzeniem i ponownie przed wysłaniem szkicu. */
fun KopiaRobocza.brakiSzkicuAllegro(): List<String> = buildList {
    if (AllegroFormat.oczyscTytul(nazwa).isBlank()) add("Wpisz nazwę wyrobu w kroku Opis.")
    if (opis.isBlank() && opisKrotki.isBlank()) add("Dodaj opis wyrobu w kroku Opis.")
    if ((zloteNaGrosze(allegroCena.ifBlank { cena }) ?: 0) <= 0) add("Wpisz cenę większą od zera.")
    if (!allegroKategoria.orEmpty().trim().matches(Regex("[0-9]+"))) add("Brakuje kategorii Allegro. Użyj „Znajdź pasującą kategorię” i wybierz z listy.")
    if ((stan.trim().toIntOrNull() ?: 0) <= 0) add("Wpisz, ile sztuk chcesz zaoferować: co najmniej 1.")
}

/** Sam HTTP 200 nie potwierdza prywatnego szkicu. Nie ponawiaj automatycznie. */
fun OdpowiedzSzkicuAllegro.bladPrywatnegoSzkicu(): String? = when {
    !ok -> blad?.takeIf { it.isNotBlank() } ?: "Serwer nie potwierdził utworzenia szkicu. Sprawdź Allegro przed ponowieniem."
    !status.equals("INACTIVE", ignoreCase = true) -> "Serwer nie potwierdził statusu prywatnego INACTIVE. Sprawdź Allegro przed ponowieniem."
    id.isNullOrBlank() && url.isNullOrBlank() -> "Brakuje numeru lub odnośnika do szkicu. Sprawdź Allegro przed ponowieniem."
    else -> null
}
