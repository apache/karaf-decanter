/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.decanter.scheduler.simple;

import java.util.Dictionary;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.karaf.decanter.api.Scheduler;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Very simple Decanter scheduler using a single thread.
 */
@Component(
    name = "org.apache.karaf.decanter.scheduler.simple",
    immediate = true
)
public class SimpleScheduler implements Runnable, Scheduler {
    private final static Logger LOGGER = LoggerFactory.getLogger(SimpleScheduler.class);

    private AtomicBoolean running = new AtomicBoolean(false);

    private long period = 5000;
    private long threadIdleTimeout = 60000;
    private int threadInitCount = 5;
    private int threadMaxCount = 200;

    private ExecutorService executorService;

    Set<Runnable> collectors;
    
    public SimpleScheduler() {
        collectors = new HashSet<>();
    }
    
    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) {
        configure(context.getProperties());
        start();
    }
    
    public void configure(Dictionary<String, String> config) {
        String periodSt = config.get("period");
        period = periodSt != null ? Integer.parseInt(periodSt) : 5000;
        String threadIdleTimeoutSt = config.get("threadIdleTimeout");
        threadIdleTimeout = threadIdleTimeoutSt != null ? Integer.parseInt(threadIdleTimeoutSt) : 60000;
        String threadInitCountSt = config.get("threadInitCount");
        threadInitCount = threadInitCountSt != null ? Integer.parseInt(threadInitCountSt) : 5;
        String threadMaxCountSt = config.get("threadMaxCount");
        threadMaxCount = threadMaxCountSt != null ? Integer.parseInt(threadMaxCountSt) : 200;
    }

    @Override
    public void run() {
        LOGGER.debug("Decanter SimpleScheduler thread started ...");

        while (running.get()) {
            LOGGER.debug("Calling the collectors ...");
            for (Runnable collector : collectors) {
                try {
                    executorService.execute(collector);
                } catch (Exception e) {
                    LOGGER.warn("Can't collect data", e);
                }
            }
            sleep();
        }

        LOGGER.debug("Decanter SimpleScheduler thread stopped ...");
    }

    private void sleep() {
        try {
            Thread.sleep(period);
        } catch (InterruptedException e) {
            running.set(false);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop() {
        try {
            executorService.awaitTermination(60L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("Collectors still active", e);
        }
        executorService.shutdownNow();
        running.set(false);
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            executorService = new ThreadPoolExecutor(threadInitCount,
                    threadMaxCount, threadIdleTimeout, TimeUnit.SECONDS,
                    new SynchronousQueue<Runnable>(), Executors.defaultThreadFactory(),
                    new ThreadPoolExecutor.CallerRunsPolicy());
            Thread thread = new Thread(this, "decanter-scheduler-simple");
            thread.start();
        }
    }

    @Override
    public boolean isStarted() throws Exception {
        return running.get();
    }

    @Override
    public String state() {
        return running.get() ? "Started" : "Stopped";
    }

    @Reference(target="(decanter.collector.name=*)", cardinality=ReferenceCardinality.MULTIPLE, policy=ReferencePolicy.DYNAMIC)
    public void setCollector(Runnable collector) {
        this.collectors.add(collector);
    }
    
    public void unsetCollector(Runnable collector) {
        this.collectors.remove(collector);
    }

}
