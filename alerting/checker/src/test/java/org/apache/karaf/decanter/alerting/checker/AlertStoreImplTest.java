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
package org.apache.karaf.decanter.alerting.checker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlertStoreImplTest {

    Logger logger = LoggerFactory.getLogger(AlertStoreImplTest.class);

    @Before
    public void setUp() {
        System.setProperty("karaf.data", "target/data");
    }

    @Test
    public void testWithoutInitFile() {
        AlertStoreImpl alertStore = new AlertStoreImpl();
        alertStore.activate();

        alertStore.add("log service unavailable", AlertStore.Level.error);
        Assert.assertTrue(alertStore.known("log service unavailable", AlertStore.Level.error));

        alertStore.add("file service stopped", AlertStore.Level.warn);
        Assert.assertTrue(alertStore.known("file service stopped", AlertStore.Level.warn));

        alertStore.deactivate();

        File file = new File(System.getProperty("karaf.data") + File.separator + "decanter" + File.separator + "alerter.db");
        Assert.assertTrue(file.exists());

        try {
            Assert.assertEquals(2, Files.lines(file.toPath()).toArray().length);
        } catch (IOException exception) {
            logger.error("error while opening alerter db file!");
        }
    }

    @Test
    public void testWithInitFile() throws IOException {
        File file = new File(System.getProperty("karaf.data") + File.separator + "decanter" + File.separator + "alerter.db");
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(),
                    Stream.of("error:log service unavailable", "warn:file service stopped").collect(Collectors.toSet()),
                StandardOpenOption.CREATE);
        Assert.assertTrue(file.exists());

        AlertStoreImpl alertStore = new AlertStoreImpl();
        alertStore.activate();

        Assert.assertTrue(alertStore.known("log service unavailable", AlertStore.Level.error));
        Assert.assertTrue(alertStore.known("file service stopped", AlertStore.Level.warn));

        alertStore.deactivate();

        Assert.assertEquals(2, Files.lines(file.toPath()).toArray().length);
    }

}