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

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.exec.*;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;

public class KibanaController {

    private final static Logger LOGGER = LoggerFactory.getLogger(KibanaController.class);
    private final static Logger KIBANA_LOGGER = LoggerFactory.getLogger("kibana");

    private final static String KIBANA_LINUX_LOCATION = "https://artifacts.elastic.co/downloads/kibana/kibana-6.1.1-linux-x86_64.tar.gz";
    private final static String KIBANA_WINDOWS_LOCATION = "https://artifacts.elastic.co/downloads/kibana/kibana-6.1.1-windows-x86_64.zip";
    private final static String KIBANA_MAC_LOCATION = "https://artifacts.elastic.co/downloads/kibana/kibana-6.1.1-darwin-x86_64.tar.gz";

    private final static String KIBANA_LOCATION = isWindows() ? KIBANA_WINDOWS_LOCATION : (isMac() ? KIBANA_MAC_LOCATION : KIBANA_LINUX_LOCATION);

    protected final static String KIBANA_LINUX_FOLDER = "kibana-6.1.1-linux-x86_64";
    protected final static String KIBANA_WINDOWS_FOLDER = "kibana-6.1.1-windows-x86_64";
    protected final static String KIBANA_MAC_FOLDER = "kibana-6.1.1-darwin-x86_64";

    protected final static String KIBANA_FOLDER = isWindows() ? KIBANA_WINDOWS_FOLDER : (isMac() ? KIBANA_MAC_FOLDER : KIBANA_LINUX_FOLDER);

    private File workingDirectory;
    private DaemonExecutor executor;
    private DefaultExecuteResultHandler executeResultHandler;

    public KibanaController(File workingDirectory) {
        this.workingDirectory = workingDirectory;
        this.workingDirectory.mkdirs();
        this.executor = new DaemonExecutor();
        PumpStreamHandler pumpStreamHandler = new PumpStreamHandler(new LogOutputStream() {
            @Override
            protected void processLine(String line, int logLevel) {
                KIBANA_LOGGER.info(line);
            }
        });
        executor.setStreamHandler(pumpStreamHandler);
        executor.setWatchdog(new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT));
        executeResultHandler = new DefaultExecuteResultHandler();
    }

    static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Win");
    }

    static boolean isMac() {
        return System.getProperty("os.name").startsWith("Mac OS X");
    }

    public void download() throws Exception {
        File target = new File(workingDirectory, KIBANA_FOLDER);
        if (target.exists()) {
            LOGGER.warn("Kibana folder already exists, download is skipped");
            return;
        }
        LOGGER.debug("Downloading Kibana from {}", KIBANA_LOCATION);
        if (isWindows()) {
            try (ZipArchiveInputStream inputStream = new ZipArchiveInputStream(new URL(KIBANA_LOCATION).openStream())) {
                ZipArchiveEntry entry;
                while ((entry = (ZipArchiveEntry) inputStream.getNextEntry()) != null) {
                    File file = new File(workingDirectory, entry.getName());
                    if (entry.isDirectory()) {
                        file.mkdirs();
                    } else {
                        int read;
                        byte[] buffer = new byte[4096];
                        try (FileOutputStream outputStream = new FileOutputStream(file)) {
                            while ((read = inputStream.read(buffer, 0, 4096)) != -1) {
                                outputStream.write(buffer, 0, read);
                            }
                        }
                    }
                }
            }
        } else {
            try (GzipCompressorInputStream gzInputStream = new GzipCompressorInputStream(new URL(KIBANA_LOCATION).openStream())) {
                try (TarArchiveInputStream inputStream = new TarArchiveInputStream(gzInputStream)) {
                    TarArchiveEntry entry;
                    while ((entry = (TarArchiveEntry) inputStream.getNextEntry()) != null) {
                        File file = new File(workingDirectory, entry.getName());
                        if (entry.isDirectory()) {
                            file.mkdirs();
                        } else {
                            int read;
                            byte[] buffer = new byte[4096];
                            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                                while ((read = inputStream.read(buffer, 0, 4096)) != -1) {
                                    outputStream.write(buffer, 0, read);
                                }
                            }
                            file.setLastModified(entry.getLastModifiedDate().getTime());
                            if (entry instanceof TarArchiveEntry) {
                                int mode = ((TarArchiveEntry) entry).getMode();
                                if ((mode & 00100) > 0) {
                                    file.setExecutable(true, (mode & 00001) == 0);
                                }
                            }
                        }
                    }
                }
            }
        }
        overrideConfig();
    }

    protected void overrideConfig() throws Exception {
        LOGGER.debug("Overriding kibana.yml");
        File kibanaYml = new File(new File(new File(workingDirectory, KIBANA_FOLDER), "config"), "kibana.yml");
        kibanaYml.delete();
        try (BufferedInputStream inputStream = new BufferedInputStream(this.getClass().getClassLoader().getResourceAsStream("kibana/conf/kibana.yml"))) {
            try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(kibanaYml))) {
                int read;
                byte[] buffer = new byte[4096];
                while ((read = inputStream.read(buffer, 0, 4096)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
            }
        }
    }

    protected void createDashboardLog(int httpPort) throws Exception {
        LOGGER.debug("Create Log Levels visualization");
        String visualization = readResource("resources/visualization_log_levels.json");
        LOGGER.debug("");
        updateKibanaSettings("http://localhost:" + httpPort + "/kibana/api/saved_objects/visualization/log-levels", visualization);

        LOGGER.debug("Create Logger Names visualization");
        visualization = readResource("resources/visualization_logger_names.json");
        updateKibanaSettings("http://localhost:" + httpPort + "/kibana/api/saved_objects/visualization/logger-names", visualization);

        LOGGER.debug("Create Logs Bundle visualization");
        visualization = readResource("resources/visualization_logs_bundle.json");
        updateKibanaSettings("http://localhost:" + httpPort + "/kibana/api/saved_objects/visualization/logs-bundle", visualization);

        LOGGER.debug("Create Log dashboard");
        String dashboard = readResource("resources/dashboard_log.json");
        updateKibanaSettings("http://localhost:" + httpPort + "/kibana/api/saved_objects/dashboard/log", dashboard);
    }

    protected void createDashboardJmx(int httpPort) throws Exception {
        LOGGER.debug("Create Available Processors visualization");
        String visualization = readResource("resources/visualization_available_processors.json");
        updateKibanaSettings("http://localhost:" + httpPort + "/kibana/api/saved_objects/visualization/available-processors", visualization);

        LOGGER.debug("Create Classloading visualization");
        visualization = readResource("resources/visualization_classloading.json");
        updateKibanaSettings("http://localhost:" + httpPort + "/kibana/api/saved_objects/visualization/classloading", visualization);

        LOGGER.debug("Create Compilation visualization");
        visualization = readResource("resources/visualization_compilation.json");
        updateKibanaSettings("http://localhost:" + httpPort + "/kibana/api/saved_objects/visualization/compilation", visualization);

        LOGGER.debug("Create GarbageCollector visualization");
        visualization = readResource("resources/visualization_garbagecollector.json");
        updateKibanaSettings("http://localhost:" + httpPort + "/kibana/api/saved_objects/visualization/garbagecollector", visualization);

        LOGGER.debug("Create Load visualization");
        visualization = readResource("resources/visualization_load.json");
        updateKibanaSettings("http://localhost:" + httpPort + "/kibana/api/saved_objects/visualization/load", visualization);

        LOGGER.debug("Create Memory visualization");
        visualization = readResource("resources/visualization_memory.json");
        updateKibanaSettings("http://localhost:" + httpPort + "/kibana/api/saved_objects/visualization/memory", visualization);

        LOGGER.debug("Create Open Files visualization");
        visualization = readResource("resources/visualization_open_files.json");
        updateKibanaSettings("http://localhost:" + httpPort + "/kibana/api/saved_objects/visualization/open-files", visualization);

        LOGGER.debug("Create System Memory visualization");
        visualization = readResource("resources/visualization_system_memory.json");
        updateKibanaSettings("http://localhost:" + httpPort + "/kibana/api/saved_objects/visualization/system-memory", visualization);

        LOGGER.debug("Create Threading visualization");
        visualization = readResource("resources/visualization_threading.json");
        updateKibanaSettings("http://localhost:" + httpPort + "/kibana/api/saved_objects/visualization/threading", visualization);

        LOGGER.debug("Create Uptime visualization");
        visualization = readResource("resources/visualization_uptime.json");
        updateKibanaSettings("http://localhost:" + httpPort + "/kibana/api/saved_objects/visualization/uptime", visualization);

        LOGGER.debug("Create JMX dashboard");
        String dashboard = readResource("resources/dashboard_jmx.json");
        updateKibanaSettings("http://localhost:" + httpPort + "/kibana/api/saved_objects/dashboard/jmx", dashboard);
    }

    private void updateKibanaSettings(String url, String request) throws Exception {
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost(url);
        HttpEntity entity = new StringEntity(request, ContentType.APPLICATION_JSON);
        post.setHeader("kbn-xsrf", "anything");
        post.setEntity(entity);
        httpClient.execute(post);
    }

    private String readResource(String resourceName) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream(resourceName)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
        }
        return builder.toString();
    }

    public void start() throws Exception {
        String command = workingDirectory.getAbsolutePath() + File.separator + KIBANA_FOLDER + File.separator + "bin" + File.separator + "kibana";
        if (isWindows()) {
            command = workingDirectory.getAbsolutePath() + File.separator + KIBANA_FOLDER + File.separator + "bin" + File.separator + "kibana.bat";
        }
        LOGGER.debug("Starting Kibana with {}", command);
        executor.setWorkingDirectory(new File(workingDirectory, KIBANA_FOLDER));
        executor.execute(CommandLine.parse(command), executeResultHandler);
    }

    public void stop() {
        LOGGER.debug("Stopping Kibana");
        executor.getWatchdog().destroyProcess();
    }

}
