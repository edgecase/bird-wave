# Responsive JavaScript With EnquireJS

For a dynamic client-side application, using CSS alone to show and hide
components for different screen sizes isn't enough. Even when hidden,
components may be listening for application state and re-rendering themselves
or taking up resources for no visible effect, giving the perception of a laggy
app. They may be making unnecessary AJAX requests to the server.

### Screen size as application state

The client environment needs to be aware of the available screen size at a more
fundamental level. In other words, screen size should be part of the
application state. This way, the client can make decisions on whether or not to
render certain components for a given screen size.

## EnquireJS

It would be great to have a way to make media queries from the client code, and
be notified when the screen size changes.
[EnquireJS](http://wicky.nillia.ms/enquire.js/) is a lightweight library that
makes it easy to do this, using the
[`window.matchMedia`](https://developer.mozilla.org/en-US/docs/Web/API/Window.matchMedia)
API and a simple registry/callback pattern that fires for different stages of
matching a media query. We can use enquirejs callbacks to set the value of a
`screen-size` variable, which our app can use when deciding whether or not to render a
component. In the following ClojureScript example, `model` is an atom that is responsible for the
application state:

```clojure
(defn update-size [model new-size]
  (fn [] (swap! model assoc :screen-size new-size)))

(defn watch-screen-size [model]
  (-> js/enquire
      (.register "screen and (min-width: 0px) and (max-width: 520px)"    (update-size model "xs"))
      (.register "screen and (min-width: 521px) and (max-width: 768px)"  (update-size model "sm"))
      (.register "screen and (min-width: 769px) and (max-width: 1024px)" (update-size model "md"))
      (.register "screen and (min-width: 1025px)"                        (update-size model "lg"))))
```

Now, by running `watch-screen-size` at startup, we can check the value of the
`screen-size` variable before rendering a component, making an AJAX call, or
even deciding which URL to call. The server API can be made smart about having
different endpoints with appropriate payloads based on the screen size.

### Here's an example

The above code snippet was taken from an app that needs to display an SVG
county map of the United States using D3 (look
[here](http://neo.com/2014/04/21/choropleths-and-d3js) for more on that). The
county map contains over 3000 SVG elements, so it rendered and updated
painfully slowly on devices such as tablets or phones. We used CSS to scale the
SVG to fit the screen better, but we had to find a quicker, more efficient way
to display the map details.

Having a handle on the `screen-size` variable allowed us to do that. Instead of
rendering the county map, we now render a state map for smaller screens. This
brought the number of data points down by orders of magnitude, making the
render much more snappy.

The data to be displayed on the map also changes based on a selected month. On
larger screens, this month selector appears as a slider, but on smaller screens
we render a simple select list instead. We could render them both all the time
and selectively shown one over the other, but knowing the screen size allows us
to not have to do that.

## Conclusion

Media queries with CSS can only take you so far. For a static site that doesn't
make a lot of AJAX calls, this may be all you need. But with a rich client-side
application, knowing the size of your user's screen is as valuable as any other
user input that your app can react to.
