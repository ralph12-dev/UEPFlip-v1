package com.example.uepflip_flashcard.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Create screen relies on [DeckNaming] to pre-fill the deck-name field from a PDF file name or
 * from typed topic text. These cases pin the trimming/truncation rules.
 */
class DeckNamingTest {

    private val fallback = "New deck"

    @Test
    fun dropsExtensionFromFileName() {
        assertEquals("UEP_Review", DeckNaming.titleFromFileName("UEP_Review.pdf", fallback))
    }

    @Test
    fun keepsFileNamesWithoutAnExtension() {
        assertEquals("chapter-notes", DeckNaming.titleFromFileName("chapter-notes", fallback))
    }

    @Test
    fun fallsBackWhenFileNameIsMissingOrBlank() {
        assertEquals(fallback, DeckNaming.titleFromFileName(null, fallback))
        assertEquals(fallback, DeckNaming.titleFromFileName("   ", fallback))
        assertEquals(fallback, DeckNaming.titleFromFileName(".pdf", fallback))
    }

    @Test
    fun usesFirstNonBlankLineOfATopic() {
        assertEquals(
            "Photosynthesis",
            DeckNaming.titleFromTopic("\n  \nPhotosynthesis\nlight reactions", fallback)
        )
    }

    @Test
    fun keepsPunctuationInsideATopic() {
        // A period in a sentence must not be treated as a file extension.
        assertEquals(
            "Chapter 1. Introduction",
            DeckNaming.titleFromTopic("Chapter 1. Introduction", fallback)
        )
    }

    @Test
    fun fallsBackWhenTopicIsBlank() {
        assertEquals(fallback, DeckNaming.titleFromTopic("   \n \n ", fallback))
        assertEquals(fallback, DeckNaming.titleFromTopic(null, fallback))
    }

    @Test
    fun collapsesInnerWhitespace() {
        assertEquals(
            "Cell membrane transport",
            DeckNaming.titleFromTopic("Cell\tmembrane  transport", fallback)
        )
    }

    @Test
    fun truncatesVeryLongTitles() {
        val long = "a".repeat(DeckNaming.MAX_TITLE_LENGTH + 40)

        val title = DeckNaming.titleFromTopic(long, fallback)

        assertEquals(DeckNaming.MAX_TITLE_LENGTH, title.length)
        assertEquals("a".repeat(DeckNaming.MAX_TITLE_LENGTH), title)
    }
}
