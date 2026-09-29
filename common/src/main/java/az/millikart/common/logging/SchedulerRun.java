package az.millikart.common.logging;

import az.millikart.common.security.TraceIdFilter;
import java.util.UUID;
import org.slf4j.MDC;

// Свой traceId у каждого прогона планировщика — по нему собираются строки одного прохода. Открывать
// первой строкой @Scheduled-метода в try-with-resources.
public final class SchedulerRun {

    private SchedulerRun() {
    }

    public static MDC.MDCCloseable start(String name) {
        return MDC.putCloseable(TraceIdFilter.MDC_TRACE_ID_KEY, name + "-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
