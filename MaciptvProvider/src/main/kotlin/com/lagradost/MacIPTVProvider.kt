package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.coroutines.runBlocking
import me.xdrop.fuzzywuzzy.FuzzySearch
import okhttp3.Interceptor
import java.lang.Math.ceil

class MacIPTVProvider : MainAPI() {
    private var defaulmacAdresse =
        "mac=00:1A:79:bf:f7:db" ///00:1a:79:4c:1b:68"//"mac=00:1A:79:31:ed:e5"//"mac=00:1A:79:28:9C:Be"//"mac=00%3A1a%3A79%3Aae%3A2a%3A30"//
    private val defaultmainUrl = "http://one-tvplus.com:1223/c"//http://hits.gentv.to:8080/c"//"http://nas.bordo1453.be"//"http://infinitymedia.live:8880"//"http://ultra-box.club"//
    private var defaultname = "OneTvplusIPTV"
    private var Basename = "MacIPTV \uD83D\uDCFA"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override var lang = "en"
    override var supportedTypes =
        setOf(TvType.Live, TvType.Movie, TvType.TvSeries, TvType.Anime) // live
    private var isNotInit = true
    private var key: String? = "" // key used in the header
    private var allCategory =
        listOf<String>() // even if we don't display all countries or categories, we need to know those avalaible
    private var helpVid: String = ""
    private var helpTag: String = ""
    private var helpAcc: String = ""

    init {
        defaultname = "Test-Account $Basename "
        name = Basename
    }

    private fun List<Js_category>.getGenreNCategoriesInit(section: String): List<MainPageData> {
        allCategory =
            allCategory + ("|\uD83C\uDD70\uD83C\uDD7B\uD83C\uDD7B $section \uD83C\uDDF9\u200B\u200B\u200B\u200B\u200B\uD83C\uDDE6\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEC\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF8\u200B\u200B\u200B\u200B\u200B|")
        var allCat = listOf<String>()
        val result = this.mapNotNull { js ->
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
                    .contains(findKeyWord(tags.toString()))
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

    /**
    check if the data are incorrect
     **/
    private fun accountInfoNotGood(url: String, mac: String?): Boolean {
        return url.uppercase().trim() == "NONE" || url.isBlank() || mac?.uppercase()
            ?.trim() == "NONE" || mac.isNullOrBlank()
    }

    /**
    Sometimes providers ask a key (token) in the headers
     **/
    private suspend fun getkey(mac: String) {
        val adresseMac = if (!mac.contains("mac=")) {
            "mac=$mac"
        } else {
            mac
        }
        val url_key =
            "$mainUrl/portal.php?type=stb&action=handshake&token=&JsHttpRequest=1-xml"
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

    /**
    From user account, get the tags (to select categories and countries), url , mac address ... if there are avalaible
     **/
    private suspend fun getAuthHeader() {
        tags = tags ?: "" // tags will allow to select more contains
        if (tags?.uppercase()?.trim() == "NONE" || tags?.isBlank() == true) tags = ".*"
        tags = tags?.uppercase()
        mainUrl = overrideUrl.toString()
        mainUrl = when { // the "c" is not needed and some provider doesn't work with it
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
            isNotGood -> { // do this if mainUrl or mac adresse is blank
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
                        /*       "Connection" to "Keep-Alive",
                               "Accept-Encoding" to "gzip",
                               "Cache-Control" to "no-cache",*/
                    )
                }
            }
        }
        app.get(
            "$mainUrl/portal.php?type=stb&action=get_modules",
            headers = headerMac
        ) // some providers need this request to work

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
        } //4 search
        isNotInit = false
    }


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


    private suspend fun createArrayChannel(
        idGenre: String,
        type: String,
        load: Boolean = true,
        rquery: String = ""
    ): Sequence<String> {
        val rgxFindJson =
            Regex("""\{[\s]*\"js\"(.*[\r\n]*)+\}""")
        var res = sequenceOf<String>()
        when (type) {
            "all" -> {
                val url = "$mainUrl/portal.php?type=itv&action=get_all_channels"
                tryParseJson<RootITV>(
                    rgxFindJson.find(
                        app.get(url, headers = headerMac).text
                    )?.groupValues?.get(
                        0
                    )
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
                                data.cmds[0].chId
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
                            )?.groupValues?.get(
                                0
                            )
                        )?.js?.data!!.forEach { data ->
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
                                    namedata + isMovie,
                                    data.cmd.toString(),
                                    data.screenshotUri?.replace("""\""", ""),
                                    "",
                                    data.id,
                                    data.categoryId,
                                    data.id,
                                    data.description,
                                    data.actors,
                                    if (data.year?.split("-")?.isNotEmpty() == true) {
                                        data.year!!.split("-")[0]
                                    } else {
                                        data.year
                                    },//2022-02-01
                                    data.ratingIm,//2.3
                                    1,
                                    data.genresStr,
                                    data.series,
                                ).toJson()
                            )
                        }
                    }
                }
            }
            "itv" -> {
                val url = "$mainUrl/portal.php?type=itv&action=get_all_channels"
                //"$mainUrl/portal.php?type=itv&action=get_ordered_list&genre=$idGenre&force_ch_link_check=&fav=0&sortby=number&hd=0&p=1"
                tryParseJson<RootITV>(
                    rgxFindJson.find(
                        app.get(url, headers = headerMac).text
                    )?.groupValues?.get(
                        0
                    )
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
                                data.cmds[0].chId
                            ).toJson()
                        )
                    }

                }
            }
            "vod", "series" -> {
                val url =
                    "$mainUrl/portal.php?type=$type&action=get_ordered_list&category=$idGenre&movie_id=0&season_id=0&episode_id=0&p=1&sortby=added"
                val getJs = tryParseJson<RootVoDnSeries>(
                    rgxFindJson.find(
                        app.get(url, headers = headerMac).text
                    )?.groupValues?.get(
                        0
                    )
                )?.js
                val x = getJs?.totalItems!!.toDouble() / getJs.maxPageItems!!
                when (load) {
                    true -> {
                        getJs.data.forEach { data ->
                            val isMovie: Int
                            val namedata = if (type == "vod") {
                                isMovie = 1
                                data.name.toString()

                            } else {
                                isMovie = 0
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
                                    data.actors,
                                    if (data.year?.split("-")?.isNotEmpty() == true) {
                                        data.year!!.split("-")[0]
                                    } else {
                                        data.year
                                    },//2022-02-01
                                    data.ratingIm,//2.3
                                    isMovie,//isMovie
                                    data.genresStr,// "Fantasy, Action, Adventure",
                                    data.series,
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
                                )?.groupValues?.get(
                                    0
                                )
                            )?.js?.data!!.forEach { data ->
                                val isMovie: Int
                                val namedata = if (type == "vod") {
                                    isMovie = 1
                                    data.name.toString()

                                } else {
                                    isMovie = 0
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
                                        data.actors,
                                        if (data.year?.split("-")?.isNotEmpty() ==
