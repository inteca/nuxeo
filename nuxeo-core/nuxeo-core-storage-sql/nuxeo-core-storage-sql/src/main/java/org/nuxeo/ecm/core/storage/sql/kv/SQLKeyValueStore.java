/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.storage.sql.kv;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.Serializable;
import java.nio.charset.CharacterCodingException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ConcurrentUpdateException;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.storage.sql.ColumnType;
import org.nuxeo.ecm.core.storage.sql.jdbc.JDBCLogger;
import org.nuxeo.ecm.core.storage.sql.jdbc.db.Column;
import org.nuxeo.ecm.core.storage.sql.jdbc.db.TableImpl;
import org.nuxeo.ecm.core.storage.sql.jdbc.dialect.Dialect;
import org.nuxeo.ecm.core.storage.sql.jdbc.dialect.DialectOracle;
import org.nuxeo.runtime.datasource.ConnectionHelper;
import org.nuxeo.runtime.kv.AbstractKeyValueStoreProvider;
import org.nuxeo.runtime.kv.KeyValueStoreDescriptor;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * SQL implementation of a Key/Value Store Provider.
 * <p>
 * The following configuration properties are available:
 * <ul>
 * <li>datasource: the datasource to use.
 * <li>table: the table to use. The default is the Store name.
 * </ul>
 * If a namespace is specified, it is used as a table name suffix, otherwise of the store name.
 * <p>
 * This implementation uses a table with a KEY column (unique and not NULL), and for the value one of these three
 * columns is used: LONG, STRING, BYTES. If possible LONG is used, then STRING, otherwise BYTES.
 * <p>
 * The TTL is stored as an expiration time (seconds since epoch) in its own column. Expiration is done by a thread
 * running a cleanup DELETE query every 60 seconds.
 *
 * @since 10.10
 */
public class SQLKeyValueStore extends AbstractKeyValueStoreProvider {

    private static final Log log = LogFactory.getLog(SQLKeyValueStore.class);

    /** Datasource configuration property. */
    public static final String DATASOURCE_PROP = "datasource";

    /** Table configuration property. Default is the store name. The namespace is also used for disambiguation. */
    public static final String TABLE_PROP = "table";

    /** Key column, a short string. */
    public static final String KEY_COL = "key";

    /** Long column, or NULL if the value is not representable as a Long. */
    public static final String LONG_COL = "long";

    /** String column, or NULL if the value is representable as a Long or not representable as a String. */
    public static final String STRING_COL = "string";

    /** Bytes column, or NULL if the value is representable as a Long or String. */
    public static final String BYTES_COL = "bytes";

    /** TTL column, holding expiration date in seconds since epoch, or NULL if there is no expiration. */
    public static final String TTL_COL = "ttl";

    protected static final int TTL_EXPIRATION_FREQUENCY_MS = 60_000; // 60 seconds

    // maximum number of retries in case of concurrency
    protected static final int MAX_RETRY = 5;

    protected JDBCLogger logger;

    protected String name;

    protected String dataSourceName;

    protected Dialect dialect;

    protected TableImpl table;

    protected Column keyCol;

    protected Column longCol;

    protected Column stringCol;

    protected Column bytesCol;

    protected Column ttlCol;

    protected String tableName;

    protected String keyColName;

    protected String longColName;

    protected String stringColName;

    protected String bytesColName;

    protected String ttlColName;

    protected Thread ttlThread;

    protected String getSQL;

    protected String getMultiSQL;

    protected String deleteAllSQL;

    protected String deleteSQL;

    protected String deleteIfLongSQL;

    protected String deleteIfStringSQL;

    protected String deleteIfBytesSQL;

    protected String expireSQL;

    protected String keyStreamSQL;

    protected String setTTLSQL;

    protected String existsSQL;

    protected String insertSQL;

    @Override
    public void initialize(KeyValueStoreDescriptor descriptor) {
        name = descriptor.name;
        logger = new JDBCLogger(name);
        Map<String, String> properties = descriptor.properties;
        dataSourceName = properties.get(DATASOURCE_PROP);
        if (StringUtils.isAllBlank(dataSourceName)) {
            throw new NuxeoException("Missing " + DATASOURCE_PROP + " property in configuration");
        }
        String tableProp = properties.get(TABLE_PROP);
        String tbl;
        if (isBlank(tableProp)) {
            tbl = name.trim();
        } else {
            tbl = tableProp.trim();
        }
        // check connection, get dialect and create table if needed
        runWithConnection(connection -> {
            dialect = Dialect.createDialect(connection, null);
            getTable(connection, tbl);
        });
        prepareSQL();
        startTTLThread();
    }

    @Override
    public void close() {
        stopTTLThread();
    }

    protected void getTable(Connection connection, String tbl) throws SQLException {
        String tablePhysicalName = dialect.getTableName(tbl);
        table = new TableImpl(dialect, tablePhysicalName, tablePhysicalName);
        keyCol = addColumn(KEY_COL, ColumnType.SYSNAME);
        keyCol.setPrimary(true);
        keyCol.setNullable(false);
        longCol = addColumn(LONG_COL, ColumnType.LONG);
        stringCol = addColumn(STRING_COL, ColumnType.CLOB);
        bytesCol = addColumn(BYTES_COL, ColumnType.BLOB);
        ttlCol = addColumn(TTL_COL, ColumnType.LONG);
        table.addIndex(TTL_COL);
        tableName = table.getQuotedName();
        keyColName = keyCol.getQuotedName();
        longColName = longCol.getQuotedName();
        stringColName = stringCol.getQuotedName();
        bytesColName = bytesCol.getQuotedName();
        ttlColName = ttlCol.getQuotedName();
        if (!tableExists(connection)) {
            createTable(connection);
        }
        checkColumns(connection);
    }

    protected Column addColumn(String columnName, ColumnType type) {
        String colPhysicalName = dialect.getColumnName(columnName);
        Column column = new Column(table, colPhysicalName, type, columnName);
        return table.addColumn(column.getKey(), column);
    }

    protected void prepareSQL() {
        getSQL = "SELECT " + longColName + ", " + stringColName + ", " + bytesColName + " FROM " + tableName + " WHERE "
                + keyColName + " = ?";
        getMultiSQL = "SELECT " + keyColName + ", " + longColName + ", " + stringColName + ", " + bytesColName
                + " FROM " + tableName + " WHERE " + keyColName + " IN (%s)";
        deleteAllSQL = "DELETE FROM " + tableName;
        deleteSQL = "DELETE FROM " + tableName + " WHERE " + keyColName + " = ?";
        deleteIfLongSQL = deleteSQL + " AND " + longColName + " = ?";
        deleteIfStringSQL = deleteSQL + " AND " + dialect.getQuotedNameForExpression(stringCol) + " = ?";
        deleteIfBytesSQL = deleteSQL + " AND " + bytesColName + " = ?";
        expireSQL = "DELETE FROM " + tableName + " WHERE " + ttlColName + " < ?";
        keyStreamSQL = "SELECT " + keyColName + " FROM " + tableName;
        setTTLSQL = "UPDATE " + tableName + " SET " + ttlColName + " = ? WHERE " + keyColName + " = ?";
        existsSQL = "SELECT 1 FROM " + tableName + " WHERE " + keyColName + " = ?";
        insertSQL = "INSERT INTO " + tableName + "(" + keyColName + ", " + longColName + ", " + stringColName + ", "
                + bytesColName + ", " + ttlColName + ") VALUES (?, ?, ?, ?, ?)";
    }

    protected void startTTLThread() {
        ttlThread = new Thread(this::expireTTLThread);
        ttlThread.setName("Nuxeo-Expire-KeyValueStore-" + name);
        ttlThread.setDaemon(true);
        ttlThread.start();
    }

    protected void stopTTLThread() {
        if (ttlThread == null) {
            return;
        }
        ttlThread.interrupt();
        ttlThread = null;
    }

    /**
     * Runs in a thread to do TTL expiration.
     */
    protected void expireTTLThread() {
        if (log.isDebugEnabled()) {
            log.debug("Starting TTL expiration thread for KeyValueStore: " + name);
        }
        try {
            // for the initial wait, use a random duration to avoid thundering herd problems
            Thread.sleep((long) (TTL_EXPIRATION_FREQUENCY_MS * Math.random()));
            for (;;) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                Thread.sleep(TTL_EXPIRATION_FREQUENCY_MS);
                expireTTLOnce();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (log.isDebugEnabled()) {
            log.debug("Stopping TTL expiration thread for KeyValueStore: " + name);
        }
    }

    /**
     * Canonicalizes value for the database: use a String or a Long if possible.
     */
    protected Object toStorage(Object value) {
        // try to convert byte array to UTF-8 string
        if (value instanceof byte[]) {
            try {
                value = bytesToString((byte[]) value);
            } catch (CharacterCodingException e) {
                // ignore
            }
        }
        // try to convert String to Long
        if (value instanceof String) {
            try {
                value = Long.valueOf((String) value);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return value;
    }

    protected byte[] toBytes(Object value) {
        if (value instanceof String) {
            return ((String) value).getBytes(UTF_8);
        } else if (value instanceof Long) {
            return ((Long) value).toString().getBytes(UTF_8);
        } else if (value instanceof byte[]) {
            return (byte[]) value;
        }
        return null;
    }

    protected String toString(Object value) {
        if (value instanceof String) {
            return (String) value;
        } else if (value instanceof Long) {
            return ((Long) value).toString();
        } else if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            try {
                return bytesToString(bytes);
            } catch (CharacterCodingException e) {
                return null;
            }
        }
        return null;
    }

    /** A {@link java.util.function.Consumer Consumer} that can throw {@link SQLException}. */
    @FunctionalInterface
    protected interface SQLConsumer<T> {

        /**
         * Performs this operation on the given argument.
         *
         * @param t the input argument
         * @throws SQLException
         */
        void accept(T t) throws SQLException;
    }

    /** A {@link java.util.function.Function Function} that can throw {@link SQLException}. */
    @FunctionalInterface
    protected interface SQLFunction<T, R> {

        /**
         * Applies this function to the given argument.
         *
         * @param t the function argument
         * @return the function result
         * @throws SQLException
         */
        R apply(T t) throws SQLException;
    }

    protected void runWithConnection(SQLConsumer<Connection> consumer) {
        TransactionHelper.runWithoutTransaction(() -> {
            try (Connection connection = getConnection()) {
                consumer.accept(connection);
            } catch (SQLException e) {
                throw new NuxeoException(e);
            }
        });
    }

    protected <R> R runWithConnection(SQLFunction<Connection, R> function) {
        return TransactionHelper.runWithoutTransaction(() -> {
            try (Connection connection = getConnection()) {
                return function.apply(connection);
            } catch (SQLException e) {
                throw new NuxeoException(e);
            }
        });
    }

    protected Connection getConnection() throws SQLException {
        return ConnectionHelper.getConnection(dataSourceName);
    }

    protected void setToPreparedStatement(String sql, PreparedStatement ps, Column column, Serializable value)
            throws SQLException {
        setToPreparedStatement(sql, ps, Arrays.asList(column), Arrays.asList(value));
    }

    protected void setToPreparedStatement(String sql, PreparedStatement ps, Column column1, Serializable value1,
            Column column2, Serializable value2) throws SQLException {
        setToPreparedStatement(sql, ps, Arrays.asList(column1, column2), Arrays.asList(value1, value2));
    }

    protected void setToPreparedStatement(String sql, PreparedStatement ps, Column column1, Serializable value1,
            Column column2, Serializable value2, Column column3, Serializable value3) throws SQLException {
        setToPreparedStatement(sql, ps, Arrays.asList(column1, column2, column3),
                Arrays.asList(value1, value2, value3));
    }

    protected void setToPreparedStatement(String sql, PreparedStatement ps, List<Column> columns,
            List<? extends Serializable> values) throws SQLException {
        if (columns.size() != values.size()) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < columns.size(); i++) {
            columns.get(i).setToPreparedStatement(ps, i + 1, values.get(i));
        }
        if (logger.isLogEnabled()) {
            logger.logSQL(sql, values);
        }
    }

    protected boolean tableExists(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String schemaName = getDatabaseSchemaName(connection);
        try (ResultSet rs = metadata.getTables(null, schemaName, table.getPhysicalName(), new String[] { "TABLE" })) {
            boolean exists = rs.next();
            if (log.isTraceEnabled()) {
                log.trace("Checking if table " + table.getPhysicalName() + " exists: " + exists);
            }
            return exists;
        }
    }

    protected void createTable(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            String createSQL = table.getCreateSql();
            logger.log(createSQL);
            st.execute(createSQL);
            for (String sql : table.getPostCreateSqls(null)) {
                logger.log(sql);
                st.execute(sql);
            }
        }
    }

    /**
     * Checks that columns have expected JDBC types.
     */
    protected void checkColumns(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String schemaName = getDatabaseSchemaName(connection);
        try (ResultSet rs = metadata.getColumns(null, schemaName, table.getPhysicalName(), "%")) {
            while (rs.next()) {
                String schema = rs.getString("TABLE_SCHEM");
                if (schema != null) { // null for MySQL, doh!
                    if ("INFORMATION_SCHEMA".equals(schema.toUpperCase())) {
                        // H2 returns some system tables (locks)
                        continue;
                    }
                }
                String columnName = rs.getString("COLUMN_NAME").toUpperCase();
                int actual = rs.getInt("DATA_TYPE");
                String actualName = rs.getString("TYPE_NAME");
                int actualSize = rs.getInt("COLUMN_SIZE");
                Column column = null;
                for (Column c : table.getColumns()) {
                    String upperName = c.getPhysicalName().toUpperCase();
                    if (upperName.equals(columnName)) {
                        column = c;
                    }
                }
                if (column == null) {
                    log.error("Column not found: " + columnName + " in table: " + tableName);
                    continue;
                }
                int expected = column.getJdbcType();
                if (!column.setJdbcType(actual, actualName, actualSize)) {
                    log.error("SQL type mismatch for " + column.getFullQuotedName() + ": expected " + expected
                            + ", database has " + actual + " / " + actualName + " (" + actualSize + ")");
                }
            }
        }
    }

    protected String getDatabaseSchemaName(Connection connection) throws SQLException {
        String schemaName = null;
        if (dialect instanceof DialectOracle) {
            try (Statement st = connection.createStatement()) {
                String sql = "SELECT SYS_CONTEXT('USERENV', 'SESSION_USER') FROM DUAL";
                logger.log(sql);
                try (ResultSet rs = st.executeQuery(sql)) {
                    if (rs.next()) {
                        schemaName = rs.getString(1);
                        logger.log("  -> schema: " + schemaName);
                    }
                }
            }
        }
        return schemaName;
    }

    protected void expireTTLOnce() {
        runWithConnection(connection -> {
            try {
                try (PreparedStatement ps = connection.prepareStatement(expireSQL)) {
                    Long ttlDeadline = getTTLValue(0);
                    setToPreparedStatement(expireSQL, ps, ttlCol, ttlDeadline);
                    int count = ps.executeUpdate();
                    logger.logCount(count);
                }
            } catch (SQLException e) {
                if (dialect.isConcurrentUpdateException(e)) {
                    // ignore
                    return;
                }
                log.debug("Exception during TTL expiration", e);
            }
        });
    }

    @Override
    public void clear() {
        runWithConnection(connection -> {
            try (Statement st = connection.createStatement()) {
                logger.log(deleteAllSQL);
                st.execute(deleteAllSQL);
            }
        });
    }

    @Override
    public Stream<String> keyStream() {
        return runWithConnection((Connection connection) -> keyStream(connection));
    }

    protected Stream<String> keyStream(Connection connection) throws SQLException {
        List<String> keys = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(keyStreamSQL)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = (String) keyCol.getFromResultSet(rs, 1);
                    keys.add(key);
                }
            }
        }
        return keys.stream();
    }

    @Override
    public byte[] get(String key) {
        Object value = getObject(key);
        if (value == null) {
            return null;
        }
        byte[] bytes = toBytes(value);
        if (bytes != null) {
            return bytes;
        }
        throw new UnsupportedOperationException(value.getClass().getName());
    }

    @Override
    public String getString(String key) {
        Object value = getObject(key);
        if (value == null) {
            return null;
        }
        String string = toString(value);
        if (string != null) {
            return string;
        }
        throw new IllegalArgumentException("Value is not a String for key: " + key);
    }

    @Override
    public Map<String, byte[]> get(Collection<String> keys) {
        Map<String, byte[]> map = new HashMap<>(keys.size());
        getObjects(keys, (key, value) -> {
            byte[] bytes = toBytes(value);
            if (bytes == null) {
                throw new UnsupportedOperationException(String.format("Value of class %s is not supported for key: %s",
                        value.getClass().getName(), key));
            }
            map.put(key, bytes);
        });
        return map;
    }

    @Override
    public Map<String, String> getStrings(Collection<String> keys) {
        Map<String, String> map = new HashMap<>(keys.size());
        getObjects(keys, (key, value) -> {
            String string = toString(value);
            if (string == null) {
                throw new IllegalArgumentException("Value is not a String for key: " + key);
            }
            map.put(key, string);
        });
        return map;
    }

    protected Object getObject(String key) {
        return runWithConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(getSQL)) {
                setToPreparedStatement(getSQL, ps, keyCol, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        if (logger.isLogEnabled()) {
                            logger.log("  -> null");
                        }
                        return null;
                    }
                    Long longValue = (Long) longCol.getFromResultSet(rs, 1);
                    String string = (String) stringCol.getFromResultSet(rs, 2);
                    byte[] bytes = (byte[]) bytesCol.getFromResultSet(rs, 3);
                    if (logger.isLogEnabled()) {
                        logger.logResultSet(rs, Arrays.asList(longCol, stringCol, bytesCol));
                    }
                    if (string != null) {
                        return string;
                    } else if (longValue != null) {
                        return longValue;
                    } else {
                        return bytes;
                    }
                }
            }
        });
    }

    protected void getObjects(Collection<String> keys, BiConsumer<String, Object> consumer) {
        if (keys.isEmpty()) {
            return;
        }
        runWithConnection((Connection connection) -> {
            String sql = String.format(getMultiSQL, nParams(keys.size()));
            logger.logSQL(sql, keys);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int i = 1;
                for (String key : keys) {
                    keyCol.setToPreparedStatement(ps, i++, key);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String key = (String) keyCol.getFromResultSet(rs, 1);
                        Long longVal = (Long) longCol.getFromResultSet(rs, 2);
                        String string = (String) stringCol.getFromResultSet(rs, 3);
                        byte[] bytes = (byte[]) bytesCol.getFromResultSet(rs, 4);
                        if (logger.isLogEnabled()) {
                            logger.logResultSet(rs, Arrays.asList(keyCol, longCol, stringCol, bytesCol));
                        }
                        Object value;
                        if (string != null) {
                            value = string;
                        } else if (longVal != null) {
                            value = longVal;
                        } else {
                            value = bytes;
                        }
                        if (value != null) {
                            consumer.accept(key, value);
                        }
                    }
                }
            }
        });
    }

    protected String nParams(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append('?');
        }
        return sb.toString();
    }

    protected Long ttlToStorage(long ttl) {
        return ttl == 0 ? null : getTTLValue(ttl);
    }

    protected Long getTTLValue(long ttl) {
        return Long.valueOf(System.currentTimeMillis() / 1000 + ttl);
    }

    @Override
    public void put(String key, byte[] bytes) {
        put(key, toStorage(bytes), 0);
    }

    @Override
    public void put(String key, byte[] bytes, long ttl) {
        put(key, toStorage(bytes), ttl);
    }

    @Override
    public void put(String key, String string) {
        put(key, toStorage(string), 0);
    }

    @Override
    public void put(String key, String string, long ttl) {
        put(key, toStorage(string), ttl);
    }

    protected void put(String key, Object value, long ttl) {
        runWithConnection((Connection connection) -> {
            if (value == null) {
                // delete
                try (PreparedStatement ps = connection.prepareStatement(deleteSQL)) {
                    setToPreparedStatement(deleteSQL, ps, keyCol, key);
                    ps.execute();
                }
            } else {
                // upsert (update or insert)
                Long longValue = value instanceof Long ? (Long) value : null;
                String stringValue = value instanceof String ? (String) value : null;
                byte[] bytesValue = value instanceof byte[] ? (byte[]) value : null;
                Long ttlValue = ttlToStorage(ttl);
                List<Column> psColumns = new ArrayList<>();
                List<Serializable> psValues = new ArrayList<>();
                String sql = dialect.getUpsertSql(Arrays.asList(keyCol, longCol, stringCol, bytesCol, ttlCol),
                        Arrays.asList(key, longValue, stringValue, bytesValue, ttlValue), psColumns, psValues);
                for (int retry = 0; retry < MAX_RETRY; retry++) {
                    try {
                        try (PreparedStatement ps = connection.prepareStatement(sql)) {
                            setToPreparedStatement(sql, ps, psColumns, psValues);
                            ps.execute();
                        }
                        return;
                    } catch (SQLException e) {
                        if (!dialect.isConcurrentUpdateException(e)) {
                            throw e;
                        }
                        // Oracle MERGE can throw DUP_VAL_ON_INDEX (ORA-0001) or NO_DATA_FOUND (ORA-01403)
                        // in that case retry a few times
                    }
                    sleepBeforeRetry();
                }
                throw new ConcurrentUpdateException("Failed to do atomic put for key: " + key);
            }
        });
    }

    @Override
    public boolean setTTL(String key, long ttl) {
        return runWithConnection((Connection connection) -> {
            try (PreparedStatement ps = connection.prepareStatement(setTTLSQL)) {
                setToPreparedStatement(setTTLSQL, ps, ttlCol, ttlToStorage(ttl), keyCol, key);
                int count = ps.executeUpdate();
                boolean set = count == 1;
                return set;
            }
        }).booleanValue();
    }

    @Override
    public boolean compareAndSet(String key, byte[] expected, byte[] value, long ttl) {
        return compareAndSet(key, toStorage(expected), toStorage(value), ttl);
    }

    @Override
    public boolean compareAndSet(String key, String expected, String value, long ttl) {
        return compareAndSet(key, toStorage(expected), toStorage(value), ttl);
    }

    protected boolean compareAndSet(String key, Object expected, Object value, long ttl) {
        return runWithConnection((Connection connection) -> {
            if (expected == null && value == null) {
                // check that document doesn't exist
                try (PreparedStatement ps = connection.prepareStatement(existsSQL)) {
                    setToPreparedStatement(existsSQL, ps, keyCol, key);
                    try (ResultSet rs = ps.executeQuery()) {
                        boolean set = !rs.next();
                        if (logger.isLogEnabled()) {
                            logger.log("  -> " + (set ? "NOP" : "FAILED"));
                        }
                        return set;
                    }
                }
            } else if (expected == null) {
                // set value if no document already exists: regular insert
                try (PreparedStatement ps = connection.prepareStatement(insertSQL)) {
                    Long longValue = value instanceof Long ? (Long) value : null;
                    String stringValue = value instanceof String ? (String) value : null;
                    byte[] bytesValue = value instanceof byte[] ? (byte[]) value : null;
                    setToPreparedStatement(insertSQL, ps, Arrays.asList(keyCol, longCol, stringCol, bytesCol, ttlCol),
                            Arrays.asList(key, longValue, stringValue, bytesValue, ttlToStorage(ttl)));
                    boolean set;
                    try {
                        ps.executeUpdate();
                        set = true;
                    } catch (SQLException e) {
                        if (!dialect.isConcurrentUpdateException(e)) {
                            throw e;
                        }
                        set = false;
                    }
                    if (logger.isLogEnabled()) {
                        logger.log("  -> " + (set ? "SET" : "FAILED"));
                    }
                    return set;
                }
            } else if (value == null) {
                // delete if previous value exists
                String sql;
                Column col;
                if (expected instanceof Long) {
                    sql = deleteIfLongSQL;
                    col = longCol;
                } else if (expected instanceof String) {
                    sql = deleteIfStringSQL;
                    col = stringCol;
                } else {
                    sql = deleteIfBytesSQL;
                    col = bytesCol;
                }
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    setToPreparedStatement(sql, ps, keyCol, key, col, (Serializable) expected);
                    int count = ps.executeUpdate();
                    boolean set = count == 1;
                    if (logger.isLogEnabled()) {
                        logger.log("  -> " + (set ? "DEL" : "FAILED"));
                    }
                    return set;
                }
            } else {
                // replace if previous value exists
                Column expectedCol = expected instanceof Long ? longCol
                        : expected instanceof String ? stringCol : bytesCol;
                Column valueCol = value instanceof Long ? longCol : value instanceof String ? stringCol : bytesCol;
                if (expectedCol != valueCol) {
                    throw new NuxeoException("TODO expected and value have different types");
                    // TODO in that case we must set to null the old value column
                }
                String sql = "UPDATE " + tableName + " SET " + valueCol.getQuotedName() + " = ?, " + ttlColName
                        + " = ? WHERE " + keyColName + " = ? AND " + dialect.getQuotedNameForExpression(expectedCol)
                        + " = ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    setToPreparedStatement(sql, ps, Arrays.asList(valueCol, ttlCol, keyCol, expectedCol),
                            Arrays.asList((Serializable) value, ttlToStorage(ttl), key, (Serializable) expected));
                    int count = ps.executeUpdate();
                    boolean set = count == 1;
                    if (logger.isLogEnabled()) {
                        logger.log("  -> " + (set ? "SET" : "FAILED"));
                    }
                    return set;
                }
            }
        }).booleanValue();
    }

    protected void sleepBeforeRetry() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NuxeoException(e);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + name + ")";
    }

}
