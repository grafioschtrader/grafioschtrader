package grafioschtrader.dto;

/**
 * One aggregated finding of the hold-table consistency check: how many rows of one tenant disagree with the transactions
 * they are derived from, for one kind of disagreement.
 *
 * <p>
 * Produced by the {@code countConsistencyDefects} named queries of the three hold repositories and consumed by
 * {@code HoldTableConsistencyCheckTask}. The per-row detail deliberately stays out of the nightly job — the report is a
 * count so that it fits into one mail; {@code scripts/check-hold-tables.mjs} produces the row-level listing.
 * </p>
 */
public interface HoldConsistencyDefect {

  /**
   * The tenant whose hold rows disagree.
   *
   * @return the tenant id
   */
  Integer getIdTenant();

  /**
   * The kind of disagreement. {@code MISSING_ROW} a period that should exist does not, {@code EXTRA_ROW} a period exists
   * that no transaction justifies, {@code VALUES} a stored amount differs beyond the rounding tolerance,
   * {@code PERIOD_CHAIN} the from/to dates of consecutive periods do not join up or more than one period is open,
   * {@code TENANT_PORTFOLIO} the denormalised tenant or portfolio id is wrong, {@code ORPHAN_DATE} a holdings period
   * starts on a date that is neither a transaction nor a split, {@code ROW_HOLDINGS} a holdings row does not match the
   * split-adjusted unit sum as of its own start date, {@code INVERTED_PERIOD} a holdings period ends before it begins
   * and can therefore never be matched by a date range.
   *
   * @return the defect kind
   */
  String getDefect();

  /**
   * How many rows show this defect for this tenant.
   *
   * @return the number of affected rows
   */
  Long getDefectCount();
}
