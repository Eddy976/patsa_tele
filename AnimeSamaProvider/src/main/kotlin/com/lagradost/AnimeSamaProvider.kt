package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.util.EnumSet

class AnimeSamaProvider : MainAPI() {
    override var mainUrl = "https://anime-sama.fr"
    override var name = "Anime-sama"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override var lang = "fr"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
    private val interceptor = CloudflareKiller()

    private val regexGetlink = Regex("""(http.*)\',""")
    private val allAnime = Regex("""panneauAnime\(\"(.*)\)\;""")

    override suspend fun search(query: String): List<SearchResponse> {
        val allResults = mutableListOf<SearchResponse>()
        val document = app.post("$mainUrl/template-php/defaut/fetch.php", data = mapOf("query" to query)).document
        val results = document.select("a")

        results.mapNotNullTo(allResults) { element ->
            val posterUrl = element.select("a > img").attr("src")
            val onclick = element.attr("onclick")
            val link = regexGetlink.find(onclick)?.groupValues?.get(1) ?: return@mapNotNullTo null
            val title = element.text()
            val tvType = when {
                title.contains("film", ignoreCase = true) -> TvType.AnimeMovie
                else -> TvType.Anime
            }
            val dubStatus = if (title.contains("vostfr", ignoreCase = true)) EnumSet.of(DubStatus.Subbed) else EnumSet.of(DubStatus.Dubbed)

            newAnimeSearchResponse(title, link, tvType, false) {
                this.posterUrl = posterUrl
                this.dubStatus = dubStatus
            }
        }

        return allResults
    }
override suspend fun load(url: String): LoadResponse {
    var targetUrl = url
    if (url.contains("*")) {
        val (latestLink, _) = avoidCloudflare(
            url.replace("*", "")
        ).document.select("div.flex.flex-wrap > script")
            .tryTofindLatestSeason()

        targetUrl = url.replace("*", "/") + latestLink.toString()
    }

    val subEpisodes = ArrayList<Episode>()
    val dubEpisodes = ArrayList<Episode>()
    val cata = regexCatalogue.find(targetUrl)!!.groupValues[0]
    val html = avoidCloudflare(targetUrl)
    val document = html.document

    val linkBack = mainUrl + cata
    val htmlBack = avoidCloudflare(linkBack)
    val documentBack = htmlBack.document
    val description = documentBack.select("p.text-sm")[0].text()
    val poster = document.select("img#imgOeuvre").attr("src")
    var title = document.select("h3#titreOeuvre").text()
    var status = false
    var urlSubDub = ""
    var htmlSubDub: NiceResponse? = null

    if (targetUrl.contains("/vostfr")) {
        urlSubDub = targetUrl.replace("/vostfr", "/vf")
        htmlSubDub = avoidCloudflare(urlSubDub)
    } else if (targetUrl.contains("/vf")) {
        urlSubDub = targetUrl.replace("/vf", "/vostfr")
        htmlSubDub = avoidCloudflare(urlSubDub)
    }

    if (targetUrl.lowercase().contains("vostfr")) {
        listOf("SUB", "DUB").apmap {
            when (it) {
                "SUB" -> {
                    subEpisodes.getEpisodes(html, targetUrl)
                    if (subEpisodes.isEmpty()) status = true
                }
                "DUB" -> {
                    if (htmlSubDub!!.document.select("h3#titreOeuvre").text() != "") {
                        dubEpisodes.getEpisodes(htmlSubDub, urlSubDub)
                        if (dubEpisodes.isNotEmpty()) {
                            title = title.replace("VOSTFR", "").replace("VF", "")
                        }
                    }
                }
            }
        }
    } else {
        listOf("SUB", "DUB").apmap {
            when (it) {
                "SUB" -> {
                    if (htmlSubDub!!.document.select("h3#titreOeuvre").text() != "") {
                        subEpisodes.getEpisodes(htmlSubDub, urlSubDub)
                        if (subEpisodes.isNotEmpty()) {
                            title = title.replace("VOSTFR", "").replace("VF", "")
                        }
                    }
                }
                "DUB" -> {
                    dubEpisodes.getEpisodes(html, targetUrl)
                    if (dubEpisodes.isEmpty()) status = true
                }
            }
        }
    }

    listOf(dubEpisodes, subEpisodes).apmap {
        it.apmap { episode ->
            episode.posterUrl = poster
        }
    }

    val allresultshome: MutableList<SearchResponse> = mutableListOf()
    val recommendations = documentBack.select("div.flex.flex-wrap > script")
    allAnime.findAll(recommendations.toString()).forEach {
        val text = it.groupValues[1]
        if (!text.contains("nom\",")) {
            val tvtype = if (text.lowercase().contains("film")) TvType.AnimeMovie else TvType.Anime
            val titleRec = text.split(",")[0].replace("\"", "").trim()
            val urlRec = linkBack + text.split(",")[1].replace("\"", "").trim()

            val res = newAnimeSearchResponse(
                titleRec,
                urlRec,
                tvtype,
                false
            ) {
                this.posterUrl = poster
            }
            allresultshome.add(res)
        }
    }

    return newAnimeLoadResponse(
        title,
        targetUrl,
        TvType.Anime,
    ) {
        posterUrl = poster
        this.plot = description
        this.recommendations = allresultshome
        if (subEpisodes.isNotEmpty()) addEpisodes(DubStatus.Subbed, subEpisodes)
        if (dubEpisodes.isNotEmpty()) addEpisodes(DubStatus.Dubbed, dubEpisodes)
        this.comingSoon = status
    }
}
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val regex = Regex("""'([^']*)'""")
        val results = regex.findAll(data)

        results.forEach { match ->
            val playerUrl = match.groupValues[1]
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

    override val mainPage = mainPageOf(
        Pair(mainUrl, "NOUVEAUX"),
        Pair(mainUrl, "A ne pas rater"),
        Pair(mainUrl, "Les classiques"),
        Pair(mainUrl, "Derniers animes ajoutés"),
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val sectionName = request.name
        val document = app.get(mainUrl).document
        val selector = "div.scrollBarStyled"

        val homeList = when {
            sectionName.contains("NOUVEAUX", true) -> emptyList() // À remplir si besoin
            sectionName.contains("ajoutés", true) -> document.select(selector)[8].select("div.shrink-0").mapNotNull { it.toSearchResponse() }
            sectionName.contains("rater", true) -> document.select(selector)[10].select("div.shrink-0").mapNotNull { it.toSearchResponse() }
            else -> document.select(selector)[9].select("div.shrink-0").mapNotNull { it.toSearchResponse() }
        }

        return newHomePageResponse(listOf(HomePageList(name = sectionName, list = homeList)))
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val posterUrl = select("a > img").attr("src")
        val title = select("a > div > h1").text()
        val link = select("a").attr("href")

        if (link.contains("search.php")) return null

        return newAnimeSearchResponse(title, "$link*", TvType.Anime, false) {
            this.posterUrl = posterUrl
        }
    }

    suspend fun avoidCloudflare(url: String): String {
        return app.get(url, interceptor = interceptor).text
    }
}
