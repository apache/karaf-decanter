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

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.osgi.framework.*;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Properties;

public class Activator implements BundleActivator {

    private final static Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    private final static String CONFIG_PID = "org.apache.karaf.decanter.appender.kafka";

    private ServiceTracker tracker;

    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        tracker = new ServiceTracker<Marshaller, ServiceRegistration>(bundleContext, Marshaller.class, null) {
            @Override
            public ServiceRegistration addingService(ServiceReference<Marshaller> reference) {
                Dictionary<String, String> properties = new Hashtable<>();
                properties.put(Constants.SERVICE_PID, CONFIG_PID);
                Marshaller marshaller = context.getService(reference);
                return bundleContext.registerService(ManagedService.class.getName(), new ConfigUpdater(bundleContext, marshaller), properties);
            }
            @Override
            public void removedService(ServiceReference<Marshaller> reference, ServiceRegistration service) {
                service.unregister();
                super.removedService(reference, service);
            }
        };
        tracker.open();
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        LOGGER.debug("Stopping Decanter Kafka appender");
        tracker.close();
    }

    private final class ConfigUpdater implements ManagedService {

        private BundleContext bundleContext;
        private ServiceRegistration registration;
        private Marshaller marshaller;

        public ConfigUpdater(BundleContext bundleContext, Marshaller marshaller) {
            this.bundleContext = bundleContext;
            this.marshaller = marshaller;
        }

        @Override
        public void updated(Dictionary config) throws ConfigurationException {
            LOGGER.debug("Updating Decanter Kafka appender");
            if (registration != null) {
                registration.unregister();
            }

            Properties kafkaProperties = new Properties();

            String bootstrapServers = getValue(config, "bootstrap.servers", "localhost:9092");
            kafkaProperties.put("bootstrap.servers", bootstrapServers);

            String clientId = getValue(config, "client.id", "");
            kafkaProperties.put("client.id", clientId);

            String compressionType = getValue(config, "compression.type", "none");
            kafkaProperties.put("compression.type", compressionType);

            String acks = getValue(config, "acks", "all");
            kafkaProperties.put("acks", acks);

            String retries = getValue(config, "retries", "0");
            kafkaProperties.put("retries", Integer.parseInt(retries));

            String batchSize = getValue(config, "batch.size", "16384");
            kafkaProperties.put("batch.size", Integer.parseInt(batchSize));

            String bufferMemory = getValue(config, "buffer.memory", "33554432");
            kafkaProperties.put("buffer.memory", Long.parseLong(bufferMemory));

            String keySerializer = getValue(config, "key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            kafkaProperties.put("key.serializer", keySerializer);

            String valueSerializer = getValue(config, "value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            kafkaProperties.put("value.serializer", valueSerializer);

            String topic = getValue(config, "topic", "decanter");

            KafkaAppender appender = new KafkaAppender(kafkaProperties, topic, marshaller);
            Dictionary<String, String> properties = new Hashtable<>();
            properties.put(EventConstants.EVENT_TOPIC, "decanter/collect/*");
            this.registration = bundleContext.registerService(EventHandler.class, appender, properties);
            LOGGER.debug("Decanter Kafka appender started ({}/{})", bootstrapServers, topic);
        }

    }

    private String getValue(Dictionary<String, Object> config, String key, String defaultValue) {
        String value = (String)config.get(key);
        return (value != null) ? value :  defaultValue;
    }

}
