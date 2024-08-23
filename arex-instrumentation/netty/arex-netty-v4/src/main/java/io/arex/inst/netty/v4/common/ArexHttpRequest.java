package io.arex.inst.netty.v4.common;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArexHttpRequest {

    private String uri;
    private String method;
    private String path;
    private Map<String, String[]> parameterMap;
    private Map<String, String> headers;
    private String requestBody;

    public ArexHttpRequest(HttpRequest request) {
        this.uri = request.getUri();
        this.method = request.getMethod().name();
        QueryStringDecoder decoder = new QueryStringDecoder(request.getUri());
        this.path = decoder.path();

        Map<String, List<String>> parameters = decoder.parameters();
        if (parameters != null && !parameters.isEmpty()) {
            this.parameterMap = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
                List<String> values = entry.getValue();
                if (values != null && !values.isEmpty()) {
                    this.parameterMap.put(entry.getKey(), values.toArray(new String[0]));
                }
            }
        }
        this.headers = NettyHelper.parseHeaders(request.headers());
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Map<String, String[]> getParameterMap() {
        return parameterMap;
    }

    public void setParameterMap(Map<String, String[]> parameterMap) {
        this.parameterMap = parameterMap;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }
}
