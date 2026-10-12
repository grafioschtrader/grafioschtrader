package grafioschtrader.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SecurityBondTermsTest {

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = { "2.875 CFFL 10-30|2.875", "1.20 AMCOM 26-32 /Z|1.2",
      "' 0,375 Stadler Rail AG 19-26'|0.375", "0% Sonova Holding AG 19-29|0" })
  @DisplayName("Reads the coupon from a number at the start of the name")
  void couponAtStartOfName(String name, double expected) {
    assertEquals(expected, SecurityBondTerms.couponRateFromName(name));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = { "voestalpine3.75%GreenN.24-29/1|3.75",
      "Raiffeisen Bank Intl 4,125 % 22-27|4.125", "3M Co 2.5% 20-30|2.5" })
  @DisplayName("Reads the coupon from a percent-marked number anywhere in the name")
  void couponMarkedWithPercent(String name, double expected) {
    assertEquals(expected, SecurityBondTerms.couponRateFromName(name));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = { "Royal Bank 0.20 21-31", "3M Co 20-30" })
  @DisplayName("Infers no coupon from an unmarked number later in the name or an issuer prefix")
  void noCoupon(String name) {
    assertNull(SecurityBondTerms.couponRateFromName(name));
  }
}
