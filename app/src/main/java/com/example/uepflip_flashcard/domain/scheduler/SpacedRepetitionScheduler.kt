package com.example.uepflip_flashcard.domain.scheduler

interface SpacedRepetitionScheduler {
    /**
     * Calculates the next review date and updated scheduling metadata for a card.
     * 
     * @param rating 1 = Hard/Again (fail), 2 = Medium/Good (pass), 3 = Easy (pass perfectly)
     * @param currentRepetitions The current consecutive successful repetitions (0 if failed last time)
     * @param currentInterval The current interval in days
     * @param currentEaseFactor The current easiness factor (default 2.5)
     * @param currentTimeMillis The current time in milliseconds to calculate the next review date from
     */
    fun calculateNextReview(
        rating: Int,
        currentRepetitions: Int,
        currentInterval: Int,
        currentEaseFactor: Float,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): ScheduleResult
}
