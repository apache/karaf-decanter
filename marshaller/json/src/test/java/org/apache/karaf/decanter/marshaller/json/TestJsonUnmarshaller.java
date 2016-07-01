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

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class TestJsonUnmarshaller {

    @Test
    public void testRead() throws IOException {
        URL url = this.getClass().getResource("/metrics.json");
        URLConnection connection = url.openConnection();
        InputStream is = connection.getInputStream();
        JsonUnmarshaller marshaller = new JsonUnmarshaller();
        Map<String, Object> map = marshaller.unmarshal(is);
        BigDecimal load = (BigDecimal)map.get("systemload.average");
        Assert.assertEquals(new BigDecimal(0.61d, new MathContext(2)), load);
        Assert.assertEquals(256000, ((Long)map.get("heap.init")).longValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> mdc = (Map<String, Object>)map.get("MDC");
        Assert.assertNotNull("MDC shouldn't be null", mdc);
        Assert.assertEquals("some test", mdc.get("testMDC"));
        Assert.assertEquals(1, mdc.size());
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>)map.get("sampleList");
        Assert.assertNotNull("List shouldn't be null", list);
        Assert.assertEquals(2, list.size());
        Assert.assertEquals(3L, list.get(0));
        Assert.assertEquals("Hello", list.get(1));
        System.out.println(map);
    }
}
