# Choropleths and D3.js

Choropleths are a great way to get a really clear depiction of how a statistic
varies by location. In our case, the statistic in question is the frequency of
sightings for a given species of bird. The idea is to be able to see which
areas of the country reflect a greater frequency month to month in order to get
a feel for when and how the bird migrates.

To have the map dynamically change with new data values, we need the map to be
composed of DOM elements whose styles can be manipulated based on changing
data. D3.js gives us ways to do exactly that, and more. The approach consists
of three components:
  1. Generate an SVG map based on standard geographical data
  1. Associate the map elements with the bird frequency data
  1. Map the frequency numbers to visual styles

The following subsections describe each of these components in detail.

## [GeoJSON](http://geojson.org/) and [TopoJSON](https://github.com/mbostock/topojson)

GeoJSON is a JSON standard that represents geographical data. For our purposes,
we can think of it as a grouping of objects in terms of their geometry.
Countries, states, counties, rivers and roads may all be represented in terms
of points and lines in a coordinate system. Easy, right?

Taking that a little further, TopoJSON is an extension to GeoJSON that has a
notion of topology and shared geometry. So, for instance, if two polygons have
a shared edge, GeoJSON will use two lines for it, but TopoJSON will use only
one. For large geometries, TopoJSON can effect a dramatic decrease in file size.

To get your hands dirty with some GeoJSON, head [to geojson.io](http://geojson.io). For
an introduction to how TopoJSON works, I highly recommend
[the author's example](http://bost.ocks.org/mike/map/). Another invaluable resource
is the [US Atlas](https://github.com/mbostock/us-atlas) project, which comes with a
handy Makefile to generate TopoJSON for almost any kind of US map you want.

[Mike Bostock](http://twitter.com/mbostock), who created TopoJSON, is also the
author of D3. Coincidence? No, not at all, because D3.js is built to consume
both GeoJSON and TopoJSON to create SVG path elements in the browser. In just
[a few lines of code](http://bl.ocks.org/mbostock/4136647), we can load up
TopoJSON from the server and display a beautiful county map of the United
States in the browser.

## D3.js Data Binding

D3's most powerful feature is its data binding, which allows us to attach
arbitrary data to DOM elements, and then manipulate any number of their
properties as a function of that data. I recommend [Scott Murray's
tutorials](http://alignedleft.com/tutorials) for anyone looking to get a better
understanding of this concept.

In our case, it's only a matter of being able to uniquely identify any county,
and have a mechanism to look up its bird frequency when needed. We do this by
ensuring that the generated TopoJSON for the map contains the state and county
name for each county as part of its bound data. Then, as we retrieve new data
for county-wise bird sightings, we can look up the frequency using this info.

For instance, say Whatcom County in Washington reported 1 sighting of the
American Bittern in Feb 2013. Once we can determine which SVG element
represents that county, we can render its color proportional to 1 bird
sighting. To do this with vanilla JS on a scale of over 2700 counties across
the US seems prohibitive, but D3's selection API makes it easy and fast. As
a special touch, the duration of an element's transition from one color to
another is also a function of the data, so we can gently animate the colors
as we move from month to month. Time for some code:

```clojure
    (defn freq-duration [data]
      (+ (* 100 (freq-for-county data)) 200))

    (defn freq-color [data]
      (color (freq-for-county data)))

    (defn update-counties [results]
      (populate-freqs results)
      ( -> js/d3
           (.selectAll "path.county")
           (.transition)
           (.duration freq-duration)
           (.style "fill" freq-color)
           (.style "stroke" freq-color)))
```

In the above code snippet, `update-counties` gets called with the response from
the server every time the month (or the species of bird selected) changes.
`populate-freqs` updates the frequency lookup table for each county name. Then
D3 is used to select all the county elements (`path.county`), and specify that
each of those should transition to the new `freq-color` over a duration of
`freq-duration`.  Both `freq-color` and `freq-duration` take a single argument,
`data`, which is simply the data bound to each county element, consisting of
(among other things) the state and county name.`freq-for-county` is the
frequency lookup function. Pretty concise for all the work it's doing!

If you notice, `freq-color` uses a `color` function to return a color from a
frequency value. This function is a special D3.js function called a [quantile
scale](https://github.com/mbostock/d3/wiki/Quantitative-Scales#quantile-scales).
Its primary purpose is to take an input value, and return a transformed value
in a specified range. In the case of `color`, it takes a number, and maps it in
a range of 9 colors based on where it falls between 0 and 5. The 9 colors come
from [ColorBrewer](http://colorbrewer2.org/), which is an awesome project by
Cynthia Brewer to provide color-blind-friendly color options for cartographers.

And so we have the basic building blocks for our migration mapper. In the next
section, we'll talk about how ClojureScript gives us the ability to build
powerful interactive components that allow us to exploit the dynamic nature of
the application.
