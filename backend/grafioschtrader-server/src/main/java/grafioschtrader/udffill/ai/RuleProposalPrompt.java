package grafioschtrader.udffill.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import grafioschtrader.dto.RuleProposalExample;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds what a rule proposal sends to the language model, and selects the examples. The template grammar consists of
 * {@code {...}} placeholders, which a prompt template engine would take for its own parameters, so the messages are
 * built from already rendered strings and passed as fixed system and user messages. The complete payload is the
 * grammar, its two examples, the current rule if any, and per example ISIN, ticker, name, MIC, currency and value.
 */
public final class RuleProposalPrompt {

  /** At most this many examples are sent. */
  public static final int MAX_EXAMPLES = 20;

  /** The grammar of the fill source TEMPLATE, with the two examples of the specification. */
  public static final String GRAMMAR = """
      A rule is one or more lines. Empty lines and lines starting with # are ignored. Lines are tried in order; the \
      first line that produces a valid value wins, every other line falls through to the next one.

      A line may start with a condition [key=v1,v2;key2=v3] followed by at least one space. Keys are mic (MIC of the \
      stock exchange) and currency (ISO 4217); each key at most once; all keys of a condition must match exactly.

      The rest of the line is text with placeholders {name|filter|filter(arg,...)}. Names: isin, ticker, name, mic, \
      currency, yahooSymbol. A line falls through when a placeholder is empty after its filters. Text outside braces \
      is copied unchanged; {{ and }} stand for a literal brace. Filter arguments cannot contain , = ( ) | { }.

      Filters, applied left to right:
      - lower, upper: case conversion
      - ascii: transliteration to ASCII (Nestlé -> Nestle, Ørsted -> Orsted)
      - legal: removes parenthesised segments and the legal-form words AG SE SA SpA NV Inc Corp Corporation Ltd \
      Limited plc Co Company GmbH KG KGaA Group Holding Holdings, then a trailing & or ,
      - slug, slug(-): ascii, lower, every run of characters outside [a-z0-9] replaced by _ (or the given separator)
      - urlencode: URL encoding with %20 for a space
      - map(K=V,...): replaces an exact key by its value; an unmapped key yields empty, so the line falls through

      Example of a rule with two lines:
      # marketbeat only knows NASDAQ and NYSE listings; every other exchange stays empty
      [mic=XNAS,XNYS] https://www.marketbeat.com/stocks/{mic|map(XNAS=NASDAQ,XNYS=NYSE)}/{ticker|urlencode}/short-interest/
      https://www.finanzen.ch/dividende/{name|legal|slug}#bilanz-guv
      """;

  private static final String SYSTEM = """
      You write a rule in the template language below that computes the value of a user-defined field of a security \
      from its master data. The user gives you the current rule, if any, and examples of securities with the value \
      the field holds. Write the shortest rule that reproduces as many example values as possible. Values that no \
      rule of the language can reproduce may stay unmatched. Answer with the rule only, in the requested format.

      """;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /**
   * The two messages of one request.
   *
   * @param system the system message
   * @param user   the user message
   */
  public record Messages(String system, String user) {
  }

  /** A candidate of the example selection. */
  public record Candidate(Security security, String value, boolean reproduced) {
  }

  private RuleProposalPrompt() {
  }

  /**
   * Builds the messages.
   *
   * @param currentRule        the current template rule of the field, or null
   * @param examples           the examples to send
   * @param formatInstructions the instructions of the structured output converter
   * @return the system and the user message
   */
  public static Messages build(String currentRule, List<RuleProposalExample> examples, String formatInstructions) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("currentRule", currentRule);
    List<Map<String, String>> rows = new ArrayList<>();
    for (RuleProposalExample e : examples) {
      Map<String, String> row = new LinkedHashMap<>();
      row.put("isin", e.isin());
      row.put("ticker", e.ticker());
      row.put("name", e.name());
      row.put("mic", e.mic());
      row.put("currency", e.currency());
      row.put("value", e.value());
      rows.add(row);
    }
    payload.put("examples", rows);
    return new Messages(SYSTEM + GRAMMAR + "\n" + formatInstructions, OBJECT_MAPPER.writeValueAsString(payload));
  }

  /**
   * Selects the examples deterministically: private securities never take part; candidates are grouped by MIC and
   * currency, groups are taken round-robin ordered by group size descending, within a group securities whose value the
   * current rule does not reproduce come first, then by name.
   *
   * @param candidates the securities of the tenant scope with a value that no APPLIED row describes as automatic
   * @param max        the maximum number of examples
   * @return the selected examples in the order they are sent
   */
  public static List<RuleProposalExample> selectExamples(List<Candidate> candidates, int max) {
    Comparator<Candidate> inGroup = Comparator.comparing(Candidate::reproduced)
        .thenComparing(c -> Objects.toString(c.security().getName(), ""), String.CASE_INSENSITIVE_ORDER)
        .thenComparing(c -> c.security().getIdSecuritycurrency());
    Map<String, List<Candidate>> groups = candidates.stream()
        .filter(c -> c.security().getIdTenantPrivate() == null && c.value() != null && !c.value().isEmpty())
        .collect(Collectors.groupingBy(RuleProposalPrompt::groupKey, LinkedHashMap::new, Collectors.toList()));
    List<List<Candidate>> ordered = groups.entrySet().stream()
        .sorted(Comparator.<Map.Entry<String, List<Candidate>>>comparingInt(e -> -e.getValue().size())
            .thenComparing(Map.Entry::getKey))
        .map(e -> e.getValue().stream().sorted(inGroup).toList()).toList();
    List<RuleProposalExample> selected = new ArrayList<>();
    for (int round = 0; selected.size() < max; round++) {
      boolean any = false;
      for (List<Candidate> group : ordered) {
        if (round < group.size() && selected.size() < max) {
          selected.add(toExample(group.get(round)));
          any = true;
        }
      }
      if (!any) {
        break;
      }
    }
    return selected;
  }

  private static String groupKey(Candidate c) {
    Stockexchange se = c.security().getStockexchange();
    return (se == null || se.getMic() == null ? "" : se.getMic()) + "|"
        + Objects.toString(c.security().getCurrency(), "").toUpperCase(Locale.ROOT);
  }

  private static RuleProposalExample toExample(Candidate c) {
    Security s = c.security();
    Stockexchange se = s.getStockexchange();
    return new RuleProposalExample(s.getIdSecuritycurrency(), s.getIsin(), s.getTickerSymbol(), s.getName(),
        se == null ? null : se.getMic(), s.getCurrency(), c.value());
  }
}
