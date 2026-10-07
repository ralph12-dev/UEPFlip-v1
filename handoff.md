# UEPflip — Project Handoff

## 1. Goal
Build **UEPflip**: an offline-first mobile flashcard Android application featuring deterministic Spaced Repetition (SM-2 algorithm) and Room persistence as the core engine, supported by deck/card management and AI-assisted PDF card generation and evaluation.

## 2. Current State
* **Architecture:** MVVM + Repository Pattern + Room Database.
* **Build Status:** Compiles cleanly with `./gradlew assembleDebug` and passes `./gradlew testDebugUnitTest`.
* **Stack:** Android SDK 37 (MinSdk 26), AGP 9.4.1, Gradle 9.6.0, KSP 2.3.9, Room 2.8.5, Coroutines 1.9.0, ViewBinding enabled.
* **Core Functionality Completed:**
  - Database entities, DAOs, SM-2 scheduling engine, and repository coordination.
  - Interactive Study loop UI (`StudyActivity` / `StudyViewModel`).
  - Deck List Management screen (`DeckListActivity` / `DeckListViewModel`) allowing users to view existing decks, add new decks, and navigate to study sessions.

## 3. Active Files
1. `app/src/main/java/com/example/uepflip_flashcard/ui/decklist/DeckListActivity.kt`
2. `app/src/main/java/com/example/uepflip_flashcard/ui/decklist/DeckListViewModel.kt`
3. `app/src/main/java/com/example/uepflip_flashcard/ui/decklist/DeckListViewModelFactory.kt`
4. `app/src/main/java/com/example/uepflip_flashcard/ui/deck/DockListAdapter.kt`
5. `app/src/main/res/layout/activity_deck_list.xml`
6. `app/src/main/res/layout/item_deck.xml`
7. `app/src/main/AndroidManifest.xml`

## 4. Changes Made
1. **Deck Management UI (Phase 5):**
   - Created `activity_deck_list.xml` with `RecyclerView`, empty state layout, and FAB for additions.
   - Built `item_deck.xml` utilizing Material Components (Chips) for metadata.
   - Implemented `DockListAdapter` inheriting from `ListAdapter<DeckEntity, DeckViewHolder>` with native `DiffUtil`.
   - Created `DeckListViewModel` mapping Room `Flow<List<DeckEntity>>` state directly to the UI.
   - Implemented `DeckListActivity` to observe state flows, show an `AlertDialog` for quick deck creation, and start `StudyActivity`.
   - Set `DeckListActivity` as the initial launcher activity in `AndroidManifest.xml`.

## 5. Failed Attempts & Fixes
1. **SwipeRefreshLayout Component Rejection:**
   - *Cause:* Utilized `SwipeRefreshLayout` in `activity_deck_list.xml` without the accompanying library dependency.
   - *Fix:* Replaced it with a standard `ConstraintLayout` and removed layout bindings from `DeckListActivity.kt`.
2. **Missing `tools:` XML Namespace:**
   - *Cause:* `item_deck.xml` rendered layout editor errors due to missing namespace.
   - *Fix:* Added `xmlns:tools="http://schemas.android.com/tools"` to the root tag.
3. **Intent Builder Mismatch:**
   - *Cause:* Invoked `StudyActivity.newIntent()` instead of the correct companion object method `StudyActivity.createIntent()`.
   - *Fix:* Updated the launch function in `DeckListActivity`.
4. **Coroutines Flow Operator Import Error:**
   - *Cause:* Imported `androidx.lifecycle.distinctUntilChanged` instead of `kotlinx.coroutines.flow.distinctUntilChanged` for the Flow stream.
   - *Fix:* Swapped the import to the Coroutines flow extension.

## 6. Specific Next Stage
* **Phase 6 (Flashcard Management UI):**
  - Implement a Flashcard List/Detail screen to allow users to view, add, edit, and delete individual flashcards inside a selected deck.
  - Bind card counts dynamically to the UI.