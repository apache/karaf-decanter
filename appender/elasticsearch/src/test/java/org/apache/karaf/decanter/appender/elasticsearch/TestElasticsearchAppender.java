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

import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;

import org.elasticsearch.client.Requests;
import org.elasticsearch.common.collect.MapBuilder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.event.Event;

import static org.elasticsearch.node.NodeBuilder.*;

public class TestElasticsearchAppender {

   @Test
   public void testAppender() throws Exception {
       
       Settings settings = settingsBuilder()
               .put("cluster.name", "elasticsearch")
               .put("http.enabled", "true")
               .put("node.data", true)
               .put("path.data", "target/data")
               .put("network.host", "127.0.0.1")
               .put("index.store.type", "memory")
               .put("index.store.fs.memory.enabled", "true")
               .put("path.plugins", "target/plugins")
               .build();
       
       Node node = nodeBuilder().settings(settings).node();
       
       ElasticsearchAppender appender = new ElasticsearchAppender("127.0.0.1", 9300);
       appender.open();
       appender.handleEvent(new Event("testTopic", MapBuilder.<String, String>newMapBuilder().put("a", "b").put("c", "d").map()));
       appender.handleEvent(new Event("testTopic", MapBuilder.<String, String>newMapBuilder().put("a", "b").put("c", "d").map()));
       appender.handleEvent(new Event("testTopic", MapBuilder.<String, String>newMapBuilder().put("a", "b").put("c", "d").map()));
       appender.close();
       
       int maxTryCount = 10;
       for(int i=0; node.client().count(Requests.countRequest()).actionGet().getCount() == 0 && i< maxTryCount; i++) {
           Thread.sleep(500);
       }
       
       Assert.assertEquals(3L, node.client().count(Requests.countRequest()).actionGet().getCount());
       node.close();
   }

}
