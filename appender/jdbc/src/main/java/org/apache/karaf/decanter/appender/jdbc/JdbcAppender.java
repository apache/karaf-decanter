package org.apache.karaf.decanter.appender.jdbc;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class JdbcAppender implements EventHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(JdbcAppender.class);

    private final SimpleDateFormat tsFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss,SSS'Z'");

    private String dataSourceName;
    private String tableName;
    private String dialect;
    private BundleContext bundleContext;

    private final static String createTableQueryGenericTemplate =
            "CREATE TABLE TABLENAME(timestamp INTEGER, content VARCHAR(8192))";
    private final static String createTableQueryMySQLTemplate =
            "CREATE TABLE TABLENAME(timestamp BIGINT, content CLOB)";
    private final static String createTableQueryDerbyTemplate =
            "CREATE TABLE TABLENAME(timestamp BIGINT, content CLOB)";

    private final static String insertQueryTemplate =
            "INSERT INTO TABLENAME(timestamp, content) VALUES(?,?)";

    public JdbcAppender(String dataSourceName, String tableName, String dialect, BundleContext bundleContext) {
        this.dataSourceName = dataSourceName;
        this.tableName = tableName;
        this.dialect = dialect;
        this.bundleContext = bundleContext;
    }

    private int getRank(ServiceReference<?> reference) {
        Object rankObj = reference.getProperty(Constants.SERVICE_RANKING);
        // If no rank, then spec says it defaults to zero.
        rankObj = (rankObj == null) ? new Integer(0) : rankObj;
        // If rank is not Integer, then spec says it defaults to zero.
        return (rankObj instanceof Integer) ? (Integer) rankObj : 0;
    }

    @Override
    public void handleEvent(Event event) {
        LOGGER.trace("Looking for the JDBC datasource");
        ServiceReference[] references;
        try {
            references = bundleContext.getServiceReferences((String) null,
                    "(&(|(" + Constants.OBJECTCLASS + "=" + DataSource.class.getName() + ")"
                            + "(" + Constants.OBJECTCLASS + "=" + XADataSource.class.getName() + "))"
                            + "(|(osgi.jndi.service.name=" + dataSourceName + ")(datasource=" + dataSourceName + ")(name=" + dataSourceName + ")(service.id=" + dataSourceName + ")))");
        } catch (Exception e) {
            throw new IllegalStateException("Can't lookup JDBC datasource " + dataSourceName, e);
        }
        if (references == null || references.length == 0) {
            throw new IllegalArgumentException("No JDBC datasource found for " + dataSourceName);
        }
        if (references.length > 1) {
            Arrays.sort(references);
            if (getRank(references[references.length - 1]) == getRank(references[references.length - 2])) {
                LOGGER.warn("Multiple JDBC datasources found with the same service ranking for " + dataSourceName);
            }
        }

        ServiceReference ref = references[references.length - 1];

        if (ref != null) {
            DataSource dataSource = (DataSource) bundleContext.getService(ref);
            Connection connection = null;
            Statement createStatement = null;
            PreparedStatement insertStatement = null;
            try {
                connection = dataSource.getConnection();

                try {
                    String createTableQuery;
                    if (dialect != null && dialect.equalsIgnoreCase("mysql")) {
                        createTableQuery = createTableQueryMySQLTemplate.replaceAll("TABLENAME", tableName);
                    } else if (dialect != null && dialect.equalsIgnoreCase("derby")) {
                        createTableQuery = createTableQueryDerbyTemplate.replaceAll("TABLENAME", tableName);
                    } else {
                        createTableQuery = createTableQueryGenericTemplate.replaceAll("TABLENAME", tableName);
                    }
                    createStatement = connection.createStatement();
                    createStatement.executeUpdate(createTableQuery);
                    LOGGER.debug("Table {} has been created", tableName);
                } catch (SQLException e) {
                    LOGGER.trace("Can't create table {}", e);
                }

                Long timestamp = (Long) event.getProperty("timestamp");
                java.util.Date date = timestamp != null ? new java.util.Date(timestamp) : new java.util.Date();
                JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
                jsonObjectBuilder.add("@timestamp", tsFormat.format(date));
                for (String key : event.getPropertyNames()) {
                    Object value = event.getProperty(key);
                    if (value instanceof Map) {
                        jsonObjectBuilder.add(key, build((Map<String, Object>) value));
                    } else {
                        addProperty(jsonObjectBuilder, key, value);
                    }
                }
                JsonObject jsonObject = jsonObjectBuilder.build();
                String jsonSt = jsonObject.toString();

                String insertQuery = insertQueryTemplate.replaceAll("TABLENAME", tableName);
                insertStatement = connection.prepareStatement(insertQuery);
                if (timestamp == null) {
                    timestamp = System.currentTimeMillis();
                }
                insertStatement.setLong(1, timestamp);
                insertStatement.setString(2, jsonSt);
                insertStatement.executeUpdate();

                connection.commit();
                LOGGER.trace("Data inserted into {} table", tableName);
            } catch (Exception e) {
                LOGGER.error("Can't store in the database", e);
                try {
                    if (connection != null) connection.rollback();
                } catch (SQLException e1) {
                    LOGGER.warn("Can't rollback", e1);
                }
            } finally {
                try {
                    if (createStatement != null) createStatement.close();
                } catch (Exception e) {
                    // nothing to do
                }
                try {
                    if (insertStatement != null) insertStatement.close();
                } catch (Exception e) {
                    // nothing to do
                }
                try {
                    if (connection != null) connection.close();
                } catch (Exception e) {
                    // nothing to do
                }
                if (ref != null) {
                    bundleContext.ungetService(references[0]);
                }
            }
        }
    }

    private JsonObject build(Map<String, Object> value) {
        JsonObjectBuilder innerBuilder = Json.createObjectBuilder();
        for (Map.Entry<String, Object> innerEntrySet : value.entrySet()) {
            addProperty(innerBuilder, innerEntrySet.getKey(), innerEntrySet.getValue());
        }
        return innerBuilder.build();
    }

    private void addProperty(JsonObjectBuilder builder, String key, Object value) {
        if (value instanceof BigDecimal)
            builder.add(key, (BigDecimal) value);
        else if (value instanceof BigInteger)
            builder.add(key, (BigInteger) value);
        else if (value instanceof String)
            builder.add(key, (String) value);
        else if (value instanceof Long)
            builder.add(key, (Long) value);
        else if (value instanceof Integer)
            builder.add(key, (Integer) value);
        else if (value instanceof Float)
            builder.add(key, (Float) value);
        else if (value instanceof Double)
            builder.add(key, (Double) value);
        else if (value instanceof Boolean)
            builder.add(key, (Boolean) value);
    }

}
