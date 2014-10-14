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

import org.apache.karaf.decanter.api.Appender;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.Dictionary;
import java.util.Properties;

public class Activator implements BundleActivator {

    private ServiceRegistration service;

    public void start(BundleContext bundleContext) {
        Appender appender = new ElasticsearchAppender();
        Properties properties = new Properties();
        properties.put("name", "elasticsearch");
        service = bundleContext.registerService(Appender.class, appender, (Dictionary) properties);
    }

    public void stop(BundleContext bundleContext) {
        if (service != null) {
            service.unregister();
        }
    }

}
