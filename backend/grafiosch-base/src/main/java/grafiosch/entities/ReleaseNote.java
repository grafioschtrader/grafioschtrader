package grafiosch.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = ReleaseNote.TABNAME)
public class ReleaseNote {

  public static final String TABNAME = "release_note";
  
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_release_note")
  private Integer idReleaseNote;

  @Column(name = "version", nullable = false, length = 20)
  private String version;

  @Column(name = "language", nullable = false, length = 2)
  private String language;

  @Column(name = "note", nullable = false, length = 1024)
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
