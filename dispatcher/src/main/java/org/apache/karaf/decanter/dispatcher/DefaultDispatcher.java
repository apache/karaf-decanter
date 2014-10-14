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
package org.apache.karaf.decanter.dispatcher;

import org.apache.karaf.decanter.api.Appender;
import org.apache.karaf.decanter.api.Dispatcher;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;

/**
 * Default dispatcher
 */
public class DefaultDispatcher implements Dispatcher {

    private final static Logger LOGGER = LoggerFactory.getLogger(DefaultDispatcher.class);

    private BundleContext bundleContext;

    public DefaultDispatcher(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void dispatch(Map<Long, Map<String, Object>> data) throws Exception {
        LOGGER.debug("Dispatching collected data");

        Collection<ServiceReference<Appender>> references = bundleContext.getServiceReferences(Appender.class, null);
        if (references != null) {
            for (ServiceReference reference : references) {
                try {
                    Appender appender = (Appender) bundleContext.getService(reference);
                    appender.append(data);
                } catch (Exception e) {
                    LOGGER.warn("Can't dispatch collected data", e);
                } finally {
                    bundleContext.ungetService(reference);
                }
            }
        }
        LOGGER.debug("Dispatching done");
    }

    public BundleContext getBundleContext() {
        return bundleContext;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

}
