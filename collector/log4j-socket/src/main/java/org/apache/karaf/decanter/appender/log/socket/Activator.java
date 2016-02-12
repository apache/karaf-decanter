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
package org.apache.karaf.decanter.appender.log.socket;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.event.EventAdmin;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class Activator implements BundleActivator {
    private final static Logger LOGGER = LoggerFactory.getLogger(Activator.class);
    private ServiceTracker<EventAdmin, ServiceRegistration> tracker;

    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        tracker = new ServiceTracker<EventAdmin, ServiceRegistration>(bundleContext, EventAdmin.class, null) {

            @Override
            public ServiceRegistration<?> addingService(ServiceReference<EventAdmin> reference) {
                EventAdmin eventAdmin = bundleContext.getService(reference);
                ConfigManager configManager = new ConfigManager(eventAdmin);
                Hashtable<String, Object> properties = new Hashtable<String, Object>();
                properties.put(Constants.SERVICE_PID, "org.apache.karaf.decanter.collector.log.socket");
                return bundleContext.registerService(ManagedService.class, configManager, properties);
            }

            @Override
            public void removedService(ServiceReference<EventAdmin> reference, ServiceRegistration reg) {
                reg.unregister();
                super.removedService(reference, reg);
            }
            
        };
        tracker.open();
    }
    public void stop(BundleContext bundleContext) throws Exception {
        tracker.close();
    }

    private final class ConfigManager implements ManagedService {
        private EventAdmin eventAdmin;
        private SocketCollector collector;

        public ConfigManager(EventAdmin eventAdmin) {
            this.eventAdmin = eventAdmin;
        }

        @Override
        public void updated(Dictionary properties) throws ConfigurationException {
            close();
            try {
                if (properties != null) {
                    int port = Integer.parseInt(getProperty(properties, "port", "4560"));
                    LOGGER.info("Starting Log4j Socket collector on port {}", port);
                    collector = new SocketCollector(port, eventAdmin);
                }
            } catch (IOException e) {
                throw new ConfigurationException(null, "Error creating SocketCollector", e); 
            }
        }

        private String getProperty(Dictionary properties, String key, String defaultValue) {
            return (properties.get(key) != null) ? (String) properties.get(key) : defaultValue;
        }

        private void close() {
            if (collector != null) {
                try {
                    LOGGER.info("Stopping log socket collector.");
                    collector.close();
                } catch (IOException e) {
                    LOGGER.warn("Error closing SocketCollector", e);
                }
                collector = null;
            }
        }
    }

}
