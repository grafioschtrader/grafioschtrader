package grafiosch.dto;

import grafiosch.types.CountScope;
import grafiosch.types.LimitType;
import grafiosch.types.OwnerScope;

/**
 * Identifies one configurable limit. It is the tuple stored in the five key columns of {@code entity_limit} and the
 * unit the resolver and the limit key registry work with.
 *
 * <p>
 * The key space has two halves. {@link LimitType#MAX} keys are registered explicitly, because a counter, a parent
 * relation, two scopes and a message key cannot be derived from an entity name. {@link LimitType#DAY_CUD} and
 * {@link LimitType#DAY_READ} keys are derived from the JPA metamodel plus the registered pseudo entity names, because
 * their counter is always {@code user_entity_change_count} and they have neither relation nor scopes.
 * </p>
 *
 * @param limitType          which mechanism the key configures, never {@code null}
 * @param entityName         JPA entity simple name or a registered pseudo name such as {@code HistoryquoteRead}
 * @param relationEntityName element entity of a nested-collection limit, {@code null} for a flat cap
 * @param countScope         per parent or across all parents, {@code null} for a flat cap
 * @param ownerScope         how existing rows are counted, {@code null} for the daily types
 */
public record LimitKey(LimitType limitType, String entityName, String relationEntityName, CountScope countScope,
    OwnerScope ownerScope) {

  /** Separator of the wire representation. Not legal in any entity name or enum constant. */
  public static final String SEPARATOR = "|";

  private static final String SEPARATOR_REGEX = "\\|";

  public LimitKey {
    if (limitType == null || entityName == null || entityName.isBlank()) {
      throw new IllegalArgumentException("A limit key requires a limit type and an entity name");
    }
  }

  /** Flat lifetime cap without a parent relation. */
  public static LimitKey max(String entityName, OwnerScope ownerScope) {
    return new LimitKey(LimitType.MAX, entityName, null, null, ownerScope);
  }

  /** Lifetime cap on the elements of a nested collection. */
  public static LimitKey max(String entityName, String relationEntityName, CountScope countScope,
      OwnerScope ownerScope) {
    return new LimitKey(LimitType.MAX, entityName, relationEntityName, countScope, ownerScope);
  }

  public static LimitKey dayCud(String entityName) {
    return new LimitKey(LimitType.DAY_CUD, entityName, null, null, null);
  }

  public static LimitKey dayRead(String entityName) {
    return new LimitKey(LimitType.DAY_READ, entityName, null, null, null);
  }

  /**
   * Stable wire identifier: the five parts joined with {@value #SEPARATOR}, an empty segment for every {@code null}
   * part. This is what the admin edit form posts back, so it must stay parseable by {@link #parse(String)}.
   *
   * @return for example {@code MAX|Watchlist|Securitycurrency|SINGLE|TENANT} or {@code DAY_CUD|Assetclass|||}
   */
  public String keyId() {
    return String.join(SEPARATOR, limitType.name(), entityName, nullToEmpty(relationEntityName),
        countScope == null ? "" : countScope.name(), ownerScope == null ? "" : ownerScope.name());
  }

  /**
   * Reverses {@link #keyId()}. The caller must still check the result against the registry or the derived set — a
   * syntactically valid key id is not necessarily a key that exists.
   *
   * @param keyId the five parts joined with {@value #SEPARATOR}
   * @return the parsed key
   * @throws IllegalArgumentException when the shape or one of the enum constants is unknown
   */
  public static LimitKey parse(String keyId) {
    if (keyId == null) {
      throw new IllegalArgumentException("Limit key id must not be null");
    }
    String[] parts = keyId.split(SEPARATOR_REGEX, -1);
    if (parts.length != 5) {
      throw new IllegalArgumentException("Limit key id must have five parts: " + keyId);
    }
    return new LimitKey(LimitType.valueOf(parts[0]), parts[1], emptyToNull(parts[2]),
        parts[3].isEmpty() ? null : CountScope.valueOf(parts[3]),
        parts[4].isEmpty() ? null : OwnerScope.valueOf(parts[4]));
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static String emptyToNull(String value) {
    return value.isEmpty() ? null : value;
  }
}
