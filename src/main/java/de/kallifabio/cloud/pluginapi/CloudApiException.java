package de.kallifabio.cloud.pluginapi;

public class CloudApiException extends RuntimeException {

    private final int statusCode;
    private final String endpoint;
    private final String responseBody;

    public CloudApiException(String message, int statusCode, String endpoint, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.endpoint = endpoint;
        this.responseBody = responseBody;
    }

    public CloudApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.endpoint = null;
        this.responseBody = null;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
