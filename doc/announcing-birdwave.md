# Announcing Birdwave

I'm happy to announce a side project I've been working on with some of my
colleagues here at Neo: [Birdwave](http://birdwave.neo.com). An app to
visualize bird migrations across the US region, Birdwave falls at the
intersection of several different personal and professional interests for me.

### Birds
Even if you aren't an aspiring
[birder](http://en.wikipedia.org/wiki/Birdwatching) like me, it's hard not to
be fascinated by how these tiny creatures cover hundreds of miles each year on
barely any food or rest.

### Big Data
[eBird.org](http://ebird.org/ebird/cmd?=Start) collects crowdsourced reports of
sightings of various species of birds all over the world, and provide the data
freely for non-commercial use. Just the reported sightings for the US region
from December 2012 through November 2013 turned out to be a whopping 20
gigabytes of data, consisting of 29.6 million data points across over 1700
species of birds.

### Data Visualization with d3js
I've already
[written](http://www.neo.com/2014/04/21/choropleths-and-d3js) about choropleths
as a data visualization technique and how d3 is amazing at handling it.

### The Clojure Stack
This consisted of:
* [Pedestal](https://github.com/pedestal/pedestal) as the web framework
* [Datomic](http://www.datomic.com/) as the data layer
* [Transit](https://github.com/cognitect/transit-format) for data transfer
* [ClojureScript](https://github.com/clojure/clojurescript) on the front-end

Using Clojure and Datomic on the back-end made the large amount of data more
manageable to serve up as an API.

### ReactJS and Om
As a data-driven app, Birdwave's front-end lent itself really well to be
structured as a set of [Om](https://github.com/swannodette/om) components.

## Go Try It Out!
We'll be posting more about some of our learnings while building this app. In
the meanwhile, please try out the app and let us know what we can do to improve
it. Here are some good species to check out:

* [Black-throated Blue Warbler](http://birdwave.neo.com/#/taxon/27926/2013/05)
* [Baltimore Oriole](http://birdwave.neo.com/#/taxon/30632/2013/07)
* [Lazuli Bunting](http://birdwave.neo.com/#/taxon/30320/2013/08)

For more detail on how to use the app, take a look at the "How this works"
section at the bottom of the page.
