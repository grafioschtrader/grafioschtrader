package grafioschtrader.entities;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.platformimport.TemplateConfiguration;

/**
 * Pure unit tests for the per-currency resolution of the calcRounding tolerance on {@link ImportTransactionPos}.
 */
class ImportTransactionPosRoundingTest {

  private ImportTransactionPos posWithMap(String currencyAccount, Map<String, Double> calcRoundingMap) {
    ImportTransactionPos pos = new ImportTransactionPos();
    pos.setCurrencyAccount(currencyAccount);
    pos.setCalcRoundingMap(calcRoundingMap);
    return pos;
  }

  @Test
  @DisplayName("Default tolerance applies when there is no per-currency override")
  void resolvesDefault() {
    Map<String, Double> map = new HashMap<>();
    map.put(TemplateConfiguration.CALC_ROUNDING_DEFAULT_KEY, 0.01);
    map.put("JPY", 1.0);
    assertThat(posWithMap("USD", map).resolveConfiguredRoundingStep()).isEqualTo(0.01);
  }

  @Test
  @DisplayName("A per-currency override takes precedence over the default")
  void resolvesOverride() {
    Map<String, Double> map = new HashMap<>();
    map.put(TemplateConfiguration.CALC_ROUNDING_DEFAULT_KEY, 0.01);
    map.put("JPY", 1.0);
    assertThat(posWithMap("JPY", map).resolveConfiguredRoundingStep()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("No tolerance is returned when the template configured none")
  void resolvesNullWhenUnconfigured() {
    assertThat(posWithMap("USD", null).resolveConfiguredRoundingStep()).isNull();
    assertThat(posWithMap("USD", new HashMap<>()).resolveConfiguredRoundingStep()).isNull();
  }
}
