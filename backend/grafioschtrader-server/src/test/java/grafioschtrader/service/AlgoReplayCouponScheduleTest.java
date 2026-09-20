package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.offset;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import grafioschtrader.entities.SecurityBondTerms;
import grafioschtrader.service.AlgoReplayCouponSchedule.Terms;
import grafioschtrader.types.CouponDayCount;

class AlgoReplayCouponScheduleTest {
  @ParameterizedTest
  @ValueSource(ints = { 1, 2, 4, 12 })
  void regularFrequenciesPayTheAnnualRateAndIncludeMaturity(int frequency) {
    LocalDate maturity = LocalDate.of(2024, 12, 31);
    LocalDate start = maturity.minusYears(1);
    var schedule = new AlgoReplayCouponSchedule(new Terms(6d, maturity, frequency, CouponDayCount.ACT_ACT_ICMA));
    assertThat(schedule.payments(start, maturity)).hasSize(frequency).endsWith(LocalDate.of(2024, 12, 31));
    assertThat(schedule.coupon(2) * frequency).isEqualTo(12);
    assertThat(schedule.accrued(1, maturity.minusMonths(12 / frequency))).isZero();
  }

  @Test
  void leapMonthDoesNotShiftTheOriginalMonthEndAnchor() {
    var schedule = schedule(CouponDayCount.ACT_ACT_ICMA);
    assertThat(schedule.payments(LocalDate.of(2024, 1, 31), LocalDate.of(2024, 5, 31))).containsExactly(
        LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 31), LocalDate.of(2024, 4, 30), LocalDate.of(2024, 5, 31));
    assertThat(schedule.accrued(1, LocalDate.of(2024, 2, 15))).isCloseTo(1d * 15 / 29, offset(1e-12));
    assertThat(schedule.accrued(1, LocalDate.of(2024, 1, 31))).isZero();
    assertThat(schedule.accrued(1, LocalDate.of(2024, 12, 31))).isZero();
    assertThat(schedule.nextPayment(LocalDate.of(2024, 12, 31))).isNull();
  }

  @Test
  void europeanThirty360UsesCappedCalendarDays() {
    assertThat(schedule(CouponDayCount.THIRTY_E_360).accrued(2, LocalDate.of(2024, 2, 15))).isEqualTo(1);
  }

  @Test
  void rejectsIncompleteOrUnsupportedTerms() {
    assertThatIllegalArgumentException().isThrownBy(() -> new AlgoReplayCouponSchedule(null));
    assertThatIllegalArgumentException().isThrownBy(() -> new AlgoReplayCouponSchedule(
        new Terms(null, LocalDate.of(2024, 12, 31), 12, CouponDayCount.ACT_ACT_ICMA)));
    assertThatIllegalArgumentException().isThrownBy(
        () -> new AlgoReplayCouponSchedule(new Terms(1d, LocalDate.of(2024, 12, 31), 3, CouponDayCount.ACT_ACT_ICMA)));
  }

  @Test
  void zeroCouponProducesNeitherPaymentsNorAccrual() {
    var schedule = new AlgoReplayCouponSchedule(new Terms(0d, LocalDate.of(2024, 12, 31), 12, null));
    assertThat(schedule.payments(LocalDate.of(2024, 1, 31), LocalDate.of(2025, 1, 1))).isEmpty();
    assertThat(schedule.accrued(1, LocalDate.of(2024, 3, 15))).isZero();
  }

  @Test
  void storedDayCountWinsOverTheCurrencyDefault() {
    var terms = new SecurityBondTerms();
    terms.setCouponRate(1.5);
    terms.setCouponDayCount(CouponDayCount.ACT_ACT_ICMA);
    assertThat(AlgoReplayInputs.couponDayCount(terms, "CHF")).isEqualTo(CouponDayCount.ACT_ACT_ICMA);
  }

  @Test
  void missingDayCountFallsBackToTheCurrencyDefault() {
    var terms = new SecurityBondTerms();
    terms.setCouponRate(1.5);
    assertThat(AlgoReplayInputs.couponDayCount(terms, "CHF")).isEqualTo(CouponDayCount.THIRTY_E_360);
    assertThat(AlgoReplayInputs.couponDayCount(null, "EUR")).isEqualTo(CouponDayCount.ACT_ACT_ICMA);
    assertThat(new AlgoReplayCouponSchedule(
        new Terms(terms.getCouponRate(), LocalDate.of(2030, 6, 30), 1, AlgoReplayInputs.couponDayCount(terms, "CHF"))))
            .isNotNull();
  }

  private AlgoReplayCouponSchedule schedule(CouponDayCount dayCount) {
    return new AlgoReplayCouponSchedule(new Terms(12d, LocalDate.of(2024, 12, 31), 12, dayCount));
  }
}
