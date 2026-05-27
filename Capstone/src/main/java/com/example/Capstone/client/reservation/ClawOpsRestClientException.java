package com.example.Capstone.client.reservation;

public class ClawOpsRestClientException extends RuntimeException {

    public enum ErrorType {
        HTTP_ERROR,
        TIMEOUT,
        NETWORK_ERROR
    }

    private final ErrorType errorType;
    private final Integer httpStatusCode;
    private final String method;
    private final String path;
    private final String responseBodySummary;

    private ClawOpsRestClientException(
            ErrorType errorType,
            Integer httpStatusCode,
            String method,
            String path,
            String responseBodySummary,
            Throwable cause
    ) {
        super(safeMessage(errorType, httpStatusCode, method, path, responseBodySummary), cause);
        this.errorType = errorType;
        this.httpStatusCode = httpStatusCode;
        this.method = method;
        this.path = path;
        this.responseBodySummary = responseBodySummary;
    }

    public static ClawOpsRestClientException httpError(
            int httpStatusCode,
            String method,
            String path,
            String responseBodySummary,
            Throwable cause
    ) {
        return new ClawOpsRestClientException(
                ErrorType.HTTP_ERROR,
                httpStatusCode,
                method,
                path,
                responseBodySummary,
                cause
        );
    }

    public static ClawOpsRestClientException timeout(
            String method,
            String path,
            Throwable cause
    ) {
        return new ClawOpsRestClientException(
                ErrorType.TIMEOUT,
                null,
                method,
                path,
                null,
                cause
        );
    }

    public static ClawOpsRestClientException networkError(
            String method,
            String path,
            Throwable cause
    ) {
        return new ClawOpsRestClientException(
                ErrorType.NETWORK_ERROR,
                null,
                method,
                path,
                null,
                cause
        );
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getResponseBodySummary() {
        return responseBodySummary;
    }

    private static String safeMessage(
            ErrorType errorType,
            Integer httpStatusCode,
            String method,
            String path,
            String responseBodySummary
    ) {
        StringBuilder builder = new StringBuilder("ClawOps REST client failed");
        builder.append(" type=").append(errorType);
        if (httpStatusCode != null) {
            builder.append(" httpStatus=").append(httpStatusCode);
        }
        builder.append(" method=").append(method);
        builder.append(" path=").append(path);
        if (responseBodySummary != null && !responseBodySummary.isBlank()) {
            builder.append(" body=").append(responseBodySummary);
        }
        return builder.toString();
    }
}
