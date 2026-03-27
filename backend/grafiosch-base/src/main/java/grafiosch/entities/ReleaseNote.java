package grafiosch.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Represents a release note entry for a specific application version and language. Release notes are stored
 * per language to support multi-language display. The combination of version and language is unique, enforced
 * by a database constraint.
 */
@Entity
@Table(name = ReleaseNote.TABNAME)
@Schema(description = """
    Release note entry describing changes for a specific application version in a specific language. \
    Each version can have separate notes per supported language (e.g., EN, DE). The REST endpoint \
    returns notes ordered by version descending with fallback to English if the requested language \
    has no entries.""")
public class ReleaseNote {

  public static final String TABNAME = "release_note";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_release_note")
  @Schema(description = "Unique identifier of the release note entry")
  private Integer idReleaseNote;

  @Column(name = "version", nullable = false, length = 20)
  @Schema(description = "Application version string (e.g., '0.33.8'). Together with language forms a unique constraint")
  private String version;

  @Column(name = "language", nullable = false, length = 2)
  @Schema(description = "ISO 639-1 language code (e.g., 'EN', 'DE'). Together with version forms a unique constraint")
  private String language;

  @Column(name = "note", nullable = false, length = 1024)
  @Schema(description = "Release note content describing changes, fixes, and new features for this version")
  private String note;

  // Constructors
  public ReleaseNote() {
  }

  public ReleaseNote(String version, String language, String note) {
    this.version = version;
    this.language = language;
    this.note = note;
  }

  // Getters and Setters
  public Integer getIdReleaseNote() {
    return idReleaseNote;
  }

  public void setIdReleaseNote(Integer idReleaseNote) {
    this.idReleaseNote = idReleaseNote;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  @Override
  public String toString() {
    return "ReleaseNote{" + "version='" + version + '\'' + ", language='" + language + '\'' + ", note='" + note + '\''
        + '}';
  }
}
