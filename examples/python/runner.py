#!/usr/bin/env python3
"""
Example Python tool called by SubprocessTool via stdin/stdout.

Usage:
  echo '{"sql": "SELECT * FROM users LIMIT 10"}' | python3 runner.py --trace-id trace-001
"""

import sys
import json
import argparse
import time


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--trace-id", default="unknown")
    args = parser.parse_args()

    # Read params from stdin
    raw = sys.stdin.read()
    params = json.loads(raw) if raw.strip() else {}
    action = params.get("_action", "echo")

    try:
        if action == "echo":
            result = {"received": params}

        elif action == "search":
            query = params.get("query", "")
            # Placeholder: actual search logic goes here
            result = {"query": query, "results": [], "total": 0}

        elif action == "transform":
            data = params.get("data", [])
            result = {"original": data, "transformed": data}

        else:
            result = {"error": f"Unknown action: {action}"}

        # Output unified ToolResponse as JSON to stdout
        print(json.dumps({"success": True, "data": result}))

    except Exception as e:
        print(json.dumps({
            "success": False,
            "data": None,
            "errorCode": "TOOL_EXEC_ERROR",
            "errorMsg": str(e)
        }))


if __name__ == "__main__":
    main()
