package grafiosch.rest;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.ReleaseNote;
import grafiosch.repository.ReleaseNoteRepository;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.RELEASE_NOTE_MAP)
@Tag(name = RequestMappings.RELEASE_NOTE, description = "Controller for release notes")
public class ReleaseNoteResource {

  @Autowired
  private ReleaseNoteRepository releaseNoteRepository;

  /**
   * GET /api/releasenotes?lang=DE&limit=3 Gets top N release notes ordered by version descending
   */
  @GetMapping
  public ResponseEntity<List<ReleaseNote>> getTopReleaseNotes(@RequestParam(defaultValue = "EN") String lang,
      @RequestParam(defaultValue = "3") int limit) {

    // Normalize language
    String language = normalizeLanguage(lang);
    Pageable pageable = PageRequest.of(0, limit);

    // Get release notes for requested language
    List<ReleaseNote> releaseNotes = releaseNoteRepository.findByLanguageOrderByVersionDesc(language, pageable);

    // Fallback to English if requested language has no results
    if (releaseNotes.isEmpty() && !"EN".equals(language)) {
      releaseNotes = releaseNoteRepository.findByLanguageOrderByVersionDesc("EN", pageable);
    }

    return ResponseEntity.ok(releaseNotes);
  }

  /**
   * Normalizes language code (de -> DE, en -> EN)
   */
  private String normalizeLanguage(String language) {
    if (language == null || language.trim().isEmpty()) {
      return "EN";
    }

    String normalized = language.trim().toUpperCase();

    // Supported languages
    if ("DE".equals(normalized) || "GERMAN".equals(normalized)) {
      return "DE";
    }

    // Default to English
    return "EN";
  }
}
