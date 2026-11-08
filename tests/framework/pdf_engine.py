"""
PDF Engine Oracle.
Faithfully mirrors PdfManagerContract and PdfAnnotationManager logic.
Validates PDF import, page count extraction, bitmap layer caching, and multi-page PDF document export.
"""

import os
from typing import Optional, Dict, Any
from tests.framework.models import Notebook, Page, PenTool


class MockPdfDocument:
    """Simulates multi-page standard PDF document rendering and binary generation."""
    def __init__(self, width: int = 1200, height: int = 1600):
        self.width = width
        self.height = height
        self.pages: list = []
        self.is_closed = False

    def add_page(self, page_index: int, page: Page, conceal_tapes: bool = False) -> Dict[str, Any]:
        drawn_strokes = len(page.strokes)
        drawn_tapes = len(page.tapes)
        record = {
            "pageNumber": page_index + 1,
            "width": self.width,
            "height": self.height,
            "paperType": page.paper_type.name,
            "strokeCount": drawn_strokes,
            "tapeCount": drawn_tapes,
            "concealTapes": conceal_tapes
        }
        self.pages.append(record)
        return record

    def write_to_file(self, output_path: str) -> bool:
        if self.is_closed:
            return False
        # Write valid minimal standard PDF binary header and structure
        os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
        with open(output_path, "wb") as f:
            header = b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n"
            body = f"1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".encode("utf-8")
            pages_obj = f"2 0 obj\n<< /Type /Pages /Count {len(self.pages)} /Kids [] >>\nendobj\n".encode("utf-8")
            xref = b"xref\n0 3\n0000000000 65535 f \n0000000015 00000 n \n0000000060 00000 n \n"
            trailer = f"trailer\n<< /Size 3 /Root 1 0 R >>\nstartxref\n120\n%%EOF\n".encode("utf-8")
            f.write(header + body + pages_obj + xref + trailer)
        return True

    def close(self):
        self.is_closed = True


class PdfManager:
    def __init__(self):
        self.current_pdf_path: Optional[str] = None
        self.page_count: int = 0
        self.is_open: bool = False

    def open_pdf(self, file_path: str) -> int:
        if not os.path.exists(file_path):
            self.close()
            return 0

        # Read page count or detect valid PDF header
        try:
            with open(file_path, "rb") as f:
                header = f.read(5)
                if not header.startswith(b"%PDF-"):
                    self.close()
                    return 0
                content = f.read()
                # Count /Type /Page occurrences
                count = content.count(b"/Type /Page")
                # Fallback to at least 1 page if valid PDF
                self.page_count = max(1, count if count > 0 else 1)
                self.current_pdf_path = file_path
                self.is_open = True
                return self.page_count
        except Exception:
            self.close()
            return 0

    def render_page_to_bitmap(self, page_index: int, target_width: int, target_height: int) -> Optional[Dict[str, Any]]:
        if not self.is_open or page_index < 0 or page_index >= self.page_count:
            return None
        if target_width <= 0 or target_height <= 0:
            return None

        return {
            "pageIndex": page_index,
            "width": target_width,
            "height": target_height,
            "config": "ARGB_8888",
            "backgroundColor": "#FFFFFF"
        }

    def export_notebook_to_pdf(self, notebook: Notebook, output_path: str,
                               width: int = 1200, height: int = 1600,
                               conceal_tapes: bool = False) -> bool:
        doc = MockPdfDocument(width, height)
        for idx, page in enumerate(notebook.pages):
            doc.add_page(idx, page, conceal_tapes=conceal_tapes)
        success = doc.write_to_file(output_path)
        doc.close()
        return success

    def close(self):
        self.current_pdf_path = None
        self.page_count = 0
        self.is_open = False
