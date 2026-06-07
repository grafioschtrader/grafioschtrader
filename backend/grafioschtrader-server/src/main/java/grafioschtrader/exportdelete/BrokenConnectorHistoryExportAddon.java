package grafioschtrader.exportdelete;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import grafiosch.entities.User;
import grafiosch.exportdelete.AdditionalExportQuery;
import grafiosch.exportdelete.IExportMyDataAddon;
import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository.HistoryConnectorExportCandidate;
import grafioschtrader.service.GlobalparametersService;

/**
 * Adds broken-EOD-connector information to the personal-data export (issue #30).
 *
 * <p>
 * When a user exports their data to migrate to a personal instance, the regular export deliberately omits the historical
 * prices of <b>active</b> securities, assuming they will be re-fetched from their EOD connector after re-import. That
 * assumption fails when an active security's history connector no longer works — its prices would be lost permanently.
 * </p>
 *
 * <p>
 * This addon detects such securities (connector retry budget exhausted, or the connector id is no longer a registered
 * {@link IFeedConnector}) and:
 * </p>
 * <ul>
 * <li>writes a plain-text report listing them into the export ZIP, and</li>
 * <li>additionally exports their {@code historyquote} rows into {@code gt_data.sql} so the prices survive re-import.</li>
 * </ul>
 */
@Component
public class BrokenConnectorHistoryExportAddon implements IExportMyDataAddon {

  /** ZIP entry name of the human-readable report. */
  static final String ZIP_ENTRY_NAME = "broken_history_connectors.txt";

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private MessageSource messageSource;

  /**
   * An active export-scope security whose history connector is considered broken.
   *
   * @param candidate        the security
   * @param connectorMissing true when the connector id is no longer registered; false when it is registered but its
   *                         retry budget is exhausted
   */
  private record BrokenSecurity(HistoryConnectorExportCandidate candidate, boolean connectorMissing) {
  }

  @Override
  public Map<String, String> getZipTextEntries(User user) {
    Locale locale = Locale.forLanguageTag(user.getLocaleStr());
    return Map.of(ZIP_ENTRY_NAME, buildReport(computeBroken(user), locale));
  }

  @Override
  public List<AdditionalExportQuery> getAdditionalExportQueries(User user) {
    List<BrokenSecurity> broken = computeBroken(user);
    if (broken.isEmpty()) {
      return Collections.emptyList();
    }
    String idList = broken.stream().map(bs -> bs.candidate().getIdSecuritycurrency().toString())
        .collect(Collectors.joining(", "));
    // Export the historyquote rows of the broken securities, excluding those already covered by HISTORYQUOTE_SELECT
    // (transaction-held securities active within the next month, and risk-free-rate underlyings) to avoid duplicate
    // primary keys on re-import.
    String sql = "SELECT h.* FROM historyquote h WHERE h.id_securitycurrency IN (" + idList + ")"
        + " AND h.id_securitycurrency NOT IN ("
        + " SELECT s.id_securitycurrency FROM transaction t JOIN security s ON t.id_securitycurrency = s.id_securitycurrency"
        + " WHERE t.id_tenant = " + user.getIdTenant() + " AND s.active_to_date < now() + interval 1 month"
        + " UNION SELECT rfm.id_securitycurrency FROM risk_free_rate_mapping rfm)";
    return List.of(new AdditionalExportQuery("historyquote", sql));
  }

  /**
   * Determines which active export-scope securities of the tenant have a broken history connector. A connector is broken
   * when it is no longer a registered {@link IFeedConnector} (fail-safe: an empty registry flags every candidate) or its
   * retry counter has reached the configured maximum.
   *
   * @param user the user whose data is being exported
   * @return the broken securities; empty when none
   */
  private List<BrokenSecurity> computeBroken(User user) {
    short maxRetry = globalparametersService.getMaxHistoryRetry();
    List<IFeedConnector> feedConnectors = securityJpaRepository.getFeedConnectors();
    List<HistoryConnectorExportCandidate> candidates = securityJpaRepository
        .findHistoryConnectorExportCandidates(user.getIdTenant(), LocalDate.now());
    List<BrokenSecurity> broken = new ArrayList<>();
    for (HistoryConnectorExportCandidate candidate : candidates) {
      boolean connectorMissing = ConnectorHelper.getConnectorByConnectorId(feedConnectors,
          candidate.getIdConnectorHistory(), FeedSupport.FS_HISTORY) == null;
      boolean retryExhausted = candidate.getRetryHistoryLoad() != null
          && candidate.getRetryHistoryLoad() >= maxRetry;
      if (connectorMissing || retryExhausted) {
        broken.add(new BrokenSecurity(candidate, connectorMissing));
      }
    }
    return broken;
  }

  /**
   * Builds the plain-text report listing the broken-connector securities, localized to the user's language.
   *
   * @param broken the broken securities
   * @param locale the user's locale
   * @return the report text (always non-empty; states explicitly when nothing was detected)
   */
  private String buildReport(List<BrokenSecurity> broken, Locale locale) {
    StringBuilder sb = new StringBuilder();
    String title = msg("gt.export.broken.connector.title", locale);
    sb.append(title).append('\n');
    sb.append("=".repeat(title.length())).append("\n\n");
    if (broken.isEmpty()) {
      sb.append(msg("gt.export.broken.connector.none", locale)).append('\n');
      return sb.toString();
    }
    sb.append(msg("gt.export.broken.connector.intro", locale)).append("\n\n");
    for (BrokenSecurity bs : broken) {
      HistoryConnectorExportCandidate c = bs.candidate();
      String reason = msg(bs.connectorMissing() ? "gt.export.broken.connector.reason.missing"
          : "gt.export.broken.connector.reason.retry", locale);
      sb.append("- ").append(c.getName());
      if (c.getIsin() != null) {
        sb.append(" (ISIN: ").append(c.getIsin()).append(')');
      }
      sb.append('\n');
      sb.append("    ").append(msg("gt.export.broken.connector.field.connector", locale)).append(": ")
          .append(c.getIdConnectorHistory()).append('\n');
      sb.append("    ").append(msg("gt.export.broken.connector.field.reason", locale)).append(": ").append(reason)
          .append('\n');
      sb.append("    ").append(msg("gt.export.broken.connector.field.retry", locale)).append(": ")
          .append(c.getRetryHistoryLoad()).append('\n');
      sb.append("    ").append(msg("gt.export.broken.connector.field.activeto", locale)).append(": ")
          .append(c.getActiveToDate()).append("\n\n");
    }
    return sb.toString();
  }

  private String msg(String key, Locale locale) {
    return messageSource.getMessage(key, null, locale);
  }
}
