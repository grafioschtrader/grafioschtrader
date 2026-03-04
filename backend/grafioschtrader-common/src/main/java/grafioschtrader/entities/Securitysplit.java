package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.BaseConstants;
import grafiosch.validation.AfterEqual;
import grafioschtrader.GlobalConstants;
import grafioschtrader.types.CreateType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "A security split hat not been mapped to security because of performance reason.")
@Entity
@Table(name = Securitysplit.TABNAME)
public class Securitysplit extends DividendSplit implements Serializable {

  public static final String TABNAME = "securitysplit";

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_securitysplit")
  private Integer idSecuritysplit;

  @Schema(description = "The date of the split, on this day the split was carried out before the stock exchange opened")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Basic(optional = false)
  @NotNull
  @AfterEqual(value = GlobalConstants.OLDEST_TRADING_DAY, format = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "split_date")
  private LocalDate splitDate;

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
      @NotNull @AfterEqual(value = GlobalConstants.OLDEST_TRADING_DAY, format = "yyyy-MM-dd") LocalDate splitDate,
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

  public LocalDate getSplitDate() {
    return splitDate;
  }

  public void setSplitDate(LocalDate splitDate) {
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
  public LocalDate getEventDate() {
    return this.splitDate;
  }

  @JsonIgnore
  public double getFactor() {
    return (double) toFactor / fromFactor;
  }

  /**
   * Returns the factor for a security split, that happened after toDate.
   *
   * @param idSecuritycurrency The security
   * @param toDate             The transaction time
   * @param securitysplitMap   This map may contain the a list of splits for different securities.
   *
   * @return In a case of a split it returns a value greater > 1, for example a split of 2 returns 2. Normally the
   *         transaction units must be multiplied by this factor when the transaction time happened before the split
   *         date.
   *
   */
  public static double calcSplitFatorForFromDate(Integer idSecuritycurrency, LocalDate toDate,
      Map<Integer, List<Securitysplit>> securitysplitMap) {
    return calcSplitFatorForFromDate(securitysplitMap.get(idSecuritycurrency), toDate);
  }

  public static double calcSplitFatorForFromDate(List<Securitysplit> securitysplitList, LocalDate toDate) {
    double factor = 1;

    if (securitysplitList != null) {
      for (Securitysplit securitySplit : securitysplitList) {
        if (toDate.isBefore(securitySplit.getSplitDate())) {
          factor *= securitySplit.getFactor();
        }
      }
    }
    return factor;
  }

  /**
   * Returns the factor for a security split between a from data an to date.
   *
   * @param idSecuritycurrency the security‐currency ID whose split events to look up; should match a key in
   *                           {@code securitysplitMap}
   * @param fromDate           the lower bound (exclusive) for considering split events; must not be null
   * @param toDate             the upper bound (exclusive) for the first factor calculation; if null, all splits after
   *                           {@code fromDate} are included in {@code fromToDateFactor}
   * @param securitysplitMap   a map from security IDs to lists of {@link Securitysplit} records; may be null or contain
   *                           no entry for the given ID
   * @return In a case of a split it returns a value greater > 1, for example a split of 2 returns 2. Normally the
   *         transaction units must be multiplied by this factor when the transaction time happened before the split
   *         date.
   *
   */
  public static SplitFactorAfterBefore calcSplitFatorForFromDateAndToDate(Integer idSecuritycurrency, LocalDate fromDate,
      LocalDate toDate, Map<Integer, List<Securitysplit>> securitysplitMap) {
    SplitFactorAfterBefore splitFactorAfterBefore = new SplitFactorAfterBefore();
    List<Securitysplit> securitysplitList = securitysplitMap.get(idSecuritycurrency);

    if (securitysplitList != null) {
      for (Securitysplit securitySplit : securitysplitList) {
        if (securitySplit.getSplitDate().isAfter(fromDate)) {
          if (toDate == null || securitySplit.getSplitDate().isBefore(toDate)) {
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
