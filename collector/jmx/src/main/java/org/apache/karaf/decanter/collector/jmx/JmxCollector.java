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
package org.apache.karaf.decanter.collector.jmx;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;

import org.apache.karaf.decanter.api.PollingCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decanter JMX Pooling Collector
 */
public class JmxCollector implements PollingCollector {

    private final static Logger LOGGER = LoggerFactory.getLogger(JmxCollector.class);

    public Map<Long, Map<String, Object>> collect() throws Exception {
        LOGGER.debug("Karaf Decanter JMX Collector starts harvesting ...");

        Map<Long, Map<String, Object>> collected = new HashMap<>();

        // TODO be able to pool remote JMX
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> names = server.queryNames(null, null);
        for (ObjectName name : names) {
            MBeanAttributeInfo[] attributes = server.getMBeanInfo(name).getAttributes();
            Map<String, Object> data = new HashMap<>();
            data.put("mbean", name.toString());
            for (MBeanAttributeInfo attribute : attributes) {
                // TODO add SLA check on attributes and filtering
                try {
                    Object attributeObject = server.getAttribute(name, attribute.getName());
                    if (attributeObject instanceof String) {
                    	data.put(attribute.getName(), (String) attributeObject);
                    } else if (attributeObject instanceof CompositeDataSupport) {
                    	CompositeDataSupport cds = (CompositeDataSupport) attributeObject;
                    	CompositeType compositeType = cds.getCompositeType();
                    	Set<String> keySet = compositeType.keySet();
                    	Map<String, Object> composite = new HashMap<String, Object>();
                    	for (String key : keySet) {
							Object cdsObject = cds.get(key);
							composite.put(key, cdsObject);
						}
                    	data.put(attribute.getName(), composite);
                    }
                } catch (Exception e) {
                    // LOGGER.warn("Can't put MBean {} attribute {} in collected data", name.toString(), attribute.getName(), e);
                }
            }
            collected.put(System.currentTimeMillis(), data);
        }

        LOGGER.debug("Karaf Decanter JMX Collector harvesting done");

        return collected;
    }

}
