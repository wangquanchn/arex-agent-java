package io.arex.inst.runtime.model;

public class RecordRuleMatchResult {

    private boolean match;
    private String urlRuleId;;
    private String httpPath;
    private String paramRuleId;

    private RecordRuleMatchResult(boolean match) {
        this.match = match;
    }

    private RecordRuleMatchResult(String urlRuleId, String httpPath) {
        this.match = true;
        this.urlRuleId = urlRuleId;
        this.httpPath = httpPath;
    }

    private RecordRuleMatchResult(String urlRuleId, String httpPath, String paramRuleId) {
        this.match = true;
        this.urlRuleId = urlRuleId;
        this.httpPath = httpPath;
        this.paramRuleId = paramRuleId;
    }

    public boolean isMatch() {
        return match;
    }

    public void setMatch(boolean match) {
        this.match = match;
    }

    public String getUrlRuleId() {
        return urlRuleId;
    }

    public String getHttpPath() {
        return httpPath;
    }

    public String getParamRuleId() {
        return paramRuleId;
    }

    public static RecordRuleMatchResult notMatched() {
        return new RecordRuleMatchResult(false);
    }

    public static RecordRuleMatchResult matched(String urlRuleId, String httpPath) {
        return new RecordRuleMatchResult(urlRuleId, httpPath);
    }

    public static RecordRuleMatchResult matched(String urlRuleId, String httpPath, String paramRuleId) {
        return new RecordRuleMatchResult(urlRuleId, httpPath, paramRuleId);
    }

    public String getTokenBucketKey() {
        return paramRuleId != null ? paramRuleId : urlRuleId;
    }
}
