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
package org.apache.karaf.decanter.collector.rest;

import java.net.MalformedURLException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.karaf.decanter.api.marshaller.Unmarshaller;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.event.EventAdmin;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class Activator implements BundleActivator {

    private final static Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    private ServiceTracker<EventAdmin, MarshallerTracker> tracker;

    public void start(final BundleContext bundleContext) throws Exception {
        tracker = new ServiceTracker<EventAdmin, MarshallerTracker>(bundleContext, EventAdmin.class, null) {

            @Override
            public MarshallerTracker addingService(ServiceReference<EventAdmin> reference) {
                EventAdmin eventAdmin = bundleContext.getService(reference);
                MarshallerTracker uTracker = new MarshallerTracker(bundleContext, eventAdmin);
                uTracker.open();
                return uTracker;
            }

            @Override
            public void removedService(ServiceReference<EventAdmin> reference, MarshallerTracker uTracker) {
                uTracker.close();
                super.removedService(reference, uTracker);
            }
            
        };
        tracker.open();
    }

    public void stop(BundleContext bundleContext) throws Exception {
        tracker.close();
    }
    
    class MarshallerTracker extends ServiceTracker<Unmarshaller, ServiceRegistration> {

        private EventAdmin eventAdmin;

        public MarshallerTracker(BundleContext context, EventAdmin eventAdmin) {
            super(context, Unmarshaller.class, null);
            this.eventAdmin = eventAdmin;
        }

        @Override
        public ServiceRegistration addingService(ServiceReference<Unmarshaller> reference) {
            Unmarshaller unmarshaller = context.getService(reference);
            ConfigManager configManager = new ConfigManager(eventAdmin, unmarshaller, context);
            Hashtable<String, Object> properties = new Hashtable<String, Object>();
            properties.put(Constants.SERVICE_PID, "org.apache.karaf.decanter.collector.rest");
            return context.registerService(ManagedServiceFactory.class, configManager, properties);
        }

        @Override
        public void removedService(ServiceReference<Unmarshaller> reference, ServiceRegistration reg) {
            reg.unregister();
            super.removedService(reference, reg);
        }
        
        
    }

    private final class ConfigManager implements ManagedServiceFactory {

        private final Map<String, ServiceRegistration> registrations = new ConcurrentHashMap<String, ServiceRegistration>();

        private EventAdmin eventAdmin;
        private BundleContext bundleContext;
        private Unmarshaller unmarshaller;

        public ConfigManager(EventAdmin eventAdmin, Unmarshaller unmarshaller, BundleContext bundleContext) {
            this.eventAdmin = eventAdmin;
            this.unmarshaller = unmarshaller;
            this.bundleContext = bundleContext;
        }

        @Override
        public String getName() {
            return "Karaf Decanter JMX collector service factory";
        }

        @SuppressWarnings("unchecked")
        @Override
        public void updated(String pid, Dictionary properties) throws ConfigurationException {
            LOGGER.debug("Updating JMX collector {}", pid);
            ServiceRegistration registration = null;
            try {
                if (properties != null) {
                    String baseUrl = getProperty(properties, "url", null);
                    String username = getProperty(properties, "username", null);
                    String password = getProperty(properties, "password", null);
                    String[] paths = getProperty(properties, "paths", "").split(",");
                    properties.remove("password");
                    properties.remove("username");
                    RestCollector collector = new RestCollector(baseUrl, username, password, paths, eventAdmin, unmarshaller, properties);
                    Dictionary<String, String> serviceProperties = new Hashtable<String, String>();
                    serviceProperties.put("decanter.collector.name", "rest");
                    registration = bundleContext.registerService(Runnable.class, collector, serviceProperties);
                }
            } catch (MalformedURLException e) {
                LOGGER.warn("Invalid url to fetch from", e);
            } finally {
                ServiceRegistration oldRegistration = (registration == null) ? registrations.remove(pid) : registrations.put(pid, registration);
                if (oldRegistration != null) {
                    LOGGER.debug("Unregistering Decanter JMX collector {}", pid);
                    oldRegistration.unregister();
                }
            }
        }

        private String getProperty(Dictionary properties, String key, String defaultValue) {
            return (properties.get(key) != null) ? (String) properties.get(key) : defaultValue;
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
