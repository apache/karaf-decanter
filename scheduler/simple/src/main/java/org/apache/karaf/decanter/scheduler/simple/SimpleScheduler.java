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

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.karaf.decanter.api.Scheduler;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Very simple Decanter scheduler using a single thread.
 */
public class SimpleScheduler implements Runnable, Scheduler {
    private final static Logger LOGGER = LoggerFactory.getLogger(SimpleScheduler.class);

    private AtomicBoolean running = new AtomicBoolean(false);
    private long interval = 30000L;
    ServiceTracker<Runnable, Runnable> collectors;
    
    SimpleScheduler() {
    }
    
    public SimpleScheduler(BundleContext bundleContext) {
        this.collectors = new ServiceTracker<>(bundleContext, collectorFilter(bundleContext), null);
        this.collectors.open();
    }

    private Filter collectorFilter(BundleContext bundleContext) {
        try {
            return bundleContext.createFilter(String.format("(&(objectClass=%s)(decanter.collector.name=*))", Runnable.class.getName()));
        } catch (InvalidSyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public void run() {
        LOGGER.debug("Decanter SimpleScheduler thread started ...");

        while (running.get()) {
            LOGGER.debug("Calling the collectors ...");
            for (Runnable collector : collectors.getServices(new Runnable[] {})) {
                try {
                    collector.run();
                } catch (Exception e) {
                    LOGGER.warn("Can't collect data", e);
                }
            }
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                running.set(false);
            }
        }

        LOGGER.debug("Decanter SimpleScheduler thread stopped ...");
    }

    public void stop() {
        running.set(false);
        if (collectors != null) {
            this.collectors.close();
        }
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            Thread thread = new Thread(this, "decanter-scheduler-simple");
            thread.start();
        }
    }

    public boolean isStarted() throws Exception {
        return running.get();
    }

    public String state() {
        if (running.get()) {
            return "Started";
        } else {
            return "Stopped";
        }
    }

}
