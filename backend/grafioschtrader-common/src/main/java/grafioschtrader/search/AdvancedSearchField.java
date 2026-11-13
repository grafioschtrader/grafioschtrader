package grafioschtrader.search;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A field the user can choose in an advanced search criterion: a user-defined field of securities or an indicator
 * column of the tenant. The list is computed per user, so a field of another user or tenant is never offered.
 */
@Schema(description = "A user-defined field or an indicator column that an advanced search criterion can refer to.")
public class AdvancedSearchField {

  /** Prefix of the key of a user-defined field, followed by its metadata id. */
  public static final String KEY_PREFIX_UDF = "U";

  /** Prefix of the key of an indicator column, followed by its id. */
  public static final String KEY_PREFIX_INDICATOR = "I";

  /**
   * Most securities that are evaluated for an indicator column which needs a quote series, a benchmark or dividends.
   * More candidates abort the search.
   */
  public static final int MAX_INDICATOR_CANDIDATES_SERIES = 500;

  /**
   * Most securities that are evaluated for indicator columns that need only the stored prices and the fundamentals.
   * More candidates abort the search.
   */
  public static final int MAX_INDICATOR_CANDIDATES_LIGHT = 5000;

  @Schema(description = """
      Identifies the field in a criterion: 'U' and the metadata id of a user-defined field, or 'I' and the id of an
      indicator column.""")
  private final String key;

  @Schema(description = "Text of the field in the language of the user.")
  private final String label;

  @Schema(description = "Decides the operator and the comparison values of a criterion on this field.")
  private final AdvancedSearchValueType valueType;

  @Schema(description = """
      True for an indicator column whose evaluation needs a quote series, a benchmark or dividends. A search on such a
      column is limited to a smaller number of candidates.""")
  private final boolean candidateLimited;

  public AdvancedSearchField(String key, String label, AdvancedSearchValueType valueType, boolean candidateLimited) {
    this.key = key;
    this.label = label;
    this.valueType = valueType;
    this.candidateLimited = candidateLimited;
  }

  public String getKey() {
    return key;
  }

  public String getLabel() {
    return label;
  }

  public AdvancedSearchValueType getValueType() {
    return valueType;
  }

  public boolean isCandidateLimited() {
    return candidateLimited;
  }
}
