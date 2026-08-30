package pl.fwdrucik.sklep.siec

import okhttp3.MultipartBody
import okhttp3.RequestBody
import pl.fwdrucik.sklep.dane.OdpowiedzLogowania
import pl.fwdrucik.sklep.dane.OdpowiedzObrazu
import pl.fwdrucik.sklep.dane.OdpowiedzOgolna
import pl.fwdrucik.sklep.dane.OdpowiedzProduktow
import pl.fwdrucik.sklep.dane.OdpowiedzZamowien
import pl.fwdrucik.sklep.dane.OdpowiedzZapisu
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * Punkty api/sklep.php i api/auth.php.
 *
 * Serwer przyjmuje POST-y jako formularz albo JSON (patrz fw_wejscie() w
 * api/_lib.php) i wszystkie kwoty czyta z tekstu — "129,90" albo "129.90".
 * Dlatego ceny lecą tu jako String, a nie Int: zamiana na grosze jest po
 * stronie PHP i nie ma sensu robić jej dwa razy.
 */
interface SklepApi {

    @FormUrlEncoded
    @POST("api/auth.php?akcja=logowanie")
    suspend fun zaloguj(
        @Field("login") login: String,
        @Field("haslo") haslo: String,
    ): OdpowiedzLogowania

    @GET("api/auth.php?akcja=ja")
    suspend fun ja(): OdpowiedzLogowania

    @POST("api/auth.php?akcja=wylogowanie")
    suspend fun wyloguj(): OdpowiedzOgolna

    /** Wszystkie produkty razem ze szkicami i ukrytymi — tylko dla admina. */
    @GET("api/sklep.php?akcja=admin-produkty")
    suspend fun produkty(@Query("kategoria") kategoria: String? = null): OdpowiedzProduktow

    @FormUrlEncoded
    @POST("api/sklep.php?akcja=produkt-zapisz")
    suspend fun zapiszProdukt(
        // id = 0 znaczy nowy produkt; serwer sam nada slug i sprawdzi kolizje.
        @Field("id") id: Int,
        @Field("nazwa") nazwa: String,
        @Field("kategoria") kategoria: String,
        @Field("opis_krotki") opisKrotki: String,
        @Field("opis") opis: String,
        @Field("cena") cena: String,
        @Field("cena_promo") cenaPromo: String,
        @Field("stan") stan: String,
        @Field("waga_g") wagaG: String,
        @Field("jednostka") jednostka: String,
        @Field("czas_realizacji") czasRealizacji: String,
        @Field("status") status: String,
        @Field("pozycja") pozycja: String,
    ): OdpowiedzZapisu

    @FormUrlEncoded
    @POST("api/sklep.php?akcja=produkt-status")
    suspend fun ustawStatusProduktu(
        @Field("id") id: Int,
        @Field("status") status: String,
    ): OdpowiedzOgolna

    @FormUrlEncoded
    @POST("api/sklep.php?akcja=produkt-usun")
    suspend fun usunProdukt(@Field("id") id: Int): OdpowiedzOgolna

    @Multipart
    @POST("api/sklep.php?akcja=obraz-wgraj")
    suspend fun wgrajObraz(
        @Part("produkt_id") produktId: RequestBody,
        @Part("alt") alt: RequestBody,
        @Part("pozycja") pozycja: RequestBody,
        @Part plik: MultipartBody.Part,
    ): OdpowiedzObrazu

    @FormUrlEncoded
    @POST("api/sklep.php?akcja=obraz-usun")
    suspend fun usunObraz(@Field("id") id: Int): OdpowiedzOgolna

    @GET("api/sklep.php?akcja=admin-zamowienia")
    suspend fun zamowienia(): OdpowiedzZamowien

    @FormUrlEncoded
    @POST("api/sklep.php?akcja=zamowienie-status")
    suspend fun ustawStatusZamowienia(
        @Field("id") id: Int,
        @Field("status") status: String,
    ): OdpowiedzOgolna
}
