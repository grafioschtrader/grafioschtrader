package grafioschtrader.reports.udfalluserfields;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import grafiosch.common.DataHelper;
import grafiosch.types.IUDFSpecialType;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup;
import grafioschtrader.types.UDFSpecialGTType;

/**
 * Service class for calculating Yield to Maturity (YTM) for fixed-income securities as a user-defined field. This class
 * extends AllUserFieldsSecurity and implements IUDFForEveryUser to provide automatic YTM calculations for bonds and
 * other interest-bearing securities across all users in the system.
 * 
 * The calculator implements a comprehensive bond pricing model based on financial mathematics, using iterative
 * numerical methods to solve for yield when given price, coupon rate, maturity date, and other bond characteristics.
 * The implementation follows standard financial formulas and day count conventions used in bond markets.
 * 
 * Key features include:<br>
 * - Automatic coupon rate extraction from security names using regex patterns<br>
 * - Support for various payment frequencies (annual, semi-annual, quarterly, monthly)<br>
 * - Multiple day count basis conventions (30/360, Actual/Actual, Actual/360, etc.)<br>
 * - Iterative yield calculation using numerical methods with convergence tolerance<br>
 * - Asset class filtering to apply calculations only to appropriate fixed-income securities<br>
 * - Integration with the UDF system for persistent storage and display<br>
 * 
 * The calculator only processes securities that:<br>
 * - Match the configured asset class and investment instrument criteria<br>
 * - Have active maturity dates in the future<br>
 * - Have valid distribution frequencies (1-12 payments per year)<br>
 * - Have extractable coupon rates from their names<br>
 * 
 * The implementation is based on standard bond pricing formulas and follows algorithms similar to those used in Apache
 * OpenOffice Calc for financial calculations.
 */
@Service
public class YieldToMaturityCalculator extends AllUserFieldsSecurity implements IUDFForEveryUser {

  /**
   * Calculates and sets Yield to Maturity for all applicable securities in the provided group. This method filters
   * securities based on asset class compatibility, active status, and valid distribution frequencies, then attempts to
   * extract coupon rates from security names and calculate YTM using current market prices.
   * 
   * The processing workflow includes:<br>
   * 1. Filtering securities by asset class matching and active maturity dates<br>
   * 2. Validating distribution frequency is within acceptable range (1-12 per year)<br>
   * 3. Extracting annual coupon rate from security name using regex pattern matching<br>
   * 4. Calculating YTM using settlement date (current date), maturity date, coupon rate, and current price<br>
   * 5. Storing the calculated YTM value in the UDF system with appropriate precision<br>
   * 
   * Only securities with extractable numeric coupon rates from their names are processed, ensuring data quality and
   * calculation accuracy.
   * 
   * @param securitycurrencyUDFGroup the group containing securities and UDF data context for processing
   * @param recreate                 if true, forces recalculation even if values already exist (currently not used in
   *                                 filtering)
   */
  @Override
  public void addUDFForEveryUser(SecuritycurrencyUDFGroup securitycurrencyUDFGroup, boolean recreate) {
    UDFMetadataSecurity udfYTM = getMetadataSecurity(getUDFSpecialType());
    Pattern numberStartTextRegex = Pattern.compile("^[0-9]{0,8}([,|.][0-9]{0,4})?");
    LocalDate now = LocalDate.now();
    securitycurrencyUDFGroup.securityPositionList.stream()
        .filter(s -> matchAssetclassAndSpecialInvestmentInstruments(udfYTM, s.securitycurrency.getAssetClass())
            && ((java.sql.Date) s.securitycurrency.getActiveToDate()).toLocalDate().isAfter(now)
            && s.securitycurrency.getDistributionFrequency().getValue() > 0
            && s.securitycurrency.getDistributionFrequency().getValue() <= 12)
        .forEach(s -> {
          calcAndSetYTM(securitycurrencyUDFGroup, udfYTM, s.securitycurrency, numberStartTextRegex, now);
        });
  }

  @Override
  public IUDFSpecialType getUDFSpecialType() {
    return UDFSpecialGTType.UDF_SPEC_INTERNAL_CALC_YIELD_TO_MATURITY;
  }

  @Override
  public boolean mayRunInBackground() {
    return false;
  }

  /**
   * Calculates the yield to maturity for a bond using iterative numerical methods. This method implements the standard
   * bond yield calculation by iteratively solving for the discount rate that makes the present value of all future cash
   * flows equal to the current bond price.
   * 
   * The algorithm uses a binary search approach with linear interpolation to converge on the yield value, with a
   * maximum of 100 iterations for performance and stability. The method handles edge cases and provides robust
   * convergence even for bonds with unusual characteristics.
   * 
   * @param settlementDate   The settlement date of the security, i.e. the date on which the bond financing arrangement
   *                         begins.
   * @param maturityDate     The maturity date of the security is the date on which the agreement between the borrower
   *                         and lender is expected to end.
   * @param annualCouponRate Annual Coupon Rate (%)
   * @param price            Bond Quote (% of Par)
   * @param redemption       The redemption value, i.e. assuming either prepayment or payment at the originally stated
   *                         maturity, of the security per the par value (”100”).
   * @param frequency        The number of coupon payments issued per year. e.g. annual, semi-annual, or quarterly.
   * @param basisThe         day count basis as stated in the lending agreement.
   *                         <ul>
   *                         <li>0 => 30 / 360 (Omitted)</li>
   *                         <li>1 => Actual / Actual</li>
   *                         <li>2 => Actual / 360</li>
   *                         <li>3 => Actual / 365</li>
   *                         <li>4 => European 30 / 360</li>
   *                         </ul>
   *
   *                         see <a href=
   *                         "https://github.com/apache/openoffice/blob/c014b5f2b55cff8d4b0c952d5c16d62ecde09ca1/
   *                         main/scaddins/source/analysis/analysishelper.cxx#L1085"> analysishelper.cxx line 1085 </a>
   */
  private double yieldToMaturity(LocalDate settlementDate, LocalDate maturityDate, double annualCouponRate,
      double price, double redemption, int frequency, int basis) {

    double fRate = annualCouponRate;
    double fPriceN = 0.0;
    double fYield1 = 0.0;
    double fYield2 = 1.0;
    double fPrice1 = price(settlementDate, maturityDate, fRate, fYield1, redemption, frequency, basis);
    double fPrice2 = price(settlementDate, maturityDate, fRate, fYield2, redemption, frequency, basis);
    double fYieldN = (fYield2 - fYield1) * 0.5;

    for (int nIter = 0; nIter < 100 && fPriceN != price; nIter++) {
      fPriceN = price(settlementDate, maturityDate, fRate, fYieldN, redemption, frequency, basis);

      if (price == fPrice1) {
        return fYield1;
      } else if (price == fPrice2) {
        return fYield2;
      } else if (price == fPriceN) {
        return fYieldN;
      } else if (price < fPrice2) {
        fYield2 *= 2.0;
        fPrice2 = price(settlementDate, maturityDate, fRate, fYield2, redemption, frequency, basis);
        fYieldN = (fYield2 - fYield1) * 0.5;
      } else {
        if (price < fPriceN) {
          fYield1 = fYieldN;
          fPrice1 = fPriceN;
        } else {
          fYield2 = fYieldN;
          fPrice2 = fPriceN;
        }

        fYieldN = fYield2 - (fYield2 - fYield1) * ((price - fPrice2) / (fPrice1 - fPrice2));
      }
    }
    return fYieldN;
  }

  /**
   * Calculates the theoretical price of a bond given its yield and other characteristics. This method implements the
   * standard bond pricing formula by calculating the present value of all future coupon payments plus the present value
   * of the principal repayment at maturity.
   * 
   * The calculation accounts for accrued interest and fractional coupon periods, providing accurate pricing for bonds
   * purchased between coupon payment dates.
   * 
   * Implementation reference: Apache OpenOffice analysishelper.cxx line 1137
   * 
   * @param settlementDate   the settlement date for the bond purchase
   * @param maturityDate     the maturity date of the bond
   * @param annualCouponRate the annual coupon rate as a decimal
   * @param annualYield      the annual yield used for discounting cash flows
   * @param redemption       the redemption value at maturity
   * @param frequency        the number of coupon payments per year
   * @param basis            the day count basis convention
   * @return the calculated bond price as a percentage of par value
   */
  private double price(LocalDate settlementDate, LocalDate maturityDate, double annualCouponRate, double annualYield,
      double redemption, int frequency, int basis) {
    double coupDays = getCoupdays(settlementDate, maturityDate, frequency, basis);
    double fDSC_E = getCoupdaysnc(settlementDate, maturityDate, frequency, basis) / coupDays;
    double fN = getCoupnum(settlementDate, maturityDate, frequency, basis);
    double fA = getCoupdaybs(settlementDate, maturityDate, frequency, basis);

    double fRet = redemption / Math.pow(1.0 + annualYield / frequency, fN - 1.0 + fDSC_E);
    fRet -= 100.0 * annualCouponRate / frequency * fA / coupDays;
    double fT1 = 100.0 * annualCouponRate / frequency;
    double fT2 = 1.0 + annualYield / frequency;
    for (int fK = 0; fK < fN; fK++) {
      fRet += fT1 / Math.pow(fT2, fK + fDSC_E);
    }
    return fRet;
  }

  /**
   * Calculates the number of coupon payments between settlement and maturity dates. Returns the count of coupon
   * payments remaining, rounded up to the nearest whole coupon. This is used in bond pricing calculations to determine
   * the number of cash flows.
   * 
   * Implementation reference: Apache OpenOffice analysishelper.cxx line 1414
   * 
   * @param settlementDate the bond settlement date
   * @param maturityDate   the bond maturity date
   * @param frequency      the number of coupon payments per year
   * @param basis          the day count basis (used for consistency, not in this calculation)
   * @return the number of coupon payments between settlement and maturity
   */
  private double getCoupnum(LocalDate settlementDate, LocalDate maturityDate, int frequency, int basis) {
    LocalDate aMat = LocalDate.from(maturityDate);
    LocalDate aDate = getPreviousCouponDate(settlementDate, aMat, frequency);
    int nMonths = (aMat.getYear() - aDate.getYear()) * 12 + aMat.getMonthValue() - aDate.getMonthValue();
    return nMonths * frequency / 12.0;
  }

  /**
   * Calculates the number of days in a coupon period. Returns the day count between the coupon date before settlement
   * and the coupon date after settlement. This is fundamental for calculating accrued interest and proper bond pricing.
   * 
   * @param settlementDate the bond settlement date
   * @param maturityDate   the bond maturity date
   * @param frequency      the number of coupon payments per year
   * @param basis          the day count basis convention
   * @return the number of days in the coupon period
   */
  private double getCoupdays(LocalDate settlementDate, LocalDate maturityDate, int frequency, int basis) {
    if (basis == 1) {
      LocalDate aDate = getPreviousCouponDate(settlementDate, maturityDate, frequency);
      LocalDate aNextDate = aDate.plusMonths(12 / frequency);
      return ChronoUnit.DAYS.between(aDate, aNextDate);
    }

    return getDaysInYear(basis) / frequency;
  }

  /**
   * Gets the number of days in a year based on the basis.
   *
   * @param nMode The day count basis.
   * @return The number of days in a year.
   */
  private int getDaysInYear(int nMode) {
    switch (nMode) {
    case 0: // 0=USA (NASD) 30/360
    case 2: // 2=exact/360
    case 4: // 4=Europe 30/360
      return 360;
    case 1: // 1=exact/exact
      // TODO Miss leap year
      return 365;
    default: // 3=exact/365
      return 365;
    }
  }

  /**
   * Returns get day count: settlement <-> coupon date after settlement
   *
   * see
   * https://github.com/apache/openoffice/blob/c014b5f2b55cff8d4b0c952d5c16d62ecde09ca1/main/scaddins/source/analysis/analysishelper.cxx#L1375
   *
   */
  private double getCoupdaysnc(LocalDate settlementDate, LocalDate maturityDate, int frequency, int basis) {
    if (basis != 0 && basis != 4) {
      LocalDate aSettle = LocalDate.from(settlementDate);
      LocalDate aDate = getNextCouponDate(aSettle, maturityDate, frequency);
      return ChronoUnit.DAYS.between(settlementDate, aDate) + 1;
    }
    return getCoupdays(settlementDate, maturityDate, frequency, basis)
        - getCoupdaybs(settlementDate, maturityDate, frequency, basis);
  }

  /**
   * Calculates the number of days from the beginning of the coupon period to the settlement date. This represents the
   * accrued interest period and is used in bond pricing calculations to account for interest that has accumulated since
   * the last coupon payment.
   * 
   * @param settlementDate the bond settlement date
   * @param maturityDate   the bond maturity date
   * @param frequency      the number of coupon payments per year
   * @param basis          the day count basis (used for consistency in the calculation framework)
   * @return the number of days from the previous coupon date to settlement
   */
  private long getCoupdaybs(LocalDate settlementDate, LocalDate maturityDate, int frequency, int basis) {
    LocalDate aDate = getPreviousCouponDate(settlementDate, maturityDate, frequency);
    return ChronoUnit.DAYS.between(aDate, settlementDate);
  }

  /**
   * Finds the last coupon date before or equal to the settlement date. This method works backward from the maturity
   * date to find the most recent coupon payment date that has occurred by the settlement date.
   * 
   * @param rSettle the settlement date
   * @param rMat    the maturity date of the bond
   * @param nFreq   the number of coupon payments per year
   * @return the previous coupon date (can be equal to settlement date)
   */
  private LocalDate getPreviousCouponDate(LocalDate rSettle, LocalDate rMat, int nFreq) {
    LocalDate rDate = LocalDate.from(rMat);
    rDate.withYear(rSettle.getYear());
    if (rDate.compareTo(rSettle) < 0) {
      rDate = rDate.plusYears(1);
    }
    while (rDate.compareTo(rSettle) > 0) {
      rDate = rDate.minusMonths(12 / nFreq);
    }
    return rDate;
  }

  /**
   * Finds the first coupon date after the settlement date. This method works forward from a reference point to find the
   * next scheduled coupon payment date after settlement.
   * 
   * @param rSettle the settlement date
   * @param rMat    the maturity date of the bond
   * @param nFreq   the number of coupon payments per year
   * @return the next coupon date (never equal to settlement date)
   */
  private LocalDate getNextCouponDate(LocalDate rSettle, LocalDate rMat, int nFreq) {
    LocalDate rDate = rMat;
    rDate.withYear(rSettle.getYear());
    if (rDate.compareTo(rSettle) > 0) {
      rDate = rDate.minusYears(1);
    }
    while (rDate.compareTo(rSettle) <= 0) {
      rDate = rDate.plusMonths(12 / nFreq);
    }
    return rDate;
  }

  /**
   * Extracts coupon rate from security name, calculates YTM, and stores the result. This method combines coupon rate
   * extraction using regex pattern matching with YTM calculation and storage in the UDF system. The coupon rate is
   * expected to appear at the beginning of the security name as a numeric value.
   * 
   * Results are rounded according to the UDF field's precision settings and stored for display in the user interface.
   * 
   * @param securitycurrencyUDFGroup the UDF group context for storing calculated values
   * @param udfYTM                   the UDF metadata for the yield to maturity field
   * @param security                 the security for which to calculate YTM
   * @param numberStartTextRegex     the regex pattern for extracting coupon rate from security name
   * @param now                      the current date used as settlement date
   */
  private void calcAndSetYTM(SecuritycurrencyUDFGroup securitycurrencyUDFGroup, UDFMetadataSecurity udfYTM,
      Security security, Pattern numberStartTextRegex, LocalDate now) {
    Matcher matcher = numberStartTextRegex.matcher(security.getName());
    if (matcher.find() && !matcher.group(0).isEmpty()) {
      Double annualCouponRate = Double.parseDouble(matcher.group(0).replace(",", ".")) / 100;
      double ytm = DataHelper.round(
          yieldToMaturity(now, ((java.sql.Date) security.getActiveToDate()).toLocalDate(), annualCouponRate,
              security.getSLast(), 100, security.getDistributionFrequency().getValue(), 4) * 100,
          udfYTM.getFieldSizeSuffix());
      putValueToJsonValue(securitycurrencyUDFGroup, udfYTM, security.getIdSecuritycurrency(), ytm, false);
    }
  }

}
