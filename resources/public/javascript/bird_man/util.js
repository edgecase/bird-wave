// Compiled by ClojureScript 0.0-2156
goog.provide('bird_man.util');
goog.require('cljs.core');
bird_man.util.debounce = (function debounce(func,wait,immediate){var timeout = cljs.core.atom.call(null,null);return (function (){var this$ = this;var context = this$;var args = arguments;var later = ((function (context,args){
return (function (){cljs.core.reset_BANG_.call(null,timeout,null);
if(cljs.core.truth_(immediate))
{return null;
} else
{return func.apply(context,args);
}
});})(context,args))
;if(cljs.core.truth_((function (){var and__7774__auto__ = immediate;if(cljs.core.truth_(and__7774__auto__))
{return cljs.core.not.call(null,cljs.core.deref.call(null,timeout));
} else
{return and__7774__auto__;
}
})()))
{func.apply(context,args);
} else
{}
clearTimeout(cljs.core.deref.call(null,timeout));
return cljs.core.reset_BANG_.call(null,timeout,setTimeout(later,wait));
});
});
goog.exportSymbol('bird_man.util.debounce', bird_man.util.debounce);
