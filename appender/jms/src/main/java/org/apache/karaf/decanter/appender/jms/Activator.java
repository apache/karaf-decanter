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

import java.util.Dictionary;
import java.util.Hashtable;

import javax.jms.ConnectionFactory;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class Activator implements BundleActivator {
    private final static Logger LOGGER = LoggerFactory.getLogger(Activator.class);
    private final static String CONFIG_PID = "org.apache.karaf.decanter.appender.jms";

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        LOGGER.debug("Starting Decanter JMS appender");
        ConfigUpdater configUpdater = new ConfigUpdater(bundleContext);
        Dictionary<String, String> properties = new Hashtable<>();
        properties.put(Constants.SERVICE_PID, CONFIG_PID);
        bundleContext.registerService(ManagedService.class.getName(), configUpdater, properties);
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        LOGGER.debug("Stopping Decanter JMS appender");
        // Services will be unregistered automatically
    }

    private final class ConfigUpdater implements ManagedService {
        private BundleContext bundleContext;
        private ServiceTracker<ConnectionFactory, ServiceRegistration> dsTracker;

        public ConfigUpdater(BundleContext bundleContext) {
            this.bundleContext = bundleContext;
        }

        @Override
        public void updated(Dictionary config) throws ConfigurationException {
            LOGGER.debug("Updating Decanter JMS appender");
            if (dsTracker != null) {
                dsTracker.close();
                dsTracker = null;
            }
            if (config == null) {
                return;
            }
            final String cfName = getProperty(config, "connection.factory.name", "jms/decanter");
            final String username = getProperty(config, "username", null);
            final String password = getProperty(config, "password", null);
            final String destinationName = getProperty(config, "destination.name", "decanter");
            final String destinationType = getProperty(config, "destination.type", "queue");
            final String filterSt = "(&(" + Constants.OBJECTCLASS + "=" + ConnectionFactory.class.getName() + ")"
                + "(|(osgi.jndi.service.name=" + cfName + ")(name=" + cfName + ")(service.id=" + cfName + ")))";
            Filter filter;
            try {
                filter = bundleContext.createFilter(filterSt);
            } catch (InvalidSyntaxException e) {
                throw new ConfigurationException("connection.factory.name", "Unable to create filter " + filterSt, e);
            }
            LOGGER.info("Tracking ConnectionFactory " + filterSt);
            dsTracker = new ServiceTracker<ConnectionFactory, ServiceRegistration>(bundleContext, filter, null) {
                
                @Override
                public ServiceRegistration addingService(ServiceReference<ConnectionFactory> reference) {
                    LOGGER.info("ConnectionFactory acquired. Starting JMS appender ({} , {})", cfName, destinationName);
                    ConnectionFactory cf = context.getService(reference);
                    JmsAppender appender = new JmsAppender(cf, username, password, destinationName, destinationType);
                    Dictionary<String, String> properties = new Hashtable<>();
                    properties.put(EventConstants.EVENT_TOPIC, "decanter/collect/*");
                    return bundleContext.registerService(EventHandler.class, appender, properties);
                }

                @Override
                public void removedService(ServiceReference<ConnectionFactory> reference, ServiceRegistration serviceReg) {
                    serviceReg.unregister();
                    super.removedService(reference, serviceReg);
                }
                
            };
            dsTracker.open();
        }

        private String getProperty(Dictionary properties, String key, String defaultValue) {
            return (properties.get(key) != null) ? (String) properties.get(key) : defaultValue;
        }
    }

}
