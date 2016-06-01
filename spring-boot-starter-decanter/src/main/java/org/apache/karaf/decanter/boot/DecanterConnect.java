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

import javax.annotation.PreDestroy;

import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.service.event.EventAdmin;
import org.osgi.util.tracker.ServiceTracker;

@org.springframework.context.annotation.Configuration
public class DecanterConnect {
    private BundleContext registryContext;

    public DecanterConnect() throws Exception {
        registryContext = new DecanterRegistryFactory().create();
        LogbackDecanterAppender.setDispatcher(getService(registryContext, EventAdmin.class));
    }

    private <S>S getService(BundleContext context, Class<S> serviceClazz) {
        ServiceTracker<S, S> tracker = new ServiceTracker<S, S>(context, serviceClazz, null);
        try {
            tracker.open();
            return tracker.getService();
        } finally {
            tracker.close();
        }
    }

    @PreDestroy
    public void close() {
        try {
            registryContext.getBundle(0).stop();
        } catch (BundleException e) {
        }
    }
}
