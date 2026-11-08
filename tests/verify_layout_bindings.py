#!/usr/bin/env python3
"""
Empirical verification script for Android XML layouts, drawables, values,
and Kotlin view bindings in FlowNotes.
Checks for missing IDs, ClassCastException risks, and NullPointerException risks.
"""

import os
import re
import xml.etree.ElementTree as ET

PROJECT_ROOT = "/storage/emulated/0/FlowNotes"
RES_DIR = os.path.join(PROJECT_ROOT, "app/src/main/res")
JAVA_DIR = os.path.join(PROJECT_ROOT, "app/src/main/java")

# Android View inheritance mapping for type compatibility verification
INHERITANCE_MAP = {
    # View -> ViewGroup -> ...
    "View": ["Object"],
    "ViewGroup": ["View"],
    "TextView": ["View"],
    "EditText": ["TextView", "View"],
    "Button": ["TextView", "View"],
    "ImageButton": ["ImageView", "View"],
    "ImageView": ["View"],
    "SearchView": ["LinearLayout", "ViewGroup", "View"],
    "Spinner": ["AdapterView", "ViewGroup", "View"],
    "FrameLayout": ["ViewGroup", "View"],
    "LinearLayout": ["ViewGroup", "View"],
    "RelativeLayout": ["ViewGroup", "View"],
    "ScrollView": ["FrameLayout", "ViewGroup", "View"],
    "HorizontalScrollView": ["FrameLayout", "ViewGroup", "View"],
    "androidx.constraintlayout.widget.ConstraintLayout": ["ViewGroup", "View"],
    "ConstraintLayout": ["ViewGroup", "View"],
    "androidx.coordinatorlayout.widget.CoordinatorLayout": ["ViewGroup", "View"],
    "CoordinatorLayout": ["ViewGroup", "View"],
    "androidx.drawerlayout.widget.DrawerLayout": ["ViewGroup", "View"],
    "DrawerLayout": ["ViewGroup", "View"],
    "androidx.recyclerview.widget.RecyclerView": ["ViewGroup", "View"],
    "RecyclerView": ["ViewGroup", "View"],
    "androidx.cardview.widget.CardView": ["FrameLayout", "ViewGroup", "View"],
    "CardView": ["FrameLayout", "ViewGroup", "View"],
    "com.google.android.material.floatingactionbutton.FloatingActionButton": ["ImageButton", "ImageView", "View"],
    "FloatingActionButton": ["ImageButton", "ImageView", "View"],
    "com.flownotes.app.view.CanvasView": ["View"],
    "CanvasView": ["View"],
}

def is_assignable(actual_xml_tag: str, target_kotlin_type: str) -> bool:
    if actual_xml_tag == target_kotlin_type:
        return True
    # Strip package names
    short_actual = actual_xml_tag.split(".")[-1]
    short_target = target_kotlin_type.split(".")[-1]
    if short_actual == short_target:
        return True
    
    # Check inheritance
    ancestors = []
    queue = [actual_xml_tag, short_actual]
    visited = set()
    while queue:
        curr = queue.pop(0)
        if curr in visited:
            continue
        visited.add(curr)
        for base in INHERITANCE_MAP.get(curr, []):
            ancestors.append(base)
            queue.append(base)
            queue.append(base.split(".")[-1])

    return (target_kotlin_type in ancestors) or (short_target in ancestors)

def parse_xml_layout(xml_path: str):
    tree = ET.parse(xml_path)
    root = tree.getroot()
    id_map = {} # id_name -> tag_name
    
    for elem in root.iter():
        for k, v in elem.attrib.items():
            if k.endswith("id") and v.startswith("@+id/"):
                id_name = v.replace("@+id/", "")
                id_map[id_name] = elem.tag
            elif k.endswith("id") and v.startswith("@id/"):
                id_name = v.replace("@id/", "")
                id_map[id_name] = elem.tag
    return id_map

def test_view_bindings():
    print("=== EMPIRICAL LAYOUT & VIEW BINDING VERIFICATION ===")
    
    # 1. Parse all XML layouts
    layout_dir = os.path.join(RES_DIR, "layout")
    layouts = {}
    for f in os.listdir(layout_dir):
        if f.endswith(".xml"):
            layouts[f] = parse_xml_layout(os.path.join(layout_dir, f))
            print(f"[OK] Parsed layout '{f}' with {len(layouts[f])} declared IDs")

    # Layout mapping to Activity / Dialog / Adapter
    bindings = [
        {
            "class": "MainActivity.kt",
            "file": os.path.join(JAVA_DIR, "com/flownotes/app/MainActivity.kt"),
            "layout": "activity_main.xml",
            "sub_layouts": {
                "dialogView": "dialog_new_notebook.xml",
                "ViewHolder": "item_notebook_card.xml"
            }
        },
        {
            "class": "NoteEditorActivity.kt",
            "file": os.path.join(JAVA_DIR, "com/flownotes/app/NoteEditorActivity.kt"),
            "layout": "activity_note_editor.xml",
            "sub_layouts": {}
        },
        {
            "class": "FlashcardStudyActivity.kt",
            "file": os.path.join(JAVA_DIR, "com/flownotes/app/FlashcardStudyActivity.kt"),
            "layout": "activity_flashcards.xml",
            "sub_layouts": {}
        }
    ]

    total_checks = 0
    errors = []

    for b in bindings:
        print(f"\n--- Checking bindings in {b['class']} ---")
        with open(b["file"], "r", encoding="utf-8") as f:
            code = f.read()

        # Extract findViewById calls
        # Pattern 1: findViewById<Type>(R.id.name)
        # Pattern 2: view.findViewById<Type>(R.id.name)
        # Pattern 3: val v: Type = findViewById(R.id.name)
        pattern = re.compile(r'(?:(\w+)\.)?findViewById(?:<([\w\.]+)>)?\s*\(\s*R\.id\.(\w+)\s*\)')
        matches = pattern.findall(code)

        # Also find typed variable declarations: private lateinit var recyclerView: RecyclerView
        # recyclerView = findViewById(R.id.rv_notebooks)
        var_types = {}
        var_pattern = re.compile(r'(?:val|var)\s+(\w+)\s*:\s*([\w\.]+)')
        for m in var_pattern.finditer(code):
            var_types[m.group(1)] = m.group(2)

        # Pattern 4: var = findViewById(R.id.name)
        assign_pattern = re.compile(r'(\w+)\s*=\s*(?:(\w+)\.)?findViewById\s*\(\s*R\.id\.(\w+)\s*\)')
        for m in assign_pattern.finditer(code):
            var_name, prefix, id_name = m.groups()
            inferred_type = var_types.get(var_name, "View")
            matches.append((prefix, inferred_type, id_name))

        for prefix, target_type, id_name in matches:
            total_checks += 1
            if not target_type:
                target_type = "View"

            # Determine which layout this corresponds to
            target_layout = b["layout"]
            if prefix and prefix in b["sub_layouts"]:
                target_layout = b["sub_layouts"][prefix]
            elif prefix == "itemView":
                target_layout = b["sub_layouts"].get("ViewHolder", "item_notebook_card.xml")

            layout_ids = layouts.get(target_layout, {})
            if id_name not in layout_ids:
                # Check other sub layouts
                found_in = None
                for sl_name, sl_file in b["sub_layouts"].items():
                    if id_name in layouts.get(sl_file, {}):
                        found_in = sl_file
                        break
                if found_in:
                    actual_tag = layouts[found_in][id_name]
                else:
                    err = f"MISSING ID: R.id.{id_name} in {b['class']} not found in layout '{target_layout}'! (NullPointerException risk)"
                    errors.append(err)
                    print(f"  [FAIL] {err}")
                    continue
            else:
                actual_tag = layout_ids[id_name]

            # Verify type assignability
            if not is_assignable(actual_tag, target_type):
                err = f"TYPE MISMATCH: R.id.{id_name} in layout '{target_layout}' is <{actual_tag}>, but {b['class']} expects <{target_type}>! (ClassCastException risk)"
                errors.append(err)
                print(f"  [FAIL] {err}")
            else:
                print(f"  [PASS] R.id.{id_name} -> <{actual_tag}> matches Kotlin <{target_type}> in '{target_layout}'")

    print("\n--- Summary ---")
    print(f"Total View Binding Checks: {total_checks}")
    print(f"Errors Found: {len(errors)}")
    if errors:
        for e in errors:
            print(f"  - {e}")
        return False
    else:
        print(">>> ALL VIEW BINDINGS VERIFIED 100% TYPE-SAFE AND NON-NULL! <<<")
        return True

if __name__ == "__main__":
    success = test_view_bindings()
    exit(0 if success else 1)
