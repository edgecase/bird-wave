// Compiled by ClojureScript 0.0-2156
goog.provide('bird_man.client');
goog.require('cljs.core');
goog.require('bird_man.util');
goog.require('bird_man.util');
goog.require('goog.string.format');
goog.require('goog.string.format');
goog.require('clojure.string');
goog.require('clojure.string');
goog.require('clojure.browser.repl');
goog.require('clojure.browser.repl');
bird_man.client.svg_dim = new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"width","width",1127031096),1000,new cljs.core.Keyword(null,"height","height",4087841945),600], null);
bird_man.client.slider_width = 800;
bird_man.client.svg = d3.select("body").append("div").attr("id","#map").append("svg").attr("height",new cljs.core.Keyword(null,"height","height",4087841945).cljs$core$IFn$_invoke$arity$1(bird_man.client.svg_dim)).attr("width",new cljs.core.Keyword(null,"width","width",1127031096).cljs$core$IFn$_invoke$arity$1(bird_man.client.svg_dim));
bird_man.client.slider = d3.select("body").append("div").attr("id","#slider");
bird_man.client.path = d3.geo.path();
if(typeof bird_man.client.freq_by_county !== 'undefined')
{} else
{bird_man.client.freq_by_county = cljs.core.atom.call(null,cljs.core.PersistentArrayMap.EMPTY);
}
if(typeof bird_man.client.current_taxon !== 'undefined')
{} else
{bird_man.client.current_taxon = cljs.core.atom.call(null,null);
}
if(typeof bird_man.client.current_month_yr !== 'undefined')
{} else
{bird_man.client.current_month_yr = cljs.core.atom.call(null,null);
}
bird_man.client.build_key = (function build_key(state,county){return cljs.core.apply.call(null,cljs.core.str,cljs.core.interpose.call(null,"-",new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [state,county], null)));
});
bird_man.client.populate_freqs = (function populate_freqs(stats){cljs.core.reset_BANG_.call(null,bird_man.client.freq_by_county,cljs.core.PersistentArrayMap.EMPTY);
var seq__10010 = cljs.core.seq.call(null,stats);var chunk__10011 = null;var count__10012 = 0;var i__10013 = 0;while(true){
if((i__10013 < count__10012))
{var s = cljs.core._nth.call(null,chunk__10011,i__10013);cljs.core.swap_BANG_.call(null,bird_man.client.freq_by_county,cljs.core.assoc,bird_man.client.build_key.call(null,(s["state"]),(s["county"])),((s["total"]) / (s["sightings"])));
{
var G__10014 = seq__10010;
var G__10015 = chunk__10011;
var G__10016 = count__10012;
var G__10017 = (i__10013 + 1);
seq__10010 = G__10014;
chunk__10011 = G__10015;
count__10012 = G__10016;
i__10013 = G__10017;
continue;
}
} else
{var temp__4092__auto__ = cljs.core.seq.call(null,seq__10010);if(temp__4092__auto__)
{var seq__10010__$1 = temp__4092__auto__;if(cljs.core.chunked_seq_QMARK_.call(null,seq__10010__$1))
{var c__8534__auto__ = cljs.core.chunk_first.call(null,seq__10010__$1);{
var G__10018 = cljs.core.chunk_rest.call(null,seq__10010__$1);
var G__10019 = c__8534__auto__;
var G__10020 = cljs.core.count.call(null,c__8534__auto__);
var G__10021 = 0;
seq__10010 = G__10018;
chunk__10011 = G__10019;
count__10012 = G__10020;
i__10013 = G__10021;
continue;
}
} else
{var s = cljs.core.first.call(null,seq__10010__$1);cljs.core.swap_BANG_.call(null,bird_man.client.freq_by_county,cljs.core.assoc,bird_man.client.build_key.call(null,(s["state"]),(s["county"])),((s["total"]) / (s["sightings"])));
{
var G__10022 = cljs.core.next.call(null,seq__10010__$1);
var G__10023 = null;
var G__10024 = 0;
var G__10025 = 0;
seq__10010 = G__10022;
chunk__10011 = G__10023;
count__10012 = G__10024;
i__10013 = G__10025;
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
bird_man.client.freq_for_county = (function freq_for_county(data){var p = (data["properties"]);var st = [cljs.core.str("US-"),cljs.core.str((p["state"]))].join('');var cty = cljs.core.first.call(null,clojure.string.split.call(null,(p["county"])," "));var keystr = bird_man.client.build_key.call(null,st,cty);var freq = cljs.core.deref.call(null,bird_man.client.freq_by_county).call(null,keystr);if(cljs.core.truth_(freq))
{return freq;
} else
{return 0.0;
}
});
bird_man.client.freq_duration = (function freq_duration(data){return (500 * bird_man.client.freq_for_county.call(null,data));
});
bird_man.client.color = d3.scale.quantile().domain([0,5]).range((colorbrewer.YlGnBu["9"]).reverse());
bird_man.client.months = d3.time.scale().domain([(new Date(2012,11)),(new Date(2013,11))]);
bird_man.client.month_axis = d3.svg.axis().scale(bird_man.client.months).orient("bottom").ticks(d3.time.months).tickSize(16,0).tickFormat(d3.time.format("%B"));
bird_man.client.freq_color = (function freq_color(data){return bird_man.client.color.call(null,bird_man.client.freq_for_county.call(null,data));
});
bird_man.client.update_counties = (function update_counties(results){bird_man.client.populate_freqs.call(null,results);
return d3.selectAll("path.county").transition().duration(bird_man.client.freq_duration).style("fill",bird_man.client.freq_color);
});
bird_man.client.fetch_month_data = (function fetch_month_data(slide,timestamp){var date = (new Date(timestamp));var month_yr = [cljs.core.str("/"),cljs.core.str(date.getFullYear()),cljs.core.str("/"),cljs.core.str(goog.string.format("%02d",(date.getMonth() + 1).toString()))].join('');console.log(month_yr);
if(cljs.core._EQ_.call(null,month_yr,cljs.core.deref.call(null,bird_man.client.current_month_yr)))
{return null;
} else
{cljs.core.reset_BANG_.call(null,bird_man.client.current_month_yr,month_yr);
if((cljs.core.deref.call(null,bird_man.client.current_taxon) == null))
{return null;
} else
{return d3.json([cljs.core.str("species/"),cljs.core.str(cljs.core.deref.call(null,bird_man.client.current_taxon)),cljs.core.str(cljs.core.deref.call(null,bird_man.client.current_month_yr))].join(''),bird_man.client.update_counties);
}
}
});
bird_man.client.plot = (function plot(us){bird_man.client.svg.append("g").selectAll("path").data((topojson.feature(us,(us["objects"]["counties"]),(function (p1__10026_SHARP_,p2__10027_SHARP_){return !(cljs.core._EQ_.call(null,p1__10026_SHARP_,p2__10027_SHARP_));
}))["features"])).enter().append("path").classed("county",true).attr("d",bird_man.client.path);
bird_man.client.svg.append("path").datum(topojson.mesh(us,(us["objects"]["states"]))).classed("states",true).attr("d",bird_man.client.path);
return bird_man.client.slider.call(d3.slider().axis(true).scale(bird_man.client.months).tickFormat(d3.time.format("%B")).step((((1000 * 60) * 60) * 24)).on("slide",bird_man.util.debounce.call(null,bird_man.client.fetch_month_data,500,false)));
});
bird_man.client.draw_map = (function draw_map(){return d3.json("data/us.json",bird_man.client.plot);
});
bird_man.client.start_client = (function start_client(){d3.select("select.bird").on("change",(function (){return cljs.core.reset_BANG_.call(null,bird_man.client.current_taxon,d3.event.target.value);
}));
bird_man.client.draw_map.call(null);
return clojure.browser.repl.connect.call(null,"http://localhost:9000/repl");
});
goog.exportSymbol('bird_man.client.start_client', bird_man.client.start_client);
