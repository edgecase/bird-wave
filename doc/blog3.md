# Using the Flickr API with ClojureScript

Flickr is an amazing resource for finding images to use in your applications,
due to how well they manage licenses for their user-submitted content. These
licenses are exposed in the Flickr API, making it easy to limit your search to,
for example, only images with a Creative Commons [Attribution
Share-Alike](http://creativecommons.org/licenses/by-sa/2.0/) license.

On one of our side projects at Neo (first talked about in [this
post](http://www.neo.com/2014/04/21/choropleths-and-d3-js)), we needed to
display a picture of a selected species of bird. Not only did Flickr meet most
of our needs, it enabled us to get productive very quickly, thanks to a mature
interface, detailed documentation and exploration tools.

Our use case was simple: A user would click on the name of a bird species. We
would search the Flickr API with that name, grab the first result, place the
image url in an `<img>` tag on our page and display the attribution alongside.
We didn't need a heavy-weight third-party library to accomplish this (though
there are some very exhaustive ones out there in several popular programming
languages). All we needed was to be able to construct the correct query urls.
This became easy to do with [Chas Emerick's](https://twitter.com/cemerick)
[url](https://github.com/cemerick/url) library, which allows us to manipulate
query strings as maps.

## Searching Photos (flickr.photos.search)

The search API is documented
[here](https://www.flickr.com/services/api/flickr.photos.search.html), and the
explorer tool for search is
[here](https://www.flickr.com/services/api/explore/flickr.photos.search). Of all
the available params, we needed 5:

* text: the search text
* per\_page: the number of photos
* sort: how to sort the results
* license: which license the photos should have
* extras: more information about the photo (such as a thumbnail url)

We decided on using only images with a Creative Commons
[Attribution](http://creativecommons.org/licenses/by/2.0/) license. We also
wanted the results to always be sorted by relevance; since Flickr photos are
not limited to birds, there was a possibility of retrieving irrelevant results
for generic bird names. Of all the extras available, we only needed the owner
name and the smallest square thumbnail url. This meant that there were only two
variable elements to the search: the name of the bird, and how many photos we
wanted to retrieve. In ClojureScript:

```clojure
    (def BY "4") ;; "Attribution License" url="http://creativecommons.org/licenses/by/2.0/"

    (defn search-params [text, num-photos]
      "Params for searching num-photos (max 500) with text"
      {:text (str "\"" text "\"")
       :per_page num-photos
       :method "flickr.photos.search"
       :sort "relevance"
       :license BY
       :extras "owner_name,url_q"})
```

Calling the API with the params above results in a JSON response like the one
shown below (text: "eastern kingbird", per\_page: 1):

```json
    { "photos": { "page": 1, "pages": "223", "perpage": 1, "total": "223",
        "photo": [
          { "id": "4769690133", "owner": "31064702@N05", "secret": "818406d0cd", "server": "4123", "farm": 5, "title": "Eastern Kingbird", "ispublic": 1, "isfriend": 0, "isfamily": 0, "ownername": "Dawn Huczek", "url_q": "https:\/\/farm5.staticflickr.com\/4123\/4769690133_818406d0cd_q.jpg", "height_q": "150", "width_q": "150" }
        ] }, "stat": "ok" }
```

The JSON is pretty straightforward, but if you notice, there's no attribution
in the search result. To retrieve that, we need to make another call, detailed
in the next section.

## Retrieving Attribution (flickr.photos.getInfo)

One of the values returned in the search result is the photo id and secret. We
can ask Flickr to give us more information about the photo using these values.
The getInfo API is documented
[here](https://www.flickr.com/services/api/flickr.photos.getInfo.html), and the
explorer tool is
[here](https://www.flickr.com/services/api/explore/flickr.photos.getInfo).

Since there are no other params required, we can build another ClojureScript
function that builds the map we need:

```clojure
    (defn info-params [photo-id, secret]
      "Params for fetching details of photo with photo-id and optional secret"
      {:method "flickr.photos.getInfo"
       :photo_id photo-id
       :secret secret})
```

The JSON response looks like the one below:

```json
    { "photo": { "id": "4769690133", "secret": "818406d0cd", "server": "4123", "farm": 5, "dateuploaded": "1278473496", "isfavorite": 0, "license": 4, "safety_level": 0, "rotation": 0, "originalsecret": "d7072dbb9a", "originalformat": "jpg",
        "owner": { "nsid": "31064702@N05", "username": "Dawn Huczek", "realname": "", "location": "USA", "iconserver": "2915", "iconfarm": 3, "path_alias": "" },
        "title": { "_content": "Eastern Kingbird" },
        "description": { "_content": "I think this is an Eastern Kingbird.\nFor Feathery Friday&quot;\nI am not BATMAN!! (with my cape and black mask)" },
        "visibility": { "ispublic": 1, "isfriend": 0, "isfamily": 0 },
        "dates": { "posted": "1278473496", "taken": "2010-07-03 17:35:11", "takengranularity": 0, "lastupdate": "1397608686" }, "views": "392",
        "editability": { "cancomment": 0, "canaddmeta": 0 },
        "publiceditability": { "cancomment": 1, "canaddmeta": 0 },
        "usage": { "candownload": 1, "canblog": 0, "canprint": 0, "canshare": 1 },
        "comments": { "_content": 40 },
        "notes": {
          "note": [
          ] },
        "people": { "haspeople": 0 },
        "tags": {
          "tag": [
            { "id": "31059362-4769690133-711781", "author": "31064702@N05", "authorname": "Dawn Huczek", "raw": "Kensington Metropark", "_content": "kensingtonmetropark", "machine_tag": 0 },
            { "id": "31059362-4769690133-587205", "author": "31064702@N05", "authorname": "Dawn Huczek", "raw": "Eastern Kingbird", "_content": "easternkingbird", "machine_tag": 0 },
            { "id": "31059362-4769690133-8822142", "author": "46484868@N00", "authorname": "Tall Bob", "raw": "AvianExcellence", "_content": "avianexcellence", "machine_tag": 0 },
            { "id": "31059362-4769690133-1874633", "author": "31064702@N05", "authorname": "Dawn Huczek", "raw": "featheryfriday", "_content": "featheryfriday", "machine_tag": 0 }
          ] },
        "urls": {
          "url": [
            { "type": "photopage", "_content": "https:\/\/www.flickr.com\/photos\/31064702@N05\/4769690133\/" }
          ] }, "media": "photo" }, "stat": "ok" }
```
