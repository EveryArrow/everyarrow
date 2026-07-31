package com.everyarrow.dittoe.utility;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * ExecutorService class.
 */
public class ExecutorService {

    public static <T> void execute(Collection<Callable<T>> tasks) throws InterruptedException, ExecutionException {
        execute(tasks, Config.PIPELINE_THREADS);
    }

    /**
     * Invokes the specified callables.
     * Creates a fixed thread pool of size equal to the number of callables
     * thereby calling each callable on separate thread.
     *
     * @param tasks Collection of tasks.
     * @throws InterruptedException Thrown if any thread is interrupted.
     * @throws ExecutionException   Thrown if any callable threw an exception.
     */
    public static <T> void execute(Collection<Callable<T>> tasks, int numThreads) throws InterruptedException, ExecutionException {
        // creating the executor service to execute threads
        try (java.util.concurrent.ExecutorService executorService = Executors.newFixedThreadPool(numThreads)) {
            // invoking the callables
            List<Future<T>> results = executorService.invokeAll(tasks);
            executorService.shutdown();
            for (Future<T> result : results)
                result.get();   // only to check if there was any exception during the execution of any callable
        }
    }
}
