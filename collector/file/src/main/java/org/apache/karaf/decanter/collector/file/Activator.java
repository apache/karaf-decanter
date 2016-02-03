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
package org.apache.karaf.decanter.collector.file;

import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListener;
import org.osgi.framework.*;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.event.EventAdmin;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("rawtypes")
public class Activator implements BundleActivator {

    private final static Logger LOGGER = LoggerFactory.getLogger(Activator.class);
    private ServiceTracker<EventAdmin, ServiceRegistration> tracker;

    public void start(final BundleContext bundleContext) throws Exception {
        LOGGER.debug("Starting decanter file collector");
        tracker = new ServiceTracker<EventAdmin, ServiceRegistration>(bundleContext, EventAdmin.class, null) {

            @Override
            public ServiceRegistration<?> addingService(ServiceReference<EventAdmin> reference) {
                EventAdmin eventAdmin = bundleContext.getService(reference);
                ConfigManager configManager = new ConfigManager(eventAdmin);
                Hashtable<String, Object> properties = new Hashtable<>();
                properties.put(Constants.SERVICE_PID, "org.apache.karaf.decanter.collector.file");
                return bundleContext.registerService(ManagedServiceFactory.class, configManager, properties);
            }

            @Override
            public void removedService(ServiceReference<EventAdmin> reference, ServiceRegistration reg) {
                reg.unregister();
                super.removedService(reference, reg);
            }

        };
        tracker.open();
    }

    public void stop(final BundleContext bundleContext) {
        LOGGER.debug("Stopping decanter file collector");
        tracker.close();
    }

    private final class ConfigManager implements ManagedServiceFactory {

        private EventAdmin eventAdmin;
        private Map<String, Tailer> tailers = new ConcurrentHashMap<>();

        public ConfigManager(EventAdmin eventAdmin) {
            this.eventAdmin = eventAdmin;
        }

        @Override
        public String getName() {
            return "Karaf Decanter File collector";
        }

        @SuppressWarnings("unchecked")
        @Override
        public void updated(String pid, Dictionary properties) throws ConfigurationException {
            LOGGER.debug("Updating File collector {}", pid);
            Tailer tailer = null;
            try {
                if (properties != null) {
                    if (properties.get("type") == null) {
                        throw new ConfigurationException("type","type property is mandatory");
                    }
                    String type = (String) properties.get("type");
                    if (properties.get("path") == null) {
                        throw new ConfigurationException("path", "path property is mandatory");
                    }
                    String path = (String) properties.get("path");

                    LOGGER.debug("Starting tail on {}", path);
                    TailerListener tailerListener = new DecanterTailerListener(type, path, eventAdmin, properties);
                    tailer = new Tailer(new File(path), tailerListener);
                    Thread thread = new Thread(tailer, "Trailer for " + path);
                    thread.start();
                }
            } finally {
                Tailer oldTailer= (tailer == null) ? tailers.remove(pid) : tailers.put(pid, tailer);
                if (oldTailer != null) {
                    LOGGER.debug("Unregistering Decanter File collector {}", pid);
                    oldTailer.stop();
                }
            }
        }

        @Override
        public void deleted(String pid) {
            LOGGER.debug("Deleting File collector {}", pid);
            Tailer tailer = tailers.remove(pid);
            LOGGER.debug("Stopping tail");
            tailer.stop();
        }

    }

}
