"""
Python HTTP Sidecar 工具模板 —— 与 Java Tool Gateway 的数据契约。

═══════════════════════════════════════════════════════════
数据契约（必须严格遵守，由 Java 端 ObjectMapper.SNAKE_CASE 决定）
═══════════════════════════════════════════════════════════

Java → Python（ToolRequest —— 网关 POST 过来的 JSON）:
{
  "tool_name": "python_search",      // 工具名
  "params":    {"query": "test"},     // 工具参数
  "trace_id":  "trace-001",           // 全链路追踪 ID
  "context": {                        // 上下文
    "protocol": "http",
    "tenant":   "default",
    "caller":   "anonymous",
    "role":     "admin"
  }
}

Python → Java（ToolResponse —— 你必须返回的 JSON）:
{
  "success":   true,                    // 必须，布尔
  "data":      {"result": "..."},       // 成功时有值
  "error_code": "E200",                // 失败时必填，见 ErrorCode 枚举
  "error_msg":  "something went wrong", // 失败时必填
  "cost_ms":    15                      // 建议，执行耗时
}

部署:
  pip install fastapi uvicorn
  uvicorn tool_server:app --host 0.0.0.0 --port 8000

注册（application.yml 中配置，启动自动注册）:
  gateway:
    external-tools:
      - name: python_search
        url: http://localhost:8000/search
        timeout: 30s
"""

from fastapi import FastAPI, Header
from pydantic import BaseModel
from typing import Any, Optional
import time

app = FastAPI(title="Python Tool Sidecar")


# ── 契约模型（字段名必须用 snake_case） ───────────────────────

class ToolRequest(BaseModel):
    tool_name: str
    params: dict[str, Any]
    trace_id: str
    context: dict[str, str]


class ToolResponse(BaseModel):
    success: bool
    data: Optional[Any] = None
    error_code: Optional[str] = None
    error_msg: Optional[str] = None
    cost_ms: int = 0


# ── 工具实现 ──────────────────────────────────────────────────

@app.post("/search")
def search(req: ToolRequest):
    start = time.time()
    try:
        query = req.params.get("query", "")
        # ─── 替换为你的实际搜索逻辑（ES、向量检索等）───
        results = {"query": query, "results": [], "hits": 0}

        return ToolResponse(
            success=True,
            data=results,
            cost_ms=int((time.time() - start) * 1000)
        ).model_dump()
    except Exception as e:
        return ToolResponse(
            success=False,
            error_code="E200",
            error_msg=str(e)
        ).model_dump()


@app.post("/analyze")
def analyze(req: ToolRequest):
    start = time.time()
    try:
        dataset = req.params.get("dataset", "")
        # ─── 替换为你的实际分析逻辑（pandas、numpy 等）───
        summary = {"dataset": dataset, "rows": 0, "insights": []}

        return ToolResponse(
            success=True,
            data=summary,
            cost_ms=int((time.time() - start) * 1000)
        ).model_dump()
    except Exception as e:
        return ToolResponse(
            success=False,
            error_code="E201",
            error_msg=str(e)
        ).model_dump()


@app.get("/health")
def health():
    return {"status": "ok"}
