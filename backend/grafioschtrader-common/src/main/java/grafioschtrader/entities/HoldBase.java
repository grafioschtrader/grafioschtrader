package grafioschtrader.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class HoldBase {

  @Column(name = "id_tenant")
  protected Integer idTenant;

  @Column(name = "id_portfolio")
  protected Integer idPortfolio;

  @Column(name = "to_hold_date")
  protected LocalDate toHoldDate;

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
