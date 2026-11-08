#!/usr/bin/env bash
# FlowNotes Launcher
cd "$(dirname "$0")"
echo "Starting FlowNotes Server on http://127.0.0.1:8080..."
python3 server.py
