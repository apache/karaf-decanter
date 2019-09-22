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
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        immediate = true
)
public class AlertStoreImpl implements AlertStore {

    private Logger logger = LoggerFactory.getLogger(AlertStoreImpl.class);

    private Set<String> errorAlerts;
    private Set<String> warnAlerts;
    private File file;

    @Activate
    public void activate() {
        this.errorAlerts = new HashSet<>();
        this.warnAlerts = new HashSet<>();

        // store the data file in $KARAF_DATA/decanter/alerter.db
        file = new File(System.getProperty("karaf.data") + File.separator + "decanter" + File.separator + "alerter.db");

        if (file.exists()) {
            try {
                Files.lines(file.toPath())
                        .forEach(line -> {
                            if (line.startsWith(Level.error.name().concat(":"))) {
                                this.errorAlerts.add(line.replaceFirst(Level.error.name().concat(":"), ""));
                            } else if (line.startsWith(Level.warn.name().concat(":"))) {
                                this.warnAlerts.add(line.replaceFirst(Level.warn.name().concat(":"), ""));
                            } else {
                                logger.error("Level unknow in line '{}'", line);
                            }
                        });
            } catch (IOException exception) {
                logger.error("Error while reading alerter store file!");
            }
        }
    }

    @Deactivate
    public void deactivate() {
        try {
            // build data to write based on level for prefix
            Set<String> data = new HashSet<>();
            this.errorAlerts.stream().forEach(value -> data.add(Level.error.name().concat(":").concat(value)));
            this.warnAlerts.stream().forEach(value -> data.add(Level.warn.name().concat(":").concat(value)));

            // create directories if not exists
            Files.createDirectories(file.getParentFile().toPath());

            // write data
            Files.write(file.toPath(), data.stream().collect(Collectors.toSet()), StandardOpenOption.CREATE);
        } catch (IOException exception) {
            logger.error("Error while writing alerter store file!");
        }
    }

    public void add(String name, Level level) {
        if (level == Level.error) {
            this.errorAlerts.add(name);
        }
        if (level == Level.warn) {
            this.warnAlerts.add(name);
        }
    }

    public void remove(String name, Level level) {
        if (level == Level.error) {
            this.errorAlerts.remove(name);
        }
        if (level == Level.warn) {
            this.warnAlerts.remove(name);
        }
    }

    public boolean known(String name, Level level) {
        if (level == Level.error) {
            return this.errorAlerts.contains(name);
        }
        if (level == Level.warn) {
            return this.warnAlerts.contains(name);
        }
        return false;
    }

    public Set<String> list(Level level) {
        if (level == Level.error) {
            return this.errorAlerts;
        }
        if (level == Level.warn) {
            return this.warnAlerts;
        }
        return null;
    }

}
