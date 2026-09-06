package pl.fwdrucik.sklep.pomoc

/**
 * Podpowiedzi w kreatorze produktu.
 *
 * Każda mówi, co wpisać i dlaczego to ma znaczenie — a nie powtarza nazwy pola.
 * "Nazwa: wpisz nazwę" nie pomaga nikomu. Treści są ułożone pod to, jak Google
 * czyta stronę produktu (dane z api/_sklep.php trafiają wprost do schema.org
 * Product), więc dobrze wypełnione pole realnie zmienia widoczność w wynikach.
 */
data class Podpowiedz(
    val pole: String,
    val krotko: String,
    val przyklad: String,
    val dlaczego: String,
)

object Podpowiedzi {

    val notatkaDlaAgenta = Podpowiedz(
        pole = "Notatka do zdjęcia",
        krotko = "Napisz w biegu, skrótami. Agent to zredaguje, nie poprawiaj sam.",
        przyklad = "brelok customizowany z czym chcesz zalany żywicą 30zł",
        dlaczego = "To jest źródło faktów dla agenta — wszystko, co tu napiszesz, " +
            "trafi do opisu jako prawda. Agent rozwinie skróty i ułoży zdania, " +
            "ale niczego nie dopowie od siebie. Wpisz też cenę, a wskoczy " +
            "w pole ceny sama. Czego nie napiszesz i nie widać na zdjęciu — " +
            "agent zapyta zamiast zgadywać.",
    )

    val nazwa = Podpowiedz(
        pole = "Nazwa",
        krotko = "Rzecz + materiał + cecha, po której klient szuka.",
        przyklad = "Stolik kawowy River Table — dąb i żywica epoksydowa",
        dlaczego = "To trafia do tytułu strony i do wyniku w Google. Sama " +
            "nazwa w rodzaju „Stolik nr 4” nie pasuje do niczego, czego ktoś szuka.",
    )

    val kategoria = Podpowiedz(
        pole = "Kategoria",
        krotko = "Jedno słowo, powtarzalne między produktami.",
        przyklad = "zywica, druk3d, spawanie, cnc",
        dlaczego = "Po kategorii działa filtr w sklepie i dobór „z tego samego " +
            "warsztatu” pod produktem. Dwie różne pisownie robią dwa osobne filtry.",
    )

    val opisKrotki = Podpowiedz(
        pole = "Krótki opis",
        krotko = "Jedno–dwa zdania, do 400 znaków. Konkret, nie zachwyt.",
        przyklad = "Blat z dwóch kawałków dębu połączonych rzeką z barwionej " +
            "żywicy, na spawanym stelażu. Kolor dobierany pod wnętrze.",
        dlaczego = "To jest opis, który Google pokazuje pod tytułem, i to samo " +
            "zdanie widać na kafelku w sklepie. Pusty opis to pusty wynik wyszukiwania.",
    )

    val opis = Podpowiedz(
        pole = "Opis pełny",
        krotko = "Wymiary, materiał, wykończenie, co jest w cenie, czego nie ma.",
        przyklad = "Wymiary blatu 110 × 60 cm, grubość 4 cm. Dąb suszony " +
            "komorowo. Żywica bezbarwna z pigmentem. W cenie stelaż stalowy " +
            "malowany proszkowo. Wysyłka wyłącznie kurierem paletowym.",
        dlaczego = "Tu klient rozstrzyga, czy pisać z pytaniem, czy kupić. " +
            "Każde pytanie, które dostajesz przez telefon, powinno tu mieć odpowiedź.",
    )

    val cena = Podpowiedz(
        pole = "Cena",
        krotko = "W złotych, z przecinkiem. Bez „zł”, bez „od”.",
        przyklad = "890 albo 129,90",
        dlaczego = "Cena idzie do danych dla Google i pokazuje się w wyniku " +
            "wyszukiwania. Wpisana z literami zostanie odrzucona przy zapisie.",
    )

    val cenaPromo = Podpowiedz(
        pole = "Cena promocyjna",
        krotko = "Zostaw puste, jeśli nie ma promocji. Musi być niższa od zwykłej.",
        przyklad = "790",
        dlaczego = "Gdy wpiszesz, sklep pokaże starą cenę przekreśloną obok nowej. " +
            "Cena wyższa albo równa zwykłej zostanie odrzucona przez serwer.",
    )

    val stan = Podpowiedz(
        pole = "Stan magazynowy",
        krotko = "Puste = robione na zamówienie. 0 = chwilowo nie ma. Liczba = tyle sztuk czeka.",
        przyklad = "3 (albo puste przy wyrobie na wymiar)",
        dlaczego = "To najważniejsze pole w całym kreatorze. Puste pole zmienia " +
            "też zasady zwrotu: rzecz wykonana na indywidualne zamówienie nie " +
            "podlega zwrotowi w 14 dni i sklep sam to napisze klientowi. " +
            "Przy stanie od 1 do 3 sklep pokazuje „Zostały 3 szt.”.",
    )

    val jednostka = Podpowiedz(
        pole = "Jednostka",
        krotko = "To, za co liczysz cenę.",
        przyklad = "szt., kpl., mb, m2",
        dlaczego = "Pokazuje się przy cenie. Przy metrach bieżących „/ szt.” myli klienta.",
    )

    val waga = Podpowiedz(
        pole = "Waga w gramach",
        krotko = "Waga gotowej paczki, nie samego wyrobu.",
        przyklad = "12000 (czyli 12 kg)",
        dlaczego = "Po wadze widać, czy rzecz zmieści się w paczkomacie. " +
            "Zero znaczy „nie podano” i waga nie pokaże się w ogóle.",
    )

    val czasRealizacji = Podpowiedz(
        pole = "Czas realizacji",
        krotko = "Uczciwy termin, licząc od zaliczki.",
        przyklad = "3–5 tygodni od potwierdzenia projektu",
        dlaczego = "Wpisany termin ucina połowę telefonów z pytaniem „na kiedy”. " +
            "Lepiej podać dłuższy i zrobić wcześniej niż odwrotnie.",
    )

    val status = Podpowiedz(
        pole = "Status",
        krotko = "Szkic — tylko Ty. Opublikowany — widzą klienci. Ukryty — zdjęty ze sprzedaży.",
        przyklad = "Zacznij od szkicu, opublikuj po dodaniu zdjęć.",
        dlaczego = "Produkt bez zdjęcia sprzedaje się źle. Publikuj dopiero, " +
            "gdy jest zdjęcie, cena i opis — inaczej Google zapamięta pustą stronę.",
    )

    val pozycja = Podpowiedz(
        pole = "Kolejność",
        krotko = "Mniejsza liczba to wyżej na liście. Domyślnie 100.",
        przyklad = "10 dla produktu, który ma być pierwszy",
        dlaczego = "Pierwsze trzy pozycje w sklepie dostają większość kliknięć. " +
            "Trzymaj tam to, co naprawdę chcesz sprzedać.",
    )

    val zdjecia = Podpowiedz(
        pole = "Zdjęcia",
        krotko = "Pierwsze zdjęcie to okładka. Rób w świetle dziennym, na czystym tle.",
        przyklad = "Całość, detal łączenia, wyrób w użyciu, skala obok znanej rzeczy.",
        dlaczego = "Zdjęcie decyduje o kliknięciu bardziej niż cena i opis razem. " +
            "Serwer przyjmuje pliki do 6 MB — większe zmniejsz w galerii.",
    )

    val opisZdjecia = Podpowiedz(
        pole = "Opis zdjęcia",
        krotko = "Napisz, co widać. Krótko, rzeczowo.",
        przyklad = "Blat dębowy z niebieską żywicą, widok z góry",
        dlaczego = "Czyta to Google Grafika i czytnik ekranu osoby niewidomej. " +
            "Puste pole to zmarnowane miejsce w wyszukiwarce.",
    )

    val allegroUrl = Podpowiedz(
        pole = "Link do aukcji Allegro",
        krotko = "Pełny adres URL do Twojej oferty na Allegro.",
        przyklad = "https://allegro.pl/oferta/stolik-debowy-river-table-1234567890",
        dlaczego = "Gdy uzupełnisz ten link, na stronie fwdrucik.pl przycisk „Kup” i „Do koszyka” " +
            "przekieruje klienta prosto na Allegro z obsługą darmowej wysyłki Allegro Smart i Allegro Protect.",
    )

    val allegroCena = Podpowiedz(
        pole = "Cena na Allegro",
        krotko = "Cena w ofercie Allegro (zwykle powiększona o prowizję ~12%).",
        przyklad = "997,00",
        dlaczego = "Pozwala kontrolować różnicę między ceną w sklepie a ceną z prowizją marketplace.",
    )

    /** Kolejność w instrukcji — od tego, co blokuje publikację, do drobiazgów. */
    val wszystkie = listOf(
        notatkaDlaAgenta, nazwa, kategoria, opisKrotki, opis, cena, cenaPromo, stan,
        jednostka, waga, czasRealizacji, allegroUrl, allegroCena, status, pozycja, zdjecia, opisZdjecia,
    )
}
