package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.entities.BaseID;
import grafioschtrader.types.HistoryquotePeriodCreateType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Schema(description = """
    There is no price data for certain securities. GT needs prices for all
    securities to calculate performance. These prices can therefore be entered
    manually. Initial prices are entered by the system, for example for a
    fixed-term deposit that does not change its price.""")
@Entity
@Table(name = HistoryquotePeriod.TABNAME)
public class HistoryquotePeriod extends BaseID<Integer> implements Serializable {

  public static final String TABNAME = "historyquote_period";

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_historyquote_period")
  private Integer idHistoryquotePeriod;

  @Schema(description = "ID Security")
  @Column(name = "id_securitycurrency")
  private Integer idSecuritycurrency;

  @Schema(description = "Start date of the price period")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Basic(optional = false)
  @Column(name = "from_date")
  @PropertyAlwaysUpdatable
  private LocalDate fromDate;

  @Schema(description = "End date of the price period. For the most recent period, this date can also be NULL.")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "to_date")
  @PropertyAlwaysUpdatable
  private LocalDate toDate;

  @Schema(description = "Price for the period")
  @Column(name = "price")
  @PropertyAlwaysUpdatable
  private double price;

  @Schema(description = "Enum is used. 0: System created, 1: User created")
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
