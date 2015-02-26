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
import java.util.Map.Entry;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

/**
 * Karaf Decanter appender which insert into Elasticsearch
 */
public class ElasticsearchAppender implements Appender {

    private final static Logger LOGGER = LoggerFactory.getLogger(ElasticsearchAppender.class);
    
    private final SimpleDateFormat tsFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    public void append(Map<Long, Map<String, Object>> data) throws Exception {
        LOGGER.debug("Appending into Elasticsearch");

        Client client = null;
        try {
            // TODO embed mode and configuration admin support for location of Elasticsearch
            LOGGER.debug("Connecting to Elasticsearch instance located localhost:9300");
            Settings settings = ImmutableSettings.settingsBuilder().classLoader(Settings.class.getClassLoader()).build();
            client = new TransportClient(settings).addTransportAddress(new InetSocketTransportAddress("localhost", 9300));
            for(Entry<Long, Map<String, Object>> entry : data.entrySet()){
            	Date date = new Date(entry.getKey());
            	entry.getValue().put("@timestamp", tsFormat.format(date));
            	String indexName = String.format("karaf_%s", dateFormat.format(date));
            	
            	JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
            	for (Entry<String, Object> valueEntry : entry.getValue().entrySet()) {
					if (valueEntry.getValue() instanceof String) {
						jsonObjectBuilder.add(valueEntry.getKey(), (String) valueEntry.getValue());
					} else if (valueEntry.getValue() instanceof Map) {
						Map<String, Object> value = (Map<String, Object>) valueEntry.getValue();
						JsonObjectBuilder innerBuilder = Json.createObjectBuilder();
						for (Entry<String, Object> innerEntrySet : value.entrySet()) {
							Object object = innerEntrySet.getValue();
							if (object instanceof String)
								innerBuilder.add(innerEntrySet.getKey(), (String) object);
							else if (object instanceof Long)
								innerBuilder.add(innerEntrySet.getKey(), (Long) object);
							else if (object instanceof Integer)
								innerBuilder.add(innerEntrySet.getKey(), (Integer) object);
							else if (object instanceof Float)
								innerBuilder.add(innerEntrySet.getKey(), (Float) object);
						}
						jsonObjectBuilder.add(valueEntry.getKey(), innerBuilder.build());
					}
				}
            	JsonObject jsonObject = jsonObjectBuilder.build();
            	client.prepareIndex(indexName, "karaf_event").setSource(jsonObject.toString()).execute().actionGet();
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
