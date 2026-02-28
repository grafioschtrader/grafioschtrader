package grafioschtrader.types;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import grafiosch.common.EnumHelper;
import grafiosch.types.StableEnum;

/**
 * Bitmask flags for feed-type-specific processing options on generic connector endpoints. Each constant declares which
 * feed types it applies to via {@link #applicableFeedSupports}. The order of constants must not be changed because the
 * byte values are persisted as bitmask positions.
 */
public enum EndpointOption implements StableEnum {

  /**
   * Skip historical price rows that fall on Saturday or Sunday. Some data providers incorrectly deliver weekend data
   * that Grafioschtrader cannot process.
   */
  SKIP_WEEKEND_DATA((byte) 0, "FS_HISTORY"),

  /**
   * Remove duplicate date entries from the historical price response. Some generic connector sources return overlapping
   * data across pagination batches or duplicate rows within a single response. When enabled, only the first entry per
   * date is kept.
   */
  REMOVE_DUPLICATE_DATES((byte) 1, "FS_HISTORY");

  private final Byte value;
  private final String applicableFeedSupports;

  EndpointOption(final Byte value, final String applicableFeedSupports) {
    this.value = value;
    this.applicableFeedSupports = applicableFeedSupports;
  }

  @Override
  public Byte getValue() {
    return this.value;
  }

  public String getApplicableFeedSupports() {
    return this.applicableFeedSupports;
  }

  /**
   * Returns the subset of constants valid for the given feed type (e.g. "FS_HISTORY" or "FS_INTRA").
   */
  public static List<EndpointOption> getApplicableOptions(String feedSupport) {
    return Arrays.stream(values())
        .filter(o -> o.applicableFeedSupports.contains(feedSupport))
        .toList();
  }

  public static long encode(EnumSet<EndpointOption> set) {
    return EnumHelper.encodeEnumSet(set);
  }

  public static EnumSet<EndpointOption> decode(long encoded) {
    return EnumHelper.decodeEnumSet(EndpointOption.class, encoded);
  }
}
