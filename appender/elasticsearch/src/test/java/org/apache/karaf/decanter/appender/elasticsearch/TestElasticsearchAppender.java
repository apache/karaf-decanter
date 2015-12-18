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

import static org.elasticsearch.common.settings.Settings.settingsBuilder;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.collect.MapBuilder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.service.event.Event;

import static org.elasticsearch.node.NodeBuilder.*;

public class TestElasticsearchAppender {

   private static Node node;
   
   private static Client client;
   
   @Before
   public void setUpCluster() {
       Settings settings = settingsBuilder()
               .put("cluster.name", "elasticsearch")
               .put("http.enabled", "true")
               .put("node.data", true)
               .put("path.data", "target/data")
               .put("path.home", "target/data")
               .put("network.host", "127.0.0.1")
               .put("index.store.fs.memory.enabled", "true")
               .put("path.plugins", "target/plugins")
               .build();
       
       node = nodeBuilder().settings(settings).node();
       
       client = node.client();
   }
   
   @After
   public void tearDownCluster() {
	   if (client != null) {
           client.close();
	   }
	   
	   if (node != null) {
           node.close();
	   }
   }
   
   @Test
   public void testAppender() throws Exception {
       
       ElasticsearchAppender appender = new ElasticsearchAppender("127.0.0.1", 9300, "elasticsearch");
       appender.open();
       appender.handleEvent(new Event("testTopic", MapBuilder.<String, String>newMapBuilder().put("a", "b").put("c", "d").map()));
       appender.handleEvent(new Event("testTopic", MapBuilder.<String, String>newMapBuilder().put("a", "b").put("c", "d").map()));
       appender.handleEvent(new Event("testTopic", MapBuilder.<String, String>newMapBuilder().put("a", "b").put("c", "d").map()));
       appender.close();
       
       int maxTryCount = 10;
       for(int i=0; client.prepareSearch().execute().actionGet().getHits().getTotalHits() == 0 && i< maxTryCount; i++) {
           Thread.sleep(1000);
       }
       SearchResponse response = client.prepareSearch().execute().actionGet();
       Assert.assertEquals(3L, response.getHits().getTotalHits());
   }

}
