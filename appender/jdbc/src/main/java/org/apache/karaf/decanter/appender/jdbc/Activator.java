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
package org.apache.karaf.decanter.appender.jdbc;

import org.osgi.framework.*;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.Dictionary;
import java.util.Hashtable;

public class Activator implements BundleActivator {

	private final static Logger LOGGER = LoggerFactory.getLogger(Activator.class);

	private static final String CONFIG_PID = "org.apache.karaf.decanter.appender.jdbc";

	private ServiceRegistration serviceRegistration;

	@Override
	public void start(final BundleContext bundleContext) throws Exception {
		LOGGER.debug("Starting Decanter JDBC appender");

		ConfigUpdater configUpdater = new ConfigUpdater(bundleContext);

		Dictionary<String, String> properties = new Hashtable<>();
		properties.put(Constants.SERVICE_PID, CONFIG_PID);
		serviceRegistration = bundleContext.registerService(ManagedService.class.getName(), configUpdater, properties);
	}

	@Override
	public void stop(BundleContext bundleContext) throws Exception {
		LOGGER.debug("Stopping Decanter JDBC appender");

		if (serviceRegistration != null) {
			serviceRegistration.unregister();
		}
	}

	private final class ConfigUpdater implements ManagedService {

		private BundleContext bundleContext;
		private ServiceRegistration registration;

		public ConfigUpdater(final BundleContext bundleContext) throws Exception {
			this.bundleContext = bundleContext;
		}

		@Override
		public void updated(Dictionary config) throws ConfigurationException {
			LOGGER.debug("Updating Decanter JDBC managed service");

			if (registration != null) {
				registration.unregister();
			}

			String dataSourceName = "jdbc/decanter";
			if (config != null && config.get("datasource.name") != null) {
				dataSourceName = (String) config.get("datasource.name");
			}
			String tableName = "decanter";
			if (config != null && config.get("table.name") != null) {
				tableName = (String) config.get("table.name");
			}
			String dialect = "generic";
			if (config != null && config.get("dialect") != null) {
				dialect = (String) config.get("dialect");
			}
			try {
				JdbcAppender appender = new JdbcAppender(dataSourceName, tableName, dialect, bundleContext);
				Dictionary<String, String> properties = new Hashtable<>();
				properties.put(EventConstants.EVENT_TOPIC, "decanter/collect/*");
				this.registration = bundleContext.registerService(EventHandler.class, appender, properties);
				LOGGER.debug("Decanter JDBC appender started ({}/{})", dataSourceName, tableName);
			} catch (Exception e) {
				LOGGER.error("Can't start Decanter JDBC service tracker", e);
				throw new ConfigurationException("table.name", "Can't start Decanter JDBC service tracker: " + e.getMessage());
			}
		}

	}

}
