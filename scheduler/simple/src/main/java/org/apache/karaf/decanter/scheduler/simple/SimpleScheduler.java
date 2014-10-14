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

import org.apache.karaf.decanter.api.Dispatcher;
import org.apache.karaf.decanter.api.PollingCollector;
import org.apache.karaf.decanter.api.Scheduler;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Very simple Decanter scheduler using a single thread.
 */
public class SimpleScheduler implements Runnable, Scheduler {

    private final static Logger LOGGER = LoggerFactory.getLogger(SimpleScheduler.class);

    private BundleContext bundleContext;
    private AtomicBoolean running = new AtomicBoolean(false);
    private long interval = 30000L;

    public SimpleScheduler() {
        this.start();
    }

    public SimpleScheduler(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
        this.start();
    }

    public void run() {
        LOGGER.debug("Decanter SimpleScheduler thread started ...");

        while (running.get()) {
            Map<Long, Map<String, Object>> collected = new HashMap<>();
            try {
                LOGGER.debug("Calling the collectors ...");
                Collection<ServiceReference<PollingCollector>> references = bundleContext.getServiceReferences(PollingCollector.class, null);
                if (references != null) {
                    for (ServiceReference<PollingCollector> reference : references) {
                        try {
                            if (reference != null) {
                                PollingCollector collector = bundleContext.getService(reference);
                                Map<Long, Map<String, Object>> data = collector.collect();
                                collected.putAll(data);
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Can't collect data", e);
                        } finally {
                            bundleContext.ungetService(reference);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Can't get polling collector services", e);
            }
            ServiceReference<Dispatcher> reference = null;
            try {
                LOGGER.debug("Calling the dispatcher ...");
                reference = bundleContext.getServiceReference(Dispatcher.class);
                if (reference != null) {
                    Dispatcher dispatcher = bundleContext.getService(reference);
                    dispatcher.dispatch(collected);
                }
            } catch (Exception e) {
                LOGGER.warn("Can't dispatch using the controller", e);
            } finally {
                if (reference != null) {
                    bundleContext.ungetService(reference);
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

    public BundleContext getBundleContext() {
        return bundleContext;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

}
