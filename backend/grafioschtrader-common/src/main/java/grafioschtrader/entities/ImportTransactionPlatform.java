package grafioschtrader.entities;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.common.PropertyAlwaysUpdatable;
import jakarta.persistence.Basic;
import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * It contains the header information of the transaction import templates.
 *
 * @author Hugo Graf
 *
 */
@Entity
@Table(name = ImportTransactionPlatform.TABNAME)
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ImportTransactionPlatform extends Auditable {

  public static final String TABNAME = "imp_trans_platform";

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_trans_imp_platform")
  private Integer idTransactionImportPlatform;

  @Basic(optional = false)
  @NotBlank
  @Size(min = 1, max = 32)
  @PropertyAlwaysUpdatable
  private String name;

  @Column(name = "id_csv_imp_impl")
  @PropertyAlwaysUpdatable
  private String idCsvImportImplementation;

  public Integer getIdTransactionImportPlatform() {
    return idTransactionImportPlatform;
  }

  public void setIdTransactionImportPlatform(Integer idTransactionImportPlatform) {
    this.idTransactionImportPlatform = idTransactionImportPlatform;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getIdCsvImportImplementation() {
    return idCsvImportImplementation;
  }

  public void setIdCsvImportImplementation(String idCsvImportImplementation) {
    this.idCsvImportImplementation = idCsvImportImplementation;
  }

  @Override
  @JsonIgnore
  public Integer getId() {
    return this.getIdTransactionImportPlatform();
  }

}
