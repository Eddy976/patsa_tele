// use an integer for version numbers
version = 2


cloudstream {
    language = "fr"
    // All of these properties are optional, you can safely remove them

    // description = "Lorem Ipsum"
    authors = listOf("Sarlay")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "Movie",
    )

    iconUrl = "https://mesfilms.click/wp-content/uploads/2021/08/mflogo_round.png"
}
