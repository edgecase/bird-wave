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
[there are many](https://github.com/search?q=flickr&ref=simplesearch) out there
in several popular programming languages). All we needed was to be able to
construct two correct query urls.  This became easy to do with [Chas
Emerick's](https://twitter.com/cemerick) [url](https://github.com/cemerick/url)
library, which allows us to manipulate query strings as maps.

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

The JSON is pretty straightforward for retrieving the url and title, but if
you notice, there's no attribution information in the search result. To
obtain that, we need to make another call, detailed in the next section.

## Retrieving Attribution (flickr.photos.getInfo)

Among the values returned in the search result are the photo id and secret. We
can ask Flickr to give us more information about the photo using these values.
The getInfo API is documented
[here](https://www.flickr.com/services/api/flickr.photos.getInfo.html), and the
explorer tool is
[here](https://www.flickr.com/services/api/explore/flickr.photos.getInfo).

Since there are no other params required, we can write another ClojureScript
function that builds the query map we need:

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
    ...
    "urls": {
      "url": [
        { "type": "photopage", "_content": "https:\/\/www.flickr.com\/photos\/31064702@N05\/4769690133\/" }
      ] }, "media": "photo" }, "stat": "ok" }
```

The attribution license requires that the image be linked to its Flickr photo
page, which is included in the urls section of the response. The owner details
and title information are the other pieces of information we look for. All
together, the following snippet can extract what we need:

```clojure
(defn attribution [photo-info]
  (let [detail  (-> photo-info
                    (js->clj)
                    (keywordize-keys)
                    (:photo))]
    {:by (first (filter not-empty (vec (vals (select-keys (:owner detail) [:realname :username :path_alias])))))
     :url (get-in detail [:urls :url 0 :_content])}))
```

First we parse the JSON into a Clojure map using the `js->clj` macro, followed
by changing the string keys to keywords with `clojure.walk/keywordize-keys`.
The name to display can be the first non-empty value for the owner's
`realname`, `username` or `pathalias` properties. The photo page url is the
`_content` property of the first url in the returned set. We return a map of
the two values for use in the application.

## Building the URL

This is the easy part. With the `cemerick.url/url` function, we can build an
instance of a URL object from a string, like so:

```clojure
(def api-base-url (url "https://api.flickr.com/services/rest/"))
```

Then, we can assign it a query string by calling `assoc`:

```clojure
(assoc api-base-url :query {:api_key "my_super_flickr_key"})
```

Calling `str` on a URL instance returns its string representation which we can
use to make AJAX calls. So we can write this function to create a URL with
static and dynamic segments:

```clojure
(def api-key-params {:api_key "my_super_flickr_key"})
(def format-params {:format "json" :nojsoncallback 1})

(defn build-url [func & args]
  (let [query (into {} [api-key-params
                        format-params
                        (apply func args)])]
    (-> api-base-url
        (assoc :query query)
        str)))
```

The format params ensure that the response is JSON. Omitting the
`nojsoncallback` param would return JSONP. The `build-url` function is a higher
order function that allows us to pass in a function name and its arguments,
the results of which are tacked into the query map along with the static api
format maps. We can use this function with our `search-params` and `info-params`
functions above to generate the urls we need:

```clojure
(make-request search-params "eastern kingbird" 1) ;; https://api.flickr.com/services/rest/?method=flickr.photos.search&api_key=my_super_flickr_key&text=eastern+kingbird&license=4&sort=relevance&extras=owner_name%2C+url_q&per_page=1&format=json&nojsoncallback=1
(make-request info-params "4769690133" "818406d0cd") ;; https://api.flickr.com/services/rest/?method=flickr.photos.getInfo&api_key=my_super_flickr_key&photo_id=4769690133&secret=818406d0cd&format=json&nojsoncallback=1
```

Writing this little helper library took about an hour once we found the url
library. Managing this with plain strings would have been way more painful.

## Conclusion

With this, we have the ability to search photos and attributions on Flickr.
Stay tuned to see how we integrate this into our migration mapping app using
Om, ClojureScript's implementation of Facebook's ReactJS.
