/*! kibana - v3.1.1 - 2014-10-02
 * Copyright (c) 2014 Rashid Khan; Licensed Apache License */

define(["module"],function(a){var b=a.config&&a.config()||{};return{load:function(a,c,d){var e=c.toUrl(a);c(["text!"+a],function(a){b.registerTemplate&&b.registerTemplate(e,a),d(a)})}}});