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
package org.apache.karaf.decanter.elasticsearch;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.CancellationException;

import org.elasticsearch.node.Node;
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

public class Activator implements BundleActivator {

	private static final String CONFIG_PID = "org.apache.karaf.decanter.elasticsearch";
	
	private final static Logger LOGGER = LoggerFactory.getLogger(Activator.class);
	
    private EmbeddedNode node;
    private ServiceRegistration service;
    private ServiceRegistration registration;

    public void start(BundleContext bundleContext) throws Exception {
        Dictionary<String, String> properties = new Hashtable<>();
        properties.put(Constants.SERVICE_PID, CONFIG_PID);
        registration = bundleContext.registerService(ManagedService.class.getName(), new ConfigUpdater(bundleContext), properties);
    }

    public void stop(BundleContext bundleContext) throws Exception {
        if (node != null) {
            node.stop();
        }
        if (service != null) {
            service.unregister();
        }
        if (registration != null) {
            registration.unregister();
        }
    }
    
    private final class ConfigUpdater implements ManagedService {
        private BundleContext bundleContext;

        public ConfigUpdater(BundleContext bundleContext) {
            this.bundleContext = bundleContext;
        }

        @Override
        public void updated(Dictionary<String, ?> config) throws ConfigurationException {
            if (node != null) {
                try {
					node.stop();
				} catch (Exception e) {
					String message = "Failed to stop embedded elasticsearch node";
					LOGGER.error(message, e);
					throw new ConfigurationException(null, message, e);
				}
                service.unregister();
                node = null;
            }
        	
        	if (node == null) {
                try {
					node = new EmbeddedNode(config);
				} catch (Exception e) {
					String message = "Failed to create embedded elasticsearch node";
					LOGGER.error(message, e);
					throw new ConfigurationException(null, message, e);
				}
            }
            service = bundleContext.registerService(Node.class, node.getNode(), null);
            try {
				node.start();
			} catch (Exception e) {
				String message = "Failed to starts embedded elasticsearch node";
				LOGGER.error(message, e);
				throw new ConfigurationException(null, message, e);
			}
        }
    }

}
