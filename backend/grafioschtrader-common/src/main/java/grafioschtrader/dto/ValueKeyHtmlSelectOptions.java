package grafioschtrader.dto;

import java.io.Serializable;

public class ValueKeyHtmlSelectOptions implements Serializable, Comparable<ValueKeyHtmlSelectOptions> {

  public String key;
  public String value;

  private static final long serialVersionUID = 1L;

  public ValueKeyHtmlSelectOptions() {
  }

  public ValueKeyHtmlSelectOptions(final String key, final String value) {
    this.key = key;
    this.value = value;
  }

  @Override
  public int compareTo(ValueKeyHtmlSelectOptions vKhso) {
    return this.value.compareTo(vKhso.value);
  }

}