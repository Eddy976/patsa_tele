package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.*
import com.lagradost.nicehttp.NiceResponse
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.util.*
import kotlin.collections.ArrayList

class AnimeSamaProvider : MainAPI() {
    override var mainUrl = "https://anime-sama.fr"
    override var name = "Anime-sama"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override var lang = "fr"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    private val interceptor = CloudflareKiller()

    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        val response = app.post("$mainUrl/template-php/defaut/fetch.php", data = mapOf("query" to query)).document
        response.select("a").apmap { results.extractSearchResponse(it) }
        return results
    }

    private val regexGetlink = Regex("""(http.*?)',""")

    private suspend fun MutableList<SearchResponse>.extractSearchResponse(element: Element) {
        val posterUrl = element.select("a>img").attr("src")
        var animeUrl = element.select("a").attr("href")
        val doc = avoidCloudflare(animeUrl).document
        val scripts = doc.select("div.flex.flex-wrap > script").toString()

        allAnime.findAll(scripts).forEach {
            val parts = it.groupValues[1].split(',')
            if (!parts[0].contains("nom")) {
                if (!animeUrl.endsWith("/")) animeUrl += "/"
                val fullUrl = animeUrl + parts[1].replace("\"", "").trim()
                add(
                    newAnimeSearchResponse(parts[0].replace("\"", "").trim(), fullUrl, TvType.Anime, false) {
                        this.posterUrl = posterUrl
                    }
                )
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links = Regex("""'([^']*)'""").findAll(data)
        links.forEach {
            val url = it.groupValues[1]
            if (url.isNotBlank()) {
                loadExtractor(httpsify(url), url, subtitleCallback) { link ->
                    callback.invoke(
                        newExtractorLink(link.source, link.name ?: "", link.url) {
                            this.referer = link.referer
                            this.quality = link.quality
                            this.isM3u8 = link.isM3u8
                            this.headers = link.headers ?: emptyMap()
                        }
                    )
                }
            }
        }
        return true
    }

    private val allAnime = Regex("""panneauAnime\("(.*)"\);""")
    private val newEp = Regex("""cartePlanningAnime\("(.*)"\);""")

    override val mainPage = mainPageOf(
        Pair(mainUrl, "NOUVEAUX"),
        Pair(mainUrl, "A ne pas rater"),
        Pair(mainUrl, "Les classiques"),
        Pair(mainUrl, "Derniers animes ajoutés")
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data
        val category = request.name
        val dayId = Calendar.getInstance().get(Calendar.DAY_OF_WEEK).let {
            when (it) {
                Calendar.MONDAY -> "1"
                Calendar.TUESDAY -> "2"
                Calendar.WEDNESDAY -> "3"
                Calendar.THURSDAY -> "4"
                Calendar.FRIDAY -> "5"
                Calendar.SATURDAY -> "6"
                else -> "0"
            }
        }

        if (page > 1) return newHomePageResponse(category, emptyList())

        val doc = avoidCloudflare(url).document
        val cssSelector = "div.scrollBarStyled"
        val scripts = doc.select("div#$dayId.fadeJours > div > script").toString()

        val items = when {
            category.contains("NOUVEAUX", ignoreCase = true) -> {
                newEp.findAll(scripts).mapNotNull { match ->
                    val data = match.groupValues[1].split(',')
                    val link = fixUrl("catalogue/" + data[1].replace("\"", "").trim())
                    val title = data[0].replace("\"", "").trim()
                    val posterUrl = avoidCloudflare(link).document.select("img#imgOeuvre").attr("src")
                    newAnimeSearchResponse(title, link, TvType.Anime, false) {
                        this.posterUrl = posterUrl
                        this.dubStatus = if (data[5].lowercase().contains("vf")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(DubStatus.Subbed)
                    }
                }
            }
            category.contains("ajoutés", ignoreCase = true) -> doc.select(cssSelector)[8].select("div.shrink-0").mapNotNull { it.toMainSearchResponse() }
            category.contains("rater", ignoreCase = true) -> doc.select(cssSelector)[10].select("div.shrink-0").mapNotNull { it.toMainSearchResponse() }
            else -> doc.select(cssSelector)[9].select("div.shrink-0").mapNotNull { it.toMainSearchResponse() }
        }

        return newHomePageResponse(category, items)
    }

    private fun Element.toMainSearchResponse(): SearchResponse? {
        val poster = select("a > img").attr("src")
        val title = select("a > div > h1").text()
        val href = select("a").attr("href")
        if (href.contains("search.php")) return null
        return newAnimeSearchResponse(title, "$href*", TvType.TvSeries, false) {
            this.posterUrl = poster
        }
    }

    suspend fun avoidCloudflare(url: String): NiceResponse {
        return app.get(url) // interceptor can be used if needed
    }

    private val findAllNumber = Regex("""([0-9]+)""")
} 
