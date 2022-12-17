package grafioschtrader.entities;

import java.io.Serializable;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = ImportTransactionPosFailed.TABNAME)
public class ImportTransactionPosFailed extends BaseID implements Serializable {

  public static final String TABNAME = "imp_trans_pos_failed";

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_imp_trans_pos_failed")
  private Integer idTransactionPosFailed;

  @Column(name = "id_trans_imp_template")
  private Integer idTransactionImportTemplate;

  @Column(name = "last_matching_property")
  private String lastMatchingProperty;

  @Column(name = "id_trans_pos")
  private Integer idTransactionPos;

  @Column(name = "error_message")
  private String errorMessage;

  public ImportTransactionPosFailed() {
    super();
  }

  public ImportTransactionPosFailed(Integer idTransactionPos, Integer idTransactionImportTemplate,
      String lastMatchingProperty) {
    this.idTransactionPos = idTransactionPos;
    this.idTransactionImportTemplate = idTransactionImportTemplate;
    this.lastMatchingProperty = lastMatchingProperty;
  }

  public ImportTransactionPosFailed(Integer idTransactionPos, Integer idTransactionImportTemplate,
      String lastMatchingProperty, String errorMessage) {
    this(idTransactionPos, idTransactionImportTemplate, lastMatchingProperty);
    this.errorMessage = errorMessage;
  }

  public Integer getIdTransactionPosFailed() {
    return idTransactionPosFailed;
  }

  public Integer getIdTransactionImportTemplate() {
    return idTransactionImportTemplate;
  }

  public void setIdTransactionImportTemplate(Integer idTransactionImportTemplate) {
    this.idTransactionImportTemplate = idTransactionImportTemplate;
  }

  public String getLastMatchingProperty() {
    return lastMatchingProperty;
  }

  public void setLastMatchingProperty(String lastMatchingProperty) {
    this.lastMatchingProperty = lastMatchingProperty;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  @Override
  public Integer getId() {
    return idTransactionPosFailed;
  }

  @Override
  public String toString() {
    return "ImportTransactionPosFailed [idTransactionPosFailed=" + idTransactionPosFailed
        + ", idTransactionImportTemplate=" + idTransactionImportTemplate + ", lastMatchingProperty="
        + lastMatchingProperty + ", idTransactionPos=" + idTransactionPos + "]";
  }

}
