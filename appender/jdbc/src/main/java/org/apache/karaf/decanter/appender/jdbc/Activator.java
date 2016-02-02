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

import java.util.Dictionary;
import java.util.Hashtable;

import javax.sql.DataSource;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);
    private static final String CONFIG_PID = "org.apache.karaf.decanter.appender.jdbc";
    private ServiceTracker<Marshaller, ServiceRegistration> tracker;

    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        LOGGER.debug("Starting Decanter JDBC appender");
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
        private ServiceTracker<DataSource, ServiceRegistration> dsTracker;

        public ConfigUpdater(final BundleContext bundleContext, Marshaller marshaller) {
            this.bundleContext = bundleContext;
            this.marshaller = marshaller;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void updated(Dictionary config) throws ConfigurationException {
            LOGGER.debug("Updating Decanter JDBC managed service");
            if (dsTracker != null) {
                dsTracker.close();
                dsTracker = null;
            }
            if (config == null) {
                return;
            }
            final String dataSourceName = getValue(config, "datasource.name", "jdbc/decanter");
            final String tableName = getValue(config, "table.name", "decanter");
            final String dialect = getValue(config, "dialect", "generic");
            final String filterSt = "(&(" + Constants.OBJECTCLASS + "=" + DataSource.class.getName() + ")"
                + "(|(osgi.jndi.service.name=" + dataSourceName + ")(datasource=" + dataSourceName + ")(name=" + dataSourceName + ")(service.id=" + dataSourceName + ")))";
            Filter filter;
            try {
                filter = bundleContext.createFilter(filterSt);
            } catch (InvalidSyntaxException e) {
                throw new ConfigurationException("datasource.name", "Unable to create DataSource filter " + filterSt, e);
            }
            LOGGER.info("Tracking DataSource " + filterSt);
            dsTracker = new ServiceTracker<DataSource, ServiceRegistration>(bundleContext, filter, null) {
                
                @Override
                public ServiceRegistration addingService(ServiceReference<DataSource> reference) {
                    LOGGER.debug("DataSource acquired. Starting JDBC appender ({}/{})", dataSourceName, tableName);
                    DataSource dataSource = context.getService(reference);
                    JdbcAppender appender = new JdbcAppender(tableName, dialect, marshaller, dataSource);
                    Dictionary<String, String> properties = new Hashtable<>();
                    properties.put(EventConstants.EVENT_TOPIC, "decanter/collect/*");
                    return bundleContext.registerService(EventHandler.class, appender, properties);
                }

                @Override
                public void removedService(ServiceReference reference, ServiceRegistration serviceReg) {
                    serviceReg.unregister();
                    super.removedService(reference, serviceReg);
                }
                
            };
            dsTracker.open();
            try {
            } catch (Exception e) {
                throw new ConfigurationException(null, "Can't start Decanter JDBC appender", e);
            }
        }

        private String getValue(Dictionary<String, Object> config, String key, String defaultValue) {
            String value = (String)config.get(key);
            return (value != null) ? value :  defaultValue;
        }
    }

}
