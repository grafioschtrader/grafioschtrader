package grafioschtrader.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Per tenant marker of how far the rows of {@link HoldDailyTotal} can be trusted.
 *
 * <p>
 * Every row before {@code recalcFromDate} is up to date; from that day on rows may be missing or outdated. A booking
 * lowers the marker to the first day it affects, the task {@code HOLD_DAILY_TOTAL_UPDATE} recomputes the rows from the
 * marker on and moves it behind the last day it could value. A tenant without a row has never been computed and is
 * computed from its first day; deleting the row is therefore the way to have a tenant recomputed in full, for example
 * after a rebuild of its hold tables.
 * </p>
 *
 * <p>
 * Derived data like {@link HoldDailyTotal}: not exported, so that an imported tenant starts without a row, and removed
 * with the tenant through the foreign key cascade. No entity limit applies, because nothing but the task and the
 * internal markers write it.
 * </p>
 */
@Entity
@Table(name = HoldDailyTotalState.TABNAME)
public class HoldDailyTotalState {

  public static final String TABNAME = "hold_daily_total_state";

  @Id
  @Column(name = "id_tenant")
  private Integer idTenant;

  /** The first day whose rows are missing or outdated. */
  @Column(name = "recalc_from_date")
  private LocalDate recalcFromDate;

  /**
   * When the task last scanned {@code historyquote} for quotes created or changed after the previous scan, in UTC. Null
   * until the first scan.
   */
  @Column(name = "quote_checked_time")
  private LocalDateTime quoteCheckedTime;

  public HoldDailyTotalState() {
  }

  public HoldDailyTotalState(Integer idTenant, LocalDate recalcFromDate) {
    this.idTenant = idTenant;
    this.recalcFromDate = recalcFromDate;
  }

  public Integer getIdTenant() {
    return idTenant;
  }

  public LocalDate getRecalcFromDate() {
    return recalcFromDate;
  }

  public void setRecalcFromDate(LocalDate recalcFromDate) {
    this.recalcFromDate = recalcFromDate;
  }

  public LocalDateTime getQuoteCheckedTime() {
    return quoteCheckedTime;
  }

  public void setQuoteCheckedTime(LocalDateTime quoteCheckedTime) {
    this.quoteCheckedTime = quoteCheckedTime;
  }

}
