package grafiosch.entities;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = TaxUpload.TABNAME)
public class TaxUpload {

  public static final String TABNAME = "tax_upload";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_tax_upload")
  private Integer idTaxUpload;

  @JsonIgnore
  @ManyToOne
  @JoinColumn(name = "id_tax_year", nullable = false)
  private TaxYear taxYear;

  @Column(name = "id_tax_year", insertable = false, updatable = false)
  private Integer idTaxYear;

  @Column(name = "file_name", nullable = false, length = 255)
  private String fileName;

  @Column(name = "file_path", nullable = false, length = 500)
  private String filePath;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
  @Column(name = "upload_date")
  private LocalDateTime uploadDate;

  @Column(name = "record_count")
  private Integer recordCount;

  public TaxUpload() {
  }

  public Integer getIdTaxUpload() {
    return idTaxUpload;
  }

  public void setIdTaxUpload(Integer idTaxUpload) {
    this.idTaxUpload = idTaxUpload;
  }

  public TaxYear getTaxYear() {
    return taxYear;
  }

  public void setTaxYear(TaxYear taxYear) {
    this.taxYear = taxYear;
  }

  public Integer getIdTaxYear() {
    return idTaxYear;
  }

  public void setIdTaxYear(Integer idTaxYear) {
    this.idTaxYear = idTaxYear;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public LocalDateTime getUploadDate() {
    return uploadDate;
  }

  public void setUploadDate(LocalDateTime uploadDate) {
    this.uploadDate = uploadDate;
  }

  public Integer getRecordCount() {
    return recordCount;
  }

  public void setRecordCount(Integer recordCount) {
    this.recordCount = recordCount;
  }
}
