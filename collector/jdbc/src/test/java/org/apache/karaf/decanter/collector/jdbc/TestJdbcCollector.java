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
package org.apache.karaf.decanter.collector.jdbc;

import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

public class TestJdbcCollector {

    private EmbeddedDataSource dataSource;

    @Before
    public void prepareBase() throws Exception {
        System.setProperty("derby.stream.error.file", "target/derby.log");
        dataSource = new EmbeddedDataSource();
        dataSource.setDatabaseName("target/testDB");
        dataSource.setCreateDatabase("create");

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();) {
            statement.executeUpdate("create table TEST(id integer not null, name varchar(26))");
            statement.executeUpdate("insert into TEST(id, name) values(1, 'TEST1')");
            statement.executeUpdate("insert into TEST(id, name) values(2, 'TEST2')");
        }
    }

    @After
    public void cleanup() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();) {
            statement.execute("drop table TEST");
        } catch (Exception e) {
            // Ignore
        }
    }

    @Test
    public void testCollector() throws Exception {
        JdbcCollector collector = new JdbcCollector();
        collector.dataSource = dataSource;
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("query", "select * from TEST");
        collector.activate(config);

        List<Map<String, Object>> dataRows = collector.query();
        Assert.assertEquals(2, dataRows.size());

        Assert.assertEquals(1, dataRows.get(0).get("ID"));
        Assert.assertEquals("TEST1", dataRows.get(0).get("NAME"));
        Assert.assertEquals(1, dataRows.get(0).get("rowId"));
        Assert.assertEquals("jdbc", dataRows.get(0).get("type"));

        Assert.assertEquals(2, dataRows.get(1).get("ID"));
        Assert.assertEquals("TEST2", dataRows.get(1).get("NAME"));
        Assert.assertEquals(2, dataRows.get(1).get("rowId"));
        Assert.assertEquals("jdbc", dataRows.get(1).get("type"));
    }

}
