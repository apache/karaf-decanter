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
package org.apache.karaf.decanter.appender.elasticsearch;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

@SuppressWarnings("rawtypes")
public class Activator implements BundleActivator {

    private ElasticsearchAppender appender;
    private ServiceTracker tracker;
    private static final String CONFIG_PID = "org.apache.karaf.decanter.appender.elasticsearch";

    public void start(final BundleContext bundleContext) {
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

    public void stop(BundleContext bundleContext) {
        tracker.close();
        if (appender != null) {
            appender.close();
        }
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
                String host = config != null ? (String)config.get("host") : "localhost";
                int port = config != null ? Integer.parseInt((String)config.get("port")) : 9300;
                String clusterName = (config != null && config.get("clusterName") != null) ? (String)config.get("clusterName") : "elasticsearch";
                appender = new ElasticsearchAppender(marshaller, host, port, clusterName);
                appender.open();
                Dictionary<String, String> properties = new Hashtable<>();
                properties.put(EventConstants.EVENT_TOPIC, "decanter/collect/*");
                serviceReg =  bundleContext.registerService(EventHandler.class, appender, properties);
            }
        }
    }
}
