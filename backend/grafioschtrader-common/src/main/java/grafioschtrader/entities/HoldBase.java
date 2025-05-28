package grafioschtrader.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * In this time series analysis, the two dates represent a period with from and to. A new period is added to the
 * previous one with the date minus 1 day. Such periods can be analyzed efficiently using SQL queries. The start date of
 * a period will be part of a key in the derived class.
 */
@MappedSuperclass
public abstract class HoldBase {

  @Column(name = "id_tenant")
  protected Integer idTenant;

  /**
   * The evaluation can be carried out both at portfolio level and for all portfolios for a tenant.
   */
  @Column(name = "id_portfolio")
  protected Integer idPortfolio;

  /**
   * The end date of the time period or null if the time period is still open.
   */
  @Column(name = "to_hold_date")
  protected LocalDate toHoldDate;

  /**
   * With these time stamps it is possible to validate that a data record is up to date. There may be a dependency on
   * historical price data, so the up-to-dateness can be verified with a date comparison.
   */
  @Column(name = "valid_timestamp")
  protected LocalDateTime validTimestamp;

  public HoldBase() {
  }

  public HoldBase(Integer idTenant, Integer idPortfolio) {
    this(idTenant, idPortfolio, null);
  }

  public HoldBase(Integer idTenant, Integer idPortfolio, LocalDate toHoldDate) {
    this.idTenant = idTenant;
    this.idPortfolio = idPortfolio;
    this.toHoldDate = toHoldDate;
    this.validTimestamp = LocalDateTime.now();
  }

  public LocalDate getToHoldDate() {
    return toHoldDate;
  }

  public void setToHoldDate(LocalDate toHoldDate) {
    this.toHoldDate = toHoldDate;
  }

  public Integer getIdTenant() {
    return idTenant;
  }

  public Integer getIdPortfolio() {
    return idPortfolio;
  }

  @Override
  public String toString() {
    return "HoldBase [idTenant=" + idTenant + ", idPortfolio=" + idPortfolio + ", toHoldDate=" + toHoldDate + "]";
  }

}
