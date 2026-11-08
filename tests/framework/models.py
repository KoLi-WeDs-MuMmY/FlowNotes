"""
FlowNotes Data Models & Serialization Oracles.
Faithfully mirrors Kotlin data classes from com.flownotes.app.model.Models
and com.flownotes.app.data.NoteRepository.
"""

import enum
import uuid
import time
from typing import List, Optional, Dict, Any


class PenTool(enum.Enum):
    BALLPOINT = "BALLPOINT"
    FOUNTAIN = "FOUNTAIN"
    HIGHLIGHTER = "HIGHLIGHTER"
    PENCIL = "PENCIL"
    ERASER_STROKE = "ERASER_STROKE"
    ERASER_PIXEL = "ERASER_PIXEL"
    TAPE = "TAPE"
    SHAPE = "SHAPE"
    LASSO = "LASSO"
    LASER = "LASER"


class PaperType(enum.Enum):
    BLANK = "BLANK"
    RULED = "RULED"
    COLLEGE_RULED = "COLLEGE_RULED"
    GRID = "GRID"
    DOTTED = "DOTTED"
    CORNELL = "CORNELL"
    PLANNER = "PLANNER"
    DARK_GRID = "DARK_GRID"
    SEPIA = "SEPIA"


class CardRating(enum.IntEnum):
    AGAIN = 1
    HARD = 2
    GOOD = 3
    EASY = 4


class CardState(enum.Enum):
    NEW = "NEW"
    LEARNING = "LEARNING"
    REVIEW = "REVIEW"
    RELEARNING = "RELEARNING"


class Point:
    def __init__(self, x: float, y: float, pressure: float = 0.5, timestamp: Optional[int] = None, audio_offset_ms: Optional[int] = None):
        self.x = float(x)
        self.y = float(y)
        self.pressure = float(pressure)
        self.timestamp = int(timestamp if timestamp is not None else time.time() * 1000)
        self.audio_offset_ms = audio_offset_ms

    def to_dict(self) -> Dict[str, Any]:
        d = {
            "x": self.x,
            "y": self.y,
            "pressure": self.pressure,
            "timestamp": self.timestamp
        }
        if self.audio_offset_ms is not None:
            d["audioOffsetMs"] = self.audio_offset_ms
        return d

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "Point":
        return cls(
            x=d.get("x", 0.0),
            y=d.get("y", 0.0),
            pressure=d.get("pressure", 0.5),
            timestamp=d.get("timestamp"),
            audio_offset_ms=d.get("audioOffsetMs")
        )


class Stroke:
    def __init__(self, tool: PenTool = PenTool.BALLPOINT, color: int = 0xFF1E293B,
                 stroke_width: float = 3.0, points: Optional[List[Point]] = None,
                 shape_type: Optional[str] = None, stroke_id: Optional[str] = None):
        self.id = stroke_id or str(uuid.uuid4())
        self.tool = tool
        self.color = int(color)
        self.stroke_width = float(stroke_width)
        self.points: List[Point] = points if points is not None else []
        self.shape_type = shape_type

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.id,
            "tool": self.tool.name,
            "color": self.color,
            "strokeWidth": self.stroke_width,
            "shapeType": self.shape_type,
            "points": [p.to_dict() for p in self.points]
        }

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "Stroke":
        tool_str = d.get("tool", "BALLPOINT")
        tool = PenTool[tool_str] if tool_str in PenTool.__members__ else PenTool.BALLPOINT
        pts = [Point.from_dict(p) for p in d.get("points", [])]
        return cls(
            tool=tool,
            color=d.get("color", 0xFF1E293B),
            stroke_width=d.get("strokeWidth", 3.0),
            points=pts,
            shape_type=d.get("shapeType"),
            stroke_id=d.get("id")
        )


class ActiveRecallTape:
    def __init__(self, left: float, top: float, right: float, bottom: float,
                 color: int = 0xFFFEF08A, is_revealed: bool = False, tape_id: Optional[str] = None):
        self.id = tape_id or str(uuid.uuid4())
        # Ensure left <= right and top <= bottom
        self.left = float(min(left, right))
        self.top = float(min(top, bottom))
        self.right = float(max(left, right))
        self.bottom = float(max(top, bottom))
        self.color = int(color)
        self.is_revealed = bool(is_revealed)

    def contains(self, x: float, y: float) -> bool:
        return self.left <= x <= self.right and self.top <= y <= self.bottom

    @property
    def width(self) -> float:
        return self.right - self.left

    @property
    def height(self) -> float:
        return self.bottom - self.top

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.id,
            "left": self.left,
            "top": self.top,
            "right": self.right,
            "bottom": self.bottom,
            "color": self.color,
            "isRevealed": self.is_revealed
        }

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "ActiveRecallTape":
        return cls(
            left=d.get("left", 0.0),
            top=d.get("top", 0.0),
            right=d.get("right", 0.0),
            bottom=d.get("bottom", 0.0),
            color=d.get("color", 0xFFFEF08A),
            is_revealed=d.get("isRevealed", False),
            tape_id=d.get("id")
        )


class TextAnnotation:
    def __init__(self, x: float, y: float, text: str, color: int = 0xFF1E293B,
                 font_size: float = 16.0, annotation_id: Optional[str] = None):
        self.id = annotation_id or str(uuid.uuid4())
        self.x = float(x)
        self.y = float(y)
        self.text = str(text)
        self.color = int(color)
        self.font_size = float(font_size)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.id,
            "x": self.x,
            "y": self.y,
            "text": self.text,
            "color": self.color,
            "fontSize": self.font_size
        }

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "TextAnnotation":
        return cls(
            x=d.get("x", 0.0),
            y=d.get("y", 0.0),
            text=d.get("text", ""),
            color=d.get("color", 0xFF1E293B),
            font_size=d.get("fontSize", 16.0),
            annotation_id=d.get("id")
        )


class StickyNote:
    def __init__(self, x: float, y: float, width: float = 200.0, height: float = 180.0,
                 content: str = "", color: int = 0xFFFEF08A, note_id: Optional[str] = None):
        self.id = note_id or str(uuid.uuid4())
        self.x = float(x)
        self.y = float(y)
        self.width = float(width)
        self.height = float(height)
        self.content = str(content)
        self.color = int(color)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.id,
            "x": self.x,
            "y": self.y,
            "width": self.width,
            "height": self.height,
            "content": self.content,
            "color": self.color
        }

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "StickyNote":
        return cls(
            x=d.get("x", 0.0),
            y=d.get("y", 0.0),
            width=d.get("width", 200.0),
            height=d.get("height", 180.0),
            content=d.get("content", ""),
            color=d.get("color", 0xFFFEF08A),
            note_id=d.get("id")
        )


class Page:
    def __init__(self, page_number: int, paper_type: PaperType = PaperType.RULED,
                 strokes: Optional[List[Stroke]] = None, tapes: Optional[List[ActiveRecallTape]] = None,
                 text_annotations: Optional[List[TextAnnotation]] = None,
                 sticky_notes: Optional[List[StickyNote]] = None,
                 pdf_page_index: Optional[int] = None, audio_memo_uri: Optional[str] = None,
                 page_id: Optional[str] = None):
        self.id = page_id or str(uuid.uuid4())
        self.page_number = int(page_number)
        self.paper_type = paper_type
        self.strokes: List[Stroke] = strokes if strokes is not None else []
        self.tapes: List[ActiveRecallTape] = tapes if tapes is not None else []
        self.text_annotations: List[TextAnnotation] = text_annotations if text_annotations is not None else []
        self.sticky_notes: List[StickyNote] = sticky_notes if sticky_notes is not None else []
        self.pdf_page_index = pdf_page_index
        self.audio_memo_uri = audio_memo_uri

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.id,
            "pageNumber": self.page_number,
            "paperType": self.paper_type.name,
            "pdfPageIndex": self.pdf_page_index,
            "audioMemoUri": self.audio_memo_uri,
            "strokes": [s.to_dict() for s in self.strokes],
            "tapes": [t.to_dict() for t in self.tapes],
            "textAnnotations": [a.to_dict() for a in self.text_annotations],
            "stickyNotes": [n.to_dict() for n in self.sticky_notes]
        }

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "Page":
        pt_str = d.get("paperType", "RULED")
        paper_type = PaperType[pt_str] if pt_str in PaperType.__members__ else PaperType.RULED
        strokes = [Stroke.from_dict(s) for s in d.get("strokes", [])]
        tapes = [ActiveRecallTape.from_dict(t) for t in d.get("tapes", [])]
        text_annotations = [TextAnnotation.from_dict(a) for a in d.get("textAnnotations", [])]
        sticky_notes = [StickyNote.from_dict(n) for n in d.get("stickyNotes", [])]
        return cls(
            page_number=d.get("pageNumber", 1),
            paper_type=paper_type,
            strokes=strokes,
            tapes=tapes,
            text_annotations=text_annotations,
            sticky_notes=sticky_notes,
            pdf_page_index=d.get("pdfPageIndex"),
            audio_memo_uri=d.get("audioMemoUri"),
            page_id=d.get("id")
        )


class Notebook:
    def __init__(self, title: str = "Untitled Notebook", cover_color: int = 0xFF3B82F6,
                 cover_pattern: str = "modern", folder: str = "All Notes",
                 default_paper_type: PaperType = PaperType.RULED,
                 pages: Optional[List[Page]] = None, pdf_path: Optional[str] = None,
                 created_at: Optional[int] = None, updated_at: Optional[int] = None,
                 notebook_id: Optional[str] = None):
        self.id = notebook_id or str(uuid.uuid4())
        self.title = str(title)
        self.cover_color = int(cover_color)
        self.cover_pattern = str(cover_pattern)
        self.folder = str(folder)
        self.default_paper_type = default_paper_type
        self.pages: List[Page] = pages if pages is not None else []
        self.pdf_path = pdf_path
        now = int(time.time() * 1000)
        self.created_at = int(created_at if created_at is not None else now)
        self.updated_at = int(updated_at if updated_at is not None else now)

    @property
    def page_count(self) -> int:
        return len(self.pages)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.id,
            "title": self.title,
            "coverColor": self.cover_color,
            "coverPattern": self.cover_pattern,
            "folder": self.folder,
            "defaultPaperType": self.default_paper_type.name,
            "pdfPath": self.pdf_path,
            "createdAt": self.created_at,
            "updatedAt": self.updated_at,
            "pages": [p.to_dict() for p in self.pages]
        }

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "Notebook":
        pt_str = d.get("defaultPaperType", "RULED")
        paper_type = PaperType[pt_str] if pt_str in PaperType.__members__ else PaperType.RULED
        pages = [Page.from_dict(p) for p in d.get("pages", [])]
        return cls(
            title=d.get("title", "Untitled Notebook"),
            cover_color=d.get("coverColor", 0xFF3B82F6),
            cover_pattern=d.get("coverPattern", "modern"),
            folder=d.get("folder", "All Notes"),
            default_paper_type=paper_type,
            pages=pages,
            pdf_path=d.get("pdfPath"),
            created_at=d.get("createdAt"),
            updated_at=d.get("updatedAt"),
            notebook_id=d.get("id")
        )


class Flashcard:
    def __init__(self, front: str, back: str, hint: str = "",
                 review_interval_days: int = 1, repetitions: int = 0,
                 ease_factor: float = 2.5, due_date: Optional[int] = None,
                 state: CardState = CardState.NEW, card_id: Optional[str] = None):
        self.id = card_id or str(uuid.uuid4())
        self.front = str(front)
        self.back = str(back)
        self.hint = str(hint)
        self.review_interval_days = int(review_interval_days)
        self.repetitions = int(repetitions)
        self.ease_factor = float(ease_factor)
        now = int(time.time() * 1000)
        self.due_date = int(due_date if due_date is not None else now)
        self.state = state

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.id,
            "front": self.front,
            "back": self.back,
            "hint": self.hint,
            "reviewIntervalDays": self.review_interval_days,
            "repetitions": self.repetitions,
            "easeFactor": self.ease_factor,
            "dueDate": self.due_date,
            "state": self.state.name
        }

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "Flashcard":
        st_str = d.get("state", "NEW")
        state = CardState[st_str] if st_str in CardState.__members__ else CardState.NEW
        return cls(
            front=d.get("front", ""),
            back=d.get("back", ""),
            hint=d.get("hint", ""),
            review_interval_days=d.get("reviewIntervalDays", 1),
            repetitions=d.get("repetitions", 0),
            ease_factor=d.get("easeFactor", 2.5),
            due_date=d.get("dueDate"),
            state=state,
            card_id=d.get("id")
        )
