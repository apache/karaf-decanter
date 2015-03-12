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
package org.apache.karaf.decanter.collector.log;

import java.util.Dictionary;
import java.util.Properties;

import org.apache.karaf.decanter.api.Collector;
import org.apache.karaf.decanter.api.Dispatcher;
import org.ops4j.pax.logging.spi.PaxAppender;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;

@SuppressWarnings("rawtypes")
public class Activator implements BundleActivator {
    private ServiceTracker<Dispatcher, ServiceRegistration> tracker;

    public void start(final BundleContext bundleContext) {
        tracker = new ServiceTracker<Dispatcher, ServiceRegistration>(bundleContext, Dispatcher.class, null) {

            @SuppressWarnings("unchecked")
            @Override
            public ServiceRegistration<?> addingService(ServiceReference<Dispatcher> reference) {
                Properties properties = new Properties();
                properties.put("org.ops4j.pax.logging.appender.name", "DecanterLogCollectorAppender");
                properties.put("name", "log");
                String[] ifAr = new String[] { PaxAppender.class.getName(), Collector.class.getName() };
                Dispatcher dispatcher = bundleContext.getService(reference);
                LogAppender appender = new LogAppender(dispatcher);
                return bundleContext.registerService(ifAr , appender, (Dictionary) properties);
            }

            @Override
            public void removedService(ServiceReference<Dispatcher> reference, ServiceRegistration reg) {
                reg.unregister();
                super.removedService(reference, reg);
            }
            
        };
        tracker.open();
    }

    public void stop(BundleContext bundleContext) {
        tracker.close();
    }

}
