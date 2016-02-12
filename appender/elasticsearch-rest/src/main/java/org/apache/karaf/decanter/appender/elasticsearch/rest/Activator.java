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
package org.apache.karaf.decanter.appender.elasticsearch.rest;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.osgi.framework.*;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.util.tracker.ServiceTracker;

import java.util.Dictionary;
import java.util.Hashtable;

public class Activator implements BundleActivator {

    private ElasticsearchAppender appender;
    private ServiceTracker tracker;
    private static final String CONFIG_PID = "org.apache.karaf.decanter.appender.elasticsearch.rest";

    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        tracker = new ServiceTracker<Marshaller, ServiceRegistration>(bundleContext, Marshaller.class, null) {

            @Override
            public ServiceRegistration addingService(ServiceReference<Marshaller> reference) {
                Dictionary<String, String> properties = new Hashtable<>();
                properties.put(Constants.SERVICE_PID, CONFIG_PID);
                Marshaller marshaller = context.getService(reference);
                return bundleContext.registerService(ManagedService.class.getName(), new ConfigUpdater(bundleContext, marshaller), properties);
            }

            @Override
            public void removedService(ServiceReference<Marshaller> reference, ServiceRegistration service) {
                service.unregister();
                super.removedService(reference, service);
            }

        };
        tracker.open();
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        tracker.close();
    }

    private final class ConfigUpdater implements ManagedService {
        private BundleContext bundleContext;
        private ServiceRegistration<?> serviceReg;
        private Marshaller marshaller;

        public ConfigUpdater(BundleContext bundleContext, Marshaller marshaller) {
            this.bundleContext = bundleContext;
            this.marshaller = marshaller;
        }

        @Override
        public void updated(Dictionary config) throws ConfigurationException {
            if (appender != null) {
                appender.close();
                serviceReg.unregister();
            }
            if (config != null) {
                String address = config != null ? (String) config.get("address") : "http://localhost:9200";
                String username = (config != null && config.get("username") != null) ? (String) config.get("username") : null;
                String password = (config != null && config.get("password") != null) ? (String) config.get("password") : null;
                try {
                    appender = new ElasticsearchAppender(marshaller, address, username, password);
                } catch (Exception e) {
                    throw new ConfigurationException(null, "Can't create appender", e);
                }
                Dictionary<String, String> properties = new Hashtable<>();
                properties.put(EventConstants.EVENT_TOPIC, "decanter/collect/*");
                serviceReg =  bundleContext.registerService(EventHandler.class, appender, properties);
            }
        }
    }

}
