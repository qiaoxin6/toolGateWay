package com.toolgateway.execution.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolgateway.core.annotation.Tool;
import com.toolgateway.core.annotation.ToolGuard;
import com.toolgateway.core.handler.ToolHandler;
import com.toolgateway.core.model.RunnerType;
import com.toolgateway.core.model.ToolRequest;
import com.toolgateway.core.model.ToolResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 通用子进程运行器：通过生成子进程调用任意外部脚本（Python、Node、Shell...）。
 * 标准输入 ← ToolRequest 参数，标准输出 → ToolResponse。
 *
 * 脚本路径通过 @Tool 的 target 字段指定。
 */
@Component
@Tool(
    name        = "subprocess_runner",
    version     = "1.0.0",
    description = "Execute arbitrary scripts via subprocess (Python, Node, Shell)",
    runnerType  = RunnerType.SUBPROCESS,
    target      = "/tools/runner.py",
    tags        = {"script", "polyglot", "generic"}
)
@ToolGuard(
    roles      = {"admin"},
    timeoutMs  = 60_000,
    retries    = 0
)
public class SubprocessTool implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(SubprocessTool.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public ToolResponse<?> execute(ToolRequest request) {
        // Resolve script path: prefer param override, then context config, then default
        String scriptPath = (String) request.params().getOrDefault(
                "_script", "/tools/runner.py");

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    getInterpreter(scriptPath), scriptPath,
                    "--trace-id", request.traceId()
            );
            pb.redirectErrorStream(false);
            Process proc = pb.start();

            // Write params to stdin
            String inputJson = objectMapper.writeValueAsString(request.params());
            try (var writer = proc.outputWriter()) {
                writer.write(inputJson);
                writer.flush();
            }

            // Read result from stdout
            boolean finished = proc.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                return ToolResponse.fail("TOOL_TIMEOUT", "Subprocess exceeded 60s limit");
            }

            String stdout = new String(proc.getInputStream().readAllBytes());
            String stderr = new String(proc.getErrorStream().readAllBytes());

            if (proc.exitValue() != 0) {
                log.error("Subprocess failed: script={}, exitCode={}, stderr={}", scriptPath, proc.exitValue(), stderr);
                return ToolResponse.fail("TOOL_EXEC_FAILED", stderr.isBlank() ? "exit code " + proc.exitValue() : stderr);
            }

            return objectMapper.readValue(stdout, ToolResponse.class);

        } catch (Exception e) {
            log.error("Subprocess execution failed: script={}", scriptPath, e);
            return ToolResponse.fail("TOOL_EXEC_ERROR", e.getMessage());
        }
    }

    private String getInterpreter(String scriptPath) {
        if (scriptPath.endsWith(".py")) return "python3";
        if (scriptPath.endsWith(".js") || scriptPath.endsWith(".mjs")) return "node";
        if (scriptPath.endsWith(".sh")) return "bash";
        return "python3"; // default
    }
}
