#!/usr/bin/env bash
# log-tool-use: forwards the hook payload to tools_used.py. Logging never blocks the run.
python "$(dirname "$0")/tools_used.py" log-tool-use || true
exit 0
