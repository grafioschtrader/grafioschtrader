package grafiosch.dto;

import grafiosch.types.CountScope;
import grafiosch.types.LimitType;
import grafiosch.types.OwnerScope;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Everything the admin edit form needs to offer one limit key and validate a value entered for it. Returned by
 * {@code GET /entitylimit/keys}.
 *
 * <p>
 * A bare key/value pair such as {@code ValueKeyHtmlSelectOptions} cannot carry this: picking a key fixes four of the
 * five key columns as read-only fields of the form, and the value the admin types has to be validated against the
 * rule that belongs to the key rather than to the row.
 * </p>
 */
@Schema(description = """
    Describes one configurable limit key offered by the administration UI. Registered MAX keys carry a counter,
    scopes, a fresh-install default and the message key the REST limit contracts return; derived daily keys carry
    only the entity name.""")
public class LimitKeyDefinition {

  @Schema(description = """
      Stable wire id of the key: the five key parts joined with '|', empty segment for a null part, for example
      'MAX|Watchlist|Securitycurrency|SINGLE|TENANT' or 'DAY_CUD|Assetclass|||'. This is what the edit form posts
      back; the backend parses it and rejects anything not in the registry or the derived set.""")
  public final String keyId;

  @Schema(description = "Which mechanism the key configures")
  public final LimitType limitType;

  @Schema(description = "JPA entity simple name or a registered pseudo name such as 'HistoryquoteRead'")
  public final String entityName;

  @Schema(description = "Element entity of a nested-collection limit, null for a flat cap")
  public final String relationEntityName;

  @Schema(description = "Per parent or across all parents of the owner, null for a flat cap")
  public final CountScope countScope;

  @Schema(description = "Column by which existing rows are counted, null for the daily limit types")
  public final OwnerScope ownerScope;

  @Schema(description = "UPPER_SNAKE_CASE translation key under which the key is displayed")
  public final String labelKey;

  @Schema(description = """
      Validation rule for the limit value, in the same DSL as globalparameters.input_rule (for example
      'min:2,max:24'). Null when the key has no rule. The rule belongs to the key, so a per-role or per-user
      override is validated exactly like the ALL default row.""")
  public final String inputRule;

  @Schema(description = "Value a fresh installation seeds for this key. Null for the derived daily keys")
  public final Integer defaultValue;

  @Schema(description = """
      Message key the limit REST contracts return for this key, for example 'MAX_CASH_ACCOUNT'. Null for the daily
      keys. Carried here because the existing endpoints and the frontend depend on those exact strings and they
      cannot be derived from the entity name""")
  public final String msgKey;

  @Schema(description = """
      True for a registered MAX key, whose ALL row must always exist: its value is editable but the row itself
      cannot be deleted, because deleting it would silently turn a mandatory cap into no cap""")
  public final boolean mandatoryAllRow;

  @Schema(description = """
      True when the guarded entity holds data shared between all tenants, false when it belongs to one client or one
      user, null when the entity behind the name cannot be resolved. The administration table shows it as an icon,
      because the owner scope says the same thing only for the MAX family; the daily families have no scope""")
  public final Boolean sharedData;

  public LimitKeyDefinition(LimitKey limitKey, String labelKey, String inputRule, Integer defaultValue, String msgKey,
      boolean mandatoryAllRow, Boolean sharedData) {
    this.keyId = limitKey.keyId();
    this.limitType = limitKey.limitType();
    this.entityName = limitKey.entityName();
    this.relationEntityName = limitKey.relationEntityName();
    this.countScope = limitKey.countScope();
    this.ownerScope = limitKey.ownerScope();
    this.labelKey = labelKey;
    this.inputRule = inputRule;
    this.defaultValue = defaultValue;
    this.msgKey = msgKey;
    this.mandatoryAllRow = mandatoryAllRow;
    this.sharedData = sharedData;
  }
}
