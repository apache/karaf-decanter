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
package org.apache.karaf.decanter.appender.utils;

import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.event.Event;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

public class EventFilterTest {

    @Test
    public void noFilter() {
        // no config
        Assert.assertTrue(EventFilter.match(prepareTestEvent(), null));
        // no filter in the config
        Dictionary<String, Object> config = new Hashtable<>();
        Assert.assertTrue(EventFilter.match(prepareTestEvent(), config));
    }

    @Test
    public void propertyNameFilter() {
        Dictionary<String, Object> config = new Hashtable<>();
        config.put(EventFilter.PROPERTY_NAME_EXCLUDE_CONFIG, "key.*");
        // exclude
        Assert.assertFalse(EventFilter.match(prepareTestEvent(), config));
        // exclude first
        config.put(EventFilter.PROPERTY_NAME_INCLUDE_CONFIG, "other");
        Assert.assertFalse(EventFilter.match(prepareTestEvent(), config));
        // include
        config.remove(EventFilter.PROPERTY_NAME_EXCLUDE_CONFIG);
        Assert.assertTrue(EventFilter.match(prepareTestEvent(), config));
    }

    @Test
    public void propertyValueFilter() {
        Dictionary<String, Object> config = new Hashtable<>();
        config.put(EventFilter.PROPERTY_VALUE_EXCLUDE_CONFIG, "value.*");
        // exclude
        Assert.assertFalse(EventFilter.match(prepareTestEvent(), config));
        // exclude first
        config.put(EventFilter.PROPERTY_VALUE_INCLUDE_CONFIG, "other");
        Assert.assertFalse(EventFilter.match(prepareTestEvent(), config));
        // include
        config.remove(EventFilter.PROPERTY_VALUE_EXCLUDE_CONFIG);
        Assert.assertTrue(EventFilter.match(prepareTestEvent(), config));
    }

    private Event prepareTestEvent() {
        Map<String, Object> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("other", "other");
        return new Event("test", map);
    }

}
