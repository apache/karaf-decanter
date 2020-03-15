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
package org.apache.karaf.decanter.alerting.service.store;

import org.apache.karaf.decanter.alerting.service.Alert;
import org.apache.karaf.decanter.alerting.service.Store;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.standard.config.PointsConfig;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.*;

@Component(name = "org.apache.karaf.decanter.alerting.store.lucene")
public class LuceneStoreImpl implements Store {

    private final static Logger LOGGER = LoggerFactory.getLogger(LuceneStoreImpl.class);

    private Directory directory;
    private IndexWriter indexWriter;
    private Map<String, PointsConfig> points;

    public static final String INDEX_DIRECTORY = "/decanter/alerting";
    public static final String POINTS_DIRECTORY = "/decanter/alerting/points";

    @Activate
    public void activate() throws Exception {
        directory = new NIOFSDirectory(Paths.get(System.getProperty("karaf.data"), INDEX_DIRECTORY));
        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(new StandardAnalyzer());
        indexWriter = new IndexWriter(directory, indexWriterConfig);
        points = loadPoints();
    }

    @Deactivate
    public void deactivate() throws Exception {
        indexWriter.close();
        savePoints(points);
    }

    /* visible for testing */
    static Map<String, PointsConfig> loadPoints() throws Exception {
        Map<String, PointsConfig> points = new HashMap<>();
        Properties pointsStore = new Properties();
        File file = Paths.get(System.getProperty("karaf.data"), POINTS_DIRECTORY).toFile();
        if (file.exists()) {
            FileReader reader = new FileReader(Paths.get(System.getProperty("karaf.data"), POINTS_DIRECTORY).toFile());
            pointsStore.load(reader);
            for (Object key : pointsStore.keySet()) {
                String value = pointsStore.get(key).toString();
                if (value.equals("double")) {
                    points.put(key.toString(), new PointsConfig(NumberFormat.getInstance(), Double.class));
                }
                if (value.equals("float")) {
                    points.put(key.toString(), new PointsConfig(NumberFormat.getInstance(), Float.class));
                }
                if (value.equals("integer")) {
                    points.put(key.toString(), new PointsConfig(NumberFormat.getInstance(), Integer.class));
                }
                if (value.equals("long")) {
                    points.put(key.toString(), new PointsConfig(NumberFormat.getInstance(), Long.class));
                }
            }
        }
        return points;
    }

    /* visible for testing */
    static void savePoints(Map<String, PointsConfig> points) throws Exception {
        Properties pointsStore = new Properties();
        for (String key : points.keySet()) {
            PointsConfig pointsConfig = points.get(key);
            if (pointsConfig.getType().getName().equals("java.lang.Double")) {
                pointsStore.put(key, "double");
            }
            if (pointsConfig.getType().getName().equals("java.lang.Float")) {
                pointsStore.put(key, "float");
            }
            if (pointsConfig.getType().getName().equals("java.lang.Integer")) {
                pointsStore.put(key, "integer");
            }
            if (pointsConfig.getType().getName().equals("java.lang.Long")) {
                pointsStore.put(key, "long");
            }
        }
        FileWriter writer = new FileWriter(Paths.get(System.getProperty("karaf.data"), POINTS_DIRECTORY).toFile());
        pointsStore.store(writer, "Decanter Alerting Service Points Configuration");
    }

    @Override
    public String store(Event event) throws Exception {
        Document document = new Document();
        for (String property : event.getPropertyNames()) {
            Object value = event.getProperty(property);
            if (value instanceof Long) {
                document.add(new LongPoint(property, ((Long) value)));
                document.add(new StoredField(property, ((Long) value)));
                points.put(property, new PointsConfig(NumberFormat.getInstance(), Long.class));
            } else if (value instanceof Integer) {
                document.add(new IntPoint(property, ((Integer) value)));
                document.add(new StoredField(property, ((Integer) value)));
                points.put(property, new PointsConfig(NumberFormat.getInstance(), Integer.class));
            } else if (value instanceof Float) {
                document.add(new FloatPoint(property, ((Float) value)));
                document.add(new StoredField(property, ((Integer) value)));
                points.put(property, new PointsConfig(NumberFormat.getInstance(), Float.class));
            } else if (value instanceof Double) {
                document.add(new DoublePoint(property, ((Double) value)));
                document.add(new StoredField(property, ((Double) value)));
                points.put(property, new PointsConfig(NumberFormat.getInstance(), Double.class));
            } else {
                if (value != null) {
                    String stringValue = value.toString();
                    if (stringValue.getBytes("UTF-8").length > 32766) {
                        stringValue = new String(stringValue.getBytes("UTF-8"), 0, 32766, "UTF-8");
                    }
                    document.add(new StringField(property, stringValue, Field.Store.YES));
                }
            }
        }
        if (event.getProperty("alertTimestamp") == null) {
            long now = System.currentTimeMillis();
            document.add(new LongPoint("alertTimestamp", System.currentTimeMillis()));
            document.add(new StoredField("alertTimestamp", now));
            points.put("alertTimestamp", new PointsConfig(NumberFormat.getInstance(), Long.class));
        }
        String uuid = UUID.randomUUID().toString();
        document.add(new StringField("alertUUID", uuid, Field.Store.YES));
        try {
            indexWriter.addDocument(document);
            indexWriter.commit();
        } catch (Exception e) {
            LOGGER.warn("Can't store alert: {}", e.getMessage());
        }
        return uuid;
    }

    @Override
    public void cleanup() throws Exception {
        indexWriter.deleteDocuments(new MatchAllDocsQuery());
        indexWriter.commit();
    }

    @Override
    public void eviction() throws Exception {
        StandardQueryParser queryParser = new StandardQueryParser();
        queryParser.setAllowLeadingWildcard(true);
        queryParser.setPointsConfigMap(points);
        Query query = queryParser.parse("alertUUID:* AND NOT alertRule:*", "");
        indexWriter.deleteDocuments(query);
        indexWriter.commit();
    }

    @Override
    public void flag(String queryString, String ruleName) throws Exception {
        List<Alert> alerts = query(queryString);
        delete(queryString);
        for (Alert alert : alerts) {
            alert.put("alertRule", ruleName);
            store(alert);
        }
    }

    private void store(Alert alert) throws Exception {
        Document document = new Document();
        for (String key : alert.get().keySet()) {
            Object value = alert.get(key);
            if (value instanceof Long) {
                document.add(new LongPoint(key, ((Long) value)));
                document.add(new StoredField(key, ((Long) value)));
                points.put(key, new PointsConfig(NumberFormat.getInstance(), Long.class));
            } else if (value instanceof Integer) {
                document.add(new IntPoint(key, ((Integer) value)));
                document.add(new StoredField(key, ((Integer) value)));
                points.put(key, new PointsConfig(NumberFormat.getInstance(), Integer.class));
            } else if (value instanceof Float) {
                document.add(new FloatPoint(key, ((Float) value)));
                document.add(new StoredField(key, ((Integer) value)));
                points.put(key, new PointsConfig(NumberFormat.getInstance(), Float.class));
            } else if (value instanceof Double) {
                document.add(new DoublePoint(key, ((Double) value)));
                document.add(new StoredField(key, ((Double) value)));
                points.put(key, new PointsConfig(NumberFormat.getInstance(), Double.class));
            } else {
                document.add(new StringField(key, value.toString(), Field.Store.YES));
            }
        }
        indexWriter.addDocument(document);
        indexWriter.commit();
    }

    @Override
    public List<Alert> list() throws Exception {
        List<Alert> alerts = new ArrayList<>();
        IndexReader indexReader = DirectoryReader.open(indexWriter);
        for (int i = 0; i < indexReader.maxDoc(); i++) {
            Document document = indexReader.document(i);
            alerts.add(documentToAlert(document));
        }
        return alerts;
    }

    private Alert documentToAlert(Document document) {
        Alert alert = new Alert();
        for (IndexableField field : document.getFields()) {
            if (field.numericValue() != null) {
                alert.put(field.name(), field.numericValue());
            } else {
                alert.put(field.name(), field.stringValue());
            }
        }
        return alert;
    }

    @Override
    public List<Alert> query(String queryString) throws Exception {
        List<Alert> alerts = new ArrayList<>();
        IndexReader indexReader = DirectoryReader.open(indexWriter);
        IndexSearcher indexSearcher = new IndexSearcher(indexReader);
        StandardQueryParser queryParser = new StandardQueryParser();
        queryParser.setAllowLeadingWildcard(true);
        queryParser.setPointsConfigMap(points);
        Query query = queryParser.parse(queryString, "");
        TopDocs topDocs = indexSearcher.search(query, Integer.MAX_VALUE, Sort.INDEXORDER, false);
        for (int i = 0; i < topDocs.scoreDocs.length; i++) {
            alerts.add(documentToAlert(indexSearcher.doc(topDocs.scoreDocs[i].doc)));
        }
        return alerts;
    }

    @Override
    public void delete(String queryString) throws Exception {
        StandardQueryParser queryParser = new StandardQueryParser();
        queryParser.setAllowLeadingWildcard(true);
        queryParser.setPointsConfigMap(points);
        Query query = queryParser.parse(queryString, "");
        indexWriter.deleteDocuments(query);
        indexWriter.commit();
    }

}
