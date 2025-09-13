package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.AuthManager
import com.lagradost.cloudstream3.syncproviders.AuthUser
import com.lagradost.cloudstream3.syncproviders.AuthToken
import com.lagradost.cloudstream3.syncproviders.AuthLoginResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.xdrop.fuzzywuzzy.FuzzySearch
import java.lang.Math.ceil
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat

// Data classes (avec categoryId ajouté)
data class Js_category(
    val id: String?,
    val title: String?,
    @JsonProperty("tags") val tags: List<String>? = null
)

data class Getkey(
    val js: TokenData?
)

data class TokenData(
    val token: String?
)

data class RootITV(
    val js: ITVData?
)

data class ITVData(
    val data: List<ChannelData>?
)

data class ChannelData(
    val id: String?,
    val name: String?,
    val logo: String?,
    @JsonProperty("tvGenreId") val tvGenreId: String?,
    val cmds: List<Cmds>?
)

data class Cmds(
    val chId: String?
)

data class RootVoDnSeries(
    val js: VoDnData?
)

data class VoDnData(
    val data: List<VoDnItem>?,
    val totalItems: Int?,
    val maxPageItems: Int?
)

data class VoDnItem(
    val id: String?,
    val name: String?,
    val path: String?,
    val cmd: String?,
    val screenshotUri: String?,
    val description: String?,
    val actors: List<String>?,
    val year: String?,
    @JsonProperty("ratingIm") val ratingIm: Int?,
    @JsonProperty("genresStr") val genresStr: String?,
    @JsonProperty("category_id") val categoryId: String? = null,  // Ajouté
    val series: List<SeriesItem>?
)

data class SeriesItem(
    val id: String?,
    val title: String?
)

data class Channel(
    var title: String,
    var url: String,
    val url_image: String?,
    val lang: String?,
    var id: String?,
    var tv_genre_id: String?,
    var ch_id: String?,
    var description: String? = null,
    var actors: String? = null,
    var year: String? = null,
    var rating: String? = null,
    var is_M: Int? = null,
    var genres_str: String? = null,
    var series: ArrayList<String> = arrayListOf(),
)

// Utilitaires
private fun cleanTitle(title: String): String = title.trim().replace("[^A-Za-z0-9 ]".toRegex(), "")

private fun cleanTitleButKeepNumber(title: String): String = title.replace("[^A-Za-z0-9 ]".toRegex(), "")

private fun findKeyWord(tags: String?): Boolean = tags?.contains("EN", true) ?: false

private fun getFlag(title: String): String = when {
    title.uppercase().contains("VF") -> "🇫🇷"
    title.uppercase().contains("VOSTFR") -> "🇫🇷"
    else -> ""
}

class MacIptvAPI : AuthAPI() {
    override val name = "MacIPTV"
    override val idPrefix = "maciptv"
    override val icon = R.drawable.ic_launcher_foreground  // Utilisez existant
    override val requiresUsername = true
    override val requiresPassword = true
    override val requiresServer = true

    override suspend fun getLatestLoginData(index: Int? = null): AuthUser? {
        val mac = loginMac ?: return null
        val token = companionName ?: return null
        return AuthUser(id = index ?: 0, name = mac, email = token)
    }

    override suspend fun login(form: AuthLoginResponse): AuthToken? {
        val username = form.username ?: return null
        val password = form.password ?: return null
        val server = form.extra?.get("server") ?: return null
        loginMac = username
        companionName = password
        overrideUrl = server
        withContext(Dispatchers.IO) { getkey(username) }
        return AuthToken(name = name, token = key ?: "", expires = null)
    }

    override suspend fun logOut() {
        super.logOut()
        loginMac = null
        companionName = null
        overrideUrl = null
        key = null
    }

    companion object {
        var overrideUrl: String? = null
        var loginMac: String? = null
        var companionName: String? = null
        var key: String? = null
    }
}

class MacIPTVProvider : MainAPI() {
    private var defaulmacAdresse = "mac=00:1A:79:bf:f7:db"
    private val defaultmainUrl = "http://one-tvplus.com:1223/c"
    private var defaultname = "OneTvplusTv"
    private var Basename = "MacIPTV \uD83D\uDCFA"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override var lang = "en"
    override var supportedTypes = setOf(TvType.Live, TvType.Movie, TvType.TvSeries, TvType.Anime)
    private var isNotInit = true
    private var allCategory = listOf<String>()
    private var helpVid: String = ""
    private var helpTag: String = ""
    private var helpAcc: String = ""
    private var headerMac: Map<String, String> = emptyMap()

    init {
        defaultname = "Test-Account $Basename "
        name = Basename
    }

    private fun List<Js_category>.getGenreNCategoriesInit(section: String): List<MainPageData> {
        // ... (votre code, avec js.id?.let { ... } pour null safety)
        return emptyList()  // Implémentez
    }

    private fun accountInfoNotGood(url: String, mac: String?): Boolean {
        return url.uppercase().trim() == "NONE" || url.isBlank() || mac?.uppercase()?.trim() == "NONE" || mac.isNullOrBlank()
    }

    private suspend fun getkey(mac: String) {
        val adresseMac = if (!mac.contains("mac=")) "mac=$mac" else mac
        val url_key = "$mainUrl/portal.php?type=stb&action=handshake&token=&JsHttpRequest=1-xml"
        val reponseGetkey = withContext(Dispatchers.IO) {
            app.get(
                url_key, headers = mapOf(
                    "Cookie" to adresseMac,
                    "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
                    "X-User-Agent" to "Model: MAG250; Link: WiFi",
                )
            )
        }
        key = tryParseJson<Getkey>(
            Regex("""\{\"js\"(.*[\r\n]*)+\}""").find(reponseGetkey.text)?.groupValues?.get(0)
        )?.js?.token ?: ""
    }

    private suspend fun getAuthHeader() {
        // ... (votre code, avec MacIptvAPI.companionName etc.)
        isNotInit = false
    }

    private suspend fun createArrayChannel(
        idGenre: String,
        type: String,
        load: Boolean = true,
        rquery: String = ""
    ): Sequence<String> {
        // ... (votre code, avec .toList() pour conversions)
        return emptySequence()  // Implémentez
    }

    override suspend fun load(url: String): LoadResponse {
        getAuthHeader()  // Init auth
        // Logique load (ex. : parse channel)
        return newTvSeriesLoadResponse(
            "IPTV Channel",
            url,
            TvType.Live,
            episodesList = listOf()  // Remplissez avec createArrayChannel
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        getAuthHeader()
        val channels = createArrayChannel("", "all", rquery = query)
        return channels.toList().map { json ->
            val ch = parseJson<Channel>(json)
            newMovieSearchResponse(ch.title, ch.url) {
                posterUrl = ch.url_image
            }
        }
    }

    override val mainPage = mainPageOf(/* vos pairs */)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        getAuthHeader()
        val type = request.data ?: ""
        val channels = createArrayChannel("", type)
        val items = channels.toList().take(20).map { json ->
            val ch = parseJson<Channel>(json)
            newLiveSearchResponse(ch.title, ch.url) {
                posterUrl = ch.url_image
            }
        }
        return newHomePageResponse(request.name, items)
    }
}

class MacIptvSettingsFragment : BottomSheetDialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)  // Créez layout
        val repo = AuthManager.getDefaultRepo<MacIptvAPI>() ?: return view
        val user = runBlocking { repo.getLatestLoginData() }
        if (user != null) {
            view.findViewById<EditText>(R.id.mac_edit)?.setText(user.name)
        }
        return view
    }
}

class MacIPTVProviderPlugin : ProviderPlugin() {
    override fun load(): List<MainAPI> = listOf(MacIPTVProvider())
}
