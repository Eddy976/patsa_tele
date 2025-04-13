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

    private val rgxGetLink = Regex("""'[^']*'""")

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val results = rgxGetLink.findAll(data)

        results.forEach { match ->
            val playerUrl = match.value.replace("'", "").replace(",", "").trim()
            if (playerUrl.isNotBlank()) {
                loadExtractor(httpsify(playerUrl), playerUrl, subtitleCallback) { link ->
                    callback(
                        newExtractorLink(
                            source = link.source,
                            name = link.name ?: "",
                            url = link.url
                        ) {
                            referer = link.referer
                            quality = link.quality
                            isM3u8 = link.isM3u8
                            headers = link.headers ?: emptyMap()
                        }
                    )
                }
            }
        }
        return true
    }

    suspend fun avoidCloudflare(url: String): NiceResponse {
        return app.get(url)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/template-php/defaut/fetch.php"
        val document = app.post(link, data = mapOf("query" to query)).document
        val results = document.select("a")
        return results.mapNotNull { element ->
            val posterUrl = element.select("a>img").attr("src")
            val linkToAnime = element.select("a").attr("href")
            val doc = avoidCloudflare(linkToAnime).document
            val scripts = doc.select("div.flex.flex-wrap > script").toString()
            Regex("""panneauAnime\(\"(.*?)\)\;""").findAll(scripts).mapNotNull { match ->
                val values = match.groupValues[1].split(",")
                if (values.size < 2) return@mapNotNull null
                val title = values[0].replace("\"", "").trim()
                val path = values[1].replace("\"", "").trim()
                val fullLink = if (linkToAnime.endsWith("/")) "$linkToAnime$path" else "$linkToAnime/$path"
                newAnimeSearchResponse(title, fullLink, TvType.Anime, false) {
                    this.posterUrl = posterUrl
                }
            }.toList()
        }.flatten()
    }

    override val mainPage = mainPageOf(
        Pair(mainUrl, "NOUVEAUX"),
        Pair(mainUrl, "A ne pas rater"),
        Pair(mainUrl, "Les classiques"),
        Pair(mainUrl, "Derniers animes ajoutés"),
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data
        val categoryName = request.name
        val document = avoidCloudflare(url).document

        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val idDay = when (today) {
            Calendar.MONDAY -> "1"
            Calendar.TUESDAY -> "2"
            Calendar.WEDNESDAY -> "3"
            Calendar.THURSDAY -> "4"
            Calendar.FRIDAY -> "5"
            Calendar.SATURDAY -> "6"
            else -> "0"
        }

        val selector = when {
            categoryName.contains("NOUVEAUX", true) -> "div#$idDay.fadeJours > div > script"
            categoryName.contains("ajoutés", true) -> "div.scrollBarStyled:nth-of-type(9) div.shrink-0"
            categoryName.contains("rater", true) -> "div.scrollBarStyled:nth-of-type(11) div.shrink-0"
            else -> "div.scrollBarStyled:nth-of-type(10) div.shrink-0"
        }

        val elements = document.select(selector)
        val items = if (selector.contains("script")) {
            Regex("""cartePlanningAnime\(\"(.*?)\)\;""").findAll(elements.toString()).mapNotNull { match ->
                val content = match.groupValues[1].split(',')
                if (content.size < 2) return@mapNotNull null
                val title = content[0].replace("\"", "").trim()
                val path = content[1].replace("\"", "").trim()
                val link = fixUrl("catalogue/$path")
                val html = avoidCloudflare(link)
                val poster = html.document.select("img#imgOeuvre").attr("src")
                newAnimeSearchResponse(title, link, TvType.Anime, false) {
                    this.posterUrl = poster
                }
            }.toList()
        } else {
            elements.mapNotNull { el ->
                val posterUrl = el.select("a > img").attr("src")
                val title = el.select("a > div > h1").text()
                val href = el.select("a").attr("href")
                if (href.contains("search.php")) return@mapNotNull null
                newAnimeSearchResponse(title, "$href*", TvType.TvSeries, false) {
                    this.posterUrl = posterUrl
                }
            }
        }
        return newHomePageResponse(categoryName, items)
    }
} 
