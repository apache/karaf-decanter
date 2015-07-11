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

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

public class Activator implements BundleActivator {

    private ElasticsearchAppender appender;
    private ServiceRegistration registration;
    private static final String CONFIG_PID = "org.apache.karaf.decanter.appender.elasticsearch";

    public void start(final BundleContext bundleContext) {
        Dictionary<String, String> properties = new Hashtable<>();
        properties.put(Constants.SERVICE_PID, CONFIG_PID);
        registration = bundleContext.registerService(ManagedService.class.getName(), new ConfigUpdater(bundleContext),
                                      properties);
    }

    public void stop(BundleContext bundleContext) {
        if (appender != null) {
            appender.close();
        }
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

        @SuppressWarnings("rawtypes")
        @Override
        public void updated(Dictionary config) throws ConfigurationException {
            if (appender != null) {
                appender.close();
                serviceReg.unregister();
            }

            String host = config != null ? (String)config.get("host") : "localhost";
            int port = config != null ? Integer.parseInt((String)config.get("port")) : 9300;
            appender = new ElasticsearchAppender(host, port);
            appender.open();
            Dictionary<String, String> properties = new Hashtable<>();
            properties.put(EventConstants.EVENT_TOPIC, "decanter/*");
        	serviceReg =  bundleContext.registerService(EventHandler.class, appender, properties);
        }
    }
}
