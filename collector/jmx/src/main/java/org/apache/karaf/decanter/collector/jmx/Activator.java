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
package org.apache.karaf.decanter.collector.jmx;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.framework.*;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.event.EventAdmin;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class Activator implements BundleActivator {

    private final static Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    private ServiceTracker<EventAdmin, ServiceRegistration> tracker;

    public void start(final BundleContext bundleContext) throws Exception {
        tracker = new ServiceTracker<EventAdmin, ServiceRegistration>(bundleContext, EventAdmin.class, null) {

            @Override
            public ServiceRegistration<?> addingService(ServiceReference<EventAdmin> reference) {
                EventAdmin eventAdmin = bundleContext.getService(reference);
                ConfigManager configManager = new ConfigManager(eventAdmin, bundleContext);
                Hashtable<String, Object> properties = new Hashtable<String, Object>();
                properties.put(Constants.SERVICE_PID, "org.apache.karaf.decanter.collector.jmx");
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

    public void stop(BundleContext bundleContext) throws Exception {
        tracker.close();
    }

    private final class ConfigManager implements ManagedServiceFactory {

        private final Map<String, ServiceRegistration> registrations = new ConcurrentHashMap<String, ServiceRegistration>();

        private EventAdmin eventAdmin;
        private BundleContext bundleContext;

        public ConfigManager(EventAdmin eventAdmin, BundleContext bundleContext) {
            this.eventAdmin = eventAdmin;
            this.bundleContext = bundleContext;
        }

        @Override
        public String getName() {
            return "Karaf Decanter JMX collector service factory";
        }

        @Override
        public void updated(String pid, Dictionary properties) throws ConfigurationException {
            LOGGER.debug("Updating JMX collector {}", pid);
            ServiceRegistration registration = null;
            try {
                if (properties != null) {
                    String type = "jmx-local";
                    if (properties.get("type") != null) {
                        type = (String) properties.get("type");
                    }
                    String url = "local";
                    if (properties.get("url") != null) {
                        url = (String) properties.get("url");
                    }
                    String username = null;
                    if (properties.get("username") != null) {
                        username = (String) properties.get("username");
                    }
                    String password = null;
                    if (properties.get("password") != null) {
                        password = (String) properties.get("password");
                    }
                    String objectName = null;
                    if (properties.get("object.name") != null) {
                        objectName = (String) properties.get("object.name");
                    }
                    JmxCollector collector = new JmxCollector(type, url, username, password, objectName, eventAdmin);
                    Dictionary<String, String> serviceProperties = new Hashtable<String, String>();
                    serviceProperties.put("decanter.collector.name", type);
                    registration = bundleContext.registerService(Runnable.class, collector, serviceProperties);
                }
            } finally {
                ServiceRegistration oldRegistration = (registration == null) ? registrations.remove(pid) : registrations.put(pid, registration);
                if (oldRegistration != null) {
                    LOGGER.debug("Unregistering Decanter JMX collector {}", pid);
                    oldRegistration.unregister();
                }
            }
        }

        @Override
        public void deleted(String pid) {
            LOGGER.debug("Deleting JMX collector {}", pid);
            ServiceRegistration registration = registrations.remove(pid);
            if (registration != null) {
                registration.unregister();
            }
        }

    }

}
