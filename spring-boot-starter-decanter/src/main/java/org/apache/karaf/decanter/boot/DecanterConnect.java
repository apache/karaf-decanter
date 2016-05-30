/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.karaf.decanter.boot;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.annotation.PreDestroy;

import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.event.EventAdmin;
import org.osgi.util.tracker.ServiceTracker;

@org.springframework.context.annotation.Configuration
public class DecanterConnect {
    private BundleContext registryContext;

    public DecanterConnect() throws Exception {
        registryContext = new DecanterRegistryFactory().create();
        Dictionary<String, String> kafka = new Hashtable<>();
        kafka.put("bootstrap.servers", "kafka:9092");
        configure(registryContext, "org.apache.karaf.decanter.appender.kafka", kafka);
        injectEventAdmin(registryContext);
    }

    private static void configure(BundleContext context, String pid, Dictionary<String, String> properties)
        throws IOException {
        ServiceTracker<ConfigurationAdmin, ConfigurationAdmin> tracker = new ServiceTracker<>(context,
                                                                                              ConfigurationAdmin.class,
                                                                                              null);
        tracker.open();
        ConfigurationAdmin configAdmin = tracker.getService();
        Configuration config = configAdmin.getConfiguration(pid);
        config.update(properties);
        tracker.close();
    }

    private static void injectEventAdmin(BundleContext context) {
        ServiceTracker<EventAdmin, EventAdmin> tracker = new ServiceTracker<>(context, EventAdmin.class,
                                                                              null);
        tracker.open();
        EventAdmin eventAdmin = tracker.getService();
        LogbackDecanterAppender.setDispatcher(eventAdmin);
        tracker.close();
    }

    @PreDestroy
    public void close() {
        try {
            registryContext.getBundle(0).stop();
        } catch (BundleException e) {
        }
    }
}
