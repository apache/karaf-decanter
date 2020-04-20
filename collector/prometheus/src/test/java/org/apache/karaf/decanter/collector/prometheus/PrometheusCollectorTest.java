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
package org.apache.karaf.decanter.collector.prometheus;

import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class PrometheusCollectorTest {

    @Test
    public void test() throws Exception {
        MockDispatcher dispatcher = new MockDispatcher();
        PrometheusCollector prometheusCollector = new PrometheusCollector();
        prometheusCollector.setDispatcher(dispatcher);
        Hashtable<String, Object> configuration = new Hashtable<>();
        configuration.put("prometheus.url", "file:target/test-classes/sample.txt");
        prometheusCollector.activate(configuration);

        prometheusCollector.run();

        Assert.assertEquals(1, dispatcher.postedEvents.size());

        Assert.assertEquals(0.0, dispatcher.postedEvents.get(0).getProperty("Test1"));
        Assert.assertEquals(8.0, dispatcher.postedEvents.get(0).getProperty("Test2"));
        Assert.assertEquals("prometheus", dispatcher.postedEvents.get(0).getProperty("type"));
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
