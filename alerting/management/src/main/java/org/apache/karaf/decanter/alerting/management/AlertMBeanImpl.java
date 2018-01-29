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
package org.apache.karaf.decanter.alerting.management;

import org.apache.karaf.decanter.alerting.checker.AlertStore;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.management.MBeanException;
import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;
import java.util.Set;

@Component(
        name = "org.apache.karaf.decanter.alerting.management",
        immediate = true,
        property = "jmx.objectname=org.apache.karaf.decanter:type=alerting,name=default"
)
public class AlertMBeanImpl extends StandardMBean implements AlertMBean {

    @Reference
    AlertStore store;

    public AlertMBeanImpl() throws NotCompliantMBeanException {
        super(AlertMBean.class);
    }

    @Override
    public Set<String> getAlerts(String level) throws MBeanException {
        return store.list(AlertStore.Level.valueOf(level));
    }

    @Override
    public void add(String name, String level) throws MBeanException {
        store.add(name, AlertStore.Level.valueOf(level));
    }

    @Override
    public void remove(String name, String level) throws MBeanException {
        store.remove(name, AlertStore.Level.valueOf(level));
    }
}
