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
package org.apache.karaf.decanter.appender.elasticsearch;

import org.apache.karaf.decanter.api.Appender;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * Karaf Decanter appender which insert into Elasticsearch
 */
public class ElasticsearchAppender implements Appender {

    private final static Logger LOGGER = LoggerFactory.getLogger(ElasticsearchAppender.class);
    
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    public void append(Map<Long, Map<String, Object>> data) throws Exception {
        LOGGER.debug("Appending into Elasticsearch");

        Client client = null;
        try {
            // TODO embed mode and configuration admin support for location of Elasticsearch
            LOGGER.debug("Connecting to Elasticsearch instance located localhost:9300");
            Settings settings = ImmutableSettings.settingsBuilder().classLoader(Settings.class.getClassLoader()).build();
            client = new TransportClient(settings).addTransportAddress(new InetSocketTransportAddress("localhost", 9300));
            for (Long unit : data.keySet()) {
                client.prepareIndex("@timestamp", dateFormat.format(new Date(unit))).setSource(data.get(unit)).execute().actionGet();
            }
            LOGGER.debug("Apppending done");
        } catch (Exception e) {
            LOGGER.warn("Can't append into Elasticsearch", e);
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

}
