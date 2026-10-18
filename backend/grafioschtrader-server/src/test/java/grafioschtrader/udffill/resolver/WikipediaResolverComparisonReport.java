package grafioschtrader.udffill.resolver;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import grafiosch.entities.User;
import grafiosch.repository.UserJpaRepository;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.repository.UDFMetadataSecurityJpaRepository;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.udffill.UDFFillScopeService;
import grafioschtrader.udffill.UDFFillTemplate;
import grafioschtrader.udffill.resolver.ResolveOutcome.Found;
import grafioschtrader.udffill.resolver.ResolveOutcome.NotFound;
import grafioschtrader.udffill.resolver.ResolveOutcome.NotFoundReason;
import grafioschtrader.udffill.resolver.ResolveOutcome.Retryable;

/**
 * Release check of the Wikipedia resolver, not a regression test: runs the resolver against the tenant scope of an
 * existing Wikipedia link field and compares every found link with the manual value. It lists identical and differing
 * links, the misses by reason and the links that are too long for the field. Nothing is written; the requests go out to
 * Wikidata.
 *
 * <p>
 * <b>Invocation</b> — double opt-in, because it reads the real database and calls a public source. The system property
 * names the field; the database password is supplied as described for the {@code prod} test profile:
 * </p>
 *
 * <pre>
 * cd backend
 * mvn -pl grafioschtrader-server test -Dtest=WikipediaResolverComparisonReport -Dgt.udf.fill.compare.field=&lt;id&gt;
 *     [-Dgt.udf.fill.compare.languages=de,en]
 * </pre>
 *
 * The report is written to {@value #OUTPUT} in the module directory. Its reference counts are those of the
 * specification of the automatic filling, measured with the language list {@code de,en}.
 */
@SpringBootTest(classes = GTforTest.class)
@ActiveProfiles("prod")
class WikipediaResolverComparisonReport {

  private static final String FIELD_PROPERTY = "gt.udf.fill.compare.field";
  private static final String LANGUAGES_PROPERTY = "gt.udf.fill.compare.languages";
  private static final String OUTPUT = "target/udf-fill-wikipedia-comparison.txt";

  private static final Logger log = LoggerFactory.getLogger(WikipediaResolverComparisonReport.class);

  @Autowired
  private UDFMetadataSecurityJpaRepository uMetaSecurityRepository;
  @Autowired
  private UserJpaRepository userJpaRepository;
  @Autowired
  private UDFFillScopeService scopeService;
  @Autowired
  private WikipediaUDFValueResolver resolver;

  @Test
  @DisplayName("Compare the Wikipedia resolver with the manual links of a field of the real database")
  void compareWithManualLinks() throws IOException {
    String idField = System.getProperty(FIELD_PROPERTY);
    assumeTrue(idField != null, () -> "Report is opt-in, run with -D" + FIELD_PROPERTY + "=<idUDFMetadata>");
    UDFMetadataSecurity field = uMetaSecurityRepository.findById(Integer.valueOf(idField)).orElseThrow();
    User owner = userJpaRepository.findById(field.getIdUser()).orElseThrow();
    String languages = resolver.normalizeArgument(System.getProperty(LANGUAGES_PROPERTY));
    Map<Integer, String> manual = scopeService.getFieldValues(owner.getIdUser(), field.getIdUDFMetadata());

    List<String> identical = new ArrayList<>();
    List<String> differing = new ArrayList<>();
    List<String> tooLong = new ArrayList<>();
    List<String> retry = new ArrayList<>();
    Map<NotFoundReason, List<String>> notFound = new EnumMap<>(NotFoundReason.class);
    for (Security security : scopeService.getTenantScope(field, owner.getIdTenant())) {
      String existing = manual.get(security.getIdSecuritycurrency());
      if (existing == null) {
        continue;
      }
      String line = security.getName() + " | " + security.getIsin() + " | " + existing;
      switch (resolver.resolve(security, languages)) {
      case Found found when UDFFillTemplate.validate(found.value(), field) != null -> tooLong
          .add(line + " | " + found.value());
      case Found found -> (found.value().equals(existing) ? identical : differing).add(line + " | " + found.value());
      case NotFound nf -> notFound.computeIfAbsent(nf.reason(), _ -> new ArrayList<>()).add(line);
      case Retryable r -> retry.add(line + " | " + r.reason());
      }
    }
    write(field, languages, manual.size(), identical, differing, tooLong, retry, notFound);
  }

  private void write(UDFMetadataSecurity field, String languages, int withValue, List<String> identical,
      List<String> differing, List<String> tooLong, List<String> retry, Map<NotFoundReason, List<String>> notFound)
      throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("Field ").append(field.getIdUDFMetadata()).append(" '").append(field.getDescription())
        .append("', languages ").append(languages).append(", values ").append(withValue).append('\n');
    section(sb, "Identical", identical);
    section(sb, "Differing (name | ISIN | manual | resolved)", differing);
    section(sb, "Invalid, too long for the field", tooLong);
    notFound.forEach((reason, lines) -> section(sb, "Not found: " + reason, lines));
    section(sb, "Retryable", retry);
    Path output = Path.of(OUTPUT);
    Files.createDirectories(output.getParent());
    Files.writeString(output, sb.toString(), StandardCharsets.UTF_8);
    log.info("Identical {}, differing {}, too long {}, retryable {}, not found {}; report in {}", identical.size(),
        differing.size(), tooLong.size(), retry.size(), notFound, output.toAbsolutePath());
  }

  private static void section(StringBuilder sb, String title, List<String> lines) {
    sb.append('\n').append(title).append(": ").append(lines.size()).append('\n');
    lines.forEach(l -> sb.append("  ").append(l).append('\n'));
  }
}
