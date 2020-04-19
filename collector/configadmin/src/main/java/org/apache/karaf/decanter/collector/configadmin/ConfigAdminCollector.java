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
package org.apache.karaf.decanter.collector.configadmin;

import org.apache.karaf.decanter.collector.utils.PropertiesPreparator;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationListener;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Component(
        name = "org.apache.karaf.decanter.collector.configadmin",
        immediate = true
)
public class ConfigAdminCollector implements ConfigurationListener {

    private final static Logger LOGGER = LoggerFactory.getLogger(ConfigAdminCollector.class);

    @Reference
    private EventAdmin dispatcher;

    @Reference
    private ConfigurationAdmin configurationAdmin;

    private Dictionary<String, Object> properties;

    @Activate
    public void activate(ComponentContext componentContext) {
        activate(componentContext.getProperties());
    }

    public void activate(Dictionary<String, Object> properties) {
        this.properties = properties;
    }

    @Override
    public void configurationEvent(ConfigurationEvent configurationEvent) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", "configadmin");

        data.put("factoryPid", configurationEvent.getFactoryPid());
        data.put("pid", configurationEvent.getPid());
        data.put("changeInt", configurationEvent.getType());
        if (configurationEvent.getType() == ConfigurationEvent.CM_DELETED) {
            data.put("change", "deleted");
        }
        if (configurationEvent.getType() == ConfigurationEvent.CM_UPDATED) {
            data.put("change", "updated");
        }
        if (configurationEvent.getType() == ConfigurationEvent.CM_LOCATION_CHANGED) {
            data.put("change", "locationChanged");
        }

        try {
            Configuration configuration = configurationAdmin.getConfiguration(configurationEvent.getPid(), null);
            Dictionary props = configuration.getProperties();
            if (props != null) {
                Enumeration<String> keys = props.keys();
                while (keys.hasMoreElements()) {
                    String key = keys.nextElement();
                    Object value = props.get(key);
                    data.put(key, value);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Can't retrieve configuration", e);
        }

        try {
            PropertiesPreparator.prepare(data, properties);
        } catch (Exception e) {
            // nothing to do
        }

        dispatcher.postEvent(new Event("decanter/collect/configadmin", data));
    }

}
