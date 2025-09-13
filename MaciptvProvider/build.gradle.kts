// use an integer for version numbers
version = 3


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "MACIPTV for clodstream"
    authors = listOf("Eddy")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1

    tvTypes = listOf("Anime","Movie","TvSeries")

    requiresResources = true
    language = "fr"


    iconUrl = "https://static.vecteezy.com/system/resources/thumbnails/028/799/777/small/television-3d-rendering-icon-illustration-png.png"
}

