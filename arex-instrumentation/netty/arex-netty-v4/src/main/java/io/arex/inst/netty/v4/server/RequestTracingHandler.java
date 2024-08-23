package io.arex.inst.netty.v4.server;

import io.arex.agent.bootstrap.constants.ConfigConstants;
import io.arex.agent.bootstrap.model.Mocker;
import io.arex.agent.bootstrap.util.CollectionUtil;
import io.arex.agent.bootstrap.util.StringUtil;
import io.arex.inst.netty.v4.common.ArexHttpRequest;
import io.arex.inst.runtime.config.Config;
import io.arex.inst.runtime.context.ContextManager;
import io.arex.inst.runtime.listener.CaseEvent;
import io.arex.inst.runtime.listener.CaseEventDispatcher;
import io.arex.inst.runtime.listener.EventProcessor;
import io.arex.inst.runtime.listener.EventSource;
import io.arex.inst.runtime.log.LogManager;
import io.arex.inst.runtime.model.ArexConstants;
import io.arex.inst.netty.v4.common.AttributeKey;
import io.arex.inst.runtime.model.RecordRuleMatchResult;
import io.arex.inst.runtime.util.IgnoreUtils;
import io.arex.inst.runtime.util.MockUtils;
import io.arex.inst.runtime.util.SkipResult;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.CharsetUtil;

import static io.arex.inst.netty.v4.common.AttributeKey.*;

public class RequestTracingHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            // init
            if (msg instanceof HttpRequest) {
                CaseEventDispatcher.onEvent(CaseEvent.ofEnterEvent());
                HttpRequest request = (HttpRequest) msg;
                ArexHttpRequest arexHttpRequest = new ArexHttpRequest(request);
                ctx.channel().attr(TRACING_AREX_HTTP_REQUEST).set(arexHttpRequest);

                String caseId = arexHttpRequest.getHeaders().get(ArexConstants.RECORD_ID);
                SkipResult skipResult = shouldSkipOnReadHeader(arexHttpRequest, caseId);
                if (skipResult.isSkip()) {
                    return;
                }
                ctx.channel().attr(TRACING_AREX_SKIP_RESULT).set(skipResult);

                String excludeMockTemplate = arexHttpRequest.getHeaders().get(ArexConstants.HEADER_EXCLUDE_MOCK);
                EventSource eventSource = EventSource.of(caseId, excludeMockTemplate);
                ctx.channel().attr(TRACING_EVENT_SOURCE).set(eventSource);
            }

            // record request body, if the request body too large, it will be separated into multiple HttpContent
            if (msg instanceof HttpContent) {
                recordBody(ctx, (HttpContent) msg);
            }
        } catch (Throwable e) {
            LogManager.warn("netty read error", e);
        } finally {
            super.channelRead(ctx, msg);
        }
    }

    private void recordBody(ChannelHandlerContext ctx, HttpContent httpContent) {
        ArexHttpRequest arexHttpRequest = ctx.channel().attr(TRACING_AREX_HTTP_REQUEST).get();
        if (arexHttpRequest == null) {
            return;
        }

        String content = httpContent.content().toString(CharsetUtil.UTF_8);
        if (StringUtil.isEmpty(content)) {
            return;
        }

        String requestBody = arexHttpRequest.getRequestBody();
        if (StringUtil.isNotEmpty(requestBody)) {
            requestBody += content;
        } else {
            requestBody = content;
        }

        arexHttpRequest.setRequestBody(requestBody);
    }

    private SkipResult shouldSkipOnReadHeader(ArexHttpRequest arexHttpRequest, String caseId) {
        if (!EventProcessor.dependencyInitComplete()) {
            return SkipResult.skip();
        }
        // Replay scene
        if (StringUtil.isNotEmpty(caseId)) {
            return SkipResult.build(Config.get().getBoolean(ConfigConstants.DISABLE_REPLAY, false));
        }

        String forceRecord = arexHttpRequest.getHeaders().get(ArexConstants.FORCE_RECORD);
        // Do not skip if header with arex-force-record=true
        if (StringUtil.isEmpty(caseId) && Boolean.parseBoolean(forceRecord)) {
            return SkipResult.notSkip();
        }

        // Skip if request header with arex-replay-warm-up=true
        if (Boolean.parseBoolean(arexHttpRequest.getHeaders().get(ArexConstants.REPLAY_WARM_UP))) {
            return SkipResult.skip();
        }

        if (IgnoreUtils.excludeEntranceOperation(arexHttpRequest.getUri())) {
            return SkipResult.skip();
        }

        // Filter RecordRule: PathRule + UrlParamRule
        if (CollectionUtil.isNotEmpty(Config.get().getRecordRuleList())) {
            RecordRuleMatchResult matchResult = IgnoreUtils.includeRecordRule(arexHttpRequest.getPath(), arexHttpRequest.getParameterMap());
            if (matchResult.isMatch()) {
                String tokenBucketKey = matchResult.getTokenBucketKey();
                String httpPath = matchResult.getHttpPath();
                String ruleId = matchResult.getTokenBucketKey();
                return SkipResult.build(Config.get().invalidRecord(tokenBucketKey), ruleId, httpPath);
            } else {
                return SkipResult.build(!Config.get().isExistBodyParamRule());
            }
        }

        return SkipResult.notSkip();
    }

    private SkipResult shouldSkipOnReadComplete(ArexHttpRequest arexHttpRequest) {
        if (CollectionUtil.isNotEmpty(Config.get().getRecordRuleList()) && Config.get().isExistBodyParamRule()) {
            RecordRuleMatchResult patternMatchResult = IgnoreUtils.includeRecordRule(arexHttpRequest.getPath(), arexHttpRequest.getRequestBody());
            if (patternMatchResult.isMatch()) {
                String tokenBucketKey = patternMatchResult.getTokenBucketKey();
                String httpPath = patternMatchResult.getHttpPath();
                String ruleId = patternMatchResult.getTokenBucketKey();
                return SkipResult.build(Config.get().invalidRecord(tokenBucketKey), ruleId, httpPath);
            } else {
                return SkipResult.skip();
            }
        }

        return SkipResult.notSkip();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        try {
            SkipResult skipResult = ctx.channel().attr(TRACING_AREX_SKIP_RESULT).getAndSet(null);
            if (skipResult == null || skipResult.isSkip()) {
                return;
            }
            ArexHttpRequest arexHttpRequest = ctx.channel().attr(TRACING_AREX_HTTP_REQUEST).getAndSet(null);
            if (arexHttpRequest == null) {
                return;
            }

            // exist url param rule and not matched, try to do match body param rule
            if (skipResult.getRuleId() == null) {
                skipResult = shouldSkipOnReadComplete(arexHttpRequest);
            }
            // both not exist url param and body param, just match token bucket filter with http_url
            if (!skipResult.isSkip()
                    && skipResult.getRuleId() == null
                    && Config.get().invalidRecord(arexHttpRequest.getPath())) {
                return;
            }

            EventSource eventSource = ctx.channel().attr(TRACING_EVENT_SOURCE).get();
            eventSource.setHttpPath(skipResult.getHttpPath());
            eventSource.setRuleId(skipResult.getRuleId());
            CaseEventDispatcher.onEvent(CaseEvent.ofCreateEvent(eventSource));
            ContextManager.currentContext().setAttachment(ArexConstants.FORCE_RECORD, arexHttpRequest.getHeaders().get(ArexConstants.FORCE_RECORD));
            if (ContextManager.needRecordOrReplay()) {
                Mocker mocker = MockUtils.createNettyProvider(arexHttpRequest.getUri());
                Mocker.Target target = mocker.getTargetRequest();
                target.setAttribute("HttpMethod", arexHttpRequest.getMethod());
                target.setAttribute("Headers", arexHttpRequest.getHeaders());
                ctx.channel().attr(AttributeKey.TRACING_MOCKER).set(mocker);
                mocker.getTargetRequest().setBody(arexHttpRequest.getRequestBody());

                if (ContextManager.needReplay()) {
                    MockUtils.replayBody(mocker);
                } else if (ContextManager.needRecord()) {
                    MockUtils.recordMocker(mocker);
                }
            }

            CaseEventDispatcher.onEvent(CaseEvent.ofExitEvent());
        } catch (Throwable e) {
            LogManager.warn("netty channelReadComplete error", e);
        } finally {
            super.channelReadComplete(ctx);
        }
    }
}
