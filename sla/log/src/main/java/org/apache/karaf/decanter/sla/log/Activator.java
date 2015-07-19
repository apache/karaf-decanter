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
package org.apache.karaf.decanter.sla.log;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import java.util.Dictionary;
import java.util.Hashtable;

public class Activator implements BundleActivator {

    private ServiceRegistration registration;

    public void start(BundleContext bundleContext) {
        Logger logger = new Logger();
        Dictionary<String, String> properties = new Hashtable<>();
        properties.put(EventConstants.EVENT_TOPIC, "decanter/alert/*");
        registration = bundleContext.registerService(EventHandler.class, logger, properties);
    }

    public void stop(BundleContext bundleContext) {
        if (registration != null) {
            registration.unregister();
        }
    }

}
