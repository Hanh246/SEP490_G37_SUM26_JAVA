package com.sep.comiverse.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates optional performance indexes outside the seed transaction.
 *
 * <p>CREATE INDEX CONCURRENTLY cannot run inside a transaction block. A
 * PostgreSQL advisory lock also guarantees that only one application instance
 * creates the indexes at a time when multiple replicas start together.</p>
 */
@Component
@RequiredArgsConstructor
@Order(2)
public class DatabaseIndexInitializer implements ApplicationRunner {

    private static final long ADVISORY_LOCK_KEY = 7_320_264_901L;

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);

            try (Statement statement = connection.createStatement()) {
                acquireAdvisoryLock(statement);
                try {
                    createIndexes(statement);
                    System.out.println("✅ Database indexes created or verified");
                } finally {
                    releaseAdvisoryLock(statement);
                }
            }
        } catch (SQLException exception) {
            // Indexes improve performance, but failure must not corrupt or abort
            // the seed transaction or prevent the application from starting.
            System.err.println(
                    "⚠️ Warning: Database index initialization failed: "
                            + rootMessage(exception)
            );
        }
    }

    private void createIndexes(Statement statement) {
        createIndexSafely(
                statement,
                "idx_comics_summary_vector_hnsw",
                "comics",
                "summary_vector",
                """
                CREATE INDEX CONCURRENTLY IF NOT EXISTS
                    idx_comics_summary_vector_hnsw
                ON comics USING hnsw
                    (summary_vector vector_cosine_ops)
                """
        );

        createIndexSafely(
                statement,
                "idx_users_user_vector_hnsw",
                "users",
                "user_vector",
                """
                CREATE INDEX CONCURRENTLY IF NOT EXISTS
                    idx_users_user_vector_hnsw
                ON users USING hnsw
                    (user_vector vector_cosine_ops)
                """
        );

        createIndexSafely(
                statement,
                "idx_chapters_project_team_id",
                "chapters",
                "project_team_id",
                """
                CREATE INDEX CONCURRENTLY IF NOT EXISTS
                    idx_chapters_project_team_id
                ON chapters (project_team_id)
                """
        );

        createIndexSafely(
                statement,
                "idx_team_tasks_project_team_id",
                "team_tasks",
                "project_team_id",
                """
                CREATE INDEX CONCURRENTLY IF NOT EXISTS
                    idx_team_tasks_project_team_id
                ON team_tasks (project_team_id)
                """
        );


        createIndexSafely(
                statement,
                "idx_team_tasks_assignee_completed",
                "team_tasks",
                "assignee_id",
                """
                CREATE INDEX CONCURRENTLY IF NOT EXISTS
                    idx_team_tasks_assignee_completed
                ON team_tasks (assignee_id, completed_at)
                WHERE LOWER(status) IN ('completed', 'complete', 'done')
                """
        );

        createIndexSafely(
                statement,
                "idx_comic_daily_views_comic_date",
                "comic_daily_views",
                "comic_id",
                """
                CREATE INDEX CONCURRENTLY IF NOT EXISTS
                    idx_comic_daily_views_comic_date
                ON comic_daily_views (comic_id, log_date)
                """
        );

        createIndexSafely(
                statement,
                "idx_user_saves_comic_created",
                "user_saves",
                "comic_id",
                """
                CREATE INDEX CONCURRENTLY IF NOT EXISTS
                    idx_user_saves_comic_created
                ON user_saves (comic_id, create_at)
                WHERE COALESCE(deleted, false) = false
                """
        );

        createIndexSafely(
                statement,
                "idx_team_join_requests_project_team_id",
                "team_join_requests",
                "project_team_id",
                """
                CREATE INDEX CONCURRENTLY IF NOT EXISTS
                    idx_team_join_requests_project_team_id
                ON team_join_requests (project_team_id)
                """
        );

        createIndexSafely(
                statement,
                "idx_team_announcements_project_team_id",
                "team_announcements",
                "project_team_id",
                """
                CREATE INDEX CONCURRENTLY IF NOT EXISTS
                    idx_team_announcements_project_team_id
                ON team_announcements (project_team_id)
                """
        );

        createIndexSafely(
                statement,
                "idx_team_messages_project_team_id",
                "team_messages",
                "project_team_id",
                """
                CREATE INDEX CONCURRENTLY IF NOT EXISTS
                    idx_team_messages_project_team_id
                ON team_messages (project_team_id)
                """
        );
    }

    private void createIndexSafely(
            Statement statement,
            String indexName,
            String tableName,
            String columnName,
            String createIndexSql
    ) {
        try {
            if (!columnExists(statement, tableName, columnName)) {
                System.out.printf(
                        "ℹ️ Skipped %s: %s.%s does not exist%n",
                        indexName,
                        tableName,
                        columnName
                );
                return;
            }

            dropInvalidIndexIfPresent(statement, indexName);
            statement.execute(createIndexSql);
            System.out.println("✅ Index created/verified: " + indexName);
        } catch (SQLException exception) {
            // Auto-commit is enabled, so one failed index does not abort the
            // creation of the remaining indexes.
            System.err.println(
                    "⚠️ Warning: Failed to create index " + indexName + ": "
                            + rootMessage(exception)
            );
        }
    }

    private boolean columnExists(
            Statement statement,
            String tableName,
            String columnName
    ) throws SQLException {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = '%s'
                      AND column_name = '%s'
                )
                """.formatted(tableName, columnName);

        try (ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() && resultSet.getBoolean(1);
        }
    }

    private void dropInvalidIndexIfPresent(
            Statement statement,
            String indexName
    ) throws SQLException {
        String checkSql = """
                SELECT NOT index_data.indisvalid
                FROM pg_index index_data
                JOIN pg_class index_class
                  ON index_class.oid = index_data.indexrelid
                JOIN pg_namespace index_namespace
                  ON index_namespace.oid = index_class.relnamespace
                WHERE index_namespace.nspname = 'public'
                  AND index_class.relname = '%s'
                """.formatted(indexName);

        boolean invalid = false;
        try (ResultSet resultSet = statement.executeQuery(checkSql)) {
            if (resultSet.next()) {
                invalid = resultSet.getBoolean(1);
            }
        }

        if (invalid) {
            statement.execute(
                    "DROP INDEX CONCURRENTLY IF EXISTS public." + indexName
            );
            System.out.println("♻️ Removed invalid index: " + indexName);
        }
    }

    private void acquireAdvisoryLock(Statement statement) throws SQLException {
        statement.execute("SELECT pg_advisory_lock(" + ADVISORY_LOCK_KEY + ")");
    }

    private void releaseAdvisoryLock(Statement statement) {
        try {
            statement.execute("SELECT pg_advisory_unlock(" + ADVISORY_LOCK_KEY + ")");
        } catch (SQLException exception) {
            System.err.println(
                    "⚠️ Warning: Failed to release database index advisory lock: "
                            + rootMessage(exception)
            );
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
