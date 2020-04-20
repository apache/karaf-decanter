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
package org.apache.karaf.decanter.collector.redis;

import org.apache.karaf.decanter.collector.utils.PropertiesPreparator;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.redisson.Redisson;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

@Component(
        name = "org.apache.karaf.decanter.collector.redis",
        immediate = true,
        property = { "decanter.collector.name=redis",
                "scheduler.period:Long=60",
                "scheduler.concurrent:Boolean=false",
                "scheduler.name=decanter-collector-redis"}
)
public class RedisCollector implements Runnable {

    private final static Logger LOGGER = LoggerFactory.getLogger(RedisCollector.class);

    @Reference
    private EventAdmin dispatcher;

    public static final String ADDRESS_DEFAULT = "localhost:6379";
    public static final String MODE_DEFAULT = "Single";

    private RedissonClient redissonClient;

    private Dictionary<String, Object> config;

    @Activate
    public void activate(ComponentContext componentContext) {
        config = componentContext.getProperties();

        String address = (config.get("address") != null) ? config.get("address").toString() : ADDRESS_DEFAULT;
        String mode = (config.get("map") != null) ? config.get("map").toString() : MODE_DEFAULT;
        String masterAddress = (config.get("masterAddress") != null) ? config.get("masterAddress").toString() : null;
        String masterName = (config.get("masterName") != null) ? config.get("masterName").toString() : null;
        int scanInterval = (config.get("scanInterval") != null) ? Integer.parseInt(config.get("scanInterval").toString()) : 2000;

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
    public void run() {
        Map<String, Object> data = new HashMap<>();
        data.put("type", "redis");
        String map = (config.get("map") != null) ? config.get("map").toString() : "Decanter";
        String keyPattern = (config.get("keyPattern") != null) ? config.get("keyPattern").toString() : "*";
        RMap rmap = redissonClient.getMap(map);
        for (Object key : rmap.keySet(keyPattern)) {
            data.put(key.toString(), rmap.get(key));
        }
        try {
            PropertiesPreparator.prepare(data, config);
        } catch (Exception e) {
            LOGGER.warn("Can't prepare data", e);
        }
        dispatcher.postEvent(new Event("decanter/collect/redis", data));
    }

}
