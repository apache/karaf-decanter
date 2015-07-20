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
package org.apache.karaf.decanter.appender.camel;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import java.util.Dictionary;
import java.util.Hashtable;

public class Activator implements BundleActivator {

    private CamelAppender appender;
    private ServiceRegistration registration;

    private final static String CONFIG_PID = "org.apache.karaf.decanter.appender.camel";

    public void start(BundleContext bundleContext) {
        Dictionary<String, String> properties = new Hashtable<>();
        properties.put(Constants.SERVICE_PID, CONFIG_PID);
        registration = bundleContext.registerService(ManagedService.class.getName(), new ConfigUpdater(bundleContext),
                properties);
    }

    public void stop(BundleContext bundleContext) {
        if (registration != null) {
            registration.unregister();
        }
    }

    private final class ConfigUpdater implements ManagedService {

        private BundleContext bundleContext;
        private ServiceRegistration<?> serviceReg;

        public ConfigUpdater(BundleContext bundleContext) {
            this.bundleContext = bundleContext;
        }

        @Override
        public void updated(Dictionary config) throws ConfigurationException {
            if (serviceReg!= null) {
                serviceReg.unregister();
            }
            if (config.get("destination.uri") == null) {
                throw new ConfigurationException("destination.uri", "destination.uri is not defined");
            }
            String destinationUri = (String) config.get("destination.uri");
            appender = new CamelAppender(destinationUri);
            Dictionary<String, String> properties = new Hashtable<>();
            properties.put(EventConstants.EVENT_TOPIC, "decanter/collect/*");
            serviceReg =  bundleContext.registerService(EventHandler.class, appender, properties);
        }

    }

}
