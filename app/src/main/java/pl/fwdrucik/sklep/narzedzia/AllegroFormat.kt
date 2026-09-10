package pl.fwdrucik.sklep.narzedzia

object AllegroFormat {

    private val ZAKAZANE_SLOWA = listOf(
        "hit", "nowość", "nowosc", "okazja", "promocja",
        "polecam", "super", "bestseller", "gratis", "najtaniej",
        "tanio", "wyprzedaż", "wyprzedaz"
    )

    /**
     * Czyści tytuł aukcji pod kątem restrykcji Allegro SEO:
     * - Limit maksymalnie 75 znaków
     * - Usunięcie słów zakazanych / karanych przez algorytmy Allegro
     * - Usunięcie wielokrotnych wykrzykników i zbędnych spacji
     */
    fun oczyscTytul(tytul: String): String {
        var t = tytul.trim()
        t = t.replace(Regex("[!]{2,}"), "!")
        t = t.replace(Regex("[?]{2,}"), "?")
        for (slowo in ZAKAZANE_SLOWA) {
            t = t.replace(Regex("(?i)\\b$slowo\\b"), "")
        }
        t = t.replace(Regex("\\s+"), " ").trim()
        if (t.length > 75) {
            val przyciete = t.take(75)
            t = if (przyciete.contains(" ")) {
                przyciete.substringBeforeLast(" ").trim()
            } else {
                przyciete.trim()
            }
        }
        return t
    }

    /**
     * Tworzy kod HTML opisu zgodny z dozwolonymi znacznikami Allegro:
     * <h2>, <p>, <b>, <ul>, <li>, <br>
     */
    fun zbudujOpisHtml(nazwa: String, opisKrotki: String, opis: String): String {
        val nzw = nazwa.ifBlank { "Wyrób rzemieślniczy F.W. DRUCIK" }
        val krotki = opisKrotki.replace("\n", "<br>").trim()
        val dlugi = opis.replace("\n", "<br>").trim()

        val sb = java.lang.StringBuilder()
        sb.append("<h2>").append(nzw).append("</h2>\n")
        if (krotki.isNotBlank()) {
            sb.append("<p>").append(krotki).append("</p>\n")
        }
        if (dlugi.isNotBlank()) {
            sb.append("<h2>Opis i wykonanie</h2>\n")
            sb.append("<p>").append(dlugi).append("</p>\n")
        }
        sb.append("<h2>Cechy wyrobu z pracowni F.W. DRUCIK</h2>\n")
        sb.append("<ul>\n")
        sb.append("  <li><b>Pracownia:</b> F.W. DRUCIK (Polska)</li>\n")
        sb.append("  <li><b>Wykonanie:</b> 100% autorskie rękodzieło</li>\n")
        sb.append("  <li><b>Dostawa:</b> Bezpieczne pakowanie ze wsparciem Allegro Smart!</li>\n")
        sb.append("</ul>\n")
        return sb.toString().trim()
    }

    fun obliczCeneAllegro(cenaSklep: Double, prowizjaProcent: Double = 12.0): Double {
        return cenaSklep * (1.0 + prowizjaProcent / 100.0)
    }
}
