package grafioschtrader.udffill.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.dto.RuleProposalExample;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.udffill.ai.RuleProposalPrompt.Candidate;
import grafioschtrader.udffill.ai.RuleProposalPrompt.Messages;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * What a rule proposal sends to the language model. No test calls a model.
 */
class RuleProposalPromptTest {

  private int nextId = 1;

  private Candidate candidate(String name, String mic, String currency, String value, Integer idTenantPrivate,
      boolean reproduced) {
    Stockexchange stockexchange = new Stockexchange();
    stockexchange.setMic(mic);
    Security security = new Security(name, currency, null, stockexchange, null, null, null,
        name.substring(0, 3).toUpperCase(), "US000000000" + nextId);
    security.setIdTenantPrivate(idTenantPrivate);
    security.setIdSecuritycurrency(nextId++);
    return new Candidate(security, value, reproduced);
  }

  @Test
  @DisplayName("The payload holds exactly the current rule and per example ISIN, ticker, name, MIC, currency, value")
  void payloadHoldsExactlyTheFields() {
    List<RuleProposalExample> examples = RuleProposalPrompt
        .selectExamples(List.of(candidate("Apple Inc.", "XNAS", "USD", "https://example.com/aapl", null, false)), 20);

    Messages messages = RuleProposalPrompt.build("https://example.com/{ticker|lower}", examples, "FORMAT");

    JsonNode payload = new ObjectMapper().readTree(messages.user());
    assertThat(fieldNames(payload)).containsExactly("currentRule", "examples");
    assertThat(payload.path("currentRule").asString()).isEqualTo("https://example.com/{ticker|lower}");
    JsonNode example = payload.path("examples").get(0);
    assertThat(fieldNames(example)).containsExactly("isin", "ticker", "name", "mic", "currency", "value");
    assertThat(example.path("value").asString()).isEqualTo("https://example.com/aapl");
    assertThat(messages.user()).doesNotContain("idSecuritycurrency");
  }

  @Test
  @DisplayName("The braces of the grammar reach the system message unchanged")
  void bracesReachTheMessageUnchanged() {
    Messages messages = RuleProposalPrompt.build(null, List.of(), "FORMAT {\"rule\": string}");

    assertThat(messages.system()).contains(
        "[mic=XNAS,XNYS] https://www.marketbeat.com/stocks/{mic|map(XNAS=NASDAQ,XNYS=NYSE)}/{ticker|urlencode}/short-interest/")
        .contains("{name|legal|slug}").contains("{{ and }}").endsWith("FORMAT {\"rule\": string}");
  }

  @Test
  @DisplayName("A private security never becomes an example")
  void privateSecurityNeverAppears() {
    List<RuleProposalExample> examples = RuleProposalPrompt
        .selectExamples(List.of(candidate("Private Fund", "XSWX", "CHF", "https://example.com/private", 7, false),
            candidate("Nestle SA", "XSWX", "CHF", "https://example.com/nesn", null, false)), 20);

    assertThat(examples).extracting(RuleProposalExample::name).containsExactly("Nestle SA");
    assertThat(RuleProposalPrompt.build(null, examples, "").user()).doesNotContain("Private Fund");
  }

  @Test
  @DisplayName("Groups by MIC and currency are taken round-robin by size; unreproduced values come first")
  void roundRobinSelection() {
    List<Candidate> candidates = new ArrayList<>();
    candidates.add(candidate("Zeta Corp", "XNAS", "USD", "z", null, false));
    candidates.add(candidate("Alpha Corp", "XNAS", "USD", "a", null, true));
    candidates.add(candidate("Beta Corp", "XNAS", "USD", "b", null, false));
    candidates.add(candidate("Nestle SA", "XSWX", "CHF", "n", null, false));
    candidates.add(candidate("Novartis AG", "XSWX", "CHF", "v", null, false));
    candidates.add(candidate("Siemens AG", "XETR", "EUR", "s", null, false));

    List<RuleProposalExample> examples = RuleProposalPrompt.selectExamples(candidates, 5);

    assertThat(examples).extracting(RuleProposalExample::name).containsExactly("Beta Corp", "Nestle SA", "Siemens AG",
        "Zeta Corp", "Novartis AG");
  }

  private static List<String> fieldNames(JsonNode node) {
    List<String> names = new ArrayList<>();
    node.propertyNames().forEach(names::add);
    return names;
  }
}
