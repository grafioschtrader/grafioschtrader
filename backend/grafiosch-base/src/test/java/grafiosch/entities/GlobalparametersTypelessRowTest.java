package grafiosch.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafiosch.dto.InputRule;

/**
 * A global parameter whose value was reset to NULL after a database import, as {@code g.gnet.my.entry.id} is, must stay
 * editable. Before, such a row refused every value because its type is read from the column holding the value.
 */
class GlobalparametersTypelessRowTest {

  @Test
  @DisplayName("A row without any value takes an integer and is an integer row afterwards")
  void typelessRowTakesInteger() {
    Globalparameters existing = new Globalparameters("g.gnet.my.entry.id");
    existing.replaceExistingPropertyValue(new Globalparameters("g.gnet.my.entry.id").setPropertyInt(21));
    assertThat(existing.getPropertyInt()).isEqualTo(21);
    assertThat(existing.getPropertyString()).isNull();
    assertThatThrownBy(
        () -> existing.replaceExistingPropertyValue(new Globalparameters("g.gnet.my.entry.id").setPropertyString("x")))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("A typed row still refuses a value of another type")
  void typedRowRefusesOtherType() {
    Globalparameters existing = new Globalparameters("g.max.something").setPropertyInt(5);
    assertThatThrownBy(
        () -> existing.replaceExistingPropertyValue(new Globalparameters("g.max.something").setPropertyString("5")))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("min, max and enum type a rule as numeric, a pattern does not")
  void numericRules() {
    assertThat(InputRule.parse("min:1").isNumeric()).isTrue();
    assertThat(InputRule.parse("enum:1,7").isNumeric()).isTrue();
    assertThat(InputRule.parse("pattern:^[A-Z]{3}$").isNumeric()).isFalse();
  }
}
