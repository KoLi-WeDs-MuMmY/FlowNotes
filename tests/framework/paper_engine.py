"""
PaperTemplateRenderer Oracle.
Faithfully mirrors PaperRendererContract and PaperTemplateRenderer geometry.
Validates layout bounds, line coordinates, dark mode colors, and template dimensions.
"""

from typing import Dict, Any, List, Tuple
from tests.framework.models import PaperType


class PaperGeometry:

    @staticmethod
    def get_cornell_layout(width: float, height: float, is_dark: bool = False) -> Dict[str, Any]:
        top_header_y = 160.0
        summary_y = height - 260.0
        cue_column_x = width * 0.32

        # Notes lines: from top_header_y + 80 to summary_y - 30 with step 50
        note_lines = []
        y = top_header_y + 80.0
        while y < summary_y - 30.0:
            note_lines.append((cue_column_x + 15.0, y, width - 60.0, y))
            y += 50.0

        bg_color = "#1A202C" if is_dark else "#FFFFFF"
        divider_color = "#4A5568" if is_dark else "#94A3B8"

        return {
            "type": PaperType.CORNELL,
            "width": width,
            "height": height,
            "is_dark": is_dark,
            "bg_color": bg_color,
            "divider_color": divider_color,
            "top_header_y": top_header_y,
            "summary_y": summary_y,
            "cue_column_x": cue_column_x,
            "note_lines": note_lines,
            "sections": ["HEADER", "CUE_COLUMN", "NOTES_AREA", "SUMMARY_BOX"]
        }

    @staticmethod
    def get_grid_layout(width: float, height: float, is_dark: bool = False, grid_size: float = 50.0) -> Dict[str, Any]:
        vertical_lines = []
        x = 60.0
        while x < width - 60.0:
            vertical_lines.append((x, 60.0, x, height - 60.0))
            x += grid_size

        horizontal_lines = []
        y = 60.0
        while y < height - 60.0:
            horizontal_lines.append((60.0, y, width - 60.0, y))
            y += grid_size

        bg_color = "#1A202C" if is_dark else "#FFFFFF"
        grid_color = "#2D3748" if is_dark else "#EDF2F7"

        return {
            "type": PaperType.GRID,
            "width": width,
            "height": height,
            "is_dark": is_dark,
            "grid_size": grid_size,
            "bg_color": bg_color,
            "grid_color": grid_color,
            "vertical_lines_count": len(vertical_lines),
            "horizontal_lines_count": len(horizontal_lines),
            "vertical_lines": vertical_lines,
            "horizontal_lines": horizontal_lines
        }

    @staticmethod
    def get_dotted_layout(width: float, height: float, is_dark: bool = False, spacing: float = 50.0) -> Dict[str, Any]:
        dots: List[Tuple[float, float]] = []
        x = 60.0
        while x < width - 60.0:
            y = 60.0
            while y < height - 60.0:
                dots.append((x, y))
                y += spacing
            x += spacing

        bg_color = "#1A202C" if is_dark else "#FFFFFF"
        dot_color = "#4A5568" if is_dark else "#CBD5E1"

        return {
            "type": PaperType.DOTTED,
            "width": width,
            "height": height,
            "is_dark": is_dark,
            "spacing": spacing,
            "dot_radius": 2.5,
            "bg_color": bg_color,
            "dot_color": dot_color,
            "dots_count": len(dots),
            "dots": dots
        }

    @staticmethod
    def get_ruled_layout(width: float, height: float, is_college: bool = False, is_dark: bool = False) -> Dict[str, Any]:
        line_spacing = 52.0 if is_college else 70.0
        lines = []
        y = 140.0
        while y < height - 60.0:
            lines.append((60.0, y, width - 60.0, y))
            y += line_spacing

        margin_line = (180.0, 60.0, 180.0, height - 60.0)
        bg_color = "#1A202C" if is_dark else "#FFFFFF"
        line_color = "#2D3748" if is_dark else "#E2E8F0"
        margin_color = "#E53E3E" if is_dark else "#FCA5A5"

        return {
            "type": PaperType.COLLEGE_RULED if is_college else PaperType.RULED,
            "width": width,
            "height": height,
            "is_dark": is_dark,
            "line_spacing": line_spacing,
            "bg_color": bg_color,
            "line_color": line_color,
            "margin_color": margin_color,
            "margin_x": 180.0,
            "lines_count": len(lines),
            "lines": lines,
            "margin_line": margin_line
        }

    @staticmethod
    def get_planner_layout(width: float, height: float, is_dark: bool = False) -> Dict[str, Any]:
        col_w = (width - 120.0) / 3.0
        row_h = (height - 240.0) / 2.0
        bg_color = "#1A202C" if is_dark else "#FFFFFF"

        boxes = []
        days = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Weekend & Notes"]
        for i in range(6):
            r = i // 3
            c = i % 3
            bx = 60.0 + c * col_w
            by = 160.0 + r * row_h
            boxes.append({
                "label": days[i],
                "left": bx,
                "top": by,
                "right": bx + col_w,
                "bottom": by + row_h
            })

        return {
            "type": PaperType.PLANNER,
            "width": width,
            "height": height,
            "is_dark": is_dark,
            "bg_color": bg_color,
            "boxes": boxes,
            "box_count": len(boxes)
        }

    @staticmethod
    def get_sepia_layout(width: float, height: float) -> Dict[str, Any]:
        return {
            "type": PaperType.SEPIA,
            "width": width,
            "height": height,
            "bg_color": "#FDF6E2",
            "is_dark": False
        }
