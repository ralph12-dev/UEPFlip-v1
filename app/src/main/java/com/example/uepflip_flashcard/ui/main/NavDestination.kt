package com.example.uepflip_flashcard.ui.main

/**
 * Marks a screen as a **top-level destination**: one that is shown with the bottom navigation bar
 * and that resets the back stack.
 *
 * [MainActivity] derives the navigation bar's visibility from this marker rather than from the call
 * site. Visibility set imperatively cannot survive the reversal of a fragment transaction, so
 * returning from a pushed detail screen (or rotating while on one) is what put the bar out of sync;
 * deriving it from the resumed fragment keeps it correct whatever the back stack does.
 *
 * The Tutor screen deliberately does *not* implement this: the design hides the bar there and the
 * screen carries its own back button.
 */
interface NavDestination
