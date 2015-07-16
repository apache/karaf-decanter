/*

  ## query

  ### Parameters
  * query ::  A string or an array of querys. String if multi is off, array if it is on
              This should be fixed, it should always be an array even if its only
              one element
*/
define([
  'angular',
  'app',
  'lodash',
  'multiselect',

  'css!./query.css'
], function (angular, app, _) {
  'use strict';

  var module = angular.module('kibana.panels.query', []);
  app.useModule(module);

  module.controller('query', function($scope, querySrv, $rootScope, dashboard, $q, $modal, fields) {
    $scope.panelMeta = {
      status  : "Stable",
      description : "Manage all of the queries on the dashboard. You almost certainly need one of "+
        "these somewhere. This panel allows you to add, remove, label, pin and color queries"
    };

    $scope.typesSel = [];
    $scope.add_cond = false;
    $scope.defaultValue = {
      sem        : fields.list[0],
      comp_list  : ['ne', 'eq'],
      select_comp: 'eq',
      input      : ''
    };

    // Set and populate defaults
    var _d = {
      values  : [angular.copy($scope.defaultValue)],
      generate: false,
      query   : "*",
      pinned  : true,
      history : [],
      remember: 10 // max: 100, angular strap can't take a variable for items param
    };
    _.defaults($scope.panel,_d);

    $scope.querySrv = querySrv;
    $scope.dashboard = dashboard;

    // A list of query types for the query config popover
    $scope.queryTypes = querySrv.types;

    var queryHelpModal = $modal({
      template: './app/panels/query/helpModal.html',
      persist: true,
      show: false,
      scope: $scope,
    });

    $scope.showMuSelectValues = function() {
      var queryString = createQuery();
      var newQueryObj = _.clone($scope.dashboard.current.services.query.list[0]); 
      var newId = _.max($scope.dashboard.current.services.query.ids) + 1;
      newQueryObj.color = '#'+('00000'+(Math.random()*0x1000000<<0).toString(16)).slice(-6);
      newQueryObj.query = queryString;
      newQueryObj.id = newId;
      $scope.dashboard.current.services.query.list[newId] = newQueryObj;
      $scope.dashboard.current.services.query.ids.push(newId);
      $scope.refresh();
    };
 
    var createQuery = function() {
      var queryString = "_type:" + $scope.typesSel.join(',');
      _.each($scope.panel.values, function(value) {
        if( value.select_comp == 'eq' ) {
          queryString += ' AND '+value.sem+':"'+value.input+'" ';
        } else if( value.select_comp == 'ne' ) {
          queryString += ' AND NOT '+value.sem+':"'+value.input+'" ';
        } else {
          queryString += ' AND '+value.sem+':'+value.select_comp+value.input;
        }
      });
      return queryString;
    }

    $scope.selected_sem = function() {
      var item = _.last($scope.panel.values);
      var nodeInfo = $scope.ejs.getFieldMapping(dashboard.indices, item.sem);
      return nodeInfo.then(function(p) {
        var types = _.uniq(jsonPath(p, '*.*.*.*.mapping.*.type'));
        if(_.intersection(types, ['long','float','integer','double']).length > 0) {
          item.comp_list =  ['<', '>', '='];
        } else {
          item.comp_list =  ['eq', 'ne'];
        }
      });
    };
 
    $scope.addCond = function(){
      if ( $scope.add_cond == false ) {
        $scope.add_cond = true;
        return;
      };
      $scope.panel.values.push(angular.copy($scope.defaultValue));
    };

    $scope.init = function() {
      setTimeout(function(){
        $("#sela").multiselect({
            selectedList:50,
            maxHeight:300
        });
      },50);
    };

    $scope.refresh = function() {
      update_history(_.pluck($scope.dashboard.current.services.query.list,'query'));
      dashboard.refresh();
    };

    $scope.render = function() {
      $rootScope.$broadcast('render');
    };

    $scope.toggle_pin = function(id) {
      dashboard.current.services.query.list[id].pin = dashboard.current.services.query.list[id].pin ? false : true;
    };

    $scope.queryIcon = function(type) {
      return querySrv.queryTypes[type].icon;
    };

    $scope.queryConfig = function(type) {
      return "./app/panels/query/editors/"+(type||'lucene')+".html";
    };

    $scope.queryHelpPath = function(type) {
      return "./app/panels/query/help/"+(type||'lucene')+".html";
    };

    $scope.queryHelp = function(type) {
      $scope.help = {
        type: type
      };
      $q.when(queryHelpModal).then(function(modalEl) {
        modalEl.modal('show');
      });
    };

    $scope.typeChange = function(q) {
      var _nq = {
        id   : q.id,
        type : q.type,
        query: q.query,
        alias: q.alias,
        color: q.color
      };
      dashboard.current.services.query.list[_nq.id] = querySrv.defaults(_nq);
    };

    var update_history = function(query) {
      if($scope.panel.remember > 0) {
        $scope.panel.history = _.union(query.reverse(),$scope.panel.history);
        var _length = $scope.panel.history.length;
        if(_length > $scope.panel.remember) {
          $scope.panel.history = $scope.panel.history.slice(0,$scope.panel.remember);
        }
      }
    };

    $scope.init();

  });

});
