/*
 * PostgreSQL wrapper
 * Copyright (C) 2021  Nguyễn Gia Phong
 *
 * This file is part of comlake-core.
 *
 * comlake-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation.
 *
 * comlake-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with comlake-core.  If not, see <https://www.gnu.org/licenses/>.
 */

package comlake_core;

import java.math.BigInteger;
import java.io.InputStream;
import java.beans.PropertyVetoException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import com.mchange.v2.c3p0.ComboPooledDataSource;

import comlake_core.BaseMetadata;
import comlake_core.Database;
import comlake_core.Metadata;

public class PostgreSQL implements Database {
    private static final String TABLE = "comlake";
    private static final String INSERT = (
        "INSERT INTO " + TABLE
        + " (cid, length, type, name, source, topics, optional)"
        + " VALUES (?, ?, ?, ?, ?, ?, ?::json)");
    private static final String CLEAR = "TRUNCATE " + TABLE;
    private static final Gson gson = new Gson();
    private ComboPooledDataSource pool;

    public PostgreSQL(String url, String user, String password) {
        pool = new ComboPooledDataSource();
        try {
            pool.setDriverClass("org.postgresql.Driver");
            pool.setJdbcUrl("jdbc:postgresql:comlake");
            // FIXME: credentials should be securely stored
            pool.setUser("postgres");
            pool.setPassword("postgres");
        } catch (PropertyVetoException e) {
            // TODO: say something
        }
    }

    public void close() {
        pool.close();
    }

    /** Clear the PostgreSQL table. **/
    public boolean clear() {
        try (var conn = pool.getConnection()) {
            conn.createStatement().executeQuery(CLEAR);
        } catch (SQLException e) {
            return false;
        }
        return true;
    }

    /** Insert given row to PostgreSQL. **/
    public boolean insert(Metadata metadata) {
        try (var conn = pool.getConnection();
             var statement = conn.prepareStatement(INSERT)) {
            statement.setObject(1, metadata.cid);
            statement.setObject(2, metadata.length);
            statement.setObject(3, metadata.type);
            statement.setObject(4, metadata.name);
            statement.setObject(5, metadata.source);
            statement.setObject(6, metadata.topics);
            statement.setObject(7, gson.toJson(metadata.optional));
            statement.executeUpdate();
        } catch (SQLException e) {
            return false;
        }
        return true;
    }

    /** Filter for rows matching predicate. **/
    public List<Metadata> search(String predicate) {
        var result = new ArrayList<Metadata>();
        var query = "SELECT * FROM " + TABLE + " WHERE " + predicate;
        try (var conn = pool.getConnection()) {
            var rs = conn.createStatement().executeQuery(query);
            while (rs.next()) {
                var base = new BaseMetadata(
                    BigInteger.valueOf(rs.getLong("length")),
                    rs.getString("type"),
                    rs.getString("name"),
                    rs.getString("source"),
                    (String[]) rs.getArray("topics").getArray(),
                    gson.fromJson(rs.getString("optional"), Map.class));
                result.add(Metadata.of(base, rs.getString("cid")));
            }
        } catch (SQLException e) {
            return null;
        }
        return result;
    }
}
