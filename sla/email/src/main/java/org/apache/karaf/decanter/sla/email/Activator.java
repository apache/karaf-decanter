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
package org.apache.karaf.decanter.sla.email;

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

    private ServiceRegistration serviceRegistration;

    private final static String CONFIG_PID = "org.apache.karaf.decanter.sla.email";

    @Override
    public void start(BundleContext bundleContext) {
        Dictionary<String, String> properties = new Hashtable<>();
        properties.put(Constants.SERVICE_PID, CONFIG_PID);
        serviceRegistration = bundleContext.registerService(ManagedService.class.getName(), new ConfigUpdater(bundleContext),
                properties);
    }

    @Override
    public void stop(BundleContext bundleContext) {
        if (serviceRegistration != null) {
            serviceRegistration.unregister();
        }
    }

    private class ConfigUpdater implements ManagedService {

        private BundleContext bundleContext;
        private ServiceRegistration registration;

        public ConfigUpdater(BundleContext bundleContext) {
            this.bundleContext = bundleContext;
        }

        public void updated(Dictionary config) throws ConfigurationException {
            if (registration != null) {
                registration.unregister();
            }
            if (config == null || config.get("from") == null) {
                throw new ConfigurationException("from", "from property is not defined");
            }
            String from = (String) config.get("from");
            if (config == null || config.get("to") == null) {
                throw new ConfigurationException("to", "to property is not defined");
            }
            String to = (String) config.get("to");
            if (config == null || config.get("host") == null) {
                throw new ConfigurationException("host", "host property is not defined");
            }
            String host = (String) config.get("host");
            String port = (String) config.get("port");
            String auth = (String) config.get("auth");
            String starttls = (String) config.get("starttls");
            String ssl = (String) config.get("ssl");
            String username = null;
            String password = null;
            if (config != null) {
                username = (String) config.get("username");
                password = (String) config.get("password");
            }
            EmailAlerter alerter = new EmailAlerter(from, to, host, port, auth, starttls, ssl, username, password);
            Dictionary<String, String> properties = new Hashtable<>();
            properties.put(EventConstants.EVENT_TOPIC, "decanter/alert/*");
            registration =  bundleContext.registerService(EventHandler.class, alerter, properties);
        }

    }
}
