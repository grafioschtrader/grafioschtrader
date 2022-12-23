package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.PropertyAlwaysUpdatable;
import grafioschtrader.types.HistoryquotePeriodCreateType;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = HistoryquotePeriod.TABNAME)
public class HistoryquotePeriod extends BaseID implements Serializable {

  public static final String TABNAME = "historyquote_period";

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_historyquote_period")
  private Integer idHistoryquotePeriod;

  @Column(name = "id_securitycurrency")
  private Integer idSecuritycurrency;

  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  @Basic(optional = false)
  @Column(name = "from_date")
  @PropertyAlwaysUpdatable
  private LocalDate fromDate;

  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  @Column(name = "to_date")
  @PropertyAlwaysUpdatable
  private LocalDate toDate;

  @Column(name = "price")
  @PropertyAlwaysUpdatable
  private double price;

  @Column(name = "create_type")
  private byte createType;

  public HistoryquotePeriod() {
  }

  public HistoryquotePeriod(Integer idSecuritycurrency, LocalDate fromDate, LocalDate toDate, double price) {
    this.idSecuritycurrency = idSecuritycurrency;
    this.fromDate = fromDate;
    this.toDate = toDate;
    this.price = price;
    this.createType = HistoryquotePeriodCreateType.SYSTEM_CREATED.getValue();
  }

  public Integer getIdHistoryquotePeriod() {
    return idHistoryquotePeriod;
  }

  public void setIdHistoryquotePeriod(Integer idHistoryquotePeriod) {
    this.idHistoryquotePeriod = idHistoryquotePeriod;
  }

  public Integer getIdSecuritycurrency() {
    return idSecuritycurrency;
  }

  public void setIdSecuritycurrency(Integer idSecuritycurrency) {
    this.idSecuritycurrency = idSecuritycurrency;
  }

  public LocalDate getFromDate() {
    return fromDate;
  }

  public void setFromDate(LocalDate fromDate) {
    this.fromDate = fromDate;
  }

  public LocalDate getToDate() {
    return toDate;
  }

  public void setToDate(LocalDate toDate) {
    this.toDate = toDate;
  }

  public double getPrice() {
    return price;
  }

  public void setPrice(double price) {
    this.price = price;
  }

  public HistoryquotePeriodCreateType getHistoryquotePeriodCreateType() {
    return HistoryquotePeriodCreateType.getHistoryquoteCreateType(createType);
  }

  public void setCreateType(HistoryquotePeriodCreateType historyquotePeriodCreateType) {
    this.createType = historyquotePeriodCreateType.getValue();
  }

  @Override
  public Integer getId() {
    return this.getIdHistoryquotePeriod();
  }

}
