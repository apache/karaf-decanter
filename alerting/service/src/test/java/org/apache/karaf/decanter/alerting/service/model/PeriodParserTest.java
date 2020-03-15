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
package org.apache.karaf.decanter.alerting.service.model;

import org.junit.Assert;
import org.junit.Test;

public class PeriodParserTest {

    @Test
    public void test() throws Exception {
        long period = PeriodParser.parse("450");
        Assert.assertEquals(450, period);

        period = PeriodParser.parse("200MILLISECONDS");
        Assert.assertEquals(200, period);

        period = PeriodParser.parse("2SECONDS");
        Assert.assertEquals(2 * 1000, period);

        period = PeriodParser.parse("5MINUTES");
        Assert.assertEquals(5 * 60 * 1000, period);

        period = PeriodParser.parse("2HOURS");
        Assert.assertEquals(2 * 60 * 60 * 1000, period);
    }

}
