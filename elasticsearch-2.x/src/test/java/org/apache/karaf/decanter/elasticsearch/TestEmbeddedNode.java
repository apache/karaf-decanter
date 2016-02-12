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
package org.apache.karaf.decanter.elasticsearch;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthStatus;
import org.elasticsearch.client.Requests;
import org.elasticsearch.node.Node;

import static org.junit.Assert.*;

import java.util.Dictionary;
import java.util.Hashtable;

import org.junit.Ignore;
import org.junit.Test;

public class TestEmbeddedNode {

    @Test
    @Ignore
    public void testNode() throws Exception {

        System.setProperty("karaf.home", "target/karaf");
        System.setProperty("karaf.name", "decanter-test");

        Dictionary<String, String> configuration = new Hashtable<>();
        configuration.put(EmbeddedNode.PATH_DATA, "target/karaf/es");
        EmbeddedNode embeddedNode = new EmbeddedNode(configuration);
        
        Node node = embeddedNode.getNode();
        embeddedNode.start();
        ClusterHealthResponse healthResponse = node.client().admin().cluster().health(Requests.clusterHealthRequest()).actionGet();
        assertEquals(ClusterHealthStatus.GREEN, healthResponse.getStatus());
        embeddedNode.stop();
        
    }
    

}
