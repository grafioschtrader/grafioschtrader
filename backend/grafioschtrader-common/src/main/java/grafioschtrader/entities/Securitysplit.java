package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.GlobalConstants;
import grafioschtrader.types.CreateType;
import grafioschtrader.validation.AfterEqual;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * A security split hat not been mapped to security because of performance
 * reason.
 *
 * @author Hugo Graf
 *
 */
@Entity
@Table(name = Securitysplit.TABNAME)
@Schema(description = "Defines the split for a security")
public class Securitysplit extends DividendSplit implements Serializable {

  public static final String TABNAME = "securitysplit";

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_securitysplit")
  private Integer idSecuritysplit;

  @Schema(description = "The date of the split, on this day the split was carried out before the stock exchange opened")
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  @Basic(optional = false)
  @NotNull
  @AfterEqual(value = GlobalConstants.OLDEST_TRADING_DAY, format = GlobalConstants.STANDARD_DATE_FORMAT)
  @Column(name = "split_date")
  @Temporal(TemporalType.DATE)
  private Date splitDate;

  @Schema(description = "From factor")
  @Basic(optional = false)
  @Column(name = "from_factor")
  @NotNull
  @Min(value = 1)
  @Max(value = 99_999_999)
  private Integer fromFactor;

  @Schema(description = "To factor")
  @Basic(optional = false)
  @Column(name = "to_factor")
  @NotNull
  @Min(value = 1)
  @Max(value = 99_999_999)
  private Integer toFactor;

  public Securitysplit() {
  }

  public Securitysplit(Integer idSecuritycurrency,
      @NotNull @AfterEqual(value = GlobalConstants.OLDEST_TRADING_DAY, format = "yyyy-MM-dd") Date splitDate,
      @NotNull @Min(1) @Max(99_999) Integer fromFactor, @NotNull @Min(1) @Max(99_999) Integer toFactor,
      CreateType createType) {
    super(idSecuritycurrency, createType);
    this.splitDate = splitDate;
    this.fromFactor = fromFactor;
    this.toFactor = toFactor;
  }

  public Integer getIdSecuritysplit() {
    return idSecuritysplit;
  }

  public void setIdSecuritysplit(Integer idSecuritysplit) {
    this.idSecuritysplit = idSecuritysplit;
  }

  public Date getSplitDate() {
    return splitDate;
  }

  public void setSplitDate(Date splitDate) {
    this.splitDate = splitDate;
  }

  public Integer getFromFactor() {
    return fromFactor;
  }

  public void setFromFactor(Integer fromFactor) {
    this.fromFactor = fromFactor;
  }

  public Integer getToFactor() {
    return toFactor;
  }

  public void setToFactor(Integer toFactor) {
    this.toFactor = toFactor;
  }

  @Override
  public Date getEventDate() {
    return this.splitDate;
  }

  public static double calcSplitFatorForFromDate(Integer idSecuritycurrency, LocalDate toDate,
      Map<Integer, List<Securitysplit>> securitysplitMap) {
    return calcSplitFatorForFromDate(idSecuritycurrency,
        Date.from(toDate.atStartOfDay(ZoneId.systemDefault()).toInstant()), securitysplitMap);
  }

  @JsonIgnore
  public double getFactor() {
    return (double) toFactor / fromFactor;
  }

  /**
   * Returns the factor for a security split, that happened after toDate.
   *
   * @param security         The security
   * @param toDate           The transaction time
   * @param securitysplitMap This map may contain the a list of splits for
   *                         different securities.
   *
   * @return In a case of a split it returns a value greater > 1, for example a
   *         split of 2 returns 2. Normally the transaction units must be
   *         multiplied by this factor when the transaction time happened before
   *         the split date.
   *
   */
  public static double calcSplitFatorForFromDate(Integer idSecuritycurrency, Date toDate,
      Map<Integer, List<Securitysplit>> securitysplitMap) {
    return calcSplitFatorForFromDate(securitysplitMap.get(idSecuritycurrency), toDate);
  }

  public static double calcSplitFatorForFromDate(List<Securitysplit> securitysplitList, Date toDate) {
    double factor = 1;

    if (securitysplitList != null) {
      for (Securitysplit securitySplit : securitysplitList) {
        if (toDate.before(securitySplit.getSplitDate())) {
          factor *= securitySplit.getFactor();
        }
      }
    }
    return factor;
  }

  /**
   * Returns the factor for a security split between a from data an to date.
   *
   * @param idSecuritycurrency
   * @param fromDate
   * @param toDate
   * @param securitysplitMap
   * @return In a case of a split it returns a value greater > 1, for example a
   *         split of 2 returns 2. Normally the transaction units must be
   *         multiplied by this factor when the transaction time happened before
   *         the split date.
   *
   */
  public static SplitFactorAfterBefore calcSplitFatorForFromDateAndToDate(Integer idSecuritycurrency, Date fromDate,
      Date toDate, Map<Integer, List<Securitysplit>> securitysplitMap) {
    SplitFactorAfterBefore splitFactorAfterBefore = new SplitFactorAfterBefore();
    List<Securitysplit> securitysplitList = securitysplitMap.get(idSecuritycurrency);

    if (securitysplitList != null) {
      for (Securitysplit securitySplit : securitysplitList) {
        if (securitySplit.getSplitDate().after(fromDate)) {
          if (toDate == null || securitySplit.getSplitDate().before(toDate)) {
            splitFactorAfterBefore.fromToDateFactor *= securitySplit.getFactor();
          } else {
            splitFactorAfterBefore.toDateUntilNow *= securitySplit.getFactor();
          }
        }
      }
    }
    return splitFactorAfterBefore;
  }

  @Override
  public Integer getId() {
    return idSecuritysplit;
  }

  @Override
  public String toString() {
    return "Securitysplit [idSecuritysplit=" + idSecuritysplit + ", splitDate=" + splitDate + ", fromFactor="
        + fromFactor + ", toFactor=" + toFactor + ", idSecuritycurrency=" + idSecuritycurrency + ", createType="
        + createType + "]";
  }

  public static class SplitFactorAfterBefore {
    public double fromToDateFactor = 1.0;
    public double toDateUntilNow = 1.0;
  }

}
