package com.nixk.recruit.service;

/**
 * The single HTTP door the evidence verifiers use. Kept as an interface so tests can
 * substitute canned responses instead of hitting GitHub / LeetCode / profile sites.
 */
public interface HttpGateway {
    /**
     * @param url absolute https URL to GET
     * @return the response, with {@code status = -1} when the request could not be
     *         completed at all (DNS failure, timeout, connection refused)
     */
    Response get(String url);

    /**
     * @param jsonBody request body, sent as application/json
     * @return same convention as {@link #get(String)}
     */
    Response postJson(String url, String jsonBody);

    record Response(int status, String body) {
        public boolean unreachable() {
            return status < 0;
        }
    }
}
