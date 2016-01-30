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
package org.apache.karaf.decanter.appender.kafka;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Properties;

public class Activator implements BundleActivator {

    private final static Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    private final static String CONFIG_PID = "org.apache.karaf.decanter.appender.kafka";

    private ServiceRegistration serviceRegistration;

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        LOGGER.debug("Starting Decanter Kafka appender");
        ConfigUpdater configUpdater = new ConfigUpdater(bundleContext);
        Dictionary<String, String> properties = new Hashtable<>();
        properties.put(Constants.SERVICE_PID, CONFIG_PID);
        serviceRegistration = bundleContext.registerService(ManagedService.class.getName(), configUpdater, properties);
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        LOGGER.debug("Stopping Decanter Kafka appender");
        if (serviceRegistration != null) {
            serviceRegistration.unregister();
        }
    }

    private final class ConfigUpdater implements ManagedService {

        private BundleContext bundleContext;
        private ServiceRegistration registration;

        public ConfigUpdater(BundleContext bundleContext) {
            this.bundleContext = bundleContext;
        }

        @Override
        public void updated(Dictionary config) throws ConfigurationException {
            LOGGER.debug("Updating Decanter Kafka appender");
            if (registration != null) {
                registration.unregister();
            }

            Properties kafkaProperties = new Properties();

            String bootstrapServers = "localhost:9092";
            kafkaProperties.put("bootstrap.servers", bootstrapServers);
            if (config != null && config.get("bootstrap.servers") != null) {
                bootstrapServers = (String) config.get("bootstrap.servers");
                kafkaProperties.put("bootstrap.servers", bootstrapServers);
            }
            kafkaProperties.put("client.id", "");
            if (config != null && config.get("client.id") != null) {
                kafkaProperties.put("client.id", (String) config.get("client.id"));
            }
            kafkaProperties.put("compression.type", "none");
            if (config != null && config.get("compression.type") != null) {
                kafkaProperties.put("compression.type", (String) config.get("compression.type"));
            }
            kafkaProperties.put("acks", "all");
            if (config != null && config.get("acks") != null) {
                kafkaProperties.put("acks", (String) config.get("acks"));
            }
            kafkaProperties.put("retries", 0);
            if (config != null && config.get("retries") != null) {
                kafkaProperties.put("retries", Integer.parseInt((String) config.get("retries")));
            }
            kafkaProperties.put("batch.size", 16384);
            if (config != null && config.get("batch.size") != null) {
                kafkaProperties.put("batch.size", Integer.parseInt((String) config.get("batch.size")));
            }
            kafkaProperties.put("buffer.memory", 33554432);
            if (config != null && config.get("buffer.memory") != null) {
                kafkaProperties.put("buffer.memory", Long.parseLong((String) config.get("buffer.memory")));
            }
            kafkaProperties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            if (config != null && config.get("key.serializer") != null) {
                kafkaProperties.put("key.serializer", (String) config.get("key.serializer"));
            }
            kafkaProperties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            if (config != null && config.get("value.serializer") != null) {
                kafkaProperties.put("value.serializer", (String) config.get("value.serializer"));
            }
            String topic = "decanter";
            if (config != null && config.get("topic") != null) {
                topic = (String) config.get("topic");
            }
            KafkaAppender appender = new KafkaAppender(kafkaProperties, topic);
            Dictionary<String, String> properties = new Hashtable<>();
            properties.put(EventConstants.EVENT_TOPIC, "decanter/collect/*");
            this.registration = bundleContext.registerService(EventHandler.class, appender, properties);
            LOGGER.debug("Decanter Kafka appender started ({}/{})", bootstrapServers, topic);
        }

    }

}
