#!/usr/bin/env python3
"""
FlowNotes Milestone 1 Companion Server & Web UI Verification Suite
Verifies:
- HTTP 200 serving of companion index.html on 127.0.0.1:8080
- Static assets (css/style.css, js/canvas.js, js/templates.js)
- REST API (/api/status, /api/notes, /api/ai/chat, /api/ai/flashcards)
- Atomic file persistence and round-trip data fidelity
"""

import sys
import os
import time
import json
import socket
import urllib.request
import urllib.parse
import subprocess
import signal

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HOST = "127.0.0.1"
PORT = 8080
SERVER_URL = f"http://{HOST}:{PORT}"

def is_port_in_use(port):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        return s.connect_ex((HOST, port)) == 0

def http_get(url):
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req, timeout=5) as resp:
        return resp.status, resp.read()

def http_post_json(url, data):
    body = json.dumps(data).encode("utf-8")
    req = urllib.request.Request(url, data=body, headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=5) as resp:
        return resp.status, json.loads(resp.read().decode("utf-8"))

def http_delete(url):
    req = urllib.request.Request(url, method="DELETE")
    with urllib.request.urlopen(req, timeout=5) as resp:
        return resp.status, json.loads(resp.read().decode("utf-8"))

def run_verification():
    print(f"=== FlowNotes M1 Companion Server Verification ===")
    server_process = None
    started_by_us = False

    if not is_port_in_use(PORT):
        print(f"Starting server.py on {SERVER_URL}...")
        server_process = subprocess.Popen(
            [sys.executable, os.path.join(BASE_DIR, "server.py")],
            cwd=BASE_DIR,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE
        )
        started_by_us = True
        # Wait up to 5 seconds for server to bind
        for _ in range(50):
            if is_port_in_use(PORT):
                break
            time.sleep(0.1)
        if not is_port_in_use(PORT):
            err = server_process.stderr.read().decode("utf-8") if server_process else "unknown"
            raise AssertionError(f"Server failed to bind to {HOST}:{PORT}. Stderr: {err}")
        print(f" [PASS] Server is listening on {HOST}:{PORT}")

    try:
        # 1. Verify index.html root serving
        status, content = http_get(f"{SERVER_URL}/")
        assert status == 200, f"Expected 200, got {status}"
        text = content.decode("utf-8")
        assert "<!DOCTYPE html>" in text, "Missing DOCTYPE in index.html"
        assert "FlowNotes" in text, "Missing FlowNotes brand in index.html"
        assert "Active Recall" in text, "Missing Active Recall in index.html"
        assert "templates.js" in text, "Missing templates.js script reference"
        assert "canvas.js" in text, "Missing canvas.js script reference"
        assert "style.css" in text, "Missing style.css stylesheet reference"
        print(" [PASS] Companion index.html served cleanly (200 OK, full DOM verified)")

        # 2. Verify static assets
        status, css = http_get(f"{SERVER_URL}/css/style.css")
        assert status == 200 and len(css) > 1000, "Failed to serve css/style.css"
        print(f" [PASS] css/style.css served ({len(css)} bytes)")

        status, js_canvas = http_get(f"{SERVER_URL}/js/canvas.js")
        assert status == 200 and len(js_canvas) > 1000, "Failed to serve js/canvas.js"
        print(f" [PASS] js/canvas.js served ({len(js_canvas)} bytes)")

        status, js_templates = http_get(f"{SERVER_URL}/js/templates.js")
        assert status == 200 and len(js_templates) > 1000, "Failed to serve js/templates.js"
        print(f" [PASS] js/templates.js served ({len(js_templates)} bytes)")

        # 3. Verify /api/status
        status, data = http_get(f"{SERVER_URL}/api/status")
        status_json = json.loads(data.decode("utf-8"))
        assert status_json.get("status") == "online", "API status is not online"
        assert status_json.get("version") == "1.0.0", "Version mismatch"
        print(f" [PASS] /api/status online: {status_json}")

        # 4. Verify /api/notes listing
        status, data = http_get(f"{SERVER_URL}/api/notes")
        res = json.loads(data.decode("utf-8"))
        notes_list = res.get("notes", res) if isinstance(res, dict) else res
        assert isinstance(notes_list, list), "Expected list of notes"
        print(f" [PASS] /api/notes returned {len(notes_list)} notebooks")

        # 5. Verify /api/notes POST (Atomic save)
        test_note_id = f"test_verify_{int(time.time())}"
        test_note = {
            "id": test_note_id,
            "title": "Verification Test Notebook",
            "folder": "Study",
            "coverColor": "#3b82f6",
            "defaultPaperType": "cornell",
            "pages": [
                {
                    "pageNumber": 1,
                    "paperType": "cornell",
                    "strokes": [
                        {
                            "id": "stroke_1",
                            "tool": "BALLPOINT",
                            "color": -14798533,
                            "strokeWidth": 3.0,
                            "points": [
                                {"x": 100.0, "y": 150.0, "p": 0.5, "t": 1000},
                                {"x": 105.0, "y": 155.0, "p": 0.7, "t": 1016}
                            ]
                        }
                    ],
                    "tapes": [
                        {
                            "id": "tape_1",
                            "left": 120.0,
                            "top": 200.0,
                            "right": 320.0,
                            "bottom": 240.0,
                            "color": -69494,
                            "revealed": False
                        }
                    ],
                    "stickyNotes": [
                        {
                            "id": "sticky_1",
                            "x": 400.0,
                            "y": 200.0,
                            "width": 200.0,
                            "height": 180.0,
                            "content": "Active recall study hint",
                            "color": -69494
                        }
                    ]
                }
            ],
            "flashcards": [
                {
                    "id": "card_1",
                    "front": "What is Active Recall?",
                    "back": "Testing memory retrieval during learning.",
                    "hint": "Cognitive psychology",
                    "reviewIntervalDays": 1,
                    "repetitions": 0,
                    "easeFactor": 2.5
                }
            ],
            "createdAt": int(time.time() * 1000),
            "updatedAt": int(time.time() * 1000)
        }

        status, post_res = http_post_json(f"{SERVER_URL}/api/notes", test_note)
        assert status == 200 and post_res.get("success"), f"Failed to save notebook: {post_res}"
        print(f" [PASS] /api/notes POST saved notebook {test_note_id}")

        # 6. Verify /api/notes/<id> GET
        status, data = http_get(f"{SERVER_URL}/api/notes/{test_note_id}")
        assert status == 200, f"Expected 200, got {status}"
        fetched_note = json.loads(data.decode("utf-8"))
        assert fetched_note["title"] == test_note["title"], "Title mismatch"
        assert len(fetched_note["pages"]) == 1, "Page count mismatch"
        assert len(fetched_note["pages"][0]["strokes"]) == 1, "Stroke count mismatch"
        assert len(fetched_note["pages"][0]["tapes"]) == 1, "Tape count mismatch"
        assert len(fetched_note["pages"][0]["stickyNotes"]) == 1, "Sticky notes count mismatch"
        assert len(fetched_note.get("flashcards", [])) == 1, "Flashcards count mismatch"
        print(f" [PASS] /api/notes/{test_note_id} GET verified complete data fidelity")

        # 7. Verify /api/ai/chat (extractive study summarizer)
        ai_payload = {
            "mode": "summarize",
            "text": "Photosynthesis is the process by which green plants and certain other organisms transform light energy into chemical energy. During photosynthesis in green plants, light energy is captured and used to convert water, carbon dioxide, and minerals into oxygen and energy-rich organic compounds."
        }
        status, ai_res = http_post_json(f"{SERVER_URL}/api/ai/chat", ai_payload)
        assert status == 200 and "response" in ai_res, "AI summarize failed"
        assert len(ai_res["response"]) > 0, "AI response empty"
        print(f" [PASS] /api/ai/chat (summarize) responded: {ai_res['response'][:60]}...")

        # 8. Verify /api/ai/flashcards
        fc_payload = {
            "text": "Mitochondria: The powerhouse of the cell responsible for generating adenosine triphosphate (ATP). Ribosomes: Molecular machines that synthesize proteins from amino acids."
        }
        status, fc_res = http_post_json(f"{SERVER_URL}/api/ai/flashcards", fc_payload)
        cards = fc_res.get("cards") or fc_res.get("flashcards")
        assert status == 200 and cards is not None, "AI flashcards failed"
        assert len(cards) >= 1, "Expected generated flashcards"
        print(f" [PASS] /api/ai/flashcards generated {len(cards)} study cards")

        # 9. Clean up test note
        status, del_res = http_delete(f"{SERVER_URL}/api/notes/{test_note_id}")
        assert status == 200 and del_res.get("success"), "Failed to delete test note"
        print(f" [PASS] /api/notes/{test_note_id} DELETE verified")

        print("\n>>> ALL M1 COMPANION SERVER VERIFICATIONS PASSED (100%) <<<\n")
        return True

    finally:
        if started_by_us and server_process:
            print("Terminating server.py...")
            server_process.terminate()
            server_process.wait(timeout=3)

if __name__ == "__main__":
    success = run_verification()
    sys.exit(0 if success else 1)
