package grafioschtrader.entities;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertySelectiveUpdatableOrWhenNull;
import grafiosch.entities.Auditable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = """
    A set of holiday rules from which the closures of one or more stock exchanges can be calculated for any year. It
    is the alternative to deriving the trading calendar from the quotes of a reference index, which is what
    Stockexchange.idIndexUpdCalendar does; a stock exchange uses either the index or a rule set, never both.

    The rules themselves are kept as YAML in ruleYaml rather than as normalized rows, because a rule is edited as a
    whole and the YAML carries the explanatory notes that justify each closure. A set may extend another one through
    idExtendsRuleSet, so a venue that follows the calendar of a larger exchange stores only its own deviations.

    This is shared data: every user may create a rule set, a user with restricted rights produces a change proposal
    instead of a direct update.""")
@Entity
@Table(name = TradingCalendarRuleSet.TABNAME)
public class TradingCalendarRuleSet extends Auditable implements Serializable {

  public static final String TABNAME = "trading_calendar_rule_set";

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_trading_calendar_rule_set")
  private Integer idTradingCalendarRuleSet;

  @Schema(description = """
      The market identifier code of the exchange this rule set was written for. It is optional and unique. When a
      stock exchange is created with this MIC, the rule set is assigned to it automatically, which is its only
      purpose -- a rule set may be used by any number of exchanges regardless of their MIC.""")
  @Column(name = "mic")
  @Size(min = 4, max = 4)
  @PropertySelectiveUpdatableOrWhenNull
  private String mic;

  @Schema(description = "Name of the rule set, usually the name of the exchange it was written for. It must be unique")
  @Basic(optional = false)
  @Column(name = "name")
  @NotBlank
  @Size(min = 2, max = 64)
  @PropertyAlwaysUpdatable
  private String name;

  @Schema(description = """
      Optional reference to a rule set whose rules apply here as well. The rules of the referenced set are evaluated
      first and this set's own rules are appended, so a deviation can add a closure the referenced set does not have.
      A deviation that has to remove an inherited closure expresses this with a validTo on the inherited rule.""")
  @Column(name = "id_extends_rule_set")
  @PropertyAlwaysUpdatable
  private Integer idExtendsRuleSet;

  @Schema(description = """
      The rules as YAML: an optional note, the optional authoritative year range, the rules themselves and the date
      level corrections additionalClosures and openDates. May be empty when the set only exists to name an exchange
      that follows another calendar unchanged, in which case idExtendsRuleSet must be set.""")
  @Column(name = "rule_yaml", columnDefinition = "TEXT")
  @PropertyAlwaysUpdatable
  private String ruleYaml;

  @Schema(description = "Name of the rule set referenced by idExtendsRuleSet, for display only")
  @Transient
  private String nameExtendsRuleSet;

  @Schema(description = "Number of stock exchanges currently using this rule set, for display only")
  @Transient
  private Integer usedByExchanges;

  public TradingCalendarRuleSet() {
  }

  public Integer getIdTradingCalendarRuleSet() {
    return idTradingCalendarRuleSet;
  }

  public void setIdTradingCalendarRuleSet(Integer idTradingCalendarRuleSet) {
    this.idTradingCalendarRuleSet = idTradingCalendarRuleSet;
  }

  public String getMic() {
    return mic;
  }

  public void setMic(String mic) {
    this.mic = mic;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Integer getIdExtendsRuleSet() {
    return idExtendsRuleSet;
  }

  public void setIdExtendsRuleSet(Integer idExtendsRuleSet) {
    this.idExtendsRuleSet = idExtendsRuleSet;
  }

  public String getRuleYaml() {
    return ruleYaml;
  }

  public void setRuleYaml(String ruleYaml) {
    this.ruleYaml = ruleYaml;
  }

  public String getNameExtendsRuleSet() {
    return nameExtendsRuleSet;
  }

  public void setNameExtendsRuleSet(String nameExtendsRuleSet) {
    this.nameExtendsRuleSet = nameExtendsRuleSet;
  }

  public Integer getUsedByExchanges() {
    return usedByExchanges;
  }

  public void setUsedByExchanges(Integer usedByExchanges) {
    this.usedByExchanges = usedByExchanges;
  }

  @JsonIgnore
  @Override
  public Integer getId() {
    return idTradingCalendarRuleSet;
  }

  @Override
  public String toString() {
    return "TradingCalendarRuleSet [idTradingCalendarRuleSet=" + idTradingCalendarRuleSet + ", mic=" + mic + ", name="
        + name + ", idExtendsRuleSet=" + idExtendsRuleSet + "]";
  }

}
