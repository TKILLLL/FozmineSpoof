package org.phantam.fozminespoofcore.database.queries;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Base interface for all database queries.
 * Each implementation represents a single SQL operation.
 */
public interface Query<T> {

    /**
     * Executes the query with the given connection.
     *
     * @param connection the database connection
     * @return the result of the query
     * @throws SQLException if an error occurs
     */
    T execute(Connection connection) throws SQLException;
}