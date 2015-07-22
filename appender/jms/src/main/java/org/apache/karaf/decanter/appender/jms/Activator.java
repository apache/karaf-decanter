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
package org.apache.karaf.decanter.appender.jms;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Hashtable;

public class Activator implements BundleActivator {

    private final static Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    private final static String CONFIG_PID = "org.apache.karaf.decanter.appender.jms";

    private ServiceRegistration serviceRegistration;

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        LOGGER.debug("Starting Decanter JMS appender");
        ConfigUpdater configUpdater = new ConfigUpdater(bundleContext);
        Dictionary<String, String> properties = new Hashtable<>();
        properties.put(Constants.SERVICE_PID, CONFIG_PID);
        serviceRegistration = bundleContext.registerService(ManagedService.class.getName(), configUpdater, properties);
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        LOGGER.debug("Stopping Decanter JMS appender");
        if (serviceRegistration != null) {
            serviceRegistration.unregister();
        }
    }

    private final class ConfigUpdater implements ManagedService {

        private BundleContext bundleContext;
        private ServiceRegistration registration;

        public ConfigUpdater(BundleContext bundleContext) {
            this.bundleContext = bundleContext;
        }

        @Override
        public void updated(Dictionary config) throws ConfigurationException {
            LOGGER.debug("Updating Decanter JMS appender");
            if (registration != null) {
                registration.unregister();
            }
            String connectionFactoryName = "jms/decanter";
            if (config != null && config.get("connection.factory.name") != null) {
                connectionFactoryName = (String) config.get("connection.factory.name");
            }
            String username = null;
            if (config != null && config.get("username") != null) {
                username = (String) config.get("username");
            }
            String password = null;
            if (config != null && config.get("password") != null) {
                password = (String) config.get("password");
            }
            String destinationName = "decanter";
            if (config != null && config.get("destination.name") != null) {
                destinationName = (String) config.get("destination.name");
            }
            String destinationType = "queue";
            if (config != null && config.get("destination.type") != null) {
                destinationType = (String) config.get("destination.type");
            }
            JmsAppender appender = new JmsAppender(bundleContext, connectionFactoryName, username, password, destinationName, destinationType);
            Dictionary<String, String> properties = new Hashtable<>();
            properties.put(EventConstants.EVENT_TOPIC, "decanter/collect/*");
            this.registration = bundleContext.registerService(EventHandler.class, appender, properties);
            LOGGER.debug("Decanter JMS appender started ({}/{})", connectionFactoryName, destinationName);
        }

    }

}
