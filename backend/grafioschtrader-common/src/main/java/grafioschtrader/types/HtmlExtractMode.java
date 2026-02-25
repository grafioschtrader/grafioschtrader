package grafioschtrader.types;

/**
 * Defines HTML extraction modes for parsing price data from web pages using JSoup.
 * REGEX_GROUPS: Select element text, clean it, then match a regex with capture groups (like FinanzenNET).
 * SPLIT_POSITIONS: Select element text, clean it, split by delimiter, access by position (like Comdirect).
 * MULTI_SELECTOR: Each field has its own individual CSS selector for extraction.
 */
public enum HtmlExtractMode {
  REGEX_GROUPS((byte) 1),
  SPLIT_POSITIONS((byte) 2),
  MULTI_SELECTOR((byte) 3);

  private final Byte value;

  private HtmlExtractMode(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static HtmlExtractMode getByValue(byte value) {
    for (HtmlExtractMode type : HtmlExtractMode.values()) {
      if (type.getValue() == value) {
        return type;
      }
    }
    return null;
  }
}
