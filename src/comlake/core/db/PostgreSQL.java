/*
 * PostgreSQL wrapper
 * Copyright (C) 2021  Nguyễn Gia Phong
 *
 * This file is part of comlake.core.
 *
 * comlake.core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation.
 *
 * comlake.core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with comlake.core.  If not, see <https://www.gnu.org/licenses/>.
 */

package comlake.core.db;

import java.math.BigInteger;
import java.io.InputStream;
import java.beans.PropertyVetoException;
import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static java.sql.Statement.RETURN_GENERATED_KEYS;

import com.google.gson.Gson;

import com.mchange.v2.c3p0.ComboPooledDataSource;

import comlake.core.db.Database;

public class PostgreSQL implements Database {
    private static final String INSERT_CONTENT = (
        "INSERT INTO content (cid, type)"
        + " VALUES (?, ?) ON CONFLICT DO NOTHING");
    private static final String INSERT_DATASET = (
        "INSERT INTO dataset (file, description, source, topics, extra)"
        + " VALUES (?, ?, ?, ?, ?::json)");
    private static final String UPDATE_DATASET = (
        "INSERT INTO dataset (file, description, source, topics, extra, parent)"
        + " SELECT %s, %s, %s, %s, %s, id FROM dataset WHERE id = %d");
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

    /** Insert given file to table content. **/
    public boolean insertFile(String cid, String type) {
        try (var conn = pool.getConnection();
             var statement = conn.prepareStatement(INSERT_CONTENT)) {
            statement.setObject(1, cid);
            statement.setObject(2, type);
            statement.executeUpdate();
        } catch (SQLException e) {
            return false;
        }
        return true;
    }

    /** Insert given directory to table content. **/
    public boolean insertDirectory(String cid) {
        return insertFile(cid, "inode/directory");
    }

    /** Insert given row to table dataset. **/
    public String insertDataset(Map<String, Object> dataset) {
        try (var conn = pool.getConnection();
             var statement = conn.prepareStatement(INSERT_DATASET,
                                                   RETURN_GENERATED_KEYS)) {
            statement.setObject(1, dataset.remove("file"));
            statement.setObject(2, dataset.remove("description"));
            statement.setObject(3, dataset.remove("source"));
            // No, I don't want to talk about this.
            var topics = (ArrayList<String>) dataset.remove("topics");
            statement.setObject(4, String.join(",", topics).split(","));
            statement.setObject(5, gson.toJson(dataset));
            statement.executeUpdate();
            var rs = statement.getGeneratedKeys();
            rs.next();
            return String.valueOf(rs.getLong("id"));
        } catch (SQLException e) {
            return null;
        }
    }

    /** Quote SQL string **/
    private String quote(String s) {
        return String.format("'%s'", s.replace("'", "''"));
    }

    /** Insert updated row to table dataset. **/
    public String updateDataset(Map<String, Object> dataset) {
        try (var conn = pool.getConnection();
             var statement = conn.createStatement()) {
            var parent = (String) dataset.remove("parent");
            var file = (String) dataset.remove("file");
            var description = (String) dataset.remove("description");
            var source = (String) dataset.remove("source");
            // No, I don't want to talk about this.
            var topics = (ArrayList<String>) dataset.remove("topics");
            var query = String.format(
                UPDATE_DATASET,
                file == null ? "file" : quote(file),
                description == null ? "description" : quote(description),
                source == null ? "source" : quote(source),
                topics == null ? "topics" : quote(String.format(
                    "{\"%s\"}", String.join("\",\"", topics))),
                dataset.isEmpty() ? "extra"
                                  : quote(gson.toJson(dataset)) + "::json",
                Long.parseLong(parent));
            statement.execute(query, RETURN_GENERATED_KEYS);
            var rs = statement.getGeneratedKeys();
            rs.next();
            return String.valueOf(rs.getLong("id"));
        } catch (SQLException e) {
            return null;
        }
    }

    /** Filter for rows matching predicate, return null on errors. **/
    public ArrayList<Map<String, Object>> search(String predicate) {
        var result = new ArrayList<Map<String, Object>>();
        var query = "SELECT * FROM dataset WHERE " + predicate;
        try (var conn = pool.getConnection();
             var statement = conn.createStatement()) {
            var rs = statement.executeQuery(query);
            while (rs.next()) {
                var row = gson.fromJson(rs.getString("extra"), Map.class);
                row.put("id", String.valueOf(rs.getLong("id")));
                row.put("file", rs.getString("file"));
                row.put("description", rs.getString("description"));
                row.put("source", rs.getString("source"));
                row.put("topics", ((Array) rs.getObject("topics")).getArray());
                row.put("parent", String.valueOf(rs.getLong("parent")));
                result.add(row);
            }
        } catch (SQLException e) {
            System.out.println(e);
            return null;
        }
        return result;
    }
}
