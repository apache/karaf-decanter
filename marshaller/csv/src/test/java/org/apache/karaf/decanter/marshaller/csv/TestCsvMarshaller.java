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
package org.apache.karaf.decanter.marshaller.csv;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

public class TestCsvMarshaller {

    private static final long EXPECTED_TIMESTAMP = 1454428780634L;
    private static final String EXPECTED_TOPIC = "testTopic";

    @Test
    public void testMarshal() throws Exception {
        Marshaller marshaller = new CsvMarshaller();
        Map<String, Object> map = new HashMap<>();
        map.put(EventConstants.TIMESTAMP, EXPECTED_TIMESTAMP);
        map.put("c", "d");
        String marshalled = marshaller.marshal(new Event(EXPECTED_TOPIC, map));
        System.out.println(marshalled);
        Assert.assertEquals("c=d,timestamp=1454428780634,event.topics=testTopic", marshalled);
    }

    @Test
    public void testInnerMap() throws Exception {
        Marshaller marshaller = new CsvMarshaller();

        Map<String, Object> map = new HashMap<>();
        map.put(EventConstants.TIMESTAMP, EXPECTED_TIMESTAMP);
        map.put("test", "test");
        Map<String, Object> inner = new HashMap<>();
        inner.put("other", "other");
        map.put("inner", inner);

        String marshalled = marshaller.marshal(new Event(EXPECTED_TOPIC, map));

        System.out.println(marshalled);

        Assert.assertEquals("test=test,inner={other=other},timestamp=1454428780634,event.topics=testTopic", marshalled);
    }

}
