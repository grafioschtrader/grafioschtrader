package grafiosch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import grafiosch.entities.ReleaseNote;

public interface ReleaseNoteRepository extends JpaRepository<ReleaseNote, Integer> {
  
  /**
   * Gets top N release notes for specific language, ordered by version descending
   */
  List<ReleaseNote> findByLanguageOrderByVersionDesc(String language, Pageable pageable);
}
