package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.AuthManager
import com.lagradost.cloudstream3.syncproviders.AuthUser
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.coroutines.runBlocking
import me.xdrop.fuzzywuzzy.FuzzySearch
import okhttp3.Interceptor
import java.lang.Math.ceil

// Data classes pour parsing JSON
data class Js_category(
    val id: String,
    val title: String,
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

// Fonctions utilitaires manquantes
private fun cleanTitle(title: String): String {
    return title.trim().replace("[^A-Za-z0-9 ]".toRegex(), "")
}

private fun cleanTitleButKeepNumber(title: String): String {
    return title.replace("[^A-Za-z0-9 ]".toRegex(), "")
}

private fun findKeyWord(tags: String?): Boolean {
    // Adaptez selon besoin, ex. : recherche de mots-clés spécifiques
    return tags?.contains("EN", true) ?: false  // Exemple pour anglais
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
    override val icon = R.drawable.ic_player_play  // Remplacez par votre icône
    override val requiresUsername = true  // Pour MAC
    override val requiresPassword = true  // Pour token
    override val requiresServer = true  // Pour URL serveur
    override val createAccountUrl = "https://example.com/register"  // Adaptez

    override suspend fun getLatestLoginData(): AuthUser? {
        // Récupérez depuis prefs ou stockage local
        val mac = loginMac ?: return null
        val token = companionName ?: return null
        val server = overrideUrl ?: return null
        return AuthUser(id = 1, name = mac, email = token, profilePicture = null)
    }

    override fun loginInfo(): com.lagradost.cloudstream3.syncproviders.LoginInfo {
        return com.lagradost.cloudstream3.syncproviders.LoginInfo(
            username = loginMac ?: "",
            password = companionName ?: "",
            extra = mapOf("server" to (overrideUrl ?: ""))
        )
    }

    override suspend fun login(username: String, password: String, extra: Map<String, String>?): AuthUser {
        val server = extra?.get("server") ?: ""
        // Logique de login : valider MAC/token avec API
        loginMac = username
        companionName = password
        overrideUrl = server
        getkey(username)  // Appel à votre fonction getkey
        return AuthUser(id = 1, name = username, email = password, profilePicture = null)
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
    }
}

class TagsMacIptvAPI : AuthAPI() {
    // Dupliquez la logique de MacIptvAPI ou fusionnez si possible
    override val name = "Tags MacIPTV"
    // ... (même implémentation que MacIptvAPI, adaptez si différent)
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
    private var key: String? = ""
    private var allCategory = listOf<String>()
    private var helpVid: String = ""
    private var helpTag: String = ""
    private var helpAcc: String = ""

    init {
        defaultname = "Test-Account $Basename "
        name = Basename
    }

    // ... (companion object déplacé vers MacIptvAPI, mais si besoin ici, dupliquez)

    private fun List<Js_category>.getGenreNCategoriesInit(section: String): List<MainPageData> {
        allCategory = allCategory + ("|\uD83C\uDD70\uD83C\uDD7B\uD83C\uDD7B $section \uD83C\uDDF9\u200B\u200B\u200B\u200B\u200B\uD83C\uDDE6\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEC\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF8\u200B\u200B\u200B\u200B\u200B|")
        var allCat = listOf<String>()
        val result = this.mapNotNull { js: Js_category ->
            val idGenre = js.id.toString()
            val categoryTitle = js.title.toString()
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
                    .contains(findKeyWord(js.tags?.toString()))
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
        return url.uppercase().trim() == "NONE" || url.isBlank() || mac?.uppercase()
            ?.trim() == "NONE" || mac.isNullOrBlank()
    }

    private suspend fun getkey(mac: String) {
        val adresseMac = if (!mac.contains("mac=")) {
            "mac=$mac"
        } else {
            mac
        }
        val url_key = "$mainUrl/portal.php?type=stb&action=handshake&token=&JsHttpRequest=1-xml"
        val reponseGetkey = app.get(
            url_key, headers = mapOf(
                "Cookie" to adresseMac,
                "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
                "X-User-Agent" to "Model: MAG250; Link: WiFi",
            )
        )
        key = tryParseJson<Getkey>(
            Regex("""\{\"js\"(.*[\r\n]*)+\}""").find(reponseGetkey.text)?.groupValues?.get(0)
        )?.js?.token ?: ""
    }

    private suspend fun getAuthHeader() {
        tags = tags ?: ""
        if (tags?.uppercase()?.trim() == "NONE" || tags?.isBlank() == true) tags = ".*"
        tags = tags?.uppercase()
        mainUrl = overrideUrl ?: defaultmainUrl
        mainUrl = when {
            mainUrl.endsWith("/c/") -> mainUrl.dropLast(3)
            mainUrl.endsWith("/c") -> mainUrl.dropLast(2)
            mainUrl.endsWith("/") -> mainUrl.dropLast(1)
            else -> mainUrl
        }
        val isNotGood = accountInfoNotGood(mainUrl, loginMac)
        if (isNotGood) {
            mainUrl = defaultmainUrl
            name = defaultname
        } else {
            name = ("$companionName $Basename") + " |${lang.uppercase()}|"
            defaulmacAdresse = "mac=$loginMac"
        }
        headerMac = when {
            isNotGood -> {
                getkey(defaulmacAdresse)
                if (key?.isNotBlank() == true) {
                    mutableMapOf(
                        "Cookie" to defaulmacAdresse,
                        "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
                        "Authorization" to "Bearer $key",
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
                getkey(loginMac.toString())
                if (key?.isNotBlank() == true) {
                    mutableMapOf(
                        "Cookie" to "mac=$loginMac",
                        "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
                        "Authorization" to "Bearer $key",
                        "X-User-Agent" to "Model: MAG250; Link: WiFi",
                    )
                } else {
                    mutableMapOf(
                        "Cookie" to "mac=$loginMac",
                        "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
                        "X-User-Agent" to "Model: MAG250; Link: WiFi",
                    )
                }
            }
        }
        app.get(
            "$mainUrl/portal.php?type=stb&action=get_modules",
            headers = headerMac
        )

        listOf(
            "https://github.com/Eddy976/cloudstream-extensions-eddy/issues/4",
            "https://github.com/Eddy976/cloudstream-extensions-eddy/issues/3",
            "https://github.com/Eddy976/cloudstream-extensions-eddy/issues/2",
        ).apmap { url ->
            when (url.takeLast(1)) {
                "4" -> helpVid = app.get(url).document.select("video").attr("src")
                "3" -> helpTag = app.get(url).document.select("video").attr("src")
                "2" -> helpAcc = app.get(url).document.select("video").attr("src")
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
                        app.get(url, headers = headerMac).text
                    )?.groupValues?.get(0)
                )?.js?.data?.forEach { data ->
                    if (FuzzySearch.ratio(
                            cleanTitleButKeepNumber(data.name.toString()).lowercase(),
                            rquery.lowercase()
                        ) > 40
                    ) {
                        res += sequenceOf(
                            Channel(
                                data.name.toString(),
                                "http://localhost/ch/${data.id}" + "_",
                                data.logo?.replace("""\""", ""),
                                "",
                                data.id,
                                data.tvGenreId,
                                data.cmds?.firstOrNull()?.chId
                            ).toJson()
                        )
                    }
                }
                List(2) { it + 1 }.apmap {
                    listOf(
                        "$mainUrl/portal.php?type=vod&action=get_ordered_list&p=$it&search=$rquery&sortby=added",
                        "$mainUrl/portal.php?type=series&action=get_ordered_list&p=$it&search=$rquery&sortby=added"
                    ).apmap { url ->
                        tryParseJson<RootVoDnSeries>(
                            rgxFindJson.find(
                                app.get(
                                    url,
                                    headers = headerMac
                                ).text
                            )?.groupValues?.get(0)
                        )?.js?.data?.forEach { data ->
                            val isMovie: String
                            val namedata = if (url.contains("type=vod")) {
                                isMovie = "&vod"
                                data.name.toString()
                            } else {
                                isMovie = "&series"
                                data.path.toString()
                            }
                            res += sequenceOf(
                                Channel(
                                    namedata,
                                    data.cmd.toString(),
                                    data.screenshotUri?.replace("""\""", ""),
                                    "",
                                    data.id,
                                    data.categoryId,
                                    data.id,
                                    data.description,
                                    data.actors?.joinToString(),
                                    if (data.year?.split("-")?.isNotEmpty() == true) {
                                        data.year!!.split("-")[0]
                                    } else {
                                        data.year ?: null
                                    },
                                    data.ratingIm?.toString(),
                                    if (url.contains("type=vod")) 1 else 0,
                                    data.genresStr,
                                    data.series?.map { it.title ?: "" } as ArrayList<String>,
                                ).toJson()
                            )
                        }
                    }
                }
            }
            // ... (autres cas similaires, avec corrections pour null safety)
            "itv" -> {
                val url = "$mainUrl/portal.php?type=itv&action=get_all_channels"
                tryParseJson<RootITV>(
                    rgxFindJson.find(
                        app.get(url, headers = headerMac).text
                    )?.groupValues?.get(0)
                )?.js?.data?.forEach { data ->
                    if (data.tvGenreId == idGenre) {
                        res += sequenceOf(
                            Channel(
                                data.name.toString(),
                                "http://localhost/ch/${data.id}" + "_",
                                data.logo?.replace("""\""", ""),
                                "",
                                data.id,
                                data.tvGenreId,
                                data.cmds?.firstOrNull()?.chId
                            ).toJson()
                        )
                    }
                }
            }
            "vod", "series" -> {
                val url = "$mainUrl/portal.php?type=$type&action=get_ordered_list&category=$idGenre&movie_id=0&season_id=0&episode_id=0&p=1&sortby=added"
                val getJs = tryParseJson<RootVoDnSeries>(
                    rgxFindJson.find(
                        app.get(url, headers = headerMac).text
                    )?.groupValues?.get(0)
                )?.js
                val x = getJs?.totalItems?.toDouble()?.div(getJs.maxPageItems ?: 1) ?: 1.0
                when (load) {
                    true -> {
                        getJs?.data?.forEach { data ->
                            val isMovie: Int = if (type == "vod") 1 else 0
                            val namedata = if (type == "vod") data.name.toString() else data.path.toString()
                            res += sequenceOf(
                                Channel(
                                    namedata,
                                    data.cmd.toString(),
                                    data.screenshotUri?.replace("""\""", ""),
                                    "",
                                    data.id,
                                    data.categoryId,
                                    data.id,
                                    data.description,
                                    data.actors?.joinToString(),
                                    if (data.year?.split("-")?.isNotEmpty() == true) {
                                        data.year!!.split("-")[0]
                                    } else {
                                        data.year
                                    },
                                    data.ratingIm?.toString(),
                                    isMovie,
                                    data.genresStr,
                                    data.series?.map { it.title ?: "" } as ArrayList<String>,
                                ).toJson()
                            )
                        }
                    }
                    else -> {
                        val takeN = ceil(x).toInt()
                        List(takeN) { it + 1 }.apmap {
                            tryParseJson<RootVoDnSeries>(
                                rgxFindJson.find(
                                    app.get(
                                        "$mainUrl/portal.php?type=$type&action=get_ordered_list&category=$idGenre&movie_id=0&season_id=0&episode_id=0&p=$it&sortby=added",
                                        headers = headerMac
                                    ).text
                                )?.groupValues?.get(0)
                            )?.js?.data?.forEach { data ->
                                val isMovie: Int = if (type == "vod") 1 else 0
                                val namedata = if (type == "vod") data.name.toString() else data.path.toString()
                                res += sequenceOf(
                                    Channel(
                                        namedata,
                                        data.cmd.toString(),
                                        data.screenshotUri?.replace("""\""", ""),
                                        "",
                                        data.id,
                                        data.categoryId,
                                        data.id,
                                        data.description,
                                        data.actors?.joinToString(),
                                        if (data.year?.split("-")?.isNotEmpty() == true) {
                                            data.year!!.split("-")[0]
                                        } else {
                                            data.year
                                        },
                                        data.ratingIm?.toString(),
                                        isMovie,
                                        data.genresStr,
                                        data.series?.map { it.title ?: "" } as ArrayList<String>,
                                    ).toJson()
                                )
                            }
                        }
                    }
                }
            }
        }
        return res
    }

    // Implémentez override suspend fun search, load, getMainPage, etc., avec contentRating = null
    // Exemple pour load :
    override suspend fun load(url: String): LoadResponse {
        // Logique...
        return newTvSeriesLoadResponse(
            title = "Exemple",
            url = url,
            TvType.TvSeries,
            episodesList = listOf(),
            contentRating = null  // Ajouté
        )
    }

    // ... (complétez les autres overrides avec corrections similaires)
}

class MacIptvSettingsFragment : SettingsFragment() {
    override fun initialize(activity: FragmentActivity) {
        val repo = AuthManager.getDefaultRepo() as? MacIptvAPI ?: return
        val user = repo.getLatestLoginData(0)  // index = 0
        // ... (logique)

        // Pour tags :
        val tagsRepo = AuthManager.getDefaultRepo() as? TagsMacIptvAPI ?: return
        val tagsUser = tagsRepo.getLatestLoginData(0)
        // ...
    }
}

// Supprimez ou commentez MacIPTVProviderPlugin si non nécessaire
