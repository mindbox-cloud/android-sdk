(function() {
    var oldFetch = window.fetch;
    window.fetch = function(input, init) {
        if (init && init.method === 'POST' && init.body) {
            SDK.sendXHRRequestData(input, init.body);
        }
        return oldFetch.apply(this, arguments);
    };
})();