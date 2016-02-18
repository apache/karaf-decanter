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

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.marshaller.json.JsonMarshaller;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.collect.MapBuilder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.junit.Test;
import org.osgi.service.event.Event;

public class TestElasticsearchAppender {
    private static final String CLUSTER_NAME = "elasticsearch-test";
    private static final int PORT = 9300;

   @Test
   public void testAppender() throws Exception {
       
       Settings settings = Settings.settingsBuilder()
               .put("cluster.name", "elasticsearch")
               .put("http.enabled", "true")
               .put("node.data", true)
               .put("path.home", "target")
               .put("path.data", "target/data")
               .put("network.host", "127.0.0.1")
               .put("index.store.type", "memory")
               .put("index.store.fs.memory.enabled", "true")
               .put("path.plugins", "target/plugins")
               .build();
       
       Node node = nodeBuilder().settings(settings).node();
       
       Marshaller marshaller = new JsonMarshaller();
       ElasticsearchAppender appender = new ElasticsearchAppender();
       appender.setMarshaller(marshaller);
       Dictionary<String, Object> config = new Hashtable<>();
       config.put("clusterName", CLUSTER_NAME);
       config.put("port", "" + PORT);
       appender.open(config);
       appender.handleEvent(new Event("testTopic", MapBuilder.<String, String>newMapBuilder().put("a", "b").put("c", "d").map()));
       appender.handleEvent(new Event("testTopic", MapBuilder.<String, String>newMapBuilder().put("a", "b").put("c", "d").map()));
       appender.handleEvent(new Event("testTopic", MapBuilder.<String, String>newMapBuilder().put("a", "b").put("c", "d").map()));
       appender.close();

       SearchResponse response = node.client().prepareSearch().execute().actionGet();
       System.out.println(response.toString());

       node.close();
   }

}
