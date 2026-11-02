#!/usr/bin/env bash
# subagent-start: forwards the hook payload to tools_used.py. Logging never blocks the run.
python "$(dirname "$0")/tools_used.py" subagent-start || true
exit 0
