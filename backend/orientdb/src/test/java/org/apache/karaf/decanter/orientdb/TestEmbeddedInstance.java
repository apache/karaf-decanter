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
package org.apache.karaf.decanter.orientdb;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;
import org.junit.Assert;
import org.junit.Test;

public class TestEmbeddedInstance {

    @Test
    public void testInstance() throws Exception {
        System.setProperty("karaf.etc", ".");
        System.setProperty("karaf.data", "target/karaf/data");

        EmbeddedInstance embeddedInstance = new EmbeddedInstance();
        embeddedInstance.activate();

        ODatabaseDocumentTx db = new ODatabaseDocumentTx("remote:localhost/decanter").open("admin", "admin");

        ODocument document = new ODocument("Metric");
        document.field("type", "jmx");
        document.field("test", "test");
        document.save();

        int count = 0;
        for (ODocument metric : db.browseClass("Metric")) {
            count++;
            Assert.assertEquals("jmx", metric.field("type"));
            Assert.assertEquals("test", metric.field("test"));
        }
        Assert.assertEquals(1, count);

        db.close();

        embeddedInstance.deactivate();
    }

}
