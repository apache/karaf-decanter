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
package org.apache.karaf.decanter.kibana6;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import static org.apache.karaf.decanter.kibana6.KibanaController.KIBANA_FOLDER;

public class KibanaControllerTest {

    private final static Logger LOGGER = LoggerFactory.getLogger(KibanaControllerTest.class);

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testDownload() throws Exception {
        File folder = temporaryFolder.newFolder();
        KibanaController controller = new KibanaController(folder);
        LOGGER.info("Downloading Kibana");
        controller.download();
        LOGGER.info("Download completed");
        Assert.assertEquals(1, folder.listFiles().length);
        Assert.assertEquals(KIBANA_FOLDER, folder.listFiles()[0].getName());
    }

    @Test
    public void testOverrideConfig() throws Exception {
        File folder = temporaryFolder.newFolder();
        File kibanaConfig = new File(new File(folder, KIBANA_FOLDER), "config");
        kibanaConfig.mkdirs();
        File kibanaYml = new File(kibanaConfig, "kibana.yml");
        kibanaYml.createNewFile();

        KibanaController controller = new KibanaController(folder);
        controller.overrideConfig();

        boolean patternFound = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(kibanaYml))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals("server.basePath: \"/kibana\"")) {
                    patternFound = true;
                    break;
                }
            }
        }

        Assert.assertTrue(patternFound);
    }

    @Test
    public void testStartStop() throws Exception {
        File folder = temporaryFolder.newFolder();
        KibanaController controller = new KibanaController(folder);
        LOGGER.info("Downloading Kibana");
        controller.download();
        LOGGER.info("Download completed, starting Kibana instance");
        controller.start();
        Thread.sleep(3000);
        LOGGER.info("Stopping Kibana instance");
        controller.stop();
    }

}
