package grafiosch.exportdelete;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import grafiosch.entities.User;

/**
 * Extension point that lets application modules contribute additional content to the personal-data export ZIP without
 * the base module having to know about application-specific entities.
 *
 * <p>
 * The base module ({@code grafiosch-server-base}) builds the export ZIP and must remain free of any application
 * dependency. Implementations of this interface are discovered as Spring beans and consulted while the ZIP is being
 * assembled. They can:
 * </p>
 * <ul>
 * <li>add extra rows to {@code gt_data.sql} via {@link #getAdditionalExportQueries(User)} — useful when the rows to
 * export depend on a runtime decision that a static {@link ExportDefinition} cannot express;</li>
 * <li>add standalone plain-text documents to the ZIP via {@link #getZipTextEntries(User)} — for example a human-readable
 * report about the exported data.</li>
 * </ul>
 *
 * <p>
 * Both methods default to contributing nothing, so an implementation can override only the part it needs.
 * </p>
 */
public interface IExportMyDataAddon {

  /**
   * Additional plain-text documents to add to the export ZIP, keyed by ZIP entry name (the file name inside the
   * archive, e.g. {@code "broken_history_connectors.txt"}). The value is the full UTF-8 text content of the entry.
   *
   * @param user the user whose data is being exported
   * @return a map of entry name to text content; empty by default
   * @throws Exception if the content cannot be produced
   */
  default Map<String, String> getZipTextEntries(User user) throws Exception {
    return Collections.emptyMap();
  }

  /**
   * Additional {@link AdditionalExportQuery}s whose rows are appended to {@code gt_data.sql} as {@code INSERT}
   * statements. Executed after the regular {@link ExportDefinition}-driven export, so any parent rows the returned rows
   * depend on (e.g. {@code securitycurrency} for {@code historyquote}) are already present.
   *
   * @param user the user whose data is being exported
   * @return the list of additional export queries; empty by default
   * @throws Exception if the queries cannot be produced
   */
  default List<AdditionalExportQuery> getAdditionalExportQueries(User user) throws Exception {
    return Collections.emptyList();
  }
}
