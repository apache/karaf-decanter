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
package org.apache.karaf.decanter.sla.checker;

import org.osgi.framework.*;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.util.tracker.ServiceTracker;

import java.util.Dictionary;
import java.util.Hashtable;

public class Activator implements BundleActivator {

    private ServiceTracker<EventAdmin, ServiceRegistration> tracker;

    public void start(final BundleContext bundleContext) throws Exception {
        tracker = new ServiceTracker<EventAdmin, ServiceRegistration>(bundleContext, EventAdmin.class, null) {

            @Override
            public ServiceRegistration<?> addingService(ServiceReference<EventAdmin> reference) {
                EventAdmin eventAdmin = bundleContext.getService(reference);
                ConfigUpdater configManager = new ConfigUpdater(bundleContext, eventAdmin);
                Hashtable<String, Object> properties = new Hashtable<String, Object>();
                properties.put(Constants.SERVICE_PID, "org.apache.karaf.decanter.sla.checker");
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

    private final class ConfigUpdater implements ManagedService {

        private BundleContext bundleContext;
        private EventAdmin eventAdmin;
        private ServiceRegistration<?> serviceReg;
        private Checker checker;

        public ConfigUpdater(BundleContext bundleContext, EventAdmin eventAdmin) {
            this.bundleContext = bundleContext;
            this.eventAdmin = eventAdmin;
        }

        @Override
        public void updated(Dictionary config) throws ConfigurationException {
            if (checker != null) {
                serviceReg.unregister();
            }
            checker = new Checker(config, eventAdmin);
            Dictionary<String, String> properties = new Hashtable<>();
            properties.put(EventConstants.EVENT_TOPIC, "decanter/collect/*");
            serviceReg = bundleContext.registerService(EventHandler.class, checker, properties);
        }

    }

}
