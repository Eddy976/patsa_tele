package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.AuthManager
import com.lagradost.cloudstream3.syncproviders.AuthUser
import com.lagradost.cloudstream3.syncproviders.LoginInfo
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.xdrop.fuzzywuzzy.FuzzySearch
import okhttp3.Interceptor
import java.lang.Math.ceil

// Data classes pour parsing JSON (null-safe)
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

// Fonctions utilitaires
private fun cleanTitle(title: String): String {
    return title.trim().replace("[^A-Za-z0-9 ]".toRegex(), "")
}

private fun cleanTitleButKeepNumber(title: String): String {
    return title.replace("[^A-Za-z0-9 ]".toRegex(), "")
}

private fun findKeyWord(tags: String?): Boolean {
    return tags?.contains("EN", true) ?: false  // Adaptez (ex. : tags pour filtrage)
}

private fun getFlag(title: String): String {
    return when {
        title.uppercase().contains("VF") -> "🇫🇷"
        title.uppercase().contains("VOSTFR") -> "🇫🇷"
        else -> ""
    }
}

class MacIptvAPI : AuthAPI() {
    override val name = "MacIPTV"
    override val idPrefix = "maciptv"
    override val icon = R.drawable.ic_player_play  // Remplacez par icône IPTV
    override val requiresUsername = true  // MAC
    override val requiresPassword = true  // Token
    override val requiresServer = true  // URL serveur
    override val createAccountUrl = "https://example.com/register"  // Lien d'inscription si applicable

    override suspend fun getLatestLoginData(): AuthUser? {
        val mac = loginMac ?: return null
        val token = companionName ?: return null
        val server = overrideUrl ?: return null
        return AuthUser(id = 1, name = mac, email = token, profilePicture = null, extra = mapOf("server" to server))
    }

    override fun loginInfo(): LoginInfo {
        return LoginInfo(
            username = loginMac ?: "",
            password = companionName ?: "",
            extra = mapOf("server" to (overrideUrl ?: ""))
        )
    }

    override suspend fun login(username: String, password: String, extra: Map<String, String>?): AuthUser {
        val server = extra?.get("server") ?: throw ErrorLoadingException("Serveur requis")
        // Logique de validation MAC/token (appelez API si besoin)
        loginMac = username
        companionName = password
        overrideUrl = server
        // Appel à getkey pour token
        withContext(Dispatchers.IO) {
            getkey(username)  // Assurez-vous que getkey est accessible
        }
        return AuthUser(id = 1, name = username, email = password, profilePicture = null, extra = mapOf("server" to server))
    }

    override suspend fun logOut() {
        removeAccountKeys()
        loginMac = null
        companionName = null
        overrideUrl = null
        key = null
    }

    companion object {
        var overrideUrl: String? = null
        var loginMac: String? = null
        var companionName: String? = null
        var key: String? = null  // Partagé si besoin
    }
}

class TagsMacIptvAPI : AuthAPI() {
    override val name = "Tags MacIPTV"
    override val idPrefix = "tagsmaciptv"
    override val icon = R.drawable.ic_player_play
    override val requiresUsername = true
    override val requiresPassword = true
    override val requiresServer = true
    override val createAccountUrl = "https://example.com/register"

    // Dupliquez la logique de MacIptvAPI (adaptez pour tags si différent)
    override suspend fun getLatestLoginData(): AuthUser? = MacIptvAPI().getLatestLoginData()

    override fun loginInfo(): LoginInfo = MacIptvAPI().loginInfo()

    override suspend fun login(username: String, password: String, extra: Map<String, String>?): AuthUser {
        return MacIptvAPI().login(username, password, extra)
    }

    override suspend fun logOut() {
        MacIptvAPI().logOut()
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
    private var headerMac: Map<String, String> = emptyMap()  // Initialisé dans getAuthHeader

    init {
        defaultname = "Test-Account $Basename "
        name = Basename
    }

    private fun List<Js_category>.getGenreNCategoriesInit(section: String): List<MainPageData> {
        allCategory = allCategory + ("|\uD83C\uDD70\uD83C\uDD7B\uD83C\uDD7B $section \uD83C\uDDF9\u200B\u200B\u200B\u200B\u200B\uD83C\uDDE6\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEC\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF8\u200B\u200B\u200B\u200B\u200B|")
        var allCat = listOf<String>()
        val result = this.mapNotNull { js: Js_category ->
            val idGenre = js.id ?: return@mapNotNull null
            val categoryTitle = js.title ?: return@mapNotNull null
            cleanTitle(
                categoryTitle.replace("&", "").replace(",", "").replace(":", "")
                    .replace("#", "").replace("|", "").replace("*", "").replace("/", "")
                    .replace("\\", "").replace("[", "").replace("]", "")
                    .replace("(", "").replace(")", "")
            ).split("""\s+""".toRegex()).forEach { titleC ->
                if (!allCat.any { it.contains(titleC, true) }) {
                    allCat = allCat + ("|$titleC|")
                }
            }

            if (idGenre.contains("""\d+""".toRegex()) && categoryTitle.uppercase()
                    .contains(findKeyWord(js.tags?.joinToString()))
            ) {
                val nameGenre = cleanTitle(getFlag(categoryTitle)).trim()

                MainPageData(nameGenre, idGenre)
            } else {
                null
            }
        }
        allCategory = allCategory + allCat.sortedBy { it }
        return result
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
        tags = tags ?: ""
        if (tags.uppercase().trim() == "NONE" || tags.isBlank()) tags = ".*"
        tags = tags.uppercase()
        mainUrl = MacIptvAPI.overrideUrl ?: defaultmainUrl
        mainUrl = when {
            mainUrl.endsWith("/c/") -> mainUrl.dropLast(3)
            mainUrl.endsWith("/c") -> mainUrl.dropLast(2)
            mainUrl.endsWith("/") -> mainUrl.dropLast(1)
            else -> mainUrl
        }
        val isNotGood = accountInfoNotGood(mainUrl, MacIptvAPI.loginMac)
        if (isNotGood) {
            mainUrl = defaultmainUrl
            name = defaultname
        } else {
            name = ("${MacIptvAPI.companionName} $Basename") + " |${lang.uppercase()}|"
            defaulmacAdresse = "mac=${MacIptvAPI.loginMac}"
        }
        headerMac = when {
            isNotGood -> {
                getkey(defaulmacAdresse)
                if (MacIptvAPI.key?.isNotBlank() == true) {
                    mutableMapOf(
                        "Cookie" to defaulmacAdresse,
                        "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
                        "Authorization" to "Bearer ${MacIptvAPI.key}",
                        "X-User-Agent" to "Model: MAG250; Link: WiFi",
                    )
                } else {
                    mutableMapOf(
                        "Cookie" to defaulmacAdresse,
                        "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
                        "X-User-Agent" to "Model: MAG250; Link: WiFi",
                    )
                }
            }
            else -> {
                getkey(MacIptvAPI.loginMac.toString())
                if (MacIptvAPI.key?.isNotBlank() == true) {
                    mutableMapOf(
                        "Cookie" to "mac=${MacIptvAPI.loginMac}",
                        "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
                        "Authorization" to "Bearer ${MacIptvAPI.key}",
                        "X-User-Agent" to "Model: MAG250; Link: WiFi",
                    )
                } else {
                    mutableMapOf(
                        "Cookie" to "mac=${MacIptvAPI.loginMac}",
                        "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
                        "X-User-Agent" to "Model: MAG250; Link: WiFi",
                    )
                }
            }
        }
        withContext(Dispatchers.IO) {
            app.get(
                "$mainUrl/portal.php?type=stb&action=get_modules",
                headers = headerMac
            )
        }

        listOf(
            "https://github.com/Eddy976/cloudstream-extensions-eddy/issues/4",
            "https://github.com/Eddy976/cloudstream-extensions-eddy/issues/3",
            "https://github.com/Eddy976/cloudstream-extensions-eddy/issues/2",
        ).apmap { url ->
            when (url.takeLast(1)) {
                "4" -> helpVid = withContext(Dispatchers.IO) { app.get(url).document.select("video").attr("src") }
                "3" -> helpTag = withContext(Dispatchers.IO) { app.get(url).document.select("video").attr("src") }
                "2" -> helpAcc = withContext(Dispatchers.IO) { app.get(url).document.select("video").attr("src") }
                else -> ""
            }
        }
        isNotInit = false
    }

    private suspend fun createArrayChannel(
        idGenre: String,
        type: String,
        load: Boolean = true,
        rquery: String = ""
    ): Sequence<String> {
        val rgxFindJson = Regex("""\{[\s]*\"js\"(.*[\r\n]*)+\}""")
        var res = sequenceOf<String>()
        when (type) {
            "all" -> {
                val url = "$mainUrl/portal.php?type=itv&action=get_all_channels"
                tryParseJson<RootITV>(
                    rgxFindJson.find(
                        withContext(Dispatchers.IO) { app.get(url, headers = headerMac).text }
                    )?.groupValues?.get(0)
                )?.js?.data?.forEach { data ->
                    data?.let { d ->
                        if (FuzzySearch.ratio(
                                cleanTitleButKeepNumber(d.name ?: "").lowercase(),
                                rquery.lowercase()
                            ) > 40
                        ) {
                            res += sequenceOf(
                                Channel(
                                    d.name ?: "",
                                    "http://localhost/ch/${d.id}" + "_",
                                    d.logo?.replace("""\""", ""),
                                    "",
                                    d.id,
                                    d.tvGenreId,
                                    d.cmds?.firstOrNull()?.chId
                                ).toJson()
                            )
                        }
                    }
                }
                List(2) { it + 1 }.apmap {
                    listOf(
                        "$mainUrl/portal.php?type=vod&action=get_ordered_list&p=$it&search=$rquery&sortby=added",
                        "$mainUrl/portal.php?type=series&action=get_ordered_list&p=$it&search=$rquery&sortby=added"
                    ).apmap { url ->
                        tryParseJson<RootVoDnSeries>(
                            rgxFindJson.find(
                                withContext(Dispatchers.IO) {
                                    app.get(
                                        url,
                                        headers = headerMac
                                    ).text
                                }
                            )?.groupValues?.get(0)
                        )?.js?.data?.forEach { data ->
                            data?.let { d ->
                                val isMovie: String = if (url.contains("type=vod")) "&vod" else "&series"
                                val namedata = if (url.contains("type=vod")) (d.name ?: "") else (d.path ?: "")
                                res += sequenceOf(
                                    Channel(
                                        namedata,
                                        d.cmd ?: "",
                                        d.screenshotUri?.replace("""\""", ""),
                                        "",
                                        d.id,
                                        d.categoryId,
                                        d.id,
                                        d.description,
                                        d.actors?.joinToString(),
                                        if ((d.year?.split("-")?.isNotEmpty() == true)) {
                                            d.year!!.split("-")[0]
                                        } else {
                                            d.year
                                        },
                                        d.ratingIm?.toString(),
                                        if (url.contains("type=vod")) 1 else 0,
                                        d.genresStr,
                                        d.series?.map { it.title ?: "" } as ArrayList<String>,
                                    ).toJson()
                                )
                            }
                        }
                    }
                }
            }
            "itv" -> {
                val url = "$mainUrl/portal.php?type=itv&action=get_all_channels"
                tryParseJson<RootITV>(
                    rgxFindJson.find(
                        withContext(Dispatchers.IO) { app.get(url, headers = headerMac).text }
                    )?.groupValues?.get(0)
                )?.js?.data?.forEach { data ->
                    data?.let { d ->
                        if (d.tvGenreId == idGenre) {
                            res += sequenceOf(
                                Channel(
                                    d.name ?: "",
                                    "http://localhost/ch/${d.id}" + "_",
                                    d.logo?.replace("""\""", ""),
                                    "",
                                    d.id,
                                    d.tvGenreId,
                                    d.cmds?.firstOrNull()?.chId
                                ).toJson()
                            )
                        }
                    }
                }
            }
            "vod", "series" -> {
                val url = "$mainUrl/portal.php?type=$type&action=get_ordered_list&category=$idGenre&movie_id=0&season_id=0&episode_id=0&p=1&sortby=added"
                val getJs = tryParseJson<RootVoDnSeries>(
                    rgxFindJson.find(
                        withContext(Dispatchers.IO) { app.get(url, headers = headerMac).text }
                    )?.groupValues?.get(0)
                )?.js
                val x = getJs?.totalItems?.toDouble()?.div(getJs.maxPageItems?.toDouble() ?: 1.0) ?: 1.0
                when (load) {
                    true -> {
                        getJs?.data?.forEach { data ->
                            data?.let { d ->
                                val isMovie: Int = if (type == "vod") 1 else 0
                                val namedata = if (type == "vod") (d.name ?: "") else (d.path ?: "")
                                res += sequenceOf(
                                    Channel(
                                        namedata,
                                        d.cmd ?: "",
                                        d.screenshotUri?.replace("""\""", ""),
                                        "",
                                        d.id,
                                        d.categoryId,
                                        d.id,
                                        d.description,
                                        d.actors?.joinToString(),
                                        if (d.year?.split("-")?.isNotEmpty() == true) {
                                            d.year!!.split("-")[0]
                                        } else {
                                            d.year
                                        },
                                        d.ratingIm?.toString(),
                                        isMovie,
                                        d.genresStr,
                                        d.series?.map { it.title ?: "" } as ArrayList<String>,
                                    ).toJson()
                                )
                            }
                        }
                    }
                    else -> {
                        val takeN = ceil(x).toInt()
                        List(takeN) { it + 1 }.apmap {
                            tryParseJson<RootVoDnSeries>(
                                rgxFindJson.find(
                                    withContext(Dispatchers.IO) {
                                        app.get(
                                            "$mainUrl/portal.php?type=$type&action=get_ordered_list&category=$idGenre&movie_id=0&season_id=0&episode_id=0&p=$it&sortby=added",
                                            headers = headerMac
                                        ).text
                                    }
                                )?.groupValues?.get(0)
                            )?.js?.data?.forEach { data ->
                                data?.let { d ->
                                    val isMovie: Int = if (type == "vod") 1 else 0
                                    val namedata = if (type == "vod") (d.name ?: "") else (d.path ?: "")
                                    res += sequenceOf(
                                        Channel(
                                            namedata,
                                            d.cmd ?: "",
                                            d.screenshotUri?.replace("""\""", ""),
                                            "",
                                            d.id,
                                            d.categoryId,
                                            d.id,
                                            d.description,
                                            d.actors?.joinToString(),
                                            if (d.year?.split("-")?.isNotEmpty() == true) {
                                                d.year!!.split("-")[0]
                                            } else {
                                                d.year
                                            },
                                            d.ratingIm?.toString(),
                                            isMovie,
                                            d.genresStr,
                                            d.series?.map { it.title ?: "" } as ArrayList<String>,
                                        ).toJson()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        return res
    }

    // Exemple d'implémentation pour load (adaptez à votre logique IPTV)
    override suspend fun load(url: String): LoadResponse {
        // Ex. : Parsing pour live/TV
        val channels = createArrayChannel("", "itv")  // Exemple
        val episodes = channels.map { json ->
            newEpisode(json) {  // Utilisez newEpisode pour IPTV streams
                name = parseJson<Channel>(json).title
            }
        }
        return newTvSeriesLoadResponse(
            "IPTV Channel",
            url,
            TvType.Live,
            episodes,
            contentRating = null
        )
    }

    // Implémentez search, getMainPage, etc., de manière similaire
    override suspend fun search(query: String): List<SearchResponse> {
        val channels = createArrayChannel("", "all", rquery = query)
        return channels.map { json ->
            val ch = parseJson<Channel>(json)
            newMovieSearchResponse(ch.title, ch.url, contentRating = null) {
                posterUrl = ch.url_image
            }
        }
    }

    override val mainPage = mainPageOf(
        Pair("Live", "itv"),
        Pair("VOD", "vod"),
        Pair("Series", "series")
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val type = request.name
        val channels = createArrayChannel("", type)
        val items = channels.take(20).map { json ->
            val ch = parseJson<Channel>(json)
            newLiveSearchResponse(ch.title, ch.url, contentRating = null) {
                posterUrl = ch.url_image
            }
        }
        return newHomePageResponse(request.name, items)
    }
}

class MacIptvSettingsFragment : SettingsFragment() {
    override fun initialize(activity: FragmentActivity) {
        val repo = AuthManager.getDefaultRepo() as? MacIptvAPI ?: return
        val user = repo.getLatestLoginData()  // Sans index si pas multiple
        // Logique pour affichage user (ex. : addPreference pour MAC/server)

        val tagsRepo = AuthManager.getDefaultRepo() as? TagsMacIptvAPI ?: return
        val tagsUser = tagsRepo.getLatestLoginData()
        // Logique tags...
    }
}

// Plugin simplifié (si nécessaire pour extension)
class MacIPTVProviderPlugin : ProviderPlugin() {
    override fun load(): List<MainAPI> {
        return listOf(MacIPTVProvider())
    }
}
