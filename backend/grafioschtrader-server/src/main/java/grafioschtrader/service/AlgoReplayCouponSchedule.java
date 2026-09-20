package grafioschtrader.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import grafioschtrader.types.CouponDayCount;

/** Contractual regular coupon dates and accrued interest on GT's nominal-per-100 unit basis. */
public final class AlgoReplayCouponSchedule {
  public static final double PAR_PER_UNIT = 100d;

  public record Terms(Double rate, LocalDate maturity, int frequency, CouponDayCount dayCount) {
  }

  private final Terms terms;
  private final int months;

  public AlgoReplayCouponSchedule(Terms terms) {
    if (terms == null || terms.rate() == null || terms.rate() < 0 || terms.maturity() == null
        || terms.rate() > 0 && terms.dayCount() == null || !List.of(1, 2, 4, 12).contains(terms.frequency())) {
      throw new IllegalArgumentException("Incomplete or unsupported regular coupon terms");
    }
    this.terms = terms;
    months = 12 / terms.frequency();
  }

  /** Dates advance from the maturity anchor, never from an already clamped month. */
  private LocalDate date(long periodsFromMaturity) {
    LocalDate anchor = terms.maturity();
    LocalDate date = anchor.plusMonths(periodsFromMaturity * months);
    return anchor.getDayOfMonth() == anchor.lengthOfMonth() ? date.withDayOfMonth(date.lengthOfMonth()) : date;
  }

  public List<LocalDate> payments(LocalDate after, LocalDate through) {
    if (terms.rate() == 0)
      return List.of();
    List<LocalDate> dates = new ArrayList<>();
    LocalDate upper = through.isBefore(terms.maturity()) ? through : terms.maturity();
    for (long index = 0;; index++) {
      LocalDate date = date(-index);
      if (!date.isAfter(after)) {
        break;
      }
      if (!date.isAfter(upper)) {
        dates.add(date);
      }
    }
    java.util.Collections.reverse(dates);
    return List.copyOf(dates);
  }

  public double coupon(double units) {
    return units * PAR_PER_UNIT * terms.rate() / 100d / terms.frequency();
  }

  /** Accrual is zero at a coupon date and on or after maturity. */
  public double accrued(double units, LocalDate asOf) {
    if (terms.rate() == 0 || !asOf.isBefore(terms.maturity())) {
      return 0;
    }
    long periodsBeforeMaturity = 0;
    LocalDate end = terms.maturity();
    LocalDate start = date(-1);
    while (start.isAfter(asOf)) {
      periodsBeforeMaturity++;
      end = start;
      start = date(-periodsBeforeMaturity - 1);
    }
    if (terms.dayCount() == CouponDayCount.ACT_ACT_ICMA)
      return coupon(units) * ChronoUnit.DAYS.between(start, asOf) / (double) ChronoUnit.DAYS.between(start, end);
    long days = 360L * (asOf.getYear() - start.getYear()) + 30L * (asOf.getMonthValue() - start.getMonthValue())
        + Math.min(30, asOf.getDayOfMonth()) - Math.min(30, start.getDayOfMonth());
    return units * PAR_PER_UNIT * terms.rate() / 100d * days / 360d;
  }

  public LocalDate nextPayment(LocalDate date) {
    return payments(date, terms.maturity()).stream().findFirst().orElse(null);
  }
}
