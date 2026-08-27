package grafiosch.gtnet;

import grafiosch.repository.GTNetJpaRepositoryImpl;

/**
 * Extension point through which a consuming application widens the GTNet data export beyond the library tables.
 *
 * <p>
 * {@code GTNetDataExportResource} exports the tables of {@code GTNetJpaRepositoryImpl.GTNET_BASE_TABLES_DELETE_ORDER}
 * under the marker {@link #BASE_EXPORT_HEADER} when no bean of this type exists. An application that owns further GTNet
 * tables - Grafioschtrader owns the price pool, the supplier detail subclasses and the security-import staging tables -
 * contributes a single bean and thereby replaces marker and table lists wholesale, so the file it produces stays
 * exactly the file it produced before this extension point existed.
 * </p>
 *
 * <p>
 * At most one bean may be defined; a second one is an ambiguity Spring reports at startup.
 * </p>
 */
public interface GTNetExportTables {

  /**
   * Marker of an export that carries the library tables alone. An importer refuses a file whose first line is not its
   * own marker, so this constant is what separates a base export from an application export.
   */
  String BASE_EXPORT_HEADER = "-- GTNET_EXPORT_V1_BASE";

  /**
   * Returns the marker line the export starts with and the import insists on.
   *
   * @return the header comment, beginning with {@code --}
   */
  String header();

  /**
   * Returns the tables that are emptied on import but never exported, because a background job rebuilds them. Children
   * of the exported tables come first.
   *
   * @return table names in delete order, possibly empty
   */
  String[] deleteOnlyTables();

  /**
   * Returns the tables that are both exported and emptied on import, in delete order - children first. The exporter
   * walks the array backwards for the INSERT statements, so the order is simultaneously the parent-first insert order.
   *
   * @return table names in delete order, never empty
   * @see GTNetJpaRepositoryImpl#GTNET_BASE_TABLES_DELETE_ORDER
   */
  String[] exportAndDeleteTables();
}
