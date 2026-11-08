"""
ActiveRecallTape Engine Oracle.
Faithfully mirrors ActiveRecallTapeContract and implementation from CanvasView.
Validates tape creation, tap-to-reveal hit testing, bulk reveal/conceal, and visual render states.
"""

from typing import List, Optional, Dict, Any
from tests.framework.models import ActiveRecallTape


class ActiveRecallTapeEngine:

    @staticmethod
    def create_tape(start_x: float, start_y: float, end_x: float, end_y: float,
                    color: int = 0xFFFEF08A, is_revealed: bool = False) -> ActiveRecallTape:
        return ActiveRecallTape(
            left=min(start_x, end_x),
            top=min(start_y, end_y),
            right=max(start_x, end_x),
            bottom=max(start_y, end_y),
            color=color,
            is_revealed=is_revealed
        )

    @staticmethod
    def find_tape_at(tapes: List[ActiveRecallTape], x: float, y: float) -> Optional[ActiveRecallTape]:
        """Returns top-most (last drawn) tape that contains the (x, y) coordinates."""
        for tape in reversed(tapes):
            if tape.contains(x, y):
                return tape
        return None

    @staticmethod
    def toggle_tape_at(tapes: List[ActiveRecallTape], x: float, y: float) -> Optional[ActiveRecallTape]:
        tape = ActiveRecallTapeEngine.find_tape_at(tapes, x, y)
        if tape is not None:
            tape.is_revealed = not tape.is_revealed
        return tape

    @staticmethod
    def set_all_tapes_revealed(tapes: List[ActiveRecallTape], revealed: bool) -> int:
        count = 0
        for tape in tapes:
            if tape.is_revealed != revealed:
                tape.is_revealed = revealed
                count += 1
            else:
                tape.is_revealed = revealed
        return count

    @staticmethod
    def get_render_state(tape: ActiveRecallTape) -> Dict[str, Any]:
        """
        Returns visual properties required for canvas rendering:
        Concealed: alpha=255 (opaque), dashed_border=False, blocks text
        Revealed: alpha=40 (subtle translucent highlight), dashed_border=True (12f, 8f dash pattern)
        """
        if tape.is_revealed:
            return {
                "alpha": 40,
                "has_dashed_border": True,
                "border_color": tape.color,
                "dash_pattern": (12.0, 8.0),
                "is_opaque": False,
                "text_visible": True
            }
        else:
            return {
                "alpha": 255,
                "has_dashed_border": False,
                "border_color": None,
                "dash_pattern": None,
                "is_opaque": True,
                "text_visible": False
            }
