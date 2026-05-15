package com.toolgateway.execution.tool;

import com.toolgateway.core.annotation.Tool;
import com.toolgateway.core.annotation.ToolGuard;
import com.toolgateway.core.handler.ToolHandler;
import com.toolgateway.core.model.RunnerType;
import com.toolgateway.core.model.ToolRequest;
import com.toolgateway.core.model.ToolResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * Example: execute read-only SQL queries against a configured DataSource.
 * In production this would use connection pooling (HikariCP).
 */
@Component
@Tool(
    name        = "mysql_query",
    version     = "1.0.0",
    description = "Execute read-only SQL queries against the primary database",
    runnerType  = RunnerType.LOCAL,
    tags        = {"database", "sql", "read"}
)
@ToolGuard(
    roles      = {"admin", "operator", "analyst"},
    timeoutMs  = 10_000,
    retries    = 2,
    circuitBreakerThreshold = 0.5
)
public class MySQLQueryTool implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(MySQLQueryTool.class);

    private final DataSource dataSource;

    public MySQLQueryTool(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public ToolResponse<?> execute(ToolRequest request) {
         // 1. 提取参数
        String sql = (String) request.params().get("sql");
        // 2. 参数校验
        if (sql == null || sql.isBlank()) {
            return ToolResponse.fail("PARAM_INVALID", "Missing required param: sql");
        }
        // 3. 安全校验
        // Safety: only allow SELECT
        String trimmed = sql.trim().toUpperCase();
        if (!trimmed.startsWith("SELECT") && !trimmed.startsWith("SHOW") && !trimmed.startsWith("DESCRIBE")) {
            return ToolResponse.fail("PARAM_INVALID", "Only read-only queries (SELECT/SHOW/DESCRIBE) are allowed");
        }

        long start = System.currentTimeMillis();
        // 4. 执行核心逻辑
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            List<Map<String, Object>> rows = new ArrayList<>();
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }
                rows.add(row);
            }

            long cost = System.currentTimeMillis() - start;
            log.info("MySQL query executed: tool={}, rows={}, costMs={}", request.toolName(), rows.size(), cost);
            return ToolResponse.success(Map.of("columns", colCount, "rows", rows, "rowCount", rows.size()), cost);

        } catch (SQLException e) {
            log.error("MySQL query failed: {}", sql, e);
            return ToolResponse.fail("TOOL_EXEC_FAILED", e.getMessage());
        }
    }

    /** Fallback when circuit breaker opens */
    @SuppressWarnings("unused")
    public ToolResponse<?> circuitBreakFallback(ToolRequest request) {
        return ToolResponse.fail("CIRCUIT_OPEN",
                "MySQL query tool is temporarily unavailable, please try again later");
    }
}
