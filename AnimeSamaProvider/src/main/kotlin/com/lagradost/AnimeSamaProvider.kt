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
        val allResults: MutableList<SearchResponse> = mutableListOf()
        val link = "$mainUrl/template-php/defaut/fetch.php"
        val document = app.post(link, data = mapOf("query" to query)).document
        val results = document.select("a")
        for (article in results) {
            allResults.toSearchResponse1(article)
        }
        return allResults
    }

    private val regexGetlink = Regex("""(http.*)\',\"""")

    private suspend fun MutableList<SearchResponse>.toSearchResponse1(element: Element) {
        val posterUrl = element.select("a>img").attr("src")
        var linkToAnime = element.select("a").attr("href")
        val document = avoidCloudflare(linkToAnime).document
        val allAnimeScripts = document.select("div.flex.flex-wrap > script")
        AnimeRegex.findAll(allAnimeScripts.toString()).forEach {
            val text = it.groupValues[1]
            if (!text.contains("nom\",")) {
                if (!linkToAnime.endsWith("/")) {
                    linkToAnime += "/"
                }
                val link = linkToAnime + text.split(",")[1].replace("\"", "").trim()
                add(newAnimeSearchResponse(
                    text.split(",")[0].replace("\"", "").trim(),
                    link,
                    TvType.Anime,
                    false
                ) {
                    this.posterUrl = posterUrl
                })
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val categoryName = request.name
        val url = request.data
        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val idDay = when (dayOfWeek) {
            Calendar.MONDAY -> "1"
            Calendar.TUESDAY -> "2"
            Calendar.WEDNESDAY -> "3"
            Calendar.THURSDAY -> "4"
            Calendar.FRIDAY -> "5"
            Calendar.SATURDAY -> "6"
            else -> "0"
        }

        if (page > 1) return HomePageResponse(emptyList())
        val document = avoidCloudflare(url).document
        val cssSelector = "div.scrollBarStyled"
        val cssSelectorN = "div#$idDay.fadeJours > div > script"

        val home = when {
            categoryName.contains("NOUVEAUX") -> {
                newEpRegex.findAll(document.select(cssSelectorN).toString()).mapNotNull {
                    it.toSearchResponseNewEp()
                }.toList()
            }
            categoryName.contains("ajoutés") -> {
                document.select(cssSelector)[8].select("div.shrink-0").mapNotNull {
                    it.toSearchResponse()
                }
            }
            categoryName.contains("rater") -> {
                document.select(cssSelector)[10].select("div.shrink-0").mapNotNull {
                    it.toSearchResponse()
                }
            }
            else -> {
                document.select(cssSelector)[9].select("div.shrink-0").mapNotNull {
                    it.toSearchResponse()
                }
            }
        }
        return newHomePageResponse(HomePageList(categoryName, home))
    }

    private val newEpRegex = Regex("""cartePlanningAnime\(\"(.*)\)\;""")
    private val AnimeRegex = Regex("""panneauAnime\(\"(.*)\)\;""")

    private suspend fun MatchResult.toSearchResponseNewEp(): SearchResponse? {
        val anime = this.groupValues[1]
        val link = "catalogue/" + anime.split(',')[1].replace("\"", "").trim()
        if (anime.split(',')[1].lowercase().trim() == "scan" || link.lowercase().contains("/scan/")) return null

        val linked = fixUrl(link)
        val html = avoidCloudflare(linked)
        val document = html.document
        val posterUrl = document.select("img#imgOeuvre").attr("src")
        val scheduleTime = anime.split(',')[3]
        val title = anime.split(',')[0]
        val dubStatus = if (anime.split(',')[5].lowercase().contains("vf")) {
            EnumSet.of(DubStatus.Dubbed)
        } else {
            EnumSet.of(DubStatus.Subbed)
        }

        return newAnimeSearchResponse(
            "$scheduleTime \n $title",
            linked,
            TvType.Anime,
            false
        ) {
            this.posterUrl = posterUrl
            this.dubStatus = dubStatus
        }
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val posterUrl = select("a > img").attr("src")
        val title = select("a >div>h1").text()
        val globalLink = select("a").attr("href")
        if (globalLink.contains("search.php")) return null

        return newAnimeSearchResponse(title, "$globalLink*", TvType.TvSeries, false) {
            this.posterUrl = posterUrl
        }
    }

    suspend fun avoidCloudflare(url: String): NiceResponse {
        return app.get(url)
    }
}
