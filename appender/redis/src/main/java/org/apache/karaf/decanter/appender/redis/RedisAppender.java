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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final static Logger LOGGER = LoggerFactory.getLogger(RedisAppender.class);

    private String address;
    private String mode;
    private String map;
    private String masterAddress;
    private String masterName;
    private int scanInterval;

    private RedissonClient redissonClient;

    @Activate
    public void activate(ComponentContext componentContext) {
        Dictionary<String, Object> properties = componentContext.getProperties();
        address = getProperty(properties, "address", "localhost:6379");
        mode = getProperty(properties, "mode", "Single");
        map = getProperty(properties, "map", "Decanter");
        masterAddress = getProperty(properties, "masterAddress", null);
        masterName = getProperty(properties, "masterName", null);
        scanInterval = Integer.parseInt(getProperty(properties, "scanInterval", "2000"));

        Config config = new Config();
        if (mode.equalsIgnoreCase("Single")) {
            config.useSingleServer().setAddress(address);
        } else if (mode.equalsIgnoreCase("Master_Slave")) {
            config.useMasterSlaveServers().setMasterAddress(masterAddress).addSlaveAddress(address);
        } else if (mode.equalsIgnoreCase("Sentinel")) {
            config.useSentinelServers().addSentinelAddress(masterName).addSentinelAddress(address);
        } else if (mode.equalsIgnoreCase("Cluster")) {
            config.useClusterServers().setScanInterval(scanInterval).addNodeAddress(address);
        }
        redissonClient = Redisson.create(config);
    }

    @Deactivate
    public void deactivate() {
        if (redissonClient != null && (!redissonClient.isShutdown() || !redissonClient.isShuttingDown())) {
            redissonClient.shutdown();
        }
    }

    @Override
    public void handleEvent(Event event) {
        Map<String, Object> redisMap = redissonClient.getMap(this.map);
        for (String name : event.getPropertyNames()) {
            redisMap.put(name, event.getProperty(name));
        }
    }

    private String getProperty(Dictionary<String, Object> properties, String key, String defaultValue) {
        return (properties.get(key) != null) ? (String) properties.get(key) : defaultValue;
    }

}
