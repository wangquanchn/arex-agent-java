package io.arex.inst.runtime.listener;

public class EventSource {
    private final String caseId;
    private final String excludeMockTemplate;
    private String httpPath;
    private String ruleId;
    private static final EventSource EMPTY = new EventSource(null, null);

    private EventSource(String caseId, String excludeMockTemplate) {
        this.caseId = caseId;
        this.excludeMockTemplate = excludeMockTemplate;
    }

    public static EventSource of(String caseId, String excludeMockTemplate){
        return new EventSource(caseId, excludeMockTemplate);
    }

    public static EventSource empty() {
        return EMPTY;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getExcludeMockTemplate() {
        return excludeMockTemplate;
    }

    public String getHttpPath() {
        return httpPath;
    }

    public void setHttpPath(String httpPath) {
        this.httpPath = httpPath;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

}
