package grafioschtrader;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.solvers.BrentSolver;

public class YTMCalculator {

  // Input parameters
  private double Price; // Bond price
  private double FaceValue; // Bond face value
  private double CouponRate; // Bond coupon rate
  private double maturity; // Bond maturity in years
  private double frequency; // Coupon payment frequency per year

  // Constructor
  public YTMCalculator(double Price, double FaceValue, double CouponRate, double maturity, double frequency) {
    this.Price = Price;
    this.FaceValue = FaceValue;
    this.CouponRate = CouponRate;
    this.maturity = maturity;
    this.frequency = frequency;
  }

  /**
   * Returns the yield on a security that pays periodic interest. Use YIELD to calculate bond yield.
   * 
   * @param settlementDate
   * @param maturityDate
   * @param annualCouponRate
   * @param price
   * @param redemption
   * @param frequency
   * @param basis
   * @return
   * 
   *  @see https://github.com/apache/openoffice/blob/c014b5f2b55cff8d4b0c952d5c16d62ecde09ca1/main/scaddins/source/analysis/analysishelper.cxx#L1085
   */
  public static double yield(LocalDate settlementDate, LocalDate maturityDate, double annualCouponRate, double price,
      double redemption, int frequency, int basis) {
    
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
   * 
   * @param settlementDate
   * @param maturityDate
   * @param annualCouponRate
   * @param annualYield
   * @param redemption
   * @param frequency
   * @param basis
   * @return
   * @see https://github.com/apache/openoffice/blob/c014b5f2b55cff8d4b0c952d5c16d62ecde09ca1/main/scaddins/source/analysis/analysishelper.cxx#L1137
   */
  public static double price(LocalDate settlementDate, LocalDate maturityDate, double annualCouponRate,
      double annualYield, double redemption, int frequency, int basis) {
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
   * Returns the number of coupons payable between the settlement date and maturity date, rounded up to the nearest whole coupon.
   * @param settlementDate
   * @param maturityDate
   * @param frequency
   * @param basis
   * @return
   * @see https://github.com/apache/openoffice/blob/c014b5f2b55cff8d4b0c952d5c16d62ecde09ca1/main/scaddins/source/analysis/analysishelper.cxx#L1414
   */
  public static double getCoupnum(LocalDate settlementDate, LocalDate maturityDate, int frequency, int basis) {
    LocalDate aMat = LocalDate.from(maturityDate);
    LocalDate aDate = lcl_GetCouppcd(settlementDate, aMat, frequency);
    int nMonths = (aMat.getYear() - aDate.getYear()) * 12 + aMat.getMonthValue() - aDate.getMonthValue();
    return nMonths * frequency / 12.0;
  }

  /**
   * get day count: coupon date before settlement <-> coupon date after settlement
   */
  public static double getCoupdays(LocalDate settlementDate, LocalDate maturityDate, int frequency, int basis) {
    if (basis == 1) {
      LocalDate aDate = lcl_GetCouppcd(settlementDate, maturityDate, frequency);
      LocalDate aNextDate = aDate.plusMonths(12 / frequency);
      return ChronoUnit.DAYS.between(aDate, aNextDate);
    }

    return getDaysInYear(basis) / frequency;
  }

  private static int getDaysInYear(int nMode) {
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
   * @see https://github.com/apache/openoffice/blob/c014b5f2b55cff8d4b0c952d5c16d62ecde09ca1/main/scaddins/source/analysis/analysishelper.cxx#L1375
   *
   */
  public static double getCoupdaysnc(LocalDate settlementDate, LocalDate maturityDate, int frequency, int basis) {

    if (basis != 0 && basis != 4) {
      LocalDate aSettle = LocalDate.from(settlementDate);
      LocalDate aDate = lcl_GetCoupncd(aSettle, maturityDate, frequency);
      return ChronoUnit.DAYS.between(settlementDate, aDate) + 1;
    }
    return getCoupdays(settlementDate, maturityDate, frequency, basis)
        - getCoupdaybs(settlementDate, maturityDate, frequency, basis);
  }

  /**
   * Returns the number of days from the beginning of a
   * coupon period until its settlement date.
   */
  public static long getCoupdaybs(LocalDate settlementDate, LocalDate maturityDate, int frequency, int basis) {
    LocalDate aDate = lcl_GetCouppcd(settlementDate, maturityDate, frequency);
    return ChronoUnit.DAYS.between(aDate, settlementDate);
  }

  /**
   * Find last coupon date before settlement (can be equal to settlement)
   */
  static LocalDate lcl_GetCouppcd(LocalDate rSettle, LocalDate rMat, int nFreq) {
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
  static LocalDate lcl_GetCoupncd(LocalDate rSettle, LocalDate rMat, int nFreq) {
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

  // Method to calculate YTM
  public double calculateYTM() {
    // Define the function to be solved
    UnivariateFunction function = new UnivariateFunction() {
      @Override
      public double value(double r) {
        double sum = 0.0;
        for (double j = 1; j <= maturity * frequency; j++) {
          sum += (CouponRate * FaceValue / frequency) / Math.pow(1 + r / frequency, j);
        }
        sum += FaceValue / Math.pow(1 + r / frequency, maturity * frequency);
        return Price - sum;
      }
    };

    // Create a solver with default accuracy and iterations
    BrentSolver solver = new BrentSolver();

    // Solve the function in the range [0, 1]
    double ytm = solver.solve(100, function, 0, 1);

    // Return the annualized YTM
    return ytm * frequency;
  }

  // Main method for testing
  public static void main(String[] args) {
    // Create a bond object with sample data
    // YTMCalculator bond = new YTMCalculator(95.0428, 100, 0.05, 1.5, 2);

    // YTMCalculator bond = new YTMCalculator(96.2, 100, 0.0732 / 100, 1, 1); //
    // 4.79
    // YTMCalculator bond = new YTMCalculator(100 - (3.845 * 1), 100, 3.845 / 100,
    // 1, 1); // 3.55
    // double ytm = bond.calculateYTM();

    System.out.println(
        YTMCalculator.yield(LocalDate.of(2024, 1, 29), LocalDate.of(2029, 12, 21), 0.0, 92.5, 100, 1, 4) * 100);
    System.out
        .println(YTMCalculator.yield(LocalDate.of(2024, 1, 29), LocalDate.of(2025, 1, 30), 0.0, 96.2, 100, 1, 4) * 100);
    System.out.println(
        YTMCalculator.yield(LocalDate.of(2024, 1, 29), LocalDate.of(2032, 8, 17), 0.03845, 102.4, 100, 1, 4) * 100);
    System.out.println(YTMCalculator.price(LocalDate.of(2024, 1, 29), LocalDate.of(2032, 8, 17), 0.03845,
        3.51291387 / 100, 100, 1, 4));

    // Calculate and print the YTM

  }
}
