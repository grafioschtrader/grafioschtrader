package grafioschtrader.entities;

import static jakarta.persistence.InheritanceType.JOINED;

import java.io.Serializable;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import grafiosch.BaseConstants;
import grafiosch.common.LockedWhenUsed;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.entities.TenantBaseID;
import grafioschtrader.types.PeriodDayPosition;
import grafioschtrader.types.RepeatUnit;
import grafioschtrader.types.TransactionType;
import grafioschtrader.types.WeekendAdjustType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Abstract base class for standing orders (recurring transactions). Uses JOINED inheritance with two concrete
 * subclasses: {@link StandingOrderCashaccount} for WITHDRAWAL/DEPOSIT and {@link StandingOrderSecurity} for
 * ACCUMULATE/REDUCE. Common scheduling fields (repeat period, day positioning, weekend adjustment, validity range)
 * are stored in this base table.
 */
@Schema(description = """
    Abstract base for standing orders (recurring transactions). Contains common scheduling fields shared by
    cash-account and security standing orders. Discriminated by dtype: 'C' for cash, 'S' for security.""")
@Entity
@Table(name = StandingOrder.TABNAME)
@Inheritance(strategy = JOINED)
@DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.STRING)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "dtype")
@JsonSubTypes({
    @JsonSubTypes.Type(value = StandingOrderCashaccount.class, name = "C"),
    @JsonSubTypes.Type(value = StandingOrderSecurity.class, name = "S")
})
public abstract class StandingOrder extends TenantBaseID implements Serializable {

  public static final String TABNAME = "standing_order";

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_standing_order")
  private Integer idStandingOrder;

  @JsonIgnore
  @Column(name = "id_tenant")
  private Integer idTenant;

  @Schema(description = "Transaction type: 0=WITHDRAWAL, 1=DEPOSIT (cash), 4=ACCUMULATE, 5=REDUCE (security)")
  @Column(name = "transaction_type")
  @NotNull
  private byte transactionType;

  @Schema(description = "Cash account that will be debited or credited by the generated transactions")
  @JoinColumn(name = "id_cash_account", referencedColumnName = "id_securitycash_account")
  @ManyToOne
  @NotNull
  @LockedWhenUsed
  private Cashaccount cashaccount;

  @Column(name = "note")
  @Size(max = 500)
  @PropertyAlwaysUpdatable
  private String note;

  @Schema(description = "Time unit for the repeat interval: 0=DAYS, 1=MONTHS, 2=YEARS")
  @Column(name = "repeat_unit")
  @NotNull
  @PropertyAlwaysUpdatable
  private byte repeatUnit;

  @Schema(description = "Number of repeat units between executions, e.g. 1 for monthly, 3 for quarterly")
  @Column(name = "repeat_interval")
  @NotNull
  @Min(1)
  @PropertyAlwaysUpdatable
  private short repeatInterval;

  @Schema(description = """
      Day of the month (1-28) when periodDayPosition is SPECIFIC_DAY and repeatUnit is MONTHS or YEARS.
      Null when repeatUnit is DAYS, or when periodDayPosition is FIRST_DAY/LAST_DAY (day is implicit).""")
  @Column(name = "day_of_execution")
  @Min(1)
  @Max(28)
  @PropertyAlwaysUpdatable
  private Byte dayOfExecution;

  @Schema(description = "Month of execution (1-12), only relevant when repeatUnit is YEARS. Null for DAYS/MONTHS.")
  @Column(name = "month_of_execution")
  @Min(1)
  @Max(12)
  @PropertyAlwaysUpdatable
  private Byte monthOfExecution;

  @Schema(description = """
      Day positioning within period: 0=SPECIFIC_DAY (use dayOfExecution), 1=FIRST_DAY, 2=LAST_DAY.
      Only relevant when repeatUnit is MONTHS or YEARS; ignored for DAYS-based intervals.""")
  @Column(name = "period_day_position")
  @NotNull
  @PropertyAlwaysUpdatable
  private byte periodDayPosition;

  @Schema(description = "Weekend adjustment: 0=shift BEFORE (Friday), 1=shift AFTER (Monday)")
  @Column(name = "weekend_adjust")
  @NotNull
  @PropertyAlwaysUpdatable
  private byte weekendAdjust;

  @Schema(description = "Start date of the standing order's active period (inclusive)")
  @Column(name = "valid_from")
  @NotNull
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @LockedWhenUsed
  private LocalDate validFrom;

  @Schema(description = "End date of the standing order's active period (inclusive)")
  @Column(name = "valid_to")
  @NotNull
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @PropertyAlwaysUpdatable
  private LocalDate validTo;

  @Schema(description = "Date of the most recent transaction creation, null if never executed")
  @Column(name = "last_execution_date")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @PropertyAlwaysUpdatable
  private LocalDate lastExecutionDate;

  @Schema(description = "Next scheduled execution date, null when deactivated or past validTo")
  @Column(name = "next_execution_date")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @PropertyAlwaysUpdatable
  private LocalDate nextExecutionDate;

  @Schema(description = "Fixed transaction cost per execution (optional)")
  @LockedWhenUsed
  @Column(name = "transaction_cost")
  private Double transactionCost;

  @Schema(description = "Whether this standing order has already created transactions (transient, not persisted)")
  @Transient
  private boolean hasTransactions;

  @Schema(description = "Number of successful transactions created by this standing order (transient, not persisted)")
  @Transient
  private int transactionCount;

  @Schema(description = "Number of persisted execution failures for this standing order (transient, not persisted)")
  @Transient
  private int failureCount;

  @JsonIgnore
  @Override
  public Integer getId() {
    return idStandingOrder;
  }

  public Integer getIdStandingOrder() {
    return idStandingOrder;
  }

  public void setIdStandingOrder(Integer idStandingOrder) {
    this.idStandingOrder = idStandingOrder;
  }

  @Override
  public Integer getIdTenant() {
    return idTenant;
  }

  @Override
  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public TransactionType getTransactionType() {
    return TransactionType.getTransactionTypeByValue(transactionType);
  }

  public void setTransactionType(TransactionType transactionType) {
    if (transactionType != null) {
      this.transactionType = transactionType.getValue();
    }
  }

  public Cashaccount getCashaccount() {
    return cashaccount;
  }

  public void setCashaccount(Cashaccount cashaccount) {
    this.cashaccount = cashaccount;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public RepeatUnit getRepeatUnit() {
    return RepeatUnit.getByValue(repeatUnit);
  }

  public void setRepeatUnit(RepeatUnit repeatUnit) {
    if (repeatUnit != null) {
      this.repeatUnit = repeatUnit.getValue();
    }
  }

  public short getRepeatInterval() {
    return repeatInterval;
  }

  public void setRepeatInterval(short repeatInterval) {
    this.repeatInterval = repeatInterval;
  }

  public Byte getDayOfExecution() {
    return dayOfExecution;
  }

  public void setDayOfExecution(Byte dayOfExecution) {
    this.dayOfExecution = dayOfExecution;
  }

  public Byte getMonthOfExecution() {
    return monthOfExecution;
  }

  public void setMonthOfExecution(Byte monthOfExecution) {
    this.monthOfExecution = monthOfExecution;
  }

  public PeriodDayPosition getPeriodDayPosition() {
    return PeriodDayPosition.getByValue(periodDayPosition);
  }

  public void setPeriodDayPosition(PeriodDayPosition periodDayPosition) {
    if (periodDayPosition != null) {
      this.periodDayPosition = periodDayPosition.getValue();
    }
  }

  public WeekendAdjustType getWeekendAdjust() {
    return WeekendAdjustType.getByValue(weekendAdjust);
  }

  public void setWeekendAdjust(WeekendAdjustType weekendAdjust) {
    if (weekendAdjust != null) {
      this.weekendAdjust = weekendAdjust.getValue();
    }
  }

  public LocalDate getValidFrom() {
    return validFrom;
  }

  public void setValidFrom(LocalDate validFrom) {
    this.validFrom = validFrom;
  }

  public LocalDate getValidTo() {
    return validTo;
  }

  public void setValidTo(LocalDate validTo) {
    this.validTo = validTo;
  }

  public LocalDate getLastExecutionDate() {
    return lastExecutionDate;
  }

  public void setLastExecutionDate(LocalDate lastExecutionDate) {
    this.lastExecutionDate = lastExecutionDate;
  }

  public LocalDate getNextExecutionDate() {
    return nextExecutionDate;
  }

  public void setNextExecutionDate(LocalDate nextExecutionDate) {
    this.nextExecutionDate = nextExecutionDate;
  }

  public Double getTransactionCost() {
    return transactionCost;
  }

  public void setTransactionCost(Double transactionCost) {
    this.transactionCost = transactionCost;
  }

  public boolean isHasTransactions() {
    return hasTransactions;
  }

  public void setHasTransactions(boolean hasTransactions) {
    this.hasTransactions = hasTransactions;
  }

  public int getTransactionCount() {
    return transactionCount;
  }

  public void setTransactionCount(int transactionCount) {
    this.transactionCount = transactionCount;
  }

  public int getFailureCount() {
    return failureCount;
  }

  public void setFailureCount(int failureCount) {
    this.failureCount = failureCount;
  }

  @Override
  public String toString() {
    return "StandingOrder [idStandingOrder=" + idStandingOrder + ", transactionType=" + transactionType
        + ", repeatUnit=" + repeatUnit + ", repeatInterval=" + repeatInterval + ", validFrom=" + validFrom
        + ", validTo=" + validTo + ", nextExecutionDate=" + nextExecutionDate + "]";
  }
}
