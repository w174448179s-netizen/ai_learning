package com.example.aigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
public class TraceIdFilter implements WebFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_CONTEXT_KEY = "traceId";

    static {
        try {
            Class.forName("io.micrometer.context.ThreadLocalAccessor");
        } catch (ClassNotFoundException e) {
            log.debug("Micrometer context not available, skipping automatic context propagation");
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = UUID.randomUUID().toString().replace("-", "");

        MDC.put(TRACE_ID_CONTEXT_KEY, traceId);
        exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, traceId);

        return chain.filter(exchange)
            .contextWrite(ctx -> ctx.put(TRACE_ID_CONTEXT_KEY, traceId))
            .doFinally(signalType -> MDC.clear());
    }
}