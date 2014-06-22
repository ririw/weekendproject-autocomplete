/**
 * Created by riri on 22/06/14.
 *
 * This is used around the search element
 */

function SearchController($scope, $http) {
    $scope.suggestions = [];
    $scope.timer = null;
    $scope.socket = new WebSocket("ws://localhost:8080");
    $scope.socket.onmessage = function(event) {
        var data = JSON.parse(event.data);
        $scope.suggestions = data.results;
    };
    var search_timer = 100;


    /**
     * This is called whenever the search field is updated.
     * It sets a timeout to call the send_search function, which
     * is the one that does an AJAX call back to the server,
     * set for 250 ms from now.
     *
     * Whenever it is called, it tries to cancel the last-set timer,
     * meaning the search will only fire 250ms after nothing has
     * happened.
     *
     * If there is nothing in the search box, then the suggestions are
     * immediately set to nothing
     */
    $scope.new_search = function(){
        var search = $scope.search;
        if (search != undefined && search != "") {
            if ($scope.timer != null) window.clearTimeout($scope.timer);
            $scope.timer = window.setTimeout(function(){$scope.send_search(search)}, search_timer);
        } else {
            $scope.suggestions = [];
        }
    };

    /**
     * This decides whether or not to even show the suggestions.
     * It checks if the suggestions list has anything in it.
     * @returns {boolean}
     */
    $scope.show_search = function(){
        return $scope.suggestions.length > 0;
    };

    /**
     * Send a search to the server for suggesting...
     * @param search
     */
    $scope.send_search = function(search) {
        if ($scope.socket.readyState = WebSocket.OPEN) {
            $scope.socket.send(search)
        } else {
            console.log("Using HTTP");
            $http.get('http://localhost:8080/search', { params: {q: search} }).success(
                function (data, status, headers, config) {
                    $scope.suggestions = data.results
                });
        }
    };

    /**
     * Use a suggestion - this waits for a mouse-click,
     * and then uses it to set the text box value to that
     * clicked value, and in doing so, Angular will update
     * everything as appropriate
     * @param $event an event - this method will use $event.currentTarget.textContent
     * as the value to set the search to.
     */
    $scope.use_suggestion = function($event){
        $scope.search = $event.currentTarget.textContent;
        $scope.send_search($scope.search);
    };

    $scope.refresh = function() {
        console.log("Restarting connection");
        if ($scope.socket != undefined) $scope.socket.close();
        $scope.socket = new WebSocket("ws://localhost:8080");
        $scope.socket.onmessage = function(event) {
            var data = JSON.parse(event.data);
            $scope.suggestions = data.results;
        };
    }
}

