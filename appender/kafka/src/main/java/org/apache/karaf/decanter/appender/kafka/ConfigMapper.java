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

import java.util.Dictionary;
import java.util.Properties;

public class ConfigMapper {
    private Properties config;
    private Dictionary<String, Object> confSource;
    
    public static Properties map(Dictionary<String, Object> conf) {
        ConfigMapper mapper = new ConfigMapper(conf);
        return mapper.config;
    }

    private ConfigMapper(Dictionary<String, Object> conf) {
        this.confSource = conf;
        config = new Properties();
        process("bootstrap.servers", "localhost:9092");
        process("client.id", "");
        process("compression.type", "none");
        process("acks", "all");
        process("retries", "0");
        process("batch.size", "16384");
        process("buffer.memory", "33554432");
        process("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        process("request.timeout.ms", "5000");
        process("max.request.size", "2097152");
        process("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        process("security.protocol");
        process("ssl.truststore.location");
        process("ssl.truststore.password");
        process("ssl.keystore.location");
        process("ssl.keystore.password");
        process("ssl.key.password");
        process("ssl.provider");
        process("ssl.cipher.suites");
        process("ssl.enabled.protocols");
        process("ssl.truststore.type");
        process("ssl.keystore.type");
        
        process("topic", "decanter");
    }
    
    private void process(String key) {
        process(key, null);
    }

    private void process(String key, String defaultValue) {
        String value = (String) confSource.get(key);
        String usedValue = (value != null) ? value : defaultValue;
        if (usedValue != null) {
            config.put(key, usedValue);
        }
    }

}
