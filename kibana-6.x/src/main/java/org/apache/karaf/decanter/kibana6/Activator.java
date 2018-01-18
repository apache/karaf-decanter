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
package org.apache.karaf.decanter.kibana6;

import org.apache.karaf.features.FeatureEvent;
import org.apache.karaf.features.FeaturesListener;
import org.apache.karaf.features.RepositoryEvent;
import org.eclipse.jetty.proxy.ProxyServlet;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.http.HttpService;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Dictionary;
import java.util.Hashtable;

public class Activator implements BundleActivator {

    private final static Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    private ServiceTracker httpTracker;
    private KibanaController kibanaController;

    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        kibanaController = new KibanaController(new File(new File(System.getProperty("karaf.data"), "decanter"), "kibana"));
        System.out.println("Downloading Kibana ...");
        LOGGER.info("Downloading Kibana ...");
        kibanaController.download();
        System.out.println("Starting Kibana ...");
        LOGGER.info("Starting Kibana ...");
        kibanaController.start();

        httpTracker = new ServiceTracker(bundleContext, HttpService.class.getName(), null) {
            @Override
            public Object addingService(ServiceReference ref) {
                HttpService httpService = (HttpService) bundleContext.getService(ref);
                try {
                    Dictionary<String, String> kibanaParams = new Hashtable<>();
                    kibanaParams.put("proxyTo", "http://localhost:5601/");
                    kibanaParams.put("prefix", "/kibana");
                    System.out.println("Starting Kibana proxy");
                    LOGGER.info("Starting Kibana proxy ...");
                    httpService.registerServlet("/kibana", new ProxyServlet.Transparent(), kibanaParams, null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return httpService;
            }

            public void removedService(ServiceReference ref, Object service) {
                try {
                    LOGGER.info("Stopping Kibana proxy ...");
                    ((HttpService) service).unregister("/kibana");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        httpTracker.open();

        int httpPort = 8181;
        try {
            ServiceTracker configAdminTracker = new ServiceTracker(bundleContext, ConfigurationAdmin.class, null);
            configAdminTracker.open();
            ConfigurationAdmin configurationAdmin = (ConfigurationAdmin) configAdminTracker.waitForService(1000);
            Configuration paxWebConfiguration = configurationAdmin.getConfiguration("org.ops4j.pax.web");
            if (paxWebConfiguration != null) {
                if (paxWebConfiguration.getProperties().get("org.osgi.service.http.port") != null) {
                    httpPort = Integer.parseInt(paxWebConfiguration.getProperties().get("org.osgi.service.http.port").toString());
                }
            }
            configAdminTracker.close();
        } catch (Exception e) {
            LOGGER.warn("Can't get HTTP service port", e);
        }

        CollectorListener listener = new CollectorListener(httpPort);
        bundleContext.registerService(FeaturesListener.class, listener, null);
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        httpTracker.close();
        System.out.println("Stopping Kibana ...");
        LOGGER.info("Stopping Kibana ...");
        kibanaController.stop();
    }

    class CollectorListener implements FeaturesListener {

        private int httpPort;

        public CollectorListener(int httpPort) {
            this.httpPort = httpPort;
        }

        @Override
        public void featureEvent(FeatureEvent event) {
            if (event.getType().equals(FeatureEvent.EventType.FeatureInstalled)) {
                if (event.getFeature().getName().equalsIgnoreCase("decanter-collector-log")) {
                    LOGGER.debug("Decanter Kibana detected installation of the decanter-collector-log feature");
                    try {
                        kibanaController.createDashboardLog(httpPort);
                    } catch (Exception e) {
                        LOGGER.warn("Can't create Kibana Log dashboard", e);
                    }
                }
                if (event.getFeature().getName().equalsIgnoreCase("decanter-collector-jmx-core")) {
                    LOGGER.debug("Decanter Kibana detected installation of the decanter-collector-jmx-core feature");
                    try {
                        kibanaController.createDashboardJmx(httpPort);
                    } catch (Exception e) {
                        LOGGER.warn("Can't create Kibana JMX dashboard", e);
                    }
                }
            }
        }

        @Override
        public void repositoryEvent(RepositoryEvent repositoryEvent) {
            // nothing to do
        }

    }
}
