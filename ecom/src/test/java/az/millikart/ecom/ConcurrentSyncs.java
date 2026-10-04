package az.millikart.ecom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// Кнопка и расписание разом (ECOM-SYNC-RACE): первый проход стоит в выборке из шлюза, второй стартует, пока
// он там, и не должен дойти до шлюза, пока первый не закончит. Ответ мока шлюза обязан звать enterTheGateway.
final class ConcurrentSyncs {

    private static final long TIMEOUT_SECONDS = 10;

    private final CountDownLatch insideTheGateway = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger gatewayCalls = new AtomicInteger();

    void enterTheGateway() throws InterruptedException {
        if (gatewayCalls.incrementAndGet() == 1) {
            insideTheGateway.countDown();
            release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    <T> List<T> run(Callable<T> sync) throws Exception {
        FutureTask<T> first = new FutureTask<>(sync);
        new Thread(first).start();
        assertTrue(insideTheGateway.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the first sync never reached the gateway");

        FutureTask<T> second = new FutureTask<>(sync);
        Thread secondThread = new Thread(second);
        secondThread.start();
        awaitParkedOrDone(secondThread);
        int callsWhileTheFirstRan = gatewayCalls.get();

        release.countDown();
        List<T> outcomes = List.of(first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS), second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(1, callsWhileTheFirstRan, "the second sync reached the gateway while the first was still running");
        return outcomes;
    }

    // Без замка второй проход не встанет, а дойдёт до конца — тогда счётчик выше покажет два вызова.
    private static void awaitParkedOrDone(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (thread.isAlive() && System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            if (state == Thread.State.BLOCKED || state == Thread.State.WAITING) {
                return;
            }
            Thread.sleep(10);
        }
    }
}
