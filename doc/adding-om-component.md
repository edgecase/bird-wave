# Adding a Component to a ClojureScript/Om Application -- a Walkthrough

[Birdwave](http://birdwave.neo.com) is a heavily data-driven app, which makes
its UI an excellent candidate to structure into Om components. In this article,
we'll add a new component to the app, and try to understand some of its inner
workings in the process.

## Intent of the Component

Birdwave displays month-to-month changes in the sightings of a selected species
of bird. Currently, the user can change the selected month in two ways,
depending on their device size (as noted in [this post](http://www.neo.com/2014/10/03/responsive-javascript-with-enquirejs)).
On larger screens there's a month slider, and on smaller screens there's a
select box with the list of available months. It would be nice to have '+' and
'-' buttons on either side of the select box for the user to go to the previous
or next month without having to open the select box each time. This is exactly
what we'll add in this article.

[Before screenshot]
[After screenshot]

## Setup

The date selector component currently looks like this:

```clojure
(defn date-select [model owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:id "slider"}
        (dom/div #js {:id "date-select"}
          (apply dom/select #js
                 {:value (:time-period model)
                  :onChange #(put! (om/get-state owner :time-period-ch) (.. % -target -value))}
                 (map #(dom/option #js {:value %} (month-name %)) dates)))))))
```

If you look at line 7 through 10, the component is responsible for generating a
select element whose value is the currently selected time period. When a change
event happens, the handler puts the new value on a /core.async/ channel,
/time-period-ch/, which is responsible for relaying the changes to the app
state, called /model/.

The available dates are stored as strings in the format
"YYYY/MM" in a vector named /dates/:

```clojure
(def dates #js ["2012/12" "2013/01" "2013/02" "2013/03" "2013/04" "2013/05"
                "2013/06" "2013/07" "2013/08" "2013/09" "2013/10" "2013/11"])
```

When a user taps on the '+', our objective is to take the current date, find
the next date in the /dates/ vector and push it onto the channel. Similarly,
for the '-' button, we find the previous date and push it onto the channel.

## Implementation

We can add our /date-plus/ component as a sibling to the select element, like
so (only showing lines 7 and after from the above snippet):

```clojure
...
(apply dom/select #js
       {:value (:time-period model)
        :onChange #(put! (om/get-state owner :time-period-ch) (.. % -target -value))}
       (map #(dom/option #js {:value %} (month-name %)) dates)))
(om/build date-plus (:time-period model) {:state {:time-period-ch (om/get-state owner :time-period-ch)}})
...
```

We know that /date-plus/ needs access to the /time-period-ch/, but since it
is part of the app's internal state, it needs to be passed in as local state to
the component. Now we can build the component itself:

```clojure
(defn date-plus [model owner]
  (reify
    om/IRender
    (render [_]
      (dom/span #js {:className "plus"
                     :onClick #(update-month! model owner)} "+"))))
```
As you can see, it's a simple span with the + text in it, and a click handler
which calls off to an /update-month!/ function. This is what that function
looks like:

```clojure
(defn update-month! [model owner]
  (let [current-position (.indexOf dates model)
        next-month (get dates (inc (js/parseInt current-position)))
        time-period-ch (om/get-state owner :time-period-ch)]
    (when-not (nil? next-month) (put! time-period-ch next-month))))
```
The function does 3 things:

* finds the next month in the dates array by incrementing the index of the
  current month (which is the model that was passed in)
* retrieves the /time-period-ch/ channel from the state set on the component
* puts the next month on the channel

It checks for nil in case the current month is the last in the array, in which
case there's nothing to do.

## Adding the '-' button

The '-' button works the exact same way, the only difference being that it
needs to *decrement* the index of the current month. We can make this happen
with a simple change to our /update-month!/ function, by letting it take a
function as a parameter in addition to /model/ and /owner/:

```clojure
(defn update-month! [model owner func]
  (let [current-position (.indexOf dates model)
        next-month (get dates (func (js/parseInt current-position)))
        time-period-ch (om/get-state owner :time-period-ch)]
    (when-not (nil? next-month) (put! time-period-ch next-month))))
```

The function is called on the current month's index to return the next month's
index. In case of /date-plus/, we pass in the /inc/ function, and for
/date-minus/, we pass in /dec/. This is what they look like now:

```clojure
(defn date-plus [model owner]
  (reify
    om/IRender
    (render [_]
      (dom/span #js {:id "date-plus"
                     :onClick #(update-month! model owner inc)} "+"))))

(defn date-minus [model owner]
  (reify
    om/IRender
    (render [_]
      (dom/span #js {:id "date-minus"
                     :onClick #(update-month! model owner dec)} "-"))))
```

And here's the final date selector:

```clojure
(defn date-select [model owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:id "slider"}
        (dom/div #js {:id "date-select"}
          (om/build date-minus (:time-period model) {:state {:time-period-ch (om/get-state owner :time-period-ch)}})
          (apply dom/select #js
                 {:value (:time-period model)
                  :onChange #(put! (om/get-state owner :time-period-ch) (.. % -target -value))}
                 (map #(dom/option #js {:value %} (month-name %)) dates))
          (om/build date-plus (:time-period model) {:state {:time-period-ch (om/get-state owner :time-period-ch)}}))))))
```

### Conclusion

And that's it! Om's convention of isolating state makes it easy to build new
interactive components with very little code (about 20 additional lines for
both the + and - buttons).
