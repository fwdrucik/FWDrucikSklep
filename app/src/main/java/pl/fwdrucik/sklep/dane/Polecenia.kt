package pl.fwdrucik.sklep.dane

/**
 * Polecenia dla modeli obrazowych — jedno miejsce dla całej aplikacji.
 *
 * PO CO OSOBNY PLIK: te same trzy zdania są potrzebne w dwóch miejscach —
 * w kreatorze, gdy człowiek wybiera zadanie ręcznie, i w trybie automatycznym,
 * gdzie zadania idą po kolei bez pytania. Dwie kopie rozjechałyby się przy
 * pierwszej poprawce, a wtedy „tło" z przycisku znaczyłoby co innego niż „tło"
 * z automatu.
 *
 * Wszystkie polecenia mają wspólny warunek: WYRÓB ZOSTAJE TAKI, JAKI JEST.
 * Zdjęcie w sklepie, które nie zgadza się z tym, co przyjdzie w paczce, kończy
 * się reklamacją — i słusznie.
 */
object Polecenia {

    /** Jedno zlecenie z Prostego: bez sprzecznego polecenia zachowania starego tła. */
    fun tloISwiatlo(coTo: String): String = tlo(coTo) +
        ", popraw światło, ostrość i kontrast, wyrównaj ekspozycję, " +
        "zachowaj rzeczywisty kolor przedmiotu i nie dodawaj szczegółów wyrobu"

    /**
     * Ozdoby tła losowane przy każdym zleceniu.
     *
     * Przy stałym tle kolejne wyroby wyglądają jak jedno zdjęcie powielone
     * dwadzieścia razy. Każda pozycja jest delikatna i ZA przedmiotem — ma
     * robić nastrój, a nie przyciągać wzrok.
     */
    val ozdoby = listOf(
        "delikatne iskry unoszące się w tle",
        "miękkie rozbłyski światła jak małe fajerwerki w oddali",
        "drobne złote gwiazdki opadające powoli",
        "łagodne smugi światła przesuwające się za przedmiotem",
        "subtelny brokatowy pył w powietrzu",
        "spokojne wzory fraktalne pulsujące w głębi tła",
        "ciepłe bokeh, rozmyte światełka w tle",
    )

    /** Pastele, na których wyrób nie ginie i nie kłóci się kolorem. */
    val pastele = listOf(
        "pastelowa mięta", "pastelowy błękit", "ciepła kość słoniowa",
        "pastelowy piaskowy beż", "bardzo jasny pastelowy róż", "pastelowa szałwia",
    )

    /** Wymiana tła: wyrób zostaje, tło znika. */
    fun tlo(coTo: String): String =
        "usuń tło całkowicie i zastąp je gładkim pastelowym gradientem studyjnym " +
            "(" + pastele.random() + "), " + ozdoby.random() + " delikatnie w tle, " +
            "przedmiot (" + coTo + ") zachowaj DOKŁADNIE bez zmian: ten sam kształt, " +
            "kolor, faktura i napisy, wyśrodkowany i cały widoczny, miękki cień " +
            "kontaktowy pod spodem, ostre krawędzie, bez tekstu i bez znaku wodnego"

    /**
     * Upiększanie: samo światło i ostrość.
     *
     * Słowa „światło", „ostrość" i „kontrast" nie są tu ozdobnikiem — serwer
     * warsztatowy po nich poznaje, że ma dołożyć Forge do wyciętego kadru
     * zamiast oddać sam wycinek (patrz `wykonaj()` w serwer.py).
     */
    fun upieksz(coTo: String): String =
        "popraw światło, ostrość i kontrast tego zdjęcia (" + coTo + "), " +
            "wyrównaj ekspozycję i balans bieli, wydobądź fakturę materiału, " +
            "usuń szum i odblaski, zachowaj tło i kompozycję bez zmian, " +
            "nie zmieniaj kształtu ani koloru przedmiotu, bez tekstu i znaku wodnego"

    /**
     * Animacja: obrót wokół własnej osi, nie najazd kamery.
     *
     * Klient ogląda wyrób, a nie film o wyrobie. Pełny obrót pokazuje kształt
     * ze wszystkich stron w kilka sekund; najazd pokazuje ciągle tę samą ścianę.
     */
    fun obrot(coTo: String): String =
        "product turntable: " + coTo +
            ", przedmiot obraca się powoli wokół własnej osi pionowej, pełny obrót, " +
            "kamera nieruchoma, przedmiot wyśrodkowany i cały widoczny, " +
            "kształt, kolor i faktura bez zmian, " + ozdoby.random() +
            ", spokojne studyjne światło, bez tekstu, bez napisów, bez znaku wodnego"
}
