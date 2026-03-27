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

/**
 * REST controller for retrieving release notes. Provides a publicly accessible endpoint (no authentication required)
 * that returns the most recent release notes for a given language with automatic fallback to English.
 */
@RestController
@RequestMapping(RequestMappings.RELEASE_NOTE_MAP)
@Tag(name = RequestMappings.RELEASE_NOTE, description = "Controller for release notes")
public class ReleaseNoteResource {

  @Autowired
  private ReleaseNoteRepository releaseNoteRepository;

  /**
   * Returns the top N most recent release notes ordered by version descending. If no release notes exist for the
   * requested language, falls back to English.
   *
   * @param lang  ISO 639-1 language code or language name (e.g., "DE", "EN", "GERMAN"); defaults to "EN"
   * @param limit maximum number of release notes to return; defaults to 3
   * @return list of release notes for the resolved language, newest version first
   */
  @GetMapping
  public ResponseEntity<List<ReleaseNote>> getTopReleaseNotes(@RequestParam(defaultValue = "EN") String lang,
      @RequestParam(defaultValue = "3") int limit) {

    String language = normalizeLanguage(lang);
    Pageable pageable = PageRequest.of(0, limit);

    List<ReleaseNote> releaseNotes = releaseNoteRepository.findByLanguageOrderByVersionDesc(language, pageable);

    // Fallback to English if requested language has no results
    if (releaseNotes.isEmpty() && !"EN".equals(language)) {
      releaseNotes = releaseNoteRepository.findByLanguageOrderByVersionDesc("EN", pageable);
    }

    return ResponseEntity.ok(releaseNotes);
  }

  /**
   * Normalizes a language parameter to a supported two-letter code. Accepts case-insensitive codes ("de", "DE")
   * and full language names ("GERMAN"). Unsupported values default to "EN".
   *
   * @param language the raw language parameter from the request
   * @return normalized two-letter language code ("DE" or "EN")
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
