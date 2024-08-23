package io.arex.inst.netty.v4.common;

import io.arex.agent.bootstrap.model.Mocker;
import io.arex.agent.bootstrap.util.ReflectUtil;
import io.arex.inst.runtime.listener.EventSource;
import io.arex.inst.runtime.util.SkipResult;

public class AttributeKey {

    public static final io.netty.util.AttributeKey<Mocker> TRACING_MOCKER = initAttributeKey("arex-netty-server-mocker");
    public static final io.netty.util.AttributeKey<ArexHttpRequest> TRACING_AREX_HTTP_REQUEST = initAttributeKey("arex-netty-arex-http-request");
    public static final io.netty.util.AttributeKey<SkipResult> TRACING_AREX_SKIP_RESULT = initAttributeKey("arex-netty-arex-skip-result");
    public static final io.netty.util.AttributeKey<EventSource> TRACING_EVENT_SOURCE = initAttributeKey("arex-netty-server-event-source");

    /**
     * compatible with different versions of netty initAttributeKey method,
     * the reason for using a lower version of netty here is support more versions of netty,
     * and discover many incompatible problem during compilation
     */
    private static <T> io.netty.util.AttributeKey<T> initAttributeKey(String attributeKey) {
        Object instance = null;
        try {
            // the user's Netty version is only known at runtime
            instance = ReflectUtil.getFieldOrInvokeMethod(
                    () -> io.netty.util.AttributeKey.class.getDeclaredMethod("valueOf", Class.class, String.class),
                    null, AttributeKey.class, attributeKey);
        } catch (Exception e) {
            // ignore, < 4.1.0 not exist valueOf method
        }
        if (instance instanceof io.netty.util.AttributeKey) {
            return (io.netty.util.AttributeKey<T>) instance;
        }
        // direct call
        return new io.netty.util.AttributeKey<>(attributeKey);
    }

}
