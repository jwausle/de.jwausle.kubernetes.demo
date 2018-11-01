package de.jwausle.kubernetes;

import org.jetbrains.annotations.NotNull;

import java.util.Stack;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class HeapConsumer {

    private final AtomicInteger memoryBarrierPercentage = new AtomicInteger(0);
    private final AtomicInteger memoryUsagePercentage = new AtomicInteger(5);
    private final CompletableFuture<Void> memoryLoop;

    public HeapConsumer() {
        memoryLoop = startLoopAsync();
        consume(memoryUsagePercentage.get());
    }

    /**
     * Set the barrier for memory consumption. The value should be balanced with the time.
     *
     * @param percentage value between [0 and 100] to set the memory consumption
     */
    public void consume(int percentage) {
        if (percentage < 0) {
            throw new IllegalArgumentException(percentage + " must be greather than 0. Its a percentage value");
        } else if (percentage > 100) {
            throw new IllegalArgumentException(percentage + " must be smaller than 100. Its a percentage value.");
        }
        memoryBarrierPercentage.set(percentage);
    }

    @NotNull
    private CompletableFuture<Void> startLoopAsync() {
        return CompletableFuture.runAsync(() -> {
            Runtime runtime = Runtime.getRuntime();
            Stack stack = new Stack();
            while (true) {
                int currentPercentageMemoryUsage = (int) (100 - (((double) (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / runtime.maxMemory()) * 100));
                memoryUsagePercentage.set(currentPercentageMemoryUsage);

                if (memoryBarrierPercentage.get() - 2 <= currentPercentageMemoryUsage && currentPercentageMemoryUsage <= memoryBarrierPercentage.get() + 2) {
                    safeSleep(100);
                    continue;
                } else if (memoryBarrierPercentage.get() + 2 < currentPercentageMemoryUsage) {
                    safeSleep(10);
                    stack = null;
                    System.gc();
                    stack = new Stack();
                    continue;
                }
                byte memoryConsumingArray[] = new byte[104857];
                stack.add(memoryConsumingArray);
            }
        });
    }

    @Override
    public String toString() {
        return "'" + memoryUsagePercentage.get() + "'% of maximal '" + memoryBarrierPercentage.get() + "'%";
    }

    private static void safeSleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            System.out.println(">> ignore interruption of java heap consumer.");
            Thread.currentThread().interrupt();
        }
    }
}
