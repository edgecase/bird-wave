// Compiled by ClojureScript 0.0-2156
goog.provide('bird_man.client');
goog.require('cljs.core');
goog.require('clojure.string');
goog.require('clojure.string');
goog.require('clojure.browser.repl');
goog.require('clojure.browser.repl');
bird_man.client.svg_dim = new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"width","width",1127031096),950,new cljs.core.Keyword(null,"height","height",4087841945),500], null);
bird_man.client.svg = d3.select("body").append("svg").attr("height",new cljs.core.Keyword(null,"height","height",4087841945).cljs$core$IFn$_invoke$arity$1(bird_man.client.svg_dim)).attr("width",new cljs.core.Keyword(null,"width","width",1127031096).cljs$core$IFn$_invoke$arity$1(bird_man.client.svg_dim));
bird_man.client.path = d3.geo.path();
bird_man.client.color = d3.scale.threshold().domain(Array(0.5,1.0,1.5,2.0,2.5)).range(Array("#f2f0f7","#dadedb","#bcbddc","#9e9ac8","#756bb1","#54278f"));
bird_man.client.freq_by_county = cljs.core.atom.call(null,cljs.core.PersistentArrayMap.EMPTY);
bird_man.client.build_key = (function build_key(state,county){return cljs.core.apply.call(null,cljs.core.str,cljs.core.interpose.call(null,"-",new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [state,county], null)));
});
bird_man.client.populate_freqs = (function populate_freqs(stats){var seq__9212 = cljs.core.seq.call(null,stats);var chunk__9213 = null;var count__9214 = 0;var i__9215 = 0;while(true){
if((i__9215 < count__9214))
{var s = cljs.core._nth.call(null,chunk__9213,i__9215);cljs.core.swap_BANG_.call(null,bird_man.client.freq_by_county,cljs.core.assoc,bird_man.client.build_key.call(null,(s["state"]),(s["county"])),((s["total"]) / (s["sightings"])));
{
var G__9216 = seq__9212;
var G__9217 = chunk__9213;
var G__9218 = count__9214;
var G__9219 = (i__9215 + 1);
seq__9212 = G__9216;
chunk__9213 = G__9217;
count__9214 = G__9218;
i__9215 = G__9219;
continue;
}
} else
{var temp__4092__auto__ = cljs.core.seq.call(null,seq__9212);if(temp__4092__auto__)
{var seq__9212__$1 = temp__4092__auto__;if(cljs.core.chunked_seq_QMARK_.call(null,seq__9212__$1))
{var c__8534__auto__ = cljs.core.chunk_first.call(null,seq__9212__$1);{
var G__9220 = cljs.core.chunk_rest.call(null,seq__9212__$1);
var G__9221 = c__8534__auto__;
var G__9222 = cljs.core.count.call(null,c__8534__auto__);
var G__9223 = 0;
seq__9212 = G__9220;
chunk__9213 = G__9221;
count__9214 = G__9222;
i__9215 = G__9223;
continue;
}
} else
{var s = cljs.core.first.call(null,seq__9212__$1);cljs.core.swap_BANG_.call(null,bird_man.client.freq_by_county,cljs.core.assoc,bird_man.client.build_key.call(null,(s["state"]),(s["county"])),((s["total"]) / (s["sightings"])));
{
var G__9224 = cljs.core.next.call(null,seq__9212__$1);
var G__9225 = null;
var G__9226 = 0;
var G__9227 = 0;
seq__9212 = G__9224;
chunk__9213 = G__9225;
count__9214 = G__9226;
i__9215 = G__9227;
continue;
}
}
} else
{return null;
}
}
break;
}
});
bird_man.client.freq_color = (function freq_color(data){var p = (data["properties"]);var st = [cljs.core.str("US-"),cljs.core.str((p["state"]))].join('');var cty = cljs.core.first.call(null,clojure.string.split.call(null,(p["county"])," "));var keystr = bird_man.client.build_key.call(null,st,cty);return bird_man.client.color.call(null,cljs.core.deref.call(null,bird_man.client.freq_by_county).call(null,keystr));
});
bird_man.client.draw = (function draw(err,us,results){bird_man.client.populate_freqs.call(null,results);
bird_man.client.svg.append("g").selectAll("path").data((topojson.feature(us,(us["objects"]["counties"]),(function (p1__9228_SHARP_,p2__9229_SHARP_){return !(cljs.core._EQ_.call(null,p1__9228_SHARP_,p2__9229_SHARP_));
}))["features"])).enter().append("path").style("fill",bird_man.client.freq_color).classed("county",true).attr("d",bird_man.client.path);
return bird_man.client.svg.append("path").datum(topojson.mesh(us,(us["objects"]["states"]))).classed("states",true).attr("d",bird_man.client.path);
});
bird_man.client.draw_map = (function draw_map(){return queue().defer(d3.json,"data/us.json").defer(d3.json,"species/European Starling").await(bird_man.client.draw);
});
bird_man.client.start_client = (function start_client(){bird_man.client.draw_map.call(null);
return clojure.browser.repl.connect.call(null,"http://localhost:9000/repl");
});
goog.exportSymbol('bird_man.client.start_client', bird_man.client.start_client);
