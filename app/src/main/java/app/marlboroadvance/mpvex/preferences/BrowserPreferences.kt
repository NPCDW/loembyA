package app.marlboroadvance.mpvex.preferences

import app.marlboroadvance.mpvex.domain.emby.ChineseSubtitleMarks
import app.marlboroadvance.mpvex.domain.emby.EmbyLibraryFilterState
import app.marlboroadvance.mpvex.preferences.preference.Preference
import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore

/** Local navigation and presentation state for the read-only Emby browser. */
class BrowserPreferences(
  private val preferenceStore: PreferenceStore,
) {
  /** Long lists share the scrollbar wrappers; disabling removes the draggable touch region too. */
  val sideScrollbarEnabled = preferenceStore.getBoolean("side_scrollbar_enabled", true)

  // Watched threshold preference (percentage 1-100).
  val watchedThreshold = preferenceStore.getInt("watched_threshold", 95)

  /** Emby /Items SortBy value, retained across library visits. */
  val embyLibrarySortBy = preferenceStore.getString("emby_library_sort_by", "SortName")

  /** POSTER / BACKDROP / BANNER presentation style. */
  val embyLibraryCardStyle = preferenceStore.getString("emby_library_card_style", "POSTER")

  /** Empty follows the sort's natural direction; otherwise Ascending / Descending. */
  val embyLibrarySortOrder = preferenceStore.getString("emby_library_sort_order", "")

  /** Per-library [EmbyLibraryFilterState] JSON; empty means no filters. */
  fun embyLibraryFilter(libraryId: String): Preference<String> =
    preferenceStore.getString("emby_library_filter_$libraryId", "")

  /** GenreIds / TagIds capability: empty = unknown, 1 = supported, 0 = use local filtering. */
  fun embyGenreIdFilterSupport(serverId: Long): Preference<String> =
    preferenceStore.getString("emby_genreid_filter_support_$serverId", "")

  /** Per-library category enum name; empty means All. */
  fun embyLibraryCategory(libraryId: String): Preference<String> =
    preferenceStore.getString("emby_library_category_$libraryId", "")

  /** Per-library mixed-content mode; empty means media only. */
  fun embyLibraryPhotoMode(libraryId: String): Preference<String> =
    preferenceStore.getString("emby_library_photo_mode_$libraryId", "")

  /** Per-server comma-separated pinned library IDs, most recently pinned first. */
  fun embyLibraryPins(serverId: Long): Preference<String> =
    preferenceStore.getString("emby_library_pins_$serverId", "")

  /** Per-server id:number pairs; pinned libraries remain ahead of numbered libraries. */
  fun embyLibraryNumbers(serverId: Long): Preference<String> =
    preferenceStore.getString("emby_library_numbers_$serverId", "")

  /** Per-server home-only library visibility, without hiding libraries from ordering controls. */
  fun embyHiddenLibraries(serverId: Long): Preference<String> =
    preferenceStore.getString("emby_hidden_libraries_$serverId", "")

  /** Per-server id:timestamp ledger for ordering resume results by recent local playback. */
  fun embyRecentPlays(serverId: Long): Preference<String> =
    preferenceStore.getString("emby_recent_plays_$serverId", "")

  /** Global resource-naming conventions as [ChineseSubtitleMarks] JSON; empty uses defaults. */
  val embyChineseSubtitleMarks = preferenceStore.getString("emby_chinese_subtitle_marks", "")

  /** Favorites tab enum name; empty means media. */
  val embyFavoritesTab = preferenceStore.getString("emby_favorites_tab", "")

  /** Favorites sort enum name; empty means name. */
  val embyFavoritesSort = preferenceStore.getString("emby_favorites_sort", "")

  val embyFavoritesSortAsc = preferenceStore.getBoolean("emby_favorites_sort_asc", true)

  /** Actor / director works sort enum name; empty means name. */
  val embyPersonSort = preferenceStore.getString("emby_person_sort", "")

  val embyPersonSortAsc = preferenceStore.getBoolean("emby_person_sort_asc", true)

  /** Library view enum name; empty means grid. */
  val embyLibraryViewMode = preferenceStore.getString("emby_library_view_mode", "")

  /** Poster grid columns, 2..6; wide artwork styles remain adaptive. */
  val embyLibraryGridColumns = preferenceStore.getInt("emby_library_grid_columns", 3)

  /** Grid / timeline title lines, 1 or 2; compact lists remain single-line. */
  val embyLibraryTitleLines = preferenceStore.getInt("emby_library_title_lines", 1)

  /** Search suggestion collapse state, separate from search results. */
  val embySearchSuggestCollapsed = preferenceStore.getBoolean("emby_search_suggest_collapsed", false)

  /** Photo grid columns, 2..4, independent of video poster density. */
  val photoGridColumns = preferenceStore.getInt("photo_grid_columns", 3)

  /** Photo /Items SortBy value. */
  val photoSortBy = preferenceStore.getString("photo_sort_by", "SortName")

  /** Empty follows the photo sort's natural direction. */
  val photoSortOrder = preferenceStore.getString("photo_sort_order", "")

  /** Last viewed / first visible photo per album; -1 means no saved position. */
  fun photoLastIndex(parentId: String) =
    preferenceStore.getInt("photo_last_index_$parentId", -1)

  /** Pixel offset paired with [photoLastIndex] for exact grid restoration. */
  fun photoLastOffset(parentId: String) =
    preferenceStore.getInt("photo_last_offset_$parentId", 0)
}
