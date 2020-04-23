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
package org.apache.karaf.decanter.collector.elasticsearch;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.common.network.NetworkModule;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.node.MockNode;
import org.elasticsearch.node.Node;
import org.elasticsearch.transport.Netty4Plugin;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import java.util.*;

@RunWith(com.carrotsearch.randomizedtesting.RandomizedRunner.class)
@ThreadLeakScope(value = ThreadLeakScope.Scope.NONE)
public class ElasticsearchCollectorTest {

    private static final String CLUSTER_NAME = "elasticsearch-test";
    private static final String HOST = "127.0.0.1";
    private static final int HTTP_PORT = 9201;
    private static final int TRANSPORT_PORT = 9301;

    private Node node;

    @Before
    public void setup() throws Exception {
        Collection plugins = Arrays.asList(Netty4Plugin.class);
        Settings settings = Settings.builder()
                .put(ClusterName.CLUSTER_NAME_SETTING.getKey(), CLUSTER_NAME)
                .put(Node.NODE_NAME_SETTING.getKey(), "test")
                .put(NetworkModule.HTTP_TYPE_KEY, Netty4Plugin.NETTY_HTTP_TRANSPORT_NAME)
                .put(Environment.PATH_HOME_SETTING.getKey(), "target/data")
                .put(Environment.PATH_DATA_SETTING.getKey(), "target/data")
                .put("network.host", HOST)
                .put("http.port", HTTP_PORT)
                .put(NetworkModule.TRANSPORT_TYPE_KEY, Netty4Plugin.NETTY_TRANSPORT_NAME)
                .put("transport.port", TRANSPORT_PORT)
                .build();
        node = new MockNode(settings, plugins);
        node.start();
    }

    @After
    public void teardown() throws Exception {
        node.close();
    }

    @Test(timeout = 60000L)
    public void testAll() throws Exception {
        HttpHost host = new HttpHost(HOST, HTTP_PORT, "http");
        RestClient client = RestClient.builder(new HttpHost[]{ host }).build();
        HttpEntity entity = new NStringEntity("{\"foo\":\"bar\"}", ContentType.APPLICATION_JSON);
        Request request = new Request("POST", "/test/_doc/");
        request.setEntity(entity);
        client.performRequest(request);

        MockDispatcher dispatcher = new MockDispatcher();
        ElasticsearchCollector collector = new ElasticsearchCollector();
        collector.setDispatcher(dispatcher);
        Hashtable<String, Object> configuration = new Hashtable<>();
        configuration.put("addresses", "http://localhost:" + HTTP_PORT);
        configuration.put("index", "test");
        collector.activate(configuration);

        collector.run();

        Assert.assertEquals(1, dispatcher.postedEvents.size());
        Assert.assertEquals("elasticsearch", dispatcher.postedEvents.get(0).getProperty("type"));
        Assert.assertEquals("decanter/collect/elasticsearch", dispatcher.postedEvents.get(0).getTopic());
    }

    @Test(timeout = 60000L)
    public void testQuery() throws Exception {
        HttpHost host = new HttpHost(HOST, HTTP_PORT, "http");
        RestClient client = RestClient.builder(new HttpHost[]{ host }).build();
        HttpEntity entity = new NStringEntity("{\"foo\":\"bar\"}", ContentType.APPLICATION_JSON);
        Request request = new Request("POST", "/test/_doc/");
        request.setEntity(entity);
        client.performRequest(request);
        entity = new NStringEntity("{\"other\":\"test\"}", ContentType.APPLICATION_JSON);
        request = new Request("POST", "/test/_doc/");
        request.setEntity(entity);
        client.performRequest(request);

        MockDispatcher dispatcher = new MockDispatcher();
        ElasticsearchCollector collector = new ElasticsearchCollector();
        collector.setDispatcher(dispatcher);
        Hashtable<String, Object> configuration = new Hashtable<>();
        configuration.put("addresses", "http://localhost:" + HTTP_PORT);
        configuration.put("index", "test");
        configuration.put("query", "foo:b*");
        collector.activate(configuration);

        collector.run();

        Assert.assertEquals(1, dispatcher.postedEvents.size());
        Assert.assertEquals("elasticsearch", dispatcher.postedEvents.get(0).getProperty("type"));
        Assert.assertEquals("decanter/collect/elasticsearch", dispatcher.postedEvents.get(0).getTopic());
    }

    class MockDispatcher implements EventAdmin {

        public List<Event> postedEvents = new ArrayList<>();
        public List<Event> sentEvents = new ArrayList<>();

        @Override
        public void postEvent(Event event) {
            postedEvents.add(event);
        }

        @Override
        public void sendEvent(Event event) {
            sentEvents.add(event);
        }

    }

}
