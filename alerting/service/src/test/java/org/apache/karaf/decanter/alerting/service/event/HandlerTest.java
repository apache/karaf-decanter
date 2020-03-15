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
package org.apache.karaf.decanter.alerting.service.event;

import org.apache.karaf.decanter.alerting.service.store.LuceneStoreImpl;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import java.util.*;

public class HandlerTest {

    @Test
    public void test() throws Exception {
        System.setProperty("karaf.data", "target/alerting/handler");
        EventAdminMock dispatcher = new EventAdminMock();
        LuceneStoreImpl store = new LuceneStoreImpl();
        store.activate();

        Dictionary<String, Object> configuration = new Hashtable<>();

        configuration.put("rule.first", "{\"condition\":\"message:*\"}");
        configuration.put("rule.second", "{\"condition\":\"counter:[100 TO *]\",\"recoverable\":true}");
        configuration.put("rule.three", "{\"condition\":\"other:[100 TO *]\",\"period\":\"5SECONDS\"}");

        Handler handler = new Handler();
        handler.setDispatcher(dispatcher);
        handler.setStore(store);
        handler.activate(configuration);

        // regular
        HashMap<String, Object> data = new HashMap<>();
        data.put("message", "first");
        Event event = new Event("collected", data);

        handler.handleEvent(event);

        Assert.assertEquals(1, dispatcher.postedEvents.size());
        Assert.assertEquals("WARN", dispatcher.postedEvents.get(0).getProperty("alertLevel"));
        Assert.assertTrue(((Long) dispatcher.postedEvents.get(0).getProperty("alertTimestamp")) > 0);
        Assert.assertEquals("message:*", dispatcher.postedEvents.get(0).getProperty("alertPattern"));
        Assert.assertEquals("first", dispatcher.postedEvents.get(0).getProperty("message"));

        Assert.assertEquals(0, store.list().size());

        data = new HashMap<>();
        data.put("foo", "bar");
        event = new Event("collected", data);

        handler.handleEvent(event);

        Assert.assertEquals(1, dispatcher.postedEvents.size());

        Assert.assertEquals(0, store.list().size());

        dispatcher.postedEvents.clear();

        // recoverable
        data = new HashMap<>();
        data.put("counter", 50);
        event = new Event("collected", data);
        handler.handleEvent(event);
        Assert.assertEquals(0, dispatcher.postedEvents.size());
        Assert.assertEquals(0, store.list().size());

        data = new HashMap<>();
        data.put("counter", 110);
        event = new Event("collected", data);
        handler.handleEvent(event);
        Assert.assertEquals(1, dispatcher.postedEvents.size());
        Assert.assertEquals(1, store.list().size());

        data = new HashMap<>();
        data.put("counter", 120);
        event = new Event("collected", data);
        handler.handleEvent(event);
        Assert.assertEquals(1, dispatcher.postedEvents.size());
        Assert.assertEquals(1, store.list().size());

        data = new HashMap<>();
        data.put("counter", 10);
        event = new Event("collected", data);
        handler.handleEvent(event);
        Assert.assertEquals(2, dispatcher.postedEvents.size());
        Assert.assertEquals(0, store.list().size());

        Assert.assertEquals(true, dispatcher.postedEvents.get(1).getProperty("alertBackToNormal"));

        dispatcher.postedEvents.clear();

        // period
        data = new HashMap<>();
        data.put("other", 10);
        event = new Event("collected", data);
        handler.handleEvent(event);
        Assert.assertEquals(0, dispatcher.postedEvents.size());
        Assert.assertEquals(0, store.list().size());

        data = new HashMap<>();
        data.put("other", 110);
        data.put("alertTimestamp", System.currentTimeMillis() - 5 * 60 * 1000);
        event = new Event("collected", data);
        handler.handleEvent(event);
        data = new HashMap<>();
        data.put("other", 120);
        event = new Event("collected", data);
        handler.handleEvent(event);
        Assert.assertEquals(1, dispatcher.postedEvents.size());
        Assert.assertEquals(0, store.list().size());

        store.deactivate();
    }

    class EventAdminMock implements EventAdmin {

        List<Event> postedEvents = new ArrayList<>();
        List<Event> sentEvents = new ArrayList<>();

        @Override
        public void postEvent(Event event) {
            this.postedEvents.add(event);
        }

        @Override
        public void sendEvent(Event event) {
            this.sentEvents.add(event);
        }

    }

}
