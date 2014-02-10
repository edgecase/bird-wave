// Compiled by ClojureScript 0.0-2156
goog.provide('bird_man.client');
goog.require('cljs.core');
goog.require('clojure.browser.repl');
goog.require('clojure.browser.repl');
bird_man.client.start_client = (function start_client(){return clojure.browser.repl.connect.call(null,"http://localhost:9000/repl");
});
goog.exportSymbol('bird_man.client.start_client', bird_man.client.start_client);
bird_man.client.svg_dim = new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"width","width",1127031096),950,new cljs.core.Keyword(null,"height","height",4087841945),500], null);
bird_man.client.svg = d3.select("body").append("svg").attr("height",new cljs.core.Keyword(null,"height","height",4087841945).cljs$core$IFn$_invoke$arity$1(bird_man.client.svg_dim)).attr("width",new cljs.core.Keyword(null,"width","width",1127031096).cljs$core$IFn$_invoke$arity$1(bird_man.client.svg_dim));
bird_man.client.path = d3.geo.path();
bird_man.client.draw = (function draw(err,us){bird_man.client.svg.append("g").selectAll("path").data((topojson.feature(us,(us["objects"]["counties"]),(function (p1__8514_SHARP_,p2__8515_SHARP_){return !(cljs.core._EQ_.call(null,p1__8514_SHARP_,p2__8515_SHARP_));
}))["features"])).enter().append("path").classed("county",true).attr("d",bird_man.client.path);
return bird_man.client.svg.append("path").datum(topojson.mesh(us,(us["objects"]["states"]))).classed("states",true).attr("d",bird_man.client.path);
});
queue().defer(d3.json,"data/us.json").await(bird_man.client.draw);
