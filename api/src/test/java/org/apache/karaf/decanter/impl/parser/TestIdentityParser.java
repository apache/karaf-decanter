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

package org.apache.karaf.decanter.impl.parser;

import org.apache.karaf.decanter.api.parser.Parser;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class TestIdentityParser {

    @Test
    public void testParse() {

        Parser parser = new IdentityParser();
        Map<String, Object> data;

        // String type test
        String lineString = "2018-09-07T08:40:41,768 | INFO  | FelixStartLevel  | core     | 14 - " +
                "org.apache.aries.jmx.core - 1.1.8 | Unregistering MBean with ObjectName " +
                "[osgi.compendium:service=cm,version=1.3,framework=org.apache.felix.framework," +
                "uuid=e75146c3-f73c-46e3-878b-1b88e58d76cf] for service with service.id [16]";

        System.out.println("line String :: " + lineString);
        data = parser.parse("line_string", lineString);
        Assert.assertNotNull("parser result is null", data);
        Assert.assertEquals("parser size result is incorrect",1, data.size());

        Assert.assertTrue("parser value is not a string",data.get("line_string") instanceof String);

        // Integer type test
        String lineInteger = "512";

        System.out.println("line Integer :: " + lineInteger);
        data = parser.parse("line_integer", lineInteger);
        Assert.assertNotNull("parser result is null", data);
        Assert.assertEquals("parser size result is incorrect", 1, data.size());

        Assert.assertTrue("parser value is not an integer",data.get("line_integer") instanceof Integer);

        // Long type test
        String lineLong = "9223372036854775806";

        System.out.println("line Long :: " + lineLong);
        data = parser.parse("line_long", lineLong);
        Assert.assertNotNull("parser result is null", data);
        Assert.assertEquals("parser size result is incorrect", 1, data.size());

        Assert.assertTrue("parser value is not a long",data.get("line_long") instanceof Long);
    }
}
