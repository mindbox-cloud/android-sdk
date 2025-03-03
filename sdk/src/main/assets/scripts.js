(function() {
    // Intercept fetch requests
    var oldFetch = window.fetch;
    window.fetch = function(input, init) {
        // If init exists and contains a body (for POST requests), capture it
        if (init && init.method === 'POST' && init.body) {
            SDK.sendXHRRequestData(input, init.body);
        }

        // Proceed with the original fetch request
        return oldFetch.apply(this, arguments);
    };
})();