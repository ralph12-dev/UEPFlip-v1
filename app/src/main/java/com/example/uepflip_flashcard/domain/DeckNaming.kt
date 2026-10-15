package com.example.uepflip_flashcard.domain

/**
 * Turns an AI import source into a sensible default deck title: either the name of a picked PDF or
 * the topic/lesson text the user typed. Kept free of Android types so it can be unit tested on the
 * JVM (the Create screen pre-fills its deck-name field from here).
 */
object DeckNaming {

    /** Longest title we keep; anything longer is truncated so the deck list stays readable. */
    const val MAX_TITLE_LENGTH = 60

    /**
     * Derives a title from a file name, dropping the extension. Returns [fallback] when there is
     * nothing usable left (no name, blank name, or a name that is only an extension).
     */
    fun titleFromFileName(fileName: String?, fallback: String): String {
        val name = fileName?.trim().orEmpty()
        return normalize(name.substringBeforeLast('.', name), fallback)
    }

    /**
     * Derives a title from typed topic/lesson text using its first non-blank line, so a multi-line
     * paste does not produce a paragraph-sized deck name. Returns [fallback] when the text is blank.
     */
    fun titleFromTopic(topic: String?, fallback: String): String {
        val firstLine = topic
            ?.lineSequence()
            ?.firstOrNull { it.isNotBlank() }
            .orEmpty()
        return normalize(firstLine, fallback)
    }

    private fun normalize(raw: String, fallback: String): String {
        val collapsed = raw.replace(WHITESPACE, " ").trim()
        if (collapsed.isEmpty()) return fallback
        return if (collapsed.length <= MAX_TITLE_LENGTH) {
            collapsed
        } else {
            collapsed.take(MAX_TITLE_LENGTH).trimEnd()
        }
    }

    private val WHITESPACE = Regex("\\s+")
}
