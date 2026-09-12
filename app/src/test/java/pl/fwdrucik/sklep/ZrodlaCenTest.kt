package pl.fwdrucik.sklep

import org.junit.Assert.*
import org.junit.Test
import pl.fwdrucik.sklep.narzedzia.bezpiecznyUrlZrodla

class ZrodlaCenTest {
    @Test fun publiczneHttpsZachowujeSciezkeParametryIKotwice() {
        assertEquals("https://sklep.pl/oferta?q=miska%20zywiczna#cena", bezpiecznyUrlZrodla("HTTPS://SKLEP.PL:443/oferta?q=miska%20zywiczna#cena"))
        assertEquals("https://sklep.pl/oferta", bezpiecznyUrlZrodla("https://sklep.pl./oferta"))
        assertNotNull(bezpiecznyUrlZrodla("https://xn--sklep-5qa.pl/oferta"))
    }

    @Test fun adresyLokalneOrazInneSchematyNieSaLinkami() {
        listOf("http://sklep.pl", "javascript:alert(1)", "intent://sklep.pl", "file:///x", "data:text/html,x", "//sklep.pl",
            "https://localhost", "https://LOCALHOST./x", "https://panel.local", "https://panel.home.arpa", "https://router",
            "https://10.0.0.1", "https://172.16.2.3", "https://192.168.1.2", "https://169.254.169.254",
            "https://127.1", "https://0177.0.0.1", "https://2130706433", "https://0x7f000001", "https://0x7f.1",
            "https://[::1]", "https://[::ffff:127.0.0.1]", "https://[fe80::1]", "https://8.8.8.8",
            "https://user:pass@sklep.pl", "https://@sklep.pl", "https://sklep.pl@localhost", "https://sklep.pl:0/x",
            "https://sklep.pl:65536/x", "https:///sklep.pl", "https://sklep..pl", "https://-sklep.pl"
        ).forEach { assertNull(it, bezpiecznyUrlZrodla(it)) }
    }

    @Test fun zakodowaneSterowanieIUkośnikiNieDocierajaDoPrzegladarki() {
        listOf("https://sklep.pl\\@localhost", "https://sklep.pl/\r\nx", " https://sklep.pl/x", "https://sklep.pl/a b",
            "https://sklep.pl/%0Ax", "https://sklep.pl/%5cx", "https://sklep.pl/%2Fx",
            "https://sklep.pl/%250ax", "https://sklep.pl/%255cx", "https://sklep.pl/\u202Ex",
            "https://sklep.pl/\u200Bx", "https://sklep.pl/%C2%85x"
        ).forEach { assertNull(it, bezpiecznyUrlZrodla(it)) }
    }
}
