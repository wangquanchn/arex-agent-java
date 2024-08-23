package io.arex.inst.runtime.util;

public class SkipResult {
    private final boolean skip;
    private String httpPath;
    private String ruleId;

    private SkipResult(boolean skip) {
        this.skip = skip;
    }

    public SkipResult(boolean skip, String ruleId, String httpPath) {
        this.skip = skip;
        this.ruleId = ruleId;
        this.httpPath = httpPath;
    }

    public boolean isSkip() {
        return skip;
    }

    public String getHttpPath() {
        return httpPath;
    }

    public String getRuleId() {
        return ruleId;
    }

    public static SkipResult build(boolean skip) {
        return new SkipResult(skip);
    }

    public static SkipResult build(boolean skip, String ruleId, String httpPath) {
        return new SkipResult(skip, ruleId, httpPath);
    }

    public static SkipResult skip() {
        return new SkipResult(true);
    }

    public static SkipResult notSkip() {
        return new SkipResult(false);
    }

}
