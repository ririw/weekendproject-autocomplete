/**
 * Created by riri on 22/06/14.
 */

function SearchController($scope, $http) {
    $scope.suggestions = [];
    $scope.timer = null;
    searchTimer = 250;

    $scope.newsearch = function(){
        var search = $scope.search;
        if (search != undefined && search != "") {
            if ($scope.timer != null) window.clearTimeout($scope.timer);
            $scope.timer = window.setTimeout(function(){$scope.sendsearch(search)}, searchTimer);
        } else {
            $scope.suggestions = [];
        }
    };

    $scope.showsearch = function(){
        return $scope.suggestions.length > 0;
    };

    $scope.sendsearch = function(search) {
        $http.get('http://localhost:8080/search', { params: {q: search} }).success(
        function(data, status, headers, config){
            $scope.suggestions = data.results
        });
    };
    $scope.usesuggestion = function($event){
        $scope.search = $event.currentTarget.textContent;
        $scope.sendsearch($scope.search);
    }
}

