package grafioschtrader.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.GlobalConstants;
import grafioschtrader.platformimport.ImportProperties;
import grafioschtrader.types.ImportKnownOtherFlags;
import grafioschtrader.types.TransactionType;

/**
 * Pure unit tests (no Spring context) for the minor currency unit handling of GitHub issue #10: the case-sensitive
 * code table in {@link MinorCurrencyUnit} and the import normalization in
 * {@link ImportProperties#normalizeMinorCurrencyUnits()}.
 */
class MinorCurrencyUnitTest {

  @Test
  @DisplayName("Minor-unit codes are recognized case-sensitively")
  void isMinorUnitTest() {
    assertThat(MinorCurrencyUnit.isMinorUnit("GBp")).isTrue();
    assertThat(MinorCurrencyUnit.isMinorUnit("GBX")).isTrue();
    assertThat(MinorCurrencyUnit.isMinorUnit("ZAc")).isTrue();
    assertThat(MinorCurrencyUnit.isMinorUnit("ZAX")).isTrue();
    // Uppercase GBP is pounds, not pence; lookup must not match case-insensitively
    assertThat(MinorCurrencyUnit.isMinorUnit("GBP")).isFalse();
    assertThat(MinorCurrencyUnit.isMinorUnit("gbx")).isFalse();
    assertThat(MinorCurrencyUnit.isMinorUnit("ZAR")).isFalse();
    assertThat(MinorCurrencyUnit.isMinorUnit(null)).isFalse();
  }

  @Test
  @DisplayName("Minor-unit codes map to their ISO 4217 major currency")
  void getMajorCurrencyTest() {
    assertThat(MinorCurrencyUnit.getMajorCurrency("GBp")).isEqualTo(GlobalConstants.MC_GBP);
    assertThat(MinorCurrencyUnit.getMajorCurrency("GBX")).isEqualTo(GlobalConstants.MC_GBP);
    assertThat(MinorCurrencyUnit.getMajorCurrency("ZAc")).isEqualTo(GlobalConstants.MC_ZAR);
    assertThat(MinorCurrencyUnit.getMajorCurrency("ZAX")).isEqualTo(GlobalConstants.MC_ZAR);
    assertThat(MinorCurrencyUnit.getMajorCurrency("GBP")).isNull();
    assertThat(MinorCurrencyUnit.getMajorCurrency(null)).isNull();
  }

  @Test
  @DisplayName("Exchange divider applies only to minor-unit quoted MIC and currency combinations")
  void getExchangeDividerTest() {
    assertThat(MinorCurrencyUnit.getExchangeDivider(GlobalConstants.STOCK_EX_MIC_UK, GlobalConstants.MC_GBP))
        .isEqualTo(MinorCurrencyUnit.MINOR_TO_MAJOR_DIVIDER);
    assertThat(MinorCurrencyUnit.getExchangeDivider(GlobalConstants.STOCK_EX_MIC_JSE, GlobalConstants.MC_ZAR))
        .isEqualTo(MinorCurrencyUnit.MINOR_TO_MAJOR_DIVIDER);
    // A USD-quoted instrument on the LSE is not traded in pence
    assertThat(MinorCurrencyUnit.getExchangeDivider(GlobalConstants.STOCK_EX_MIC_UK, "USD")).isEqualTo(1.0);
    assertThat(MinorCurrencyUnit.getExchangeDivider("XSWX", "CHF")).isEqualTo(1.0);
    assertThat(MinorCurrencyUnit.getExchangeDivider(null, GlobalConstants.MC_GBP)).isEqualTo(1.0);
    assertThat(MinorCurrencyUnit.getExchangeDivider(GlobalConstants.STOCK_EX_MIC_UK, null)).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Import normalization converts GBp security currency, quotation and pseudo exchange rate")
  void normalizeSecurityCurrencyTest() {
    ImportProperties ip = createImportProperties();
    ip.setCin("GBp");
    ip.setCac(GlobalConstants.MC_GBP);
    ip.setQuotation(1983.0);
    ip.setCex(100.0);
    ip.normalizeMinorCurrencyUnits();
    assertThat(ip.getCin()).isEqualTo(GlobalConstants.MC_GBP);
    assertThat(ip.getQuotation()).isEqualTo(19.83);
    // The factor 100 was the pence/pounds conversion, not a real FX rate
    assertThat(ip.getCex()).isNull();
  }

  @Test
  @DisplayName("Import normalization keeps a real exchange rate and non-minor currencies untouched")
  void normalizeKeepsRealExchangeRateTest() {
    ImportProperties ip = createImportProperties();
    ip.setCin("GBX");
    ip.setCac("CHF");
    ip.setQuotation(250.0);
    ip.setCex(1.13);
    ip.normalizeMinorCurrencyUnits();
    assertThat(ip.getCin()).isEqualTo(GlobalConstants.MC_GBP);
    assertThat(ip.getQuotation()).isEqualTo(2.5);
    assertThat(ip.getCex()).isEqualTo(1.13);

    ImportProperties untouched = createImportProperties();
    untouched.setCin(GlobalConstants.MC_GBP);
    untouched.setQuotation(19.83);
    untouched.normalizeMinorCurrencyUnits();
    assertThat(untouched.getCin()).isEqualTo(GlobalConstants.MC_GBP);
    assertThat(untouched.getQuotation()).isEqualTo(19.83);
  }

  @Test
  @DisplayName("Import normalization converts costs and taxes quoted in a minor unit")
  void normalizeCostTaxCurrencyTest() {
    ImportProperties ip = createImportProperties();
    ip.setCct("ZAc");
    ip.setTc1(150.0);
    ip.setTc2(50.0);
    ip.setTt1(30.0);
    ip.setReduce(10.0);
    ip.normalizeMinorCurrencyUnits();
    assertThat(ip.getCct()).isEqualTo(GlobalConstants.MC_ZAR);
    assertThat(ip.getTc1()).isEqualTo(1.5);
    assertThat(ip.getTc2()).isEqualTo(0.5);
    assertThat(ip.getTt1()).isEqualTo(0.3);
    assertThat(ip.getTt2()).isNull();
    assertThat(ip.getReduce()).isEqualTo(0.1);
  }

  private ImportProperties createImportProperties() {
    return new ImportProperties(Map.<String, TransactionType>of(), EnumSet.noneOf(ImportKnownOtherFlags.class), null);
  }

}
