package grafioschtrader.gtnet.model;

/**
 * Represents the categorization scheme used for asset class sub-categories. Most GT installations use either
 * regional/geographical or sector/industry-based categorization for grouping assets within the same category type and
 * investment instrument.
 */
public enum SubCategoryScheme {

  /** Regional/geographical categorization (World, Emerging Markets, USA, Europe, etc.) */
  REGIONAL,

  /** Sector/industry categorization (Finance, Technology, Healthcare, etc.) */
  SECTOR,

  /** Unable to determine or mixed categorization */
  UNKNOWN
}
