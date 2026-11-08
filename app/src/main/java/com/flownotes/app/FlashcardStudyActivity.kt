package com.flownotes.app

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.flownotes.app.ai.LocalAiStudyEngine
import com.flownotes.app.data.NoteRepository
import com.flownotes.app.model.Flashcard
import com.flownotes.app.util.ThemeManager

class FlashcardStudyActivity : AppCompatActivity() {

    private lateinit var repository: NoteRepository
    private var cards = listOf<Flashcard>()
    private var currentIndex = 0
    private var isFlipped = false

    private lateinit var tvFront: TextView
    private lateinit var tvBack: TextView
    private lateinit var tvHint: TextView
    private lateinit var tvProgress: TextView
    private lateinit var cardFrontLayout: View
    private lateinit var cardBackLayout: View

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flashcards)

        repository = NoteRepository(this)
        val notebookId = intent.getStringExtra("EXTRA_NOTEBOOK_ID")
        val notebook = if (notebookId != null) repository.getNotebook(notebookId) else null

        if (notebook == null) {
            Toast.makeText(this, "Notebook not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        cards = LocalAiStudyEngine.generateLocalFlashcards(notebook)

        setupViews(notebook.title)
        showCard(0)
    }

    private fun setupViews(title: String) {
        findViewById<TextView>(R.id.tv_deck_title).text = "$title Flashcards"
        findViewById<View>(R.id.btn_close_study).setOnClickListener { finish() }

        tvFront = findViewById(R.id.tv_card_front)
        tvBack = findViewById(R.id.tv_card_back)
        tvHint = findViewById(R.id.tv_card_hint)
        tvProgress = findViewById(R.id.tv_study_progress)

        cardFrontLayout = findViewById(R.id.card_front_layout)
        cardBackLayout = findViewById(R.id.card_back_layout)

        val cardContainer = findViewById<FrameLayout>(R.id.card_container)
        cardContainer.setOnClickListener {
            flipCard()
        }

        findViewById<Button>(R.id.btn_rate_again).setOnClickListener { nextCard(score = 1) }
        findViewById<Button>(R.id.btn_rate_hard).setOnClickListener { nextCard(score = 2) }
        findViewById<Button>(R.id.btn_rate_good).setOnClickListener { nextCard(score = 3) }
        findViewById<Button>(R.id.btn_rate_easy).setOnClickListener { nextCard(score = 4) }
    }

    private fun showCard(index: Int) {
        if (index !in cards.indices) {
            showStudyComplete()
            return
        }
        currentIndex = index
        isFlipped = false

        val card = cards[index]
        tvFront.text = card.front
        tvBack.text = card.back
        tvHint.text = if (card.hint.isNotEmpty()) "Hint: ${card.hint}" else "Tap card to flip"

        cardFrontLayout.visibility = View.VISIBLE
        cardBackLayout.visibility = View.GONE

        tvProgress.text = "Card ${index + 1} of ${cards.size}"
    }

    private fun flipCard() {
        isFlipped = !isFlipped
        if (isFlipped) {
            cardFrontLayout.visibility = View.GONE
            cardBackLayout.visibility = View.VISIBLE
        } else {
            cardFrontLayout.visibility = View.VISIBLE
            cardBackLayout.visibility = View.GONE
        }
    }

    private fun nextCard(score: Int) {
        if (currentIndex < cards.size - 1) {
            showCard(currentIndex + 1)
        } else {
            showStudyComplete()
        }
    }

    private fun showStudyComplete() {
        Toast.makeText(this, "Deck Complete! Great work reviewing.", Toast.LENGTH_LONG).show()
        finish()
    }
}
