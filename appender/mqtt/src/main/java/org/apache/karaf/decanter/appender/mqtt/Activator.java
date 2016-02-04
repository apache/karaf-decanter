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
package org.apache.karaf.decanter.appender.mqtt;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.util.tracker.ServiceTracker;

@SuppressWarnings("rawtypes")
public class Activator implements BundleActivator {
    private static final String CONFIG_PID = "org.apache.karaf.decanter.appender.mqtt";
    private ServiceTracker<Marshaller, ServiceRegistration> tracker;

    public void start(final BundleContext bundleContext) {
        tracker = new ServiceTracker<Marshaller, ServiceRegistration>(bundleContext, Marshaller.class, null) {
            @Override
            public ServiceRegistration addingService(ServiceReference<Marshaller> reference) {
                Dictionary<String, String> properties = new Hashtable<>();
                properties.put(Constants.SERVICE_PID, CONFIG_PID);
                Marshaller marshaller = context.getService(reference);
                return bundleContext.registerService(ManagedService.class.getName(),
                                                     new ConfigUpdater(bundleContext, marshaller),
                                                     properties);
            }

            @Override
            public void removedService(ServiceReference<Marshaller> reference, ServiceRegistration service) {
                service.unregister();
                super.removedService(reference, service);
            }
        };
        tracker.open();
    }

    public void stop(BundleContext bundleContext) {
        tracker.close();
    }

    private final class ConfigUpdater implements ManagedService {
        private BundleContext bundleContext;
        private ServiceRegistration<?> serviceReg;
        private Marshaller marshaller;
        private MqttAppender appender;

        public ConfigUpdater(BundleContext bundleContext, Marshaller marshaller) {
            this.bundleContext = bundleContext;
            this.marshaller = marshaller;
        }

        @Override
        public void updated(Dictionary config) throws ConfigurationException {
            if (appender != null) {
                safeClose(appender);
                serviceReg.unregister();
            }
            
            if (config == null) {
                return;
            }

            String serverURI = getProperty(config, "server", "tcp://localhost:9300");
            String topic = getProperty(config, "topic", "decanter");
            String clientId = getProperty(config, "clientId", "decanter");
            try {
                appender = new MqttAppender(serverURI, clientId, topic, marshaller);
                Dictionary<String, String> properties = new Hashtable<>();
                properties.put(EventConstants.EVENT_TOPIC, "decanter/collect/*");
                serviceReg =  bundleContext.registerService(EventHandler.class, appender, properties);
            } catch (MqttException e) {
                throw new ConfigurationException(null, "Error starting mqtt appender sending to server " + serverURI, e);
            }
        }

        
        private String getProperty(Dictionary properties, String key, String defaultValue) {
            return (properties.get(key) != null) ? (String) properties.get(key) : defaultValue;
        }
    }
    
    private void safeClose(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

}
