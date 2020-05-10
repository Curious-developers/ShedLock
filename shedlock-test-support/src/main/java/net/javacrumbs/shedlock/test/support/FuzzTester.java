/**
 * Copyright 2009-2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.test.support;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Increments counter from several threads coordinating using lock provided under test.
 */
public class FuzzTester {

    private static final int THREADS = 8;
    private static final int ITERATIONS = 100;
    public static final int SHORT_ITERATION = 10;

    private final LockProvider lockProvider;

    private static final LockConfiguration config1 = new LockConfiguration("lock1", Duration.of(5, ChronoUnit.MINUTES), Duration.ZERO);
    private static final LockConfiguration config2 = new LockConfiguration("lock2", Duration.of(5, ChronoUnit.MINUTES), Duration.ZERO);

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public FuzzTester(LockProvider lockProvider) {
        this.lockProvider = lockProvider;
    }

    public void doFuzzTest() throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        int[] iterations = range(0, THREADS).map(i -> ITERATIONS).toArray();
        iterations[0] = SHORT_ITERATION; // short task to simulate MySql issues
        Job job1 = new Job(config1);
        Job job2 = new Job(config2);


        List<Callable<Void>> tasks = range(0, THREADS).mapToObj(i -> (Callable<Void>) () ->
            this.task(iterations[i], i % 2 == 0 ? job1 : job2)).collect(toList()
        );
        waitForIt(executor.invokeAll(tasks));

        assertThat(job2.getCounter()).isEqualTo(THREADS / 2 * ITERATIONS);
        assertThat(job1.getCounter()).isEqualTo((THREADS / 2 - 1) * ITERATIONS + SHORT_ITERATION);
    }

    private void waitForIt(List<Future<Void>> futures) throws InterruptedException, ExecutionException {
        for (Future<Void> f : futures) {
            f.get();
        }
    }

    protected Void task(int iterations, Job job) {
        try {
            for (int i = 0; i < iterations;) {
                Optional<SimpleLock> lock = lockProvider.lock(job.getLockConfiguration());
                if (lock.isPresent()) {
                    int n = job.getCounter();
                    if (shouldLog()) logger.debug("action=getLock value={} i={}", n, i);
                    sleep();
                    if (shouldLog()) logger.debug("action=setCounter value={} i={}", n + 1, i);
                    job.setCounter(n + 1);
                    lock.get().unlock();
                    i++;
                }
            }
            logger.debug("action=finished");
            return null;
        } catch (RuntimeException e) {
            logger.error("Unexpected exception", e);
            throw e;
        }
    }

    protected boolean shouldLog() {
        return false;
    }

    private void sleep() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected static class Job {
        private final LockConfiguration lockConfiguration;
        private int counter = 0;

        Job(LockConfiguration lockConfiguration) {
            this.lockConfiguration = lockConfiguration;
        }

        public LockConfiguration getLockConfiguration() {
            return lockConfiguration;
        }

        public int getCounter() {
            return counter;
        }

        public void setCounter(int counter) {
            this.counter = counter;
        }
    }
}


