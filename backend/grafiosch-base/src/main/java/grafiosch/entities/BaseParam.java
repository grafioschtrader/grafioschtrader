package grafiosch.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * Abstract base class for typed parameters stored in JPA element collections.
 *
 * This class provides a common structure for key-value parameter maps where the key is defined
 * by {@code @MapKeyColumn} in the owning entity and the value is stored as a string. Subclasses
 * are used as embeddable value types in {@code @ElementCollection} mappings.
 *
 * Known subclasses:
 * <ul>
 *   <li>{@code GTNetMessage.GTNetMessageParam} - Parameters for GTNet inter-instance messages</li>
 *   <li>{@code AlgoRule.AlgoRuleParam2} - Parameters for algorithmic trading rules</li>
 *   <li>{@code AlgoRuleStrategy.AlgoRuleStrategyParam} - Parameters for trading strategies</li>
 * </ul>
 *
 * The string value can represent any serializable data type; parsing/conversion is the responsibility
 * of the consuming code based on the parameter key's expected type.
 */
@MappedSuperclass
@Schema(description = """
    Abstract base class for typed parameters stored in JPA element collections. Provides a common structure
    for key-value parameter maps where the key is defined by the owning entity's @MapKeyColumn and the value
    is stored as a string. The string can represent any serializable data type; parsing is based on the
    parameter key's expected type.""")
public abstract class BaseParam {

  public BaseParam() {
  }

  public BaseParam(String paramValue) {
    this.paramValue = paramValue;
  }

  @Schema(description = """
      The parameter value stored as a string. Can represent any data type (numbers, dates, booleans, JSON, etc.)
      depending on the parameter key. Parsing and type conversion is handled by the consuming code based on
      the expected type for each parameter name.""")
  @Column(name = "param_value")
  protected String paramValue;

  public String getParamValue() {
    return paramValue;
  }

  public void setParamValue(String paramValue) {
    this.paramValue = paramValue;
  }
}
