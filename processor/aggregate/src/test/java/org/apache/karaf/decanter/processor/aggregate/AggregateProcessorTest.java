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
package org.apache.karaf.decanter.processor.aggregate;

import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

public class AggregateProcessorTest {

    @Test
    public void testWithOverwrite() throws Exception {
        MockDispatcher dispatcher = new MockDispatcher();
        AggregateProcessor aggregateProcessor = new AggregateProcessor();
        aggregateProcessor.setDispatcher(dispatcher);
        Hashtable<String, Object> configuration = new Hashtable<>();
        configuration.put("period", "2");
        configuration.put("overwrite", "true");
        aggregateProcessor.activate(configuration);

        HashMap<String, Object> data1 = new HashMap<>();
        data1.put("first", "first");
        Event event1 = new Event("decanter/collect/first", data1);
        aggregateProcessor.handleEvent(event1);

        HashMap<String, Object> data2 = new HashMap<>();
        data2.put("second", "second");
        Event event2 = new Event("decanter/collect/second", data2);
        aggregateProcessor.handleEvent(event2);

        HashMap<String, Object> data3 = new HashMap<>();
        data3.put("second", "overwrite");
        Event event3 = new Event("decanter/collect/third", data3);
        aggregateProcessor.handleEvent(event3);

        Thread.sleep(3000);

        Assert.assertEquals(1, dispatcher.postedEvents.size());
        Assert.assertEquals("first", dispatcher.postedEvents.get(0).getProperty("first"));
        Assert.assertEquals("overwrite", dispatcher.postedEvents.get(0).getProperty("second"));
        Assert.assertEquals("aggregate", dispatcher.postedEvents.get(0).getProperty("processor"));
        Assert.assertEquals("decanter/process/aggregate", dispatcher.postedEvents.get(0).getTopic());

        HashMap<String, Object> data4 = new HashMap<>();
        data4.put("foo", "bar");
        Event event4 = new Event("decanter/collect/foo", data4);
        aggregateProcessor.handleEvent(event4);

        Thread.sleep(3000);

        Assert.assertEquals(2, dispatcher.postedEvents.size());
        Assert.assertEquals("bar", dispatcher.postedEvents.get(1).getProperty("foo"));

        aggregateProcessor.deactivate();
    }

    @Test
    public void testWithoutOverwrite() throws Exception {
        MockDispatcher dispatcher = new MockDispatcher();
        AggregateProcessor aggregateProcessor = new AggregateProcessor();
        aggregateProcessor.setDispatcher(dispatcher);
        Hashtable<String, Object> configuration = new Hashtable<>();
        configuration.put("period", "2");
        aggregateProcessor.activate(configuration);

        HashMap<String, Object> data1 = new HashMap<>();
        data1.put("foo", "first");
        Event event1 = new Event("decanter/collect/foo", data1);
        aggregateProcessor.handleEvent(event1);

        HashMap<String, Object> data2 = new HashMap<>();
        data2.put("foo", "second");
        Event event2 = new Event("decanter/collect/foo", data2);
        aggregateProcessor.handleEvent(event2);

        Thread.sleep(4000);

        Assert.assertEquals(1, dispatcher.postedEvents.size());
        Assert.assertEquals("first", dispatcher.postedEvents.get(0).getProperty("0.foo"));
        Assert.assertEquals("second", dispatcher.postedEvents.get(0).getProperty("1.foo"));

        aggregateProcessor.deactivate();
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
