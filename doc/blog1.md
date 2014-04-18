# Visualizing Bird Migration: Data-driven Development of a Different Kind

> TODO: remove this outline when done.
* TLDR
* Motivation
  * Birding
  * Learning data visualization
* Data - eBird
* Front-end
  * D3js
    * Choropleths, GeoJSON, TopoJSON
    * Colorbrewer
  * ClojureScript
    * Data structures, Om/React, Secretary

This is the first in a series of blog posts about a personal project I recently
had the pleasure of working on.

## TL;DR

## Motivation

I was teaching myself [D3js](http://d3js.org), and was inspired by the idea of
[choropleths](http://en.wikipedia.org/wiki/Choropleth) as a data visualization
technique. Also, as a birder, I managed to procure a very large dataset of bird
sightings from [eBird.org](http://ebird.org), which I wanted to use to observe
bird movement across the US region over the period of a year. The result is
[BirdWave](http://birdwave.neo.com). Go check it out, I'll wait.

## The Data

The [Cornell Lab of Ornithology](http://www.birds.cornell.edu/) curates a
phenomenal amount of crowdsourced reports of sightings of various species of
birds all over the world at [eBird.org](http://ebird.org/ebird/cmd?=Start).
They provide the data freely for non-commercial use (be sure to check their
terms of use). Upon my request, they gave me a packaged download of all the
reported sightings for the US region from December 2012 through November 2013.
This turned out to be a whopping 20G of data, consisting of 29.6 million data
points across over 1700 species of birds.

My awesome colleagues [John Andrews](https://github.com/xandrews) and [Chris
Westra](https://github.com/Bestra) took it upon themselves to munge the data to
create a responsive API for the app. Blog posts later in this series will cover
some of the challenges and lessons we learned in the process. In this post, I'll
talk about the visualization itself.

## The Front-end
