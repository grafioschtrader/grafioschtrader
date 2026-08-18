package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.rest.UpdateCreateDeleteAuditResource;
import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.entities.TradingCalendarRuleSet;
import grafioschtrader.repository.TradingCalendarRuleSetJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestGTMappings.TRADINGCALENDARRULESET_MAP)
@Tag(name = RequestGTMappings.TRADINGCALENDARRULESET, description = "Controller for the trading calendar rule sets")
public class TradingCalendarRuleSetResource extends UpdateCreateDeleteAuditResource<TradingCalendarRuleSet> {

  @Autowired
  private TradingCalendarRuleSetJpaRepository tradingCalendarRuleSetJpaRepository;

  @Operation(summary = "Returns all trading calendar rule sets", description = """
      Each rule set carries the name of the set it extends and the number of stock exchanges using it, so the
      overview shows which sets are in use without a further request.""")
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<TradingCalendarRuleSet>> getAllRuleSets() {
    return new ResponseEntity<>(tradingCalendarRuleSetJpaRepository.getAllRuleSets(), HttpStatus.OK);
  }

  @Operation(summary = "Returns the rule sets as select options", description = """
      Used by the stock exchange dialog, where a rule set is the alternative to updating the trading calendar from a
      reference index.""")
  @GetMapping(value = "/options", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ValueKeyHtmlSelectOptions>> getRuleSetOptions() {
    return new ResponseEntity<>(tradingCalendarRuleSetJpaRepository.getRuleSetOptions(), HttpStatus.OK);
  }

  @Operation(summary = "Returns the rule set id of every MIC that has one", description = """
      The stock exchange dialog preselects the matching rule set as soon as a MIC is chosen.""")
  @GetMapping(value = "/micmapping", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Integer>> getRuleSetIdByMic() {
    return new ResponseEntity<>(tradingCalendarRuleSetJpaRepository.getRuleSetIdByMic(), HttpStatus.OK);
  }

  @Operation(summary = "Validates the YAML of a rule set", description = """
      Checks YAML syntax, the JSON Schema and the completeness of every rule. Returns the messages, empty when the
      YAML is valid.""")
  @PostMapping(value = "/validateyaml", produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<String>> validateYaml(@RequestBody String ruleYaml) {
    return new ResponseEntity<>(tradingCalendarRuleSetJpaRepository.validateRuleYaml(ruleYaml), HttpStatus.OK);
  }

  @Operation(summary = "Previews the closures of one year", description = """
      Resolves the rule set against the set it extends and returns the closure dates of the requested year with the
      name of the rule that produced each one. An unsaved YAML may be passed in the body to preview a change before
      saving it; the closures of the inherited rules are always taken from the stored sets.""")
  @PostMapping(value = "/{idTradingCalendarRuleSet}/preview", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SortedMap<String, String>> previewClosures(
      @PathVariable final Integer idTradingCalendarRuleSet,
      @RequestParam(required = false) final Integer year,
      @RequestBody(required = false) final String ruleYaml) {
    int evaluatedYear = year == null ? LocalDate.now().getYear() : year;
    return new ResponseEntity<>(tradingCalendarRuleSetJpaRepository.previewClosures(idTradingCalendarRuleSet, ruleYaml,
        evaluatedYear), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> deleteResource(@PathVariable final Integer id) {
    tradingCalendarRuleSetJpaRepository.assertDeletable(id);
    return super.deleteResource(id);
  }

  @Override
  protected UpdateCreateJpaRepository<TradingCalendarRuleSet> getUpdateCreateJpaRepository() {
    return tradingCalendarRuleSetJpaRepository;
  }


}
