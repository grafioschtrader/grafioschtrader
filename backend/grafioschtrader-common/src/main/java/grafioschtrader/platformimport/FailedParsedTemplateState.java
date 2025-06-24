package grafioschtrader.platformimport;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Represents a template that failed to parse document data, containing diagnostic information about where parsing stopped")
public class FailedParsedTemplateState extends ParsedTemplateState {

  @Schema(description = "The last transaction field that was successfully matched before parsing failed, used for troubleshooting template issues")
  private String lastMatchingProperty;

  public FailedParsedTemplateState(String templatePurpose, Date validSince, String lastMatchingProperty) {
    super(templatePurpose, validSince);
    this.lastMatchingProperty = lastMatchingProperty;
  }

  public String getLastMatchingProperty() {
    return lastMatchingProperty;
  }

  public void setLastMatchingProperty(String lastMatchingProperty) {
    this.lastMatchingProperty = lastMatchingProperty;
  }

}
