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
package org.apache.karaf.decanter.marshaller.json;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;

public class TestJsonMarshaller {
    private static final long EXPECTED_TIMESTAMP = 1454428780634L;
    private static final String EXPECTED_TOPIC = "testTopic";

   @Test
   public void testMarshal() throws Exception {
       Marshaller marshaller = new JsonMarshaller();
       Map<String, Object> map = new HashMap<>();
       map.put(EventConstants.TIMESTAMP, EXPECTED_TIMESTAMP);
       map.put("c", "d");
       String jsonSt = marshaller.marshal(new Event(EXPECTED_TOPIC, map));
       System.out.println(jsonSt);
       JsonReader reader = Json.createReader(new StringReader(jsonSt));
       JsonObject jsonO = reader.readObject();
       Assert.assertEquals("Timestamp string", "2016-02-02T15:59:40,634Z",jsonO.getString("@timestamp"));
       long ts = jsonO.getJsonNumber(EventConstants.TIMESTAMP).longValue();
       Assert.assertEquals("timestamp long", EXPECTED_TIMESTAMP, ts);
       Assert.assertEquals("Topic", EXPECTED_TOPIC, jsonO.getString(EventConstants.EVENT_TOPIC.replace('.', '_')));

   }

}
