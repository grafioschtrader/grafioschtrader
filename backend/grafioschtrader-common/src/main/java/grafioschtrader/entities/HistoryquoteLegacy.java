package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Shadow archive of a security's end-of-day prices, preserved when its feed connector or {@code url_history_extend}
 * changes so that history the new connector can no longer supply is not lost. Rows are populated automatically on a
 * connector change and via CSV import; they are consulted on every subsequent reload to supplement the live
 * {@link Historyquote} table with dates the new connector does not cover.
 *
 * <p>
 * Unlike the live table, the archive carries a {@code transfer_date} (the archival batch boundary, used to compute the
 * post-archival split factor at supplement time). The trading {@code date} is unique per security. Editing and deleting
 * individual archived rows follows the same propose-change approval flow as {@link Historyquote} (see
 * {@link BaseHistoryquote}); new rows are not created one-by-one through the UI.
 * </p>
 */
@Schema(description = "A single archived end-of-day quote in the historyquote_legacy shadow table")
@Entity
@Table(name = HistoryquoteLegacy.TABNAME)
public class HistoryquoteLegacy extends BaseHistoryquote implements Serializable {

  public static final String TABNAME = "historyquote_legacy";

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_historyquote_legacy")
  private Integer idHistoryquoteLegacy;

  @Schema(description = "Archival batch date: when these prices were copied into the shadow archive. Managed by the "
      + "system and not user-editable; it determines the post-archival split factor applied at supplement time.")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "transfer_date")
  private LocalDate transferDate;

  public HistoryquoteLegacy() {
  }

  @Override
  public Integer getId() {
    return this.idHistoryquoteLegacy;
  }

  public Integer getIdHistoryquoteLegacy() {
    return idHistoryquoteLegacy;
  }

  public void setIdHistoryquoteLegacy(Integer idHistoryquoteLegacy) {
    this.idHistoryquoteLegacy = idHistoryquoteLegacy;
  }

  public LocalDate getTransferDate() {
    return transferDate;
  }

  public void setTransferDate(LocalDate transferDate) {
    this.transferDate = transferDate;
  }

  @Override
  public String toString() {
    return "HistoryquoteLegacy [idHistoryquoteLegacy=" + idHistoryquoteLegacy + ", transferDate=" + transferDate
        + ", date=" + date + ", close=" + close + ", volume=" + volume + ", open=" + open + ", high=" + high + ", low="
        + low + ", idSecuritycurrency=" + idSecuritycurrency + "]";
  }

}
