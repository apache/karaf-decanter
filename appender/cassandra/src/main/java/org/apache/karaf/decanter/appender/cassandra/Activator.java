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
package org.apache.karaf.decanter.appender.cassandra;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Activator implements BundleActivator {

	private final static Logger LOGGER = LoggerFactory.getLogger(Activator.class);
	private static final String CONFIG_PID = "org.apache.karaf.decanter.appender.cassandra";
	private ServiceTracker<Marshaller, ServiceRegistration> tracker;

    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        LOGGER.debug("Starting Decanter Cassandra appender");
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

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        LOGGER.debug("Stopping Decanter JDBC appender");
        tracker.close();
    }

    private final class ConfigUpdater implements ManagedService {

		private BundleContext bundleContext;
        private Marshaller marshaller;
        private ServiceRegistration registration;
        

		public ConfigUpdater(final BundleContext bundleContext, Marshaller marshaller) {
            this.bundleContext = bundleContext;
            this.marshaller = marshaller;
        }

		@Override
		public void updated(Dictionary config) throws ConfigurationException {
			LOGGER.debug("Updating Decanter Cassandra managed service");
			
			if (registration != null) {
                registration.unregister();
            }

			if (config == null) {
                return;
            }
			
			String keyspace = "decanter";
			if (config != null && config.get("keyspace.name") != null) {
                keyspace = (String) config.get("keyspace.name");
            }
			String tableName = "decanter";
			if (config != null && config.get("table.name") != null) {
				tableName = (String) config.get("table.name");
			}
			
			String cassandraHost = "localhost";
			if (config != null && config.get("cassandra.host") != null) {
			    cassandraHost = (String) config.get("cassandra.host");
			}
			
			Integer cassandraPort = 9042;
			if (config != null && config.get("cassandra.port") != null) {
			    cassandraPort = (Integer) config.get("cassandra.port");
			}
			
			try {
				CassandraAppender appender = new CassandraAppender(marshaller, keyspace, tableName, cassandraHost, cassandraPort);
				Dictionary<String, String> properties = new Hashtable<>();
				properties.put(EventConstants.EVENT_TOPIC, "decanter/collect/*");
				this.registration = bundleContext.registerService(EventHandler.class, appender, properties);
				LOGGER.debug("Decanter Cassandra appender started ({}/{})", keyspace, tableName);
			} catch (Exception e) {
				LOGGER.error("Can't start Decanter Cassandra service tracker", e);
				throw new ConfigurationException("table.name", "Can't start Decanter Cassandra service tracker: " + e.getMessage());
			}
		}

	}

}
