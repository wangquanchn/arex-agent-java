package io.arex.inst.runtime.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.arex.agent.bootstrap.util.CollectionUtil;
import io.arex.agent.bootstrap.util.ConcurrentHashSet;
import io.arex.inst.runtime.log.LogManager;
import io.arex.inst.runtime.config.Config;
import io.arex.inst.runtime.context.ArexContext;
import io.arex.inst.runtime.context.ContextManager;
import io.arex.agent.bootstrap.util.StringUtil;
import io.arex.inst.runtime.model.ParamRuleEntity;
import io.arex.inst.runtime.model.RecordRuleEntity;
import io.arex.inst.runtime.model.RecordRuleMatchResult;
import io.arex.inst.runtime.model.ValueRuleEntity;

import java.util.*;


public class IgnoreUtils {
    private static final String SEPARATOR_STAR = "*";

    public static final String PARAM_TYPE_QUERY_STRING = "QUERY_STRING";
    public static final String PARAM_TYPE_JSON_BODY = "JSON_BODY";

    /**
     *  operation cache: can not serialize args or response
     */
    private static final Set<Integer> INVALID_OPERATION_HASH_CACHE = new ConcurrentHashSet<>();

    public static boolean ignoreMockResult(String serviceKey, String operationKey) {
        if (StringUtil.isEmpty(serviceKey)) {
            return false;
        }
        ArexContext context = ContextManager.currentContext();
        if (context == null || context.getExcludeMockTemplate() == null) {
            return false;
        }
        Map<String, Set<String>> excludeMockTemplate = context.getExcludeMockTemplate();
        if (!excludeMockTemplate.containsKey(serviceKey)) {
            return false;
        }
        Set<String> operationSet = excludeMockTemplate.get(serviceKey);
        // If empty, this service all operations ignore mock result
        if (operationSet == null || operationSet.isEmpty()) {
            LogManager.info("ignoreMock", StringUtil.format("service:%s all operations ignore mock result", serviceKey));
            return true;
        }
        // Specified operation ignore mock result
        if (operationSet.contains(operationKey)) {
            LogManager.info("ignoreMock", StringUtil.format("operation:%s.%s ignore mock result", serviceKey, operationKey));
            return true;
        }
        return false;
    }

    /**
     * Include the operation that need to record or replay
     */
    public static boolean includeOperation(String targetName) {
        if (StringUtil.isEmpty(targetName) || Config.get() == null) {
            return false;
        }

        Set<String> includeServiceOperations = Config.get().getIncludeServiceOperations();
        return operationMatched(targetName, includeServiceOperations);
    }

    public static RecordRuleMatchResult includeRecordRule(String targetName, Map<String, String[]> parameterMap, String jsonBody) {
        if (StringUtil.isEmpty(targetName) || Config.get() == null) {
            return RecordRuleMatchResult.notMatched();
        }

        List<RecordRuleEntity> recordRuleList = Config.get().getRecordRuleList();
        return recordRuleMatched(recordRuleList, targetName, parameterMap, jsonBody);
    }

    private static RecordRuleMatchResult recordRuleMatched(List<RecordRuleEntity> recordRuleList,
                                             String targetName,
                                             Map<String, String[]> parameterMap,
                                             String jsonBody) {
        if (CollectionUtil.isEmpty(recordRuleList)) {
            return RecordRuleMatchResult.notMatched();
        }

        for (RecordRuleEntity recordRule : recordRuleList) {
            if (!recordRule.getHttpPath().equalsIgnoreCase(targetName)) {
                continue;
            }

            List<ParamRuleEntity> paramRuleList = recordRule.getParamRuleEntityList();
            if (CollectionUtil.isEmpty(paramRuleList)) {
                return RecordRuleMatchResult.matched(recordRule.getUrlRuleId());
            }

            // re
            for (ParamRuleEntity paramRule : paramRuleList) {
                switch (paramRule.getParamType()) {
                    case PARAM_TYPE_QUERY_STRING:
                        if (urlParamRuleMatched(paramRule, parameterMap)) {
                            return RecordRuleMatchResult.matched(recordRule.getUrlRuleId(), paramRule.getParamRuleId());
                        }
                        break;
                    case PARAM_TYPE_JSON_BODY:
                        if (bodyParamRuleMatched(paramRule, jsonBody)) {
                            return RecordRuleMatchResult.matched(recordRule.getUrlRuleId(), paramRule.getParamRuleId());
                        }
                        break;
                    default:
                        break;
                }
            }
        }
        return RecordRuleMatchResult.notMatched();
    }

    private static boolean urlParamRuleMatched(ParamRuleEntity paramRule,
                                               Map<String, String[]> parameterMap) {
        List<ValueRuleEntity> valueRuleList = paramRule.getValueRuleEntityList();
        if (CollectionUtil.isEmpty(valueRuleList)) {
            return false;
        }

        for (ValueRuleEntity valueRule : valueRuleList) {
            String[] values = parameterMap.get(valueRule.getKey());
            if (values == null || values.length == 0) {
                continue;
            }

            boolean matched = Arrays.stream(values).anyMatch(value -> value.matches(valueRule.getValue()));
            if (matched) {
                return true;
            }
        }

        return false;
    }

    private static boolean bodyParamRuleMatched(ParamRuleEntity paramRule, String jsonBody) {
        List<ValueRuleEntity> valueRuleList = paramRule.getValueRuleEntityList();
        if (CollectionUtil.isEmpty(valueRuleList)) {
            return false;
        }

        for (ValueRuleEntity valueRule : valueRuleList) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(jsonBody);
                if (jsonElementMatched(jsonNode, valueRule)) {
                    return true;
                }
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        return false;
    }

    public static boolean jsonElementMatched(JsonNode jsonNode, ValueRuleEntity valueRule) {
        String matchedKey = valueRule.getKey();
        String matchedValue = valueRule.getValue();
        if (jsonNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String jsonKey = field.getKey();
                JsonNode jsonValue = field.getValue();
                if (jsonKey.equals(matchedKey) && jsonValue.asText().matches(matchedValue)) {
                    return true;
                }

                if (jsonElementMatched(jsonValue, valueRule)) {
                    return true;
                }
            }
        } else if (jsonNode.isArray()) {
            // 如果 JSON 元素是一个数组，递归检查数组中的每个元素
            for (JsonNode arrayElement : jsonNode) {
                if (jsonElementMatched(arrayElement, valueRule)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Exclude the operation that not need to record or replay
     */
    public static boolean excludeOperation(String targetName) {
        if (StringUtil.isEmpty(targetName) || Config.get() == null) {
            return false;
        }
        Set<String> excludeServiceOperations = Config.get().excludeServiceOperations();
        boolean isOperationMatched = operationMatched(targetName, excludeServiceOperations);
        if (isOperationMatched && ContextManager.needReplay()) {
            LogManager.warn("replay.hitBlockList", StringUtil.format("Hit block list, target name: %s", targetName));
        }
        return isOperationMatched;
    }

    /**
     * Exclude entrance operation by includeServiceOperations and excludeServiceOperations.
     * First if includeServiceOperations is not empty, only use excludeServiceOperations to judge.
     * Second if includeServiceOperations is empty, use excludeServiceOperations to jude.
     */
    public static boolean excludeEntranceOperation(String targetName) {
        if (Config.get() != null && CollectionUtil.isNotEmpty(Config.get().getIncludeServiceOperations())) {
            return !includeOperation(targetName);
        }
        return excludeOperation(targetName);
    }

    /**
     * targetName match searchOperations
     * @param searchOperations: includeServiceOperations or excludeServiceOperations.
     * @return includeServiceOperations: true -> notNeedIgnore, excludeServiceOperations: true -> needIgnore
      */
    private static boolean operationMatched(String targetName, Set<String> searchOperations) {
        if (CollectionUtil.isEmpty(searchOperations)) {
            return false;
        }
        for (String searchOperation : searchOperations) {
            if (searchOperation.equalsIgnoreCase(targetName)) {
                return true;
            }
            // startWith * and endWith *
            if (searchOperation.length() > 2 &&
                searchOperation.startsWith(SEPARATOR_STAR) &&
                searchOperation.endsWith(SEPARATOR_STAR) &&
                targetName.contains(searchOperation.substring(1, searchOperation.length() - 1))) {
                return true;
            }
            if (searchOperation.length() > 1) {
                if (searchOperation.startsWith(SEPARATOR_STAR) &&
                    targetName.endsWith(searchOperation.substring(1))) {
                    return true;
                }
                if (searchOperation.endsWith(SEPARATOR_STAR) &&
                    targetName.startsWith(searchOperation.substring(0, searchOperation.length() - 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean invalidOperation(String operationSignature) {
        return INVALID_OPERATION_HASH_CACHE.contains(StringUtil.encodeAndHash(operationSignature));
    }

    public static void addInvalidOperation(String operationSignature) {
        INVALID_OPERATION_HASH_CACHE.add(StringUtil.encodeAndHash(operationSignature));
    }

    public static void clearInvalidOperation() {
        INVALID_OPERATION_HASH_CACHE.clear();
    }
}
