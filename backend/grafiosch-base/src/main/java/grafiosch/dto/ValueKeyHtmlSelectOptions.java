package grafiosch.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Represents an option for HTML select elements, containing an internal key (identifier) and a display value.")
public class ValueKeyHtmlSelectOptions implements Serializable, Comparable<ValueKeyHtmlSelectOptions> {

  @Schema(description = "Internal key or identifier for the option")
  public String key;
  
  @Schema(description = "The display value shown to the user in the select dropdown.")
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