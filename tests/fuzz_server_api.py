#!/usr/bin/env python3
"""
Adversarial HTTP Fuzzing & Stress Testing Suite for FlowNotes server.py.
Tests:
1. Path traversal attacks on /api/notes/<id> (GET, DELETE, POST)
2. Malformed / invalid JSON bodies
3. Oversized payloads (1MB, 5MB, 10MB)
4. Protocol violations (negative Content-Length, non-numeric Content-Length, bad methods)
5. Upload PDF robustness against corrupt and boundary-less payloads
6. Post-fuzz liveness verification
"""

import sys
import os
import time
import json
import socket
import urllib.request
import urllib.error
import urllib.parse
import subprocess
import shutil

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HOST = "127.0.0.1"
PORT = 8080
SERVER_URL = f"http://{HOST}:{PORT}"
DATA_DIR = os.path.join(BASE_DIR, "data")
NOTES_DIR = os.path.join(DATA_DIR, "notebooks")

def is_port_in_use(port):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        return s.connect_ex((HOST, port)) == 0

def raw_http_request(method, path, headers=None, body=b"", timeout=5):
    """Sends a raw HTTP request over TCP socket to bypass client-side URL normalization."""
    headers = headers or {}
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(timeout)
    s.connect((HOST, PORT))
    
    req_lines = [f"{method} {path} HTTP/1.1", f"Host: {HOST}:{PORT}"]
    for k, v in headers.items():
        req_lines.append(f"{k}: {v}")
    if body and "Content-Length" not in headers:
        req_lines.append(f"Content-Length: {len(body)}")
    req_lines.append("Connection: close")
    req_lines.append("")
    req_lines.append("")
    
    header_bytes = "\r\n".join(req_lines).encode("utf-8")
    s.sendall(header_bytes + body)
    
    response = b""
    try:
        while True:
            chunk = s.recv(4096)
            if not chunk:
                break
            response += chunk
    except socket.timeout:
        pass
    finally:
        s.close()
        
    # Parse status line
    if not response:
        return 0, b"", b""
    parts = response.split(b"\r\n\r\n", 1)
    header_part = parts[0].decode("utf-8", errors="replace")
    body_part = parts[1] if len(parts) > 1 else b""
    status_line = header_part.split("\r\n")[0]
    status_code = int(status_line.split(" ")[1]) if len(status_line.split(" ")) > 1 and status_line.split(" ")[1].isdigit() else 500
    return status_code, header_part, body_part

def run_adversarial_fuzzing():
    print("================================================================")
    print("=== FlowNotes server.py Empirical Adversarial Fuzzing Suite ===")
    print("================================================================")
    
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
        for _ in range(50):
            if is_port_in_use(PORT):
                break
            time.sleep(0.1)
        if not is_port_in_use(PORT):
            err = server_process.stderr.read().decode("utf-8") if server_process else "unknown"
            raise AssertionError(f"Server failed to bind to {HOST}:{PORT}. Stderr: {err}")
        print(f"[OK] Server is listening on {HOST}:{PORT}")

    findings = []
    tests_run = 0

    try:
        # ---------------------------------------------------------
        # Category 1: Path Traversal Tests
        # ---------------------------------------------------------
        print("\n--- CATEGORY 1: Path Traversal Stress Tests ---")
        traversal_vectors = [
            ("../../server", "Relative traversal to project root"),
            ("../../../../etc/passwd", "Deep traversal to /etc/passwd"),
            ("..%2f..%2fserver", "URL-encoded slash traversal"),
            ("%2e%2e%2f%2e%2e%2fserver", "Fully URL-encoded traversal"),
            ("..\\..\\server", "Windows-style backslash traversal"),
            ("/storage/emulated/0/FlowNotes/server", "Absolute path escape"),
            ("note%00evil", "Null-byte poison traversal"),
        ]

        # 1.1 GET Path Traversal
        for target, desc in traversal_vectors:
            tests_run += 1
            path = f"/api/notes/{target}"
            code, header, body = raw_http_request("GET", path)
            print(f"  [Vector] GET {path} ({desc}) -> HTTP {code}")
            
            # Check if server leaked file contents outside NOTES_DIR
            if code == 200:
                findings.append(f"CRITICAL PATH TRAVERSAL READ: GET {path} returned 200 OK! Content leaked: {body[:80]}")
                print(f"    [!] VULNERABILITY CONFIRMED: File content leaked outside NOTES_DIR!")
            elif code in (400, 403, 404):
                print(f"    [SAFE] Request safely blocked/not found with HTTP {code}")
            else:
                print(f"    [INFO] Responded with HTTP {code}")

        # 1.2 DELETE Path Traversal
        # Create a canary file in project root to test if DELETE can remove it
        canary_path = os.path.join(BASE_DIR, "traversal_delete_canary.tmp")
        with open(canary_path, "w") as f:
            f.write("canary")

        tests_run += 1
        path = f"/api/notes/../../traversal_delete_canary.tmp"
        code, header, body = raw_http_request("DELETE", path)
        print(f"  [Vector] DELETE {path} -> HTTP {code}")
        # server.py appends .json: f"{note_id}.json", so test without .tmp extension
        canary_json_path = os.path.join(BASE_DIR, "traversal_delete_canary.json")
        with open(canary_json_path, "w") as f:
            f.write("canary_json")

        tests_run += 1
        path = f"/api/notes/../../traversal_delete_canary"
        code, header, body = raw_http_request("DELETE", path)
        print(f"  [Vector] DELETE {path} -> HTTP {code}")
        if not os.path.exists(canary_json_path):
            findings.append(f"CRITICAL PATH TRAVERSAL DELETE: DELETE {path} deleted file outside NOTES_DIR ({canary_json_path})!")
            print(f"    [!] VULNERABILITY CONFIRMED: Arbitrary file deletion outside NOTES_DIR!")
        else:
            print(f"    [SAFE] Canary file survived; not deleted")
            os.remove(canary_json_path)
        if os.path.exists(canary_path):
            os.remove(canary_path)

        # 1.3 POST Path Traversal (Arbitrary File Write via "id" field)
        tests_run += 1
        traversal_id = "../../traversal_write_canary"
        target_written_file = os.path.join(BASE_DIR, "traversal_write_canary.json")
        payload = json.dumps({"id": traversal_id, "title": "Evil Note", "pages": []}).encode("utf-8")
        code, header, body = raw_http_request("POST", "/api/notes", {"Content-Type": "application/json"}, payload)
        print(f"  [Vector] POST /api/notes with id='{traversal_id}' -> HTTP {code}")
        if os.path.exists(target_written_file):
            findings.append(f"CRITICAL PATH TRAVERSAL WRITE: POST /api/notes wrote file outside NOTES_DIR at {target_written_file}!")
            print(f"    [!] VULNERABILITY CONFIRMED: Arbitrary file write outside NOTES_DIR!")
            os.remove(target_written_file)
        else:
            print(f"    [SAFE] Target file outside NOTES_DIR was not created")

        # Test absolute path escape
        tests_run += 1
        abs_traversal_id = "/storage/emulated/0/FlowNotes/abs_traversal_canary"
        target_abs_file = "/storage/emulated/0/FlowNotes/abs_traversal_canary.json"
        payload = json.dumps({"id": abs_traversal_id, "title": "Evil Abs Note", "pages": []}).encode("utf-8")
        code, header, body = raw_http_request("POST", "/api/notes", {"Content-Type": "application/json"}, payload)
        print(f"  [Vector] POST /api/notes with id='{abs_traversal_id}' -> HTTP {code}")
        if os.path.exists(target_abs_file):
            findings.append(f"CRITICAL PATH TRAVERSAL WRITE (Absolute Path): POST /api/notes wrote file at {target_abs_file}!")
            print(f"    [!] VULNERABILITY CONFIRMED: Absolute path file write outside NOTES_DIR!")
            os.remove(target_abs_file)
        else:
            print(f"    [SAFE] Absolute target file was not created")

        # ---------------------------------------------------------
        # Category 2: Invalid / Malformed JSON Bodies
        # ---------------------------------------------------------
        print("\n--- CATEGORY 2: Malformed JSON Bodies ---")
        malformed_cases = [
            ("Non-JSON raw string", b"THIS_IS_NOT_JSON"),
            ("Incomplete truncated JSON", b'{"title": "Unfinished string'),
            ("Empty body with POST", b""),
            ("JSON array instead of object", b"[1, 2, 3, 4]"),
            ("JSON integer primitive", b"42"),
            ("JSON boolean primitive", b"true"),
            ("JSON null primitive", b"null"),
            ("Single quote JSON", b"{'title': 'single quotes'}"),
            ("Null bytes in JSON", b'{"id": "null_byte", "title": "Test\x00Name"}'),
            ("Non-UTF8 binary data", b"\xff\xfe\xfd\x80\x81"),
            ("Deeply nested JSON object", ('{"a":' * 200 + '{"leaf": 1}' + '}' * 200).encode("utf-8")),
            ("Empty JSON object", b"{}"),
            ("Wrong types in note fields", b'{"id": 12345, "title": {"nested": "title"}, "pages": "not_a_list"}')
        ]

        for desc, body in malformed_cases:
            tests_run += 1
            code, header, resp_body = raw_http_request("POST", "/api/notes", {"Content-Type": "application/json"}, body)
            print(f"  [Test] POST /api/notes with {desc} -> HTTP {code}")
            if code in (400, 200):
                print(f"    [SAFE] Server responded safely (HTTP {code})")
            elif code == 500:
                print(f"    [WARNING] Server threw internal error 500: {resp_body[:60]}")
            elif code == 0:
                findings.append(f"SERVER CRASH: Server closed connection / crashed on {desc}!")
                print(f"    [!] SERVER CRASHED OR CLOSED CONNECTION")

        # Test malformed payloads on AI endpoints
        ai_endpoints = ["/api/ai/chat", "/api/ai/flashcards"]
        for ep in ai_endpoints:
            tests_run += 1
            code, header, resp_body = raw_http_request("POST", ep, {"Content-Type": "application/json"}, b"[1, 2, 3]")
            print(f"  [Test] POST {ep} with array payload -> HTTP {code}")
            if code not in (200, 400):
                print(f"    [INFO] Responded with HTTP {code}: {resp_body[:60]}")

            tests_run += 1
            code, header, resp_body = raw_http_request("POST", ep, {"Content-Type": "application/json"}, b"not json")
            print(f"  [Test] POST {ep} with non-JSON -> HTTP {code}")
            if code == 400:
                print(f"    [SAFE] Handled malformed input with 400")

        # ---------------------------------------------------------
        # Category 3: Oversized Requests (Resource Pressure)
        # ---------------------------------------------------------
        print("\n--- CATEGORY 3: Oversized Requests & Resource Pressure ---")
        sizes = [
            ("1 MB JSON Payload", 1 * 1024 * 1024),
            ("5 MB JSON Payload", 5 * 1024 * 1024),
            ("10 MB JSON Payload", 10 * 1024 * 1024)
        ]

        for desc, size in sizes:
            tests_run += 1
            large_note_id = f"fuzz_large_{size}_{int(time.time())}"
            # Construct large payload with repetitions of dummy points
            padding = "A" * (size - 200)
            large_payload = json.dumps({
                "id": large_note_id,
                "title": f"Large Note {desc}",
                "padding": padding
            }).encode("utf-8")
            
            start_t = time.time()
            code, header, resp_body = raw_http_request("POST", "/api/notes", {"Content-Type": "application/json"}, large_payload, timeout=15)
            elapsed = time.time() - start_t
            print(f"  [Test] POST /api/notes with {desc} ({len(large_payload)} bytes) -> HTTP {code} in {elapsed:.2f}s")
            
            if code == 200:
                print(f"    [PASS] Large payload accepted and saved successfully ({elapsed:.2f}s)")
                # Cleanup
                clean_path = os.path.join(NOTES_DIR, f"{large_note_id}.json")
                if os.path.exists(clean_path):
                    os.remove(clean_path)
            elif code in (400, 413):
                print(f"    [PASS] Large payload safely rejected with HTTP {code}")
            elif code == 0:
                findings.append(f"OOM / CRASH: Server crashed on {desc}!")
                print(f"    [!] SERVER CRASHED ON OVERSIZED PAYLOAD")

        # ---------------------------------------------------------
        # Category 4: Protocol Violations & Header Fuzzing
        # ---------------------------------------------------------
        print("\n--- CATEGORY 4: Protocol Violations & Header Fuzzing ---")
        
        # 4.1 Negative Content-Length
        tests_run += 1
        code, header, resp_body = raw_http_request("POST", "/api/notes", {"Content-Length": "-10"}, b"")
        print(f"  [Test] Negative Content-Length: -10 -> HTTP {code}")
        
        # 4.2 Non-numeric Content-Length
        tests_run += 1
        code, header, resp_body = raw_http_request("POST", "/api/notes", {"Content-Length": "not_a_number"}, b"test")
        print(f"  [Test] Non-numeric Content-Length: 'not_a_number' -> HTTP {code}")

        # 4.3 Unsupported HTTP methods
        unsupported_methods = ["TRACE", "PUT", "PATCH", "FOOBAR", "HEAD"]
        for m in unsupported_methods:
            tests_run += 1
            code, header, resp_body = raw_http_request(m, "/api/notes")
            print(f"  [Test] Method {m} /api/notes -> HTTP {code}")

        # 4.4 Non-existent route
        tests_run += 1
        code, header, resp_body = raw_http_request("GET", "/api/non_existent_random_endpoint")
        print(f"  [Test] GET /api/non_existent_random_endpoint -> HTTP {code}")
        if code in (404, 501):
            print(f"    [SAFE] Properly returned {code} for non-existent route")

        # ---------------------------------------------------------
        # Category 5: Upload PDF Endpoint Fuzzing
        # ---------------------------------------------------------
        print("\n--- CATEGORY 5: Upload PDF Endpoint Fuzzing ---")
        
        # 5.1 Empty body
        tests_run += 1
        code, header, resp_body = raw_http_request("POST", "/api/upload-pdf", {"Content-Type": "application/pdf"}, b"")
        print(f"  [Test] POST /api/upload-pdf with empty body -> HTTP {code}")

        # 5.2 Malformed multipart with missing boundary
        tests_run += 1
        code, header, resp_body = raw_http_request("POST", "/api/upload-pdf", {"Content-Type": "multipart/form-data"}, b"dummy content without boundary")
        print(f"  [Test] POST /api/upload-pdf with missing boundary -> HTTP {code}")

        # 5.3 Corrupt binary data (2 MB of random bytes)
        tests_run += 1
        corrupt_bytes = os.urandom(2 * 1024 * 1024)
        code, header, resp_body = raw_http_request("POST", "/api/upload-pdf", {"Content-Type": "application/pdf"}, corrupt_bytes)
        print(f"  [Test] POST /api/upload-pdf with 2MB random binary -> HTTP {code}")
        if code == 200:
            resp_json = json.loads(resp_body.decode("utf-8", errors="replace"))
            uploaded_fn = resp_json.get("filename")
            if uploaded_fn:
                uploaded_path = os.path.join(DATA_DIR, "uploads", uploaded_fn)
                if os.path.exists(uploaded_path):
                    os.remove(uploaded_path)

        # ---------------------------------------------------------
        # Category 6: Server Liveness & Health Check
        # ---------------------------------------------------------
        print("\n--- CATEGORY 6: Post-Fuzz Server Liveness Check ---")
        tests_run += 1
        code, header, body = raw_http_request("GET", "/api/status")
        print(f"  [Test] GET /api/status -> HTTP {code}")
        if code == 200:
            print("  [PASS] server.py is HEALTHY and responsive after intensive fuzzing!")
        else:
            findings.append("LIVENESS FAILURE: server.py failed to respond with 200 OK after fuzzing!")
            print(f"  [!] SERVER UNHEALTHY: Code {code}")

    finally:
        if started_by_us and server_process:
            print("\nStopping fuzzed server.py...")
            server_process.terminate()
            server_process.wait(timeout=3)

    print("\n================================================================")
    print("=== Adversarial Fuzzing Summary ===")
    print("================================================================")
    print(f"Total Fuzz Vectors Executed: {tests_run}")
    print(f"Security Vulnerabilities / Critical Findings: {len(findings)}")
    for f in findings:
        print(f"  - {f}")
    print("================================================================\n")
    return findings

if __name__ == "__main__":
    findings = run_adversarial_fuzzing()
    # Exit with status 0 if tested, but print full report
    sys.exit(0)
