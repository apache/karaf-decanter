define([
  'angular',
  'lodash',
  'underscore.string',
  'simple_statistics'
],
function (angular, _, str, ss) {
  'use strict';

  var module = angular.module('kibana.services');

  module.service('monitor', function() {

    var self = this;

    this.check = function(data, title, threshold) {
      var ret, latestId;
      latestId = _.max(_.keys(data));
      if (threshold) {
        ret = data[latestId] - threshold > 0 ? true : false;
      } else {
        ret = detect(data);
      };
      if (ret) {
        self.notify(title, data[latestId]);
      };
    };

    var detect = function(data) {
      var timeSeries = _.pairs(data);
      var duration = (new Date().getTime() - parseInt(timeSeries[0][0]))/3600000;
      if (duration < 1 ) {
        return false;
      }
      var count = 0;
      _.each([grubbs, histogram_bins, first_hour_average, stddev_from_average, stddev_from_moving_average, mean_subtraction_cumulation, median_absolute_deviation, least_squares], function(f) {
        if (f(timeSeries)) {
          count += 1;
        };
      }); 
      if (count > 5) {
        return true;
      } else {
        return false;
      }
    };

    // ema from Gauss.js
    var ema = function(vector, period) {
      var ratio = 2 / (period + 1);
      var sum = ss.sum(vector.slice(0, period));
      var ema = [sum / period];
      for (var i = 1; i < vector.length - period + 1; i++) {
        ema.push(
          ratio * (vector[i + period - 1] - ema[i - 1]) + ema[i - 1]
        );
      }
      return ema;
    };

    // Perl5's Statistics::Distributions
    var SIGNIFICANT = 5;
    var _subu = function(p) {
      var y = 0 - Math.log(4 * p * (1 - p));
      var x = Math.sqrt(
        y * (1.570796288
        + y * (.03706987906
          + y * (-.8364353589E-3
          + y * (-.2250947176E-3
            + y * (.6841218299E-5
            + y * (0.5824238515E-5
              + y * (-.104527497E-5
              + y * (.8360937017E-7
                + y * (-.3231081277E-8
                + y * (.3657763036E-10
                  + y *.6936233982E-12)))))))))));
      if (p>0.5) {
        x = -x;
      };
      return x;
    };
    var _subt = function(n, p) {
      if (p >= 1 || p <= 0) {
        alert("Invalid p: "+p);
      }
      if (p == 0.5) {
        return 0;
      } else if (p < 0.5) {
        return 0 - _subt(n, 1 - p);
      }
 
      var u = _subu(p);
      var u2 = Math.pow(u, 2);
      var a = (u2 + 1) / 4;
      var b = ((5 * u2 + 16) * u2 + 3) / 96;
      var c = (((3 * u2 + 19) * u2 + 17) * u2 - 15) / 384;
      var d = ((((79 * u2 + 776) * u2 + 1482) * u2 - 1920) * u2 - 945) / 92160;
      var e = (((((27 * u2 + 339) * u2 + 930) * u2 - 1782) * u2 - 765) * u2 + 17955) / 368640;
      var x = u * (1 + (a + (b + (c + (d + e / n) / n) / n) / n) / n);
      if (n <= Math.pow((Math.log(p)/Math.log(10)), 2) + 3) {
        var round;
        do { 
          var p1 = _subtprob(n, x);
          var n1 = n + 1;
          var delta = (p1 - p) 
          / Math.exp((n1 * Math.log(n1 / (n + x * x)) 
          + Math.log(n/n1/2/Math.PI) - 1 
          + (1/n1 - 1/n) / 6) / 2);
          x += delta;
          round = str.sprintf("%."+Math.abs(parseInt((Math.log(Math.abs(x))/Math.log(10)) -4))+"f", delta);
        } while ((x) && (round != 0));
      }
      return x;
    };
    var _subtprob = function(n, x) {
      var a, b;
      var w = Math.atan2(x / Math.sqrt(n), 1);
      var z = Math.pow(Math.cos(w), 2);
      var y = 1;
 
      for (var i = n-2; i >= 2; i -= 2) {
        y = 1 + (i-1) / i * z * y;
      } 
 
      if (n % 2 == 0) {
        a = Math.sin(w)/2;
        b = 0.5;
      } else {
        a = (n == 1) ? 0 : Math.sin(w)*Math.cos(w)/Math.PI;
        b= 0.5 + w/Math.PI;
      }
      return _.max(0, 1 - b - a * y);
    };
    var precision = function(x) {
      return Math.abs(parseInt((Math.log(Math.abs(x))/Math.log(10)) - SIGNIFICANT));
    };
    var precision_string = function(x) {
      if (x) {
        return str.sprintf("%." + precision(x) + "f", x);
      } else {
        return "0";
      }
    };

    var tdistr = function(p, df) {
      if (df <= 0 || Math.abs(df) - Math.abs(parseInt(df)) != 0) {
        alert("Invalid df: "+df);
      }
      if (p <= 0 || p >= 1) {
        alert("Invalid p: "+p);
      }
      return precision_string(_subt(df, p));
    };

    //Etsy's Skyline
    var grubbs = function(timeSeries) {
      var vector = _.map(timeSeries, function(item) {return item[1]});
      var mean = ss.mean(vector);
      var stdDev = ss.standard_deviation(vector);
      var z_score = ss.z_score(vector, mean, stdDev);
      var len_series = _.size(vector);
      var threshold = tdistr( 0.05/(2*len_series), len_series-2 );
      var threshold_squared = threshold * threshold;
      var grubbs_score = ( ( len_series - 1 ) / Math.sqrt(len_series) ) * Math.sqrt( threshold_squared / ( len_series - 2 + threshold_squared ) );
      return z_score > grubbs_score;
    };

    var histogram_bins = function(timeSeries) {
      var vector = _.map(timeSeries, function(item) {return item[1]});
      var min = ss.min(vector);
      var max = ss.max(vector);
      var bins = parseInt((max - min) / 15);
      var hist = _.range(min, max, bins);
      var histogram = {};
      var last = _.last(vector);
      var lasthis;
      _.each(vector, function(i) {
        var his = _.first(_.first(hist, function(h) {
          return i > h;
        }));
        histogram[his]++;
        if( i === last ) {
          lasthis = histogram[his];
        };
      });
      return lasthis <= 20;
    };

    var first_hour_average = function(timeSeries) {
      var firstHour = parseInt(timeSeries[0][0]) + 3600000;
      var fvector = _.map(timeSeries, function(item) {
        if (item[0] < firstHour) {
          return item[1];
        };
      });
      return Math.abs(parseInt(_.last(timeSeries)[1]) - ss.mean(fvector))  > 3 * ss.standard_deviation(fvector);
    };

    var stddev_from_average = function(timeSeries) {
      var vector = _.map(timeSeries, function(item) {return item[1]});
      return Math.abs(parseInt(_.last(vector)) - ss.mean(vector))  > 3 * ss.standard_deviation(vector);
    };

    var stddev_from_moving_average = function(timeSeries) {
      var vector = _.map(timeSeries, function(item) {return item[1]});
      var mvector = ema(vector, 10);
      return Math.abs(parseInt(_.last(vector)) - _.last(mvector))  > 3 * mvector.stdev;
    };

    var mean_subtraction_cumulation = function(timeSeries) {
      var vector = _.map(timeSeries, function(item) {return item[1]});
      var mean = ss.mean(vector);
      vector = _.map(vector, function(i) { return i - mean });
      return Math.abs(_.last(vector))  > 3 * ss.standard_deviation(vector);
    };

    var median_absolute_deviation = function(timeSeries) {
      var vector = _.map(timeSeries, function(item) {return item[1]});
      var median = ss.median(vector);
      vector = _.map(vector, function(i) { return Math.abs(i - median) });
      return _.last(vector)  > 6 * ss.median(vector);
    };

    var least_squares = function(timeSeries) {
      var y = ss.linear_regression().data(timeSeries).line();
      var yvector = _.map(timeSeries, function(i) {
        return y(i[0]);
      });
      return Math.abs(_.last(yvector)) > 3 * ss.standard_deviation(yvector);
    };

    this.notify = function(title, msg) {
      if(!("Notification" in window)){
        console.log("not support notification");
      }
      else if (Notification.permission === 'granted') {
        var notification = new Notification(title, {
          body: msg,
          dir : 'rtl',
          lang: 'zh-guoyu',
        });
        notification.onshow = function() {
          setTimeout(notification.close, 1000);
        };
      }
      else if (Notification.permission !== 'denied') {
        Notification.requestPermission(function(permission){
          if (!('permission' in Notification)) {
            Notification.permission = permission;
          }
          if (permission === 'granted') {
            var notification = new Notification("Notify begin", {
              body: 'kibana histogram panel notification'
            });
          }
        });
      };
    };

  });

});
