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

@Service
public class YieldToMaturityCalculator extends AllUserFieldsSecurity implements IUDFForEveryUser {

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
   * Returns the yield on a security that pays periodic interest. Use YIELD to calculate bond yield.
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
   * https://github.com/apache/openoffice/blob/c014b5f2b55cff8d4b0c952d5c16d62ecde09ca1/main/scaddins/source/analysis/analysishelper.cxx#L1137
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
   * Returns the number of coupons payable between the settlement date and maturity date, rounded up to the nearest
   * whole coupon.
   * 
   * https://github.com/apache/openoffice/blob/c014b5f2b55cff8d4b0c952d5c16d62ecde09ca1/main/scaddins/source/analysis/analysishelper.cxx#L1414
   */
  private double getCoupnum(LocalDate settlementDate, LocalDate maturityDate, int frequency, int basis) {
    LocalDate aMat = LocalDate.from(maturityDate);
    LocalDate aDate = getPreviousCouponDate(settlementDate, aMat, frequency);
    int nMonths = (aMat.getYear() - aDate.getYear()) * 12 + aMat.getMonthValue() - aDate.getMonthValue();
    return nMonths * frequency / 12.0;
  }

  /**
   * get day count: coupon date before settlement <-> coupon date after settlement
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
   * Returns the number of days from the beginning of a coupon period until its settlement date.
   */
  private long getCoupdaybs(LocalDate settlementDate, LocalDate maturityDate, int frequency, int basis) {
    LocalDate aDate = getPreviousCouponDate(settlementDate, maturityDate, frequency);
    return ChronoUnit.DAYS.between(aDate, settlementDate);
  }

  /**
   * Find last coupon date before settlement (can be equal to settlement)
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
   * Find first coupon date after settlement (is never equal to settlement)
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
