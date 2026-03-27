package grafiosch.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.entities.ReleaseNote;

/**
 * Repository for managing {@link ReleaseNote} entities. Provides language-filtered queries with pagination
 * for displaying the most recent release notes to users.
 */
public interface ReleaseNoteRepository extends JpaRepository<ReleaseNote, Integer> {

  /**
   * Retrieves release notes for a specific language, ordered by version descending (newest first).
   * Combined with a {@link Pageable} parameter this effectively returns the top N most recent release notes.
   *
   * @param language the ISO 639-1 language code to filter by (e.g., "EN", "DE")
   * @param pageable pagination parameter controlling the number of results (typically {@code PageRequest.of(0, limit)})
   * @return list of release notes matching the language, ordered by version descending
   */
  List<ReleaseNote> findByLanguageOrderByVersionDesc(String language, Pageable pageable);
}
