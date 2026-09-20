package grafioschtrader.entities;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafiosch.common.DynamicFormField;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.entities.Auditable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Marks an instrument whose issuer no longer supplies price data, so that the end of day task keeps its history
 * complete instead of leaving gaps that stop every report of everyone holding it.
 *
 * <p>
 * One missing closing price is not a local defect. The period performance report drops a whole day from its result as
 * soon as a single held instrument has no quote for it, that day is then reported as a day with missing quotes, and
 * once the gap reaches the present there is no usable trading day left at all. A bond of a bankrupt issuer, which is
 * quoted sporadically or not at all while remaining far from its maturity, is exactly the case that produces this.
 * </p>
 *
 * <p>
 * A row here is a statement about the instrument, not about a client, so the table is shared reference data and one
 * instrument may appear once. The instrument keeps its connector: whenever the issuer does deliver a price again, that
 * real quote is written as usual and the filling only covers the days around it.
 * </p>
 *
 * <p>
 * The Integer surrogate primary key is required by the CRUD infrastructure ({@code Auditable} / {@code BaseID<Integer>}
 * ); the functional key is {@code idSecuritycurrency}, enforced by a UNIQUE index in the schema.
 * </p>
 */
@Schema(description = """
    Marks an instrument that no longer receives price data, so the end of day task fills its missing trading days from
    the last known price instead of leaving gaps that break the performance reports of every client holding it.""")
@Entity
@Table(name = BankruptSecurity.TABNAME)
public class BankruptSecurity extends Auditable {

  private static final long serialVersionUID = 1L;

  public static final String TABNAME = "bankrupt_security";

  @Schema(description = "Integer surrogate primary key.")
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_bankrupt_security")
  private Integer idBankruptSecurity;

  @Schema(description = "Instrument that no longer receives price data. Unique across the table.")
  @Basic(optional = false)
  @NotNull
  @Column(name = "id_securitycurrency")
  private Integer idSecuritycurrency;

  @Schema(description = """
      Day from which the issuer stopped delivering prices. Documentation for the next reader only, the filling always
      starts at the last closing price actually present.""")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "no_data_since")
  @DynamicFormField(uiOrder = "1.1")
  @PropertyAlwaysUpdatable
  private LocalDate noDataSince;

  @Schema(description = "Why this instrument was marked, for the next person who wonders where its prices come from.")
  @Size(max = 120)
  @Column(name = "note", length = 120)
  @DynamicFormField(uiOrder = "1.2")
  @PropertyAlwaysUpdatable
  private String note;

  public BankruptSecurity() {
  }

  public BankruptSecurity(Integer idSecuritycurrency, LocalDate noDataSince, String note) {
    this.idSecuritycurrency = idSecuritycurrency;
    this.noDataSince = noDataSince;
    this.note = note;
  }

  @Override
  public Integer getId() {
    return idBankruptSecurity;
  }

  public Integer getIdBankruptSecurity() {
    return idBankruptSecurity;
  }

  public void setIdBankruptSecurity(Integer idBankruptSecurity) {
    this.idBankruptSecurity = idBankruptSecurity;
  }

  public Integer getIdSecuritycurrency() {
    return idSecuritycurrency;
  }

  public void setIdSecuritycurrency(Integer idSecuritycurrency) {
    this.idSecuritycurrency = idSecuritycurrency;
  }

  public LocalDate getNoDataSince() {
    return noDataSince;
  }

  public void setNoDataSince(LocalDate noDataSince) {
    this.noDataSince = noDataSince;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }
}
