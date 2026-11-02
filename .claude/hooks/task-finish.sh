#!/usr/bin/env bash
# task-finish: forwards the hook payload to tools_used.py. Logging never blocks the run.
python "$(dirname "$0")/tools_used.py" task-finish || true
exit 0
