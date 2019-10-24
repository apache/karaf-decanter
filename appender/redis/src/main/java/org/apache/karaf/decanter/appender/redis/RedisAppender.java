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
package org.apache.karaf.decanter.appender.redis;

import org.apache.karaf.decanter.appender.utils.EventFilter;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.redisson.Config;
import org.redisson.Redisson;
import org.redisson.RedissonClient;

import java.util.Dictionary;
import java.util.Map;

/**
 * Redis appender
 */
@Component(
        configurationPid = "org.apache.karaf.decanter.appender.redis",
        service = EventHandler.class,
        property = {EventConstants.EVENT_TOPIC + "=decanter/collect/*" }
)
public class RedisAppender implements EventHandler {

    public static final String ADDRESS_PROPERTY = "address";
    public static final String MODE_PROPERTY = "mode";
    public static final String MAP_PROPERTY = "map";
    public static final String MASTER_ADDRESS_PROPERTY = "masterAddress";
    public static final String MASTER_NAME_PROPERTY = "masterName";
    public static final String SCAN_INTERVAL_PROPERTY = "scanInterval";

    public static final String ADDRESS_DEFAULT = "localhost:6379";
    public static final String MODE_DEFAULT = "Single";
    public static final String MAP_DEFAULT = "Decanter";
    public static final String MASTER_ADDRESS_DEFAULT = null;
    public static final String MASTER_NAME_DEFAULT = null;
    public static final String SCAN_INTERVAL_DEFAULT = "2000";

    private RedissonClient redissonClient;

    private Dictionary<String, Object> config;

    @Activate
    public void activate(ComponentContext componentContext) {
        config = componentContext.getProperties();

        String address = getValue(config, ADDRESS_PROPERTY, ADDRESS_DEFAULT);
        String mode = getValue(config, MODE_PROPERTY, MODE_DEFAULT);
        String map = getValue(config, MAP_PROPERTY, MAP_DEFAULT);
        String masterAddress = getValue(config, MASTER_ADDRESS_PROPERTY, MASTER_ADDRESS_DEFAULT);
        String masterName = getValue(config, MASTER_NAME_PROPERTY, MASTER_NAME_DEFAULT);
        int scanInterval = Integer.parseInt(getValue(config, SCAN_INTERVAL_PROPERTY, SCAN_INTERVAL_DEFAULT));

        Config redissonConfig = new Config();
        if (mode.equalsIgnoreCase("Single")) {
            redissonConfig.useSingleServer().setAddress(address);
        } else if (mode.equalsIgnoreCase("Master_Slave")) {
            redissonConfig.useMasterSlaveServers().setMasterAddress(masterAddress).addSlaveAddress(address);
        } else if (mode.equalsIgnoreCase("Sentinel")) {
            redissonConfig.useSentinelServers().addSentinelAddress(masterName).addSentinelAddress(address);
        } else if (mode.equalsIgnoreCase("Cluster")) {
            redissonConfig.useClusterServers().setScanInterval(scanInterval).addNodeAddress(address);
        }
        redissonClient = Redisson.create(redissonConfig);
    }

    @Deactivate
    public void deactivate() {
        if (redissonClient != null && (!redissonClient.isShutdown() || !redissonClient.isShuttingDown())) {
            redissonClient.shutdown();
        }
    }

    @Override
    public void handleEvent(Event event) {
        if (EventFilter.match(event, config)) {
            Map<String, Object> redisMap = redissonClient.getMap(getValue(config, MAP_PROPERTY, MAP_DEFAULT));
            for (String name : event.getPropertyNames()) {
                redisMap.put(name, event.getProperty(name));
            }
        }
    }

    private String getValue(Dictionary<String, Object> properties, String key, String defaultValue) {
        return (properties.get(key) != null) ? (String) properties.get(key) : defaultValue;
    }

}
