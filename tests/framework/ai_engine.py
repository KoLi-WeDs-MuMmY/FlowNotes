"""
LocalAiStudyEngine Oracle.
Faithfully mirrors LocalAiStudyContract and LocalAiStudyEngine logic.
Validates 100% offline extractive summarization, formula/definition extraction,
3-step problem decomposition, and automated flashcard generation.
"""

import re
from typing import List, Dict, Any, Tuple
from tests.framework.models import Notebook, Flashcard


STOP_WORDS = {
    "the", "and", "is", "in", "it", "of", "to", "a", "for", "on", "with", "as", "at",
    "by", "an", "be", "this", "that", "which", "from", "or", "are", "was", "were", "but",
    "not", "what", "all", "when", "we", "there", "can", "your", "has", "have"
}


class LocalAiStudyEngine:

    @staticmethod
    def extract_notebook_text(notebook: Notebook) -> str:
        lines = []
        for page in notebook.pages:
            for t in page.text_annotations:
                if t.text.strip():
                    lines.append(t.text.strip())
            for s in page.sticky_notes:
                if s.content.strip():
                    lines.append(s.content.strip())
        return "\n".join(lines)

    @staticmethod
    def extract_keywords(text: str, top_n: int = 6) -> List[str]:
        words = re.findall(r"[a-zA-Z]{4,}", text.lower())
        filtered = [w for w in words if w not in STOP_WORDS]
        counts: Dict[str, int] = {}
        for w in filtered:
            counts[w] = counts.get(w, 0) + 1
        sorted_words = sorted(counts.items(), key=lambda x: x[1], reverse=True)
        return [w.capitalize() for w, _ in sorted_words[:top_n]]

    @staticmethod
    def extract_definitions(text: str) -> List[Tuple[str, str]]:
        pattern = r"([A-Z][a-zA-Z\s]{1,30})\s+(?:is|are|refers to|defined as|means)\s+([^.\n]+)"
        matches = re.findall(pattern, text)
        return [(m[0].strip(), m[1].strip()) for m in matches if len(m[0].strip()) > 2]

    @staticmethod
    def extract_formulas(text: str) -> List[str]:
        # Detect LaTeX formulas ($...$) or explicit algebraic equations (y = mx + b, E = mc^2)
        latex_matches = re.findall(r"\$([^$]+)\$", text)
        eq_matches = re.findall(r"([A-Za-z][A-Za-z0-9_()]*\s*=\s*[^.\n$]+)", text)
        all_formulas = []
        for f in latex_matches + eq_matches:
            cleaned = f.strip()
            if len(cleaned) > 2 and cleaned not in all_formulas:
                all_formulas.append(cleaned)
        return all_formulas

    @staticmethod
    def generate_local_summary(title: str, raw_text: str, page_count: int = 1) -> str:
        text = raw_text.strip() if raw_text.strip() else f"Lecture on {title}. Key principles and systematic notes covering definitions, properties, and core takeaways."
        sentences = [s.strip() for s in re.split(r"[.!?\n]+", text) if len(s.strip()) > 15]
        keywords = LocalAiStudyEngine.extract_keywords(text, top_n=6)
        keyword_str = ", ".join(keywords) if keywords else f"{title}, Foundations, Applications"

        if len(sentences) > 2:
            key_sentences = sentences[:4]
        else:
            key_sentences = [
                f"Primary concepts and definitions recorded for {title}.",
                "Systematic review of foundational rules and core properties.",
                f"Practical examples and annotations organized across {page_count} page(s)."
            ]

        res = [
            "### 📝 Instant Study Summary (On-Device)\n",
            f"**Core Topic:** {title}",
            f"**Key Focus Concepts:** {keyword_str}\n",
            "**1. Essential Insights:**"
        ]
        for s in key_sentences:
            res.append(f"• {s}")
        res.append("\n**2. Review Strategy:**")
        res.append("• Use **Active Recall Tape** over vocabulary & equations to self-test.")
        res.append("• Practice with the generated on-device flashcards for spaced repetition.")
        res.append("• 100% private & offline: No data left your device.")
        return "\n".join(res)

    @staticmethod
    def solve_problem_locally(query: str, context_text: str = "") -> str:
        q = query.strip()
        lines = [
            "### 💡 On-Device Problem Breakdown\n",
            f"**Problem Analyzed:** *\"{q}\"*\n",
            "**Step 1: Identify Knowns and Variables**",
            "• Map out the core query parameters and underlying definitions.",
            "• Reference context from your active notebook notes.\n",
            "**Step 2: Methodological Formulation**",
            "• Apply the relevant foundational theorems or standard properties.",
            "• Simplify algebraically or conceptually step-by-step.\n",
            "**Step 3: Synthesis & Verification**",
            "• Verify dimensions, sign conventions, and boundary values.",
            "• **Conclusion:** You can tap 'Insert into Note' to keep this breakdown in your notebook!"
        ]
        return "\n".join(lines)

    @staticmethod
    def answer_study_question(question: str, notebook: Notebook) -> str:
        q_lower = question.lower()
        title = notebook.title

        if "tape" in q_lower or "active recall" in q_lower or "memory" in q_lower:
            return (
                "💡 **Active Recall Technique:**\n"
                "Use the **Tape Tool** from the floating dock to draw concealing tape over your formulas or vocabulary terms.\n"
                "When revising, tap any tape to test whether you remember the answer before revealing it!"
            )

        if "cornell" in q_lower or "template" in q_lower:
            return (
                "📐 **Cornell Notes Method:**\n"
                "• **Cue Column (Left):** Write down questions, key dates, or terms during or right after class.\n"
                "• **Notes Column (Right):** Record lecture bullet points, diagrams, and outlines.\n"
                "• **Summary Area (Bottom):** Write a 2-3 sentence synthesis in your own words."
            )

        keywords = LocalAiStudyEngine.extract_keywords(question, top_n=3)
        kw_line = f"• **Focus Areas:** {' • '.join(keywords)}\n" if keywords else ""
        return (
            f"### 🎓 Local Study Assistant\n\n"
            f"Regarding: **\"{question}\"**\n\n"
            f"Based on your **{title}** notebook ({notebook.page_count} pages):\n\n"
            f"• **Key Connection:** This relates directly to your study topics on {title}.\n"
            f"{kw_line}"
            f"• **Study Advice:** Convert this question into an active recall card in the Flashcard tab to test yourself periodically."
        )

    @staticmethod
    def generate_local_flashcards(notebook: Notebook) -> List[Flashcard]:
        cards = [
            Flashcard(
                front="What is the primary topic of this notebook?",
                back=f"{notebook.title} — covers foundational principles, active notes, and diagram annotations.",
                hint="Notebook Title"
            ),
            Flashcard(
                front="What is the benefit of Freenotes Active Recall Tape?",
                back="It conceals keywords or formulas so you can test your memory retrieval before tapping to reveal the answer.",
                hint="Testing effect & memory retention"
            ),
            Flashcard(
                front="What are the 3 essential components of Cornell Notes layout?",
                back="1. Cue Column (left, for questions & keywords)\n2. Note-taking Area (right, for detailed lecture notes)\n3. Summary Area (bottom, for synthesis)",
                hint="Layout structure"
            )
        ]

        text = LocalAiStudyEngine.extract_notebook_text(notebook)
        defs = LocalAiStudyEngine.extract_definitions(text)
        for term, explanation in defs[:5]:
            cards.append(Flashcard(
                front=f"Define: {term}",
                back=explanation,
                hint=f"Context from {notebook.title}"
            ))

        formulas = LocalAiStudyEngine.extract_formulas(text)
        for eq in formulas[:3]:
            cards.append(Flashcard(
                front="What formula is used for this relationship?",
                back=eq,
                hint="Mathematical equation"
            ))

        # Also generate cards from active recall tapes across pages
        for page in notebook.pages:
            for idx, tape in enumerate(page.tapes):
                cards.append(Flashcard(
                    front=f"Active Recall Tape #{idx+1} on Page {page.page_number}",
                    back="Retrieve the formula or definition hidden underneath this tape.",
                    hint="Check canvas tape position"
                ))
        return cards
