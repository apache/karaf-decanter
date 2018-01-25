/** @scratch /panels/statisticstrend/0
 * == statisticstrend
 * Status: *Testing*
 *
 * A table, bar chart or pie chart based on the results of an Elasticsearch terms facet.
 *
 */
define([
  'angular',
  'app',
  'lodash',
  'jquery',
  'kbn'
],
function (angular, app, _, $, kbn) {
  'use strict';

  var module = angular.module('kibana.panels.statisticstrend', []);
  app.useModule(module);

  module.controller('statisticstrend', function($scope, kbnIndex, querySrv, dashboard, filterSrv, fields) {
    $scope.panelMeta = {
      modals : [
        {
          description: "Inspect",
          icon: "icon-info-sign",
          partial: "app/partials/inspector.html",
          show: $scope.panel.spyable
        }
      ],
      editorTabs : [
        {title:'Queries', src:'app/partials/querySelect.html'}
      ],
      status  : "Dev",
      description : "不一定准确,取决于trysize"
    };

    // Set and populate defaults
    var _d = {
      /** 
       * === Parameters
       *
       * field:: The field on which to computer the facet
       */
      mode          : 'count',
      field   : '_type',
      /** 
       * exclude:: terms to exclude from the results
       */
      missing : true,
      /** 
       * other:: Set to false to disable the display of a counter representing the aggregate of all
       * values outside of the scope of your +size+ property
       */
      other   : true,
      /**
       * size:: Show this many terms
       */
      size    : 10,
      /**
       * order:: count, term, reverse_count or reverse_term
       */
      order   : 'asce',
      style   : { "font-size": '10pt'},
      /**
       * donut:: In pie chart mode, draw a hole in the middle of the pie to make a tasty donut.
       */
      donut   : false,
      /**
       * tilt:: In pie chart mode, tilt the chart back to appear as more of an oval shape
       */
      tilt    : false,
      /**
       * lables:: In pie chart mode, draw labels in the pie slices
       */
      labels  : true,
      /**
       * arrangement:: In bar or pie mode, arrangement of the legend. horizontal or vertical
       */
      arrangement : 'horizontal',
      /**
       * chart:: table, bar or pie
       */
      chart       : 'bar',
      /**
       * spyable:: Set spyable to false to disable the inspect button
       */
      spyable     : true,
      /** @scratch /panels/statisticstrend/5
       * ==== Queries
       * queries object:: This object describes the queries to use on this panel.
       * queries.mode::: Of the queries available, which to use. Options: +all, pinned, unpinned, selected+
       * queries.ids::: In +selected+ mode, which query ids are selected.
       */
      queries     : {
        mode        : 'all',
        ids         : []
      },
    };
    _.defaults($scope.panel,_d);

    $scope.init = function () {
      $scope.hits = 0;

      $scope.$on('refresh',function(){
        $scope.get_data();
      });
      $scope.get_data();

    };

    $scope.set_refresh = function (state) {
      $scope.refresh = state;
    };

    $scope.get_data = function(firstTime) {
      // Make sure we have everything for the request to complete
      if(dashboard.indices.length === 0) {
        return;
      }
      //ctrip , copied form trends
      if (firstTime === undefined){

          // This logic can be simplifie greatly with the new kbn.parseDate
          $scope.time = filterSrv.timeRange('last');
          $scope.old_time = {
            from : new Date($scope.time.from.getTime() - kbn.interval_to_ms($scope.panel.ago)),
            to   : new Date($scope.time.to.getTime() - kbn.interval_to_ms($scope.panel.ago))
          };



          $scope.index = dashboard.indices;
          kbnIndex.indices(
            $scope.old_time.from,
            $scope.old_time.to,
            dashboard.current.index.pattern,
            dashboard.current.index.interval
          ).then(function (p) {
            $scope.index = _.union(p,$scope.index);
            $scope.get_data(true);
          });
          return;
      }


      $scope.panelMeta.loading = true;
      var request,
        results,
        boolQuery,
        queries;

      $scope.field = _.contains(fields.list,$scope.panel.field+'.raw') ?
        $scope.panel.field+'.raw' : $scope.panel.field;



      request = $scope.ejs.Request();

      $scope.panel.queries.ids = querySrv.idsByMode($scope.panel.queries);
      queries = querySrv.getQueryObjs($scope.panel.queries.ids);

      // This could probably be changed to a BoolFilter
      boolQuery = $scope.ejs.BoolQuery();
      _.each(queries,function(q) {
        boolQuery = boolQuery.should(querySrv.toEjsObj(q));
      });


      if(_.isNull($scope.panel.value_field)) {
        $scope.panel.error = "In " + $scope.panel.mode + " mode a field must be specified";
        return;
      }

      // Determine a time field
      var timeField = _.uniq(_.pluck(filterSrv.getByType('time'),'field'));
      if(timeField.length > 1) {
        $scope.panel.error = "Time field must be consistent amongst time filters";
        return;
      } else if(timeField.length === 0) {
        $scope.panel.error = "A time filter must exist for this panel to function";
        return;
      } else {
        timeField = timeField[0];
      }

      var _ids_without_time = _.difference(filterSrv.ids,filterSrv.idsByType('time'));

      if ($scope.panel.mode === 'count'){
        var facet = $scope.ejs.TermsFacet('terms').field($scope.field).global(true);
      }
      else{
        var facet = $scope.ejs.TermStatsFacet('terms').keyField($scope.field).valueField($scope.panel.value_field).global(true);
      }
      request = request
        .facet(facet
          .size($scope.panel.trysize || $scope.panel.size)
          //.order($scope.panel.order)
          .facetFilter($scope.ejs.QueryFilter(
            $scope.ejs.FilteredQuery(
              boolQuery,
              filterSrv.getBoolFilter(_ids_without_time).must(
                  $scope.ejs.RangeFilter(timeField)
                    .from($scope.time.from)
                    .to($scope.time.to)
                  )
              )))).size(0);


      // And again for the old time period
      if ($scope.panel.mode === 'count'){
        var facet = $scope.ejs.TermsFacet('oldterms').field($scope.field).global(true);
      }
      else{
        var facet = $scope.ejs.TermStatsFacet('oldterms').keyField($scope.field).valueField($scope.panel.value_field).global(true);
      }
      request = request
        .facet(facet
          .size($scope.panel.trysize || $scope.panel.size)
          //.order($scope.panel.order)
          .facetFilter($scope.ejs.QueryFilter(
            $scope.ejs.FilteredQuery(
              boolQuery,
              filterSrv.getBoolFilter(_ids_without_time).must(
                  $scope.ejs.RangeFilter(timeField)
                    .from($scope.old_time.from)
                    .to($scope.old_time.to)
                  )
              )))).size(0);


      // Populate the inspector panel
      $scope.inspector = request.toJSON();

      results = $scope.ejs.doSearch($scope.index, request);

      // Populate scope when we have results
      results.then(function(results) {

        var all_filed_values = {};
        for(var k in results.facets.terms.terms) {
            var v = results.facets.terms.terms[k];
            all_filed_values[v.term] = [v[$scope.panel.mode]];
        };

        for (var k in results.facets.oldterms.terms) {
            var v = results.facets.oldterms.terms[k];
            if (v.term in all_filed_values){
                all_filed_values[v.term].push(v[$scope.panel.mode]);
            }
        };

        //filter all_filed_values
        //for(var term in all_filed_values){
            //if (all_filed_values[term].length == 1){
                //delete all_filed_values[term];
            //}
        //}

        var l =[];
        for(var term in all_filed_values){
            if (all_filed_values[term].length !== 1 && all_filed_values[term][0]*all_filed_values[term][1]!=0){
                l.push([term].concat(all_filed_values[term]));
            }
        }
        
        for(var i in l){
            var p = l[i][2] === 0 ? 100 : 100*(l[i][1] - l[i][2])/l[i][2];
            l[i].push(p);
        }


        if ($scope.panel.order == 'asce')
            l.sort(function(x,y){return x[3]-y[3]});
        else
            l.sort(function(x,y){return y[3]-x[3]});

        l.splice($scope.panel.size);

        $scope.hits = results.hits.total;
        $scope.data = [];

        for(var i in l){
            $scope.data.push({ label : l[i][0], data : [[i,l[i][3].toFixed(2)]], extra:[l[i][1].toFixed(2),l[i][2].toFixed(2)], actions: true});
            //$scope.data.push({ label :  l[i][0], data : [[i,100]], actions: true});
        }
        
        $scope.panelMeta.loading = false;
        //var all_terms = results.facets.terms.terms;

        //for(var i = 0; i < all_terms.length; i++){
          //var v = all_terms[i];
          //if ($scope.data.length >= $scope.panel.size){
            //break;
          //}
          //if ( $scope.panel.mincount && v.count < $scope.panel.mincount){
            //continue;
          //}
          //if ( $scope.panel.maxcount && v.count > $scope.panel.maxcount){
            //continue;
          //}
          //var _d = v[$scope.panel.mode];
          //if (_d !=  _d.toFixed(2)) {
              //_d = _d.toFixed(2);
          //}
          //var slice = { label : v.term, data : [[k,_d],v.count], actions: true};
          //$scope.data.push(slice);
          //k = k + 1;
        //}


        //$scope.data.push({label:'Missing field',
          //data:[[k,results.facets.terms.missing]],meta:"missing",color:'#aaa',opacity:0});
        //$scope.data.push({label:'Other values',
          //data:[[k+1,results.facets.terms.other]],meta:"other",color:'#444'});

        $scope.$emit('render');
      });
    };

    $scope.build_search = function(term,negate) {
      if(_.isUndefined(term.meta)) {
        filterSrv.set({type:'terms',field:$scope.field,value:term.label,
          mandate:(negate ? 'mustNot':'must')});
      } else if(term.meta === 'missing') {
        filterSrv.set({type:'exists',field:$scope.field,
          mandate:(negate ? 'must':'mustNot')});
      } else {
        return;
      }
    };

    $scope.set_refresh = function (state) {
      $scope.refresh = state;
    };

    $scope.close_edit = function() {
      if($scope.refresh) {
        $scope.get_data();
      }
      $scope.refresh =  false;
      $scope.$emit('render');
    };

    $scope.showMeta = function(term) {
      if(_.isUndefined(term.meta)) {
        return true;
      }
      if(term.meta === 'other' && !$scope.panel.other) {
        return false;
      }
      if(term.meta === 'missing' && !$scope.panel.missing) {
        return false;
      }
      return true;
    };

  });

  module.directive('statisticstrendChart', function(querySrv) {
    return {
      restrict: 'A',
      link: function(scope, elem) {

        // Receive render events
        scope.$on('render',function(){
          render_panel();
        });

        // Re-render if the window is resized
        angular.element(window).bind('resize', function(){
          render_panel();
        });

        // Function for rendering panel
        function render_panel() {
          var plot, chartData;

          // IE doesn't work without this
          elem.css({height:scope.panel.height||scope.row.height});

          // Make a clone we can operate on.
          chartData = _.clone(scope.data);
          chartData = scope.panel.missing ? chartData :
            _.without(chartData,_.findWhere(chartData,{meta:'missing'}));
          chartData = scope.panel.other ? chartData :
          _.without(chartData,_.findWhere(chartData,{meta:'other'}));

          // Populate element.
          require(['jquery.flot.pie'], function(){
            // Populate element
            try {
              // Add plot to scope so we can build out own legend
              if(scope.panel.chart === 'bar') {
                plot = $.plot(elem, chartData, {
                  legend: { show: false },
                  series: {
                    lines:  { show: false, },
                    bars:   { show: true,  fill: 1, barWidth: 0.8, horizontal: false },
                    shadowSize: 1
                  },
                  yaxis: { show: true, min: 0, color: "#c8c8c8" },
                  xaxis: { show: false },
                  grid: {
                    borderWidth: 0,
                    borderColor: '#eee',
                    color: "#eee",
                    hoverable: true,
                    clickable: true
                  },
                  colors: querySrv.colors
                });
              }
              if(scope.panel.chart === 'pie') {
                var labelFormat = function(label, series){
                  return '<div ng-click="build_search(panel.field,\''+label+'\')'+
                    ' "style="font-size:8pt;text-align:center;padding:2px;color:white;">'+
                    label+'<br/>'+Math.round(series.percent)+'%</div>';
                };

                plot = $.plot(elem, chartData, {
                  legend: { show: false },
                  series: {
                    pie: {
                      innerRadius: scope.panel.donut ? 0.4 : 0,
                      tilt: scope.panel.tilt ? 0.45 : 1,
                      radius: 1,
                      show: true,
                      combine: {
                        color: '#999',
                        label: 'The Rest'
                      },
                      stroke: {
                        width: 0
                      },
                      label: {
                        show: scope.panel.labels,
                        radius: 2/3,
                        formatter: labelFormat,
                        threshold: 0.1
                      }
                    }
                  },
                  //grid: { hoverable: true, clickable: true },
                  grid:   { hoverable: true, clickable: true },
                  colors: querySrv.colors
                });
              }

              // Populate legend
              if(elem.is(":visible")){
                setTimeout(function(){
                  scope.legend = plot.getData();
                  if(!scope.$$phase) {
                    scope.$apply();
                  }
                });
              }

            } catch(e) {
              elem.text(e);
            }
          });
        }

        elem.bind("plotclick", function (event, pos, object) {
          if(object) {
            scope.build_search(scope.data[object.seriesIndex]);
          }
        });

        var $tooltip = $('<div>');
        elem.bind("plothover", function (event, pos, item) {
          if (item) {
            var value = scope.panel.chart === 'bar' ? item.datapoint[1] : item.datapoint[1][0][1];
            $tooltip
              .html(
                kbn.query_color_dot(item.series.color, 20) + ' ' +
                item.series.label + " (" + value.toFixed(0)+")"
              )
              .place_tt(pos.pageX, pos.pageY);
          } else {
            $tooltip.remove();
          }
        });

      }
    };
  });

});
