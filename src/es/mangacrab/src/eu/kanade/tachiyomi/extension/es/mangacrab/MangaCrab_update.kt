package eu.kanade.tachiyomi.extension.es.mangacrab

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.widget.preference.PasswordEditTextPreference
import keiyoushi.utils.getPreferences
import okhttp3.FormBody
import okhttp3.Headers
import org.jsoup.Jsoup
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class MangaCrab :
    Madara(
        "Manga Crab",
        "https://mangacrab2.yopres.com",
        "es",
        SimpleDateFormat("dd/MM/yyyy", Locale("es")),
    ),
    ConfigurableSource {

    private val preferences: SharedPreferences = getPreferences()

    override val client = super.client.newBuilder()
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .rateLimit(1, 2)
        .build()

    override val mangaSubString = "series"
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override fun popularMangaSelector() = "div.manga__item"
    override val popularMangaUrlSelector = "div.post-title a"
    override fun chapterListSelector() = "div.listing-chapters_wrap > ul > li"
    override val mangaDetailsSelectorTitle = "h1.post-title"
    override val mangaDetailsSelectorDescription = "div.c-page__content div.modal-contenido"

    private var isLoggedIn = false

    private val loggedInHeaders: Headers by lazy {
        headers.newBuilder()
            .apply {
                // You might need to add specific headers obtained after login here
            }
            .build()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)

        val loginTitle = PreferenceCategory(screen.context).apply {
            title = "Cuenta" // You can use a string resource here
        }
        val usernamePref = EditTextPreference(screen.context).apply {
            key = "mangacrab_username"
            title = "Usuario" // You can use a string resource here
            summary = "Introduce tu nombre de usuario de Manga Crab" // You can use a string resource here
            setDefaultValue("")
        }
        val passwordPref = PasswordEditTextPreference(screen.context).apply {
            key = "mangacrab_password"
            title = "Contraseña" // You can use a string resource here
            summary = "Introduce tu contraseña de Manga Crab" // You can use a string resource here
            setDefaultValue("")
        }
        val loginPreference = androidx.preference.Preference(screen.context).apply {
            title = "Iniciar Sesión" // You can use a string resource here
            summary = if (isLoggedIn) "Sesión iniciada" else "Toca para iniciar sesión" // You can use a string resource here
            setOnPreferenceClickListener {
                login()
                summary = if (isLoggedIn) "Sesión iniciada" else "Toca para iniciar sesión" // Update summary
                true
            }
        }

        screen.addPreference(loginTitle)
        loginTitle.addPreference(usernamePref)
        loginTitle.addPreference(passwordPref)
        loginTitle.addPreference(loginPreference)
    }

    private fun login(): Boolean {
        val username = preferences.getString("mangacrab_username", "") ?: ""
        val password = preferences.getString("mangacrab_password", "") ?: ""

        if (username.isEmpty() || password.isEmpty()) {
            return false // Not logged in, or credentials not provided
        }

        return try {
            val loginUrl = "https://mangacrab2.yopres.com/login" // Replace with the actual login URL
            val requestBody = FormBody.Builder()
                .add("user", username) // Replace "user" with the actual form field name
                .add("pass", password) // Replace "pass" with the actual form field name
                .add("remember", "1") // You might need to adjust this based on the website
                .build()

            val request = POST(loginUrl, headers, requestBody)
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                // Analyze the response for successful login (you might need to adjust this)
                val responseBody = response.body?.string().orEmpty()
                if (responseBody.contains("Bienvenido, $username")) { // Example success check
                    isLoggedIn = true
                    // You might need to store cookies or other session identifiers
                    // The client should automatically handle cookies.
                    response.close()
                    true
                } else {
                    isLoggedIn = false
                    response.close()
                    false
                }
            } else {
                isLoggedIn = false
                response.close()
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isLoggedIn = false
            false
        }
    }

    override fun fetchFavorites(page: Int): Observable<MangasPage> {
        return if (isLoggedIn || login()) { // Attempt login if not already logged in
            val favoritesUrl = "https://mangacrab2.yopres.com/lista-de-seguimiento" // Replace with the actual favorites URL
            val request = GET(favoritesUrl, loggedInHeaders) // Use loggedInHeaders
            client.newCall(request).executeAsObservableSuccess()
                .map { response ->
                    val document = Jsoup.parse(response.body!!.string())
                    val mangas = document.select(favoriteMangaSelector()).map { element ->
                        SManga.create().apply {
                            title = element.select(favoriteMangaTitleSelector()).text()
                            thumbnail_url = element.select(favoriteMangaThumbnailSelector()).attr("abs:src")
                            link = element.select(favoriteMangaUrlSelector()).attr("abs:href")
                            source = this@MangaCrab.id
                        }
                    }
                    MangasPage(mangas, false)
                }
                .onErrorReturn {
                    MangasPage(emptyList(), false)
                }
        } else {
            Observable.just(MangasPage(emptyList(), false))
        }
    }

    open fun favoriteMangaSelector() = "div.manga__item" // Adjust this selector
    open fun favoriteMangaTitleSelector() = "div.post-title a" // Adjust this selector
    open fun favoriteMangaThumbnailSelector() = "div.post-img img" // Adjust this selector
    open val favoriteMangaUrlSelector = "div.post-title a" // Adjust this selector

    // You might need to override other functions like mangaDetailsParse and chapterListParse
    // to handle logged-in state if necessary for accessing all information.
}
