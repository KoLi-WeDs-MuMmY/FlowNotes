"""
Local NoteRepository & Storage Engine Oracle.
Faithfully mirrors NoteRepository logic and SQLite indexing.
Provides local JSON serialization, SQLite metadata indexing, title search, and folder filtering.
"""

import os
import json
import sqlite3
from typing import List, Optional, Dict, Any
from tests.framework.models import Notebook, Page, PaperType


class StorageEngine:
    def __init__(self, base_dir: str):
        self.base_dir = base_dir
        self.notebooks_dir = os.path.join(base_dir, "notebooks")
        self.audio_dir = os.path.join(base_dir, "audio")
        self.pdf_dir = os.path.join(base_dir, "pdf")
        self.db_path = os.path.join(base_dir, "flownotes.db")

        os.makedirs(self.notebooks_dir, exist_ok=True)
        os.makedirs(self.audio_dir, exist_ok=True)
        os.makedirs(self.pdf_dir, exist_ok=True)

        self._init_sqlite()

    def _init_sqlite(self):
        conn = sqlite3.connect(self.db_path)
        cur = conn.cursor()
        cur.execute("""
            CREATE TABLE IF NOT EXISTS notebook_index (
                id TEXT PRIMARY KEY,
                title TEXT,
                cover_color INTEGER,
                cover_pattern TEXT,
                folder TEXT,
                default_paper_type TEXT,
                page_count INTEGER,
                pdf_path TEXT,
                created_at INTEGER,
                updated_at INTEGER
            )
        """)
        conn.commit()
        conn.close()

    def save_notebook(self, notebook: Notebook) -> str:
        # 1. Write JSON file
        file_path = os.path.join(self.notebooks_dir, f"{notebook.id}.json")
        data = notebook.to_dict()
        with open(file_path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

        # 2. Update SQLite index
        conn = sqlite3.connect(self.db_path)
        cur = conn.cursor()
        cur.execute("""
            INSERT OR REPLACE INTO notebook_index
            (id, title, cover_color, cover_pattern, folder, default_paper_type, page_count, pdf_path, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            notebook.id,
            notebook.title,
            notebook.cover_color,
            notebook.cover_pattern,
            notebook.folder,
            notebook.default_paper_type.name,
            notebook.page_count,
            notebook.pdf_path,
            notebook.created_at,
            notebook.updated_at
        ))
        conn.commit()
        conn.close()
        return file_path

    def get_notebook(self, notebook_id: str) -> Optional[Notebook]:
        file_path = os.path.join(self.notebooks_dir, f"{notebook_id}.json")
        if not os.path.exists(file_path):
            return None
        try:
            with open(file_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            return Notebook.from_dict(data)
        except Exception:
            return None

    def delete_notebook(self, notebook_id: str) -> bool:
        file_path = os.path.join(self.notebooks_dir, f"{notebook_id}.json")
        deleted = False
        if os.path.exists(file_path):
            os.remove(file_path)
            deleted = True

        conn = sqlite3.connect(self.db_path)
        cur = conn.cursor()
        cur.execute("DELETE FROM notebook_index WHERE id = ?", (notebook_id,))
        conn.commit()
        conn.close()
        return deleted

    def list_notebooks(self, folder: Optional[str] = None, search_query: Optional[str] = None) -> List[Dict[str, Any]]:
        conn = sqlite3.connect(self.db_path)
        cur = conn.cursor()

        sql = "SELECT id, title, cover_color, cover_pattern, folder, default_paper_type, page_count, pdf_path, created_at, updated_at FROM notebook_index WHERE 1=1"
        params = []

        if folder and folder != "All Notes":
            sql += " AND folder = ?"
            params.append(folder)

        if search_query and search_query.strip():
            sql += " AND title LIKE ?"
            params.append(f"%{search_query.strip()}%")

        sql += " ORDER BY updated_at DESC"
        cur.execute(sql, tuple(params))
        rows = cur.fetchall()
        conn.close()

        results = []
        for r in rows:
            results.append({
                "id": r[0],
                "title": r[1],
                "coverColor": r[2],
                "coverPattern": r[3],
                "folder": r[4],
                "defaultPaperType": r[5],
                "pageCount": r[6],
                "pdfPath": r[7],
                "createdAt": r[8],
                "updatedAt": r[9]
            })
        return results
