package io.arex.inst.runtime.model;

public class RecordRuleMatchResult {

    private boolean match;
    private String urlRuleId;
    private String paramRuleId;

    private RecordRuleMatchResult(boolean match) {
        this.match = match;
    }

    private RecordRuleMatchResult(String urlRuleId) {
        this.match = true;
        this.urlRuleId = urlRuleId;
    }

    private RecordRuleMatchResult(String urlRuleId, String paramRuleId) {
        this.match = true;
        this.urlRuleId = urlRuleId;
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

    public void setUrlRuleId(String urlRuleId) {
        this.urlRuleId = urlRuleId;
    }

    public String getParamRuleId() {
        return paramRuleId;
    }

    public void setParamRuleId(String paramRuleId) {
        this.paramRuleId = paramRuleId;
    }

    public static RecordRuleMatchResult notMatched() {
        return new RecordRuleMatchResult(false);
    }

    public static RecordRuleMatchResult matched(String urlRuleId) {
        return new RecordRuleMatchResult(urlRuleId);
    }

    public static RecordRuleMatchResult matched(String urlRuleId, String paramRuleId) {
        return new RecordRuleMatchResult(urlRuleId, paramRuleId);
    }

    public String getTokenBucketKey() {
        return paramRuleId != null ? paramRuleId : urlRuleId;
    }
}
