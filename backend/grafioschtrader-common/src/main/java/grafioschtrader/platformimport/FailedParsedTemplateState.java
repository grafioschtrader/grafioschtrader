package grafioschtrader.platformimport;

import java.util.Date;

/**
 * A template can fail to parse form data. An instance of this class references
 * the failed template and contains the last matching property.
 *
 * @author Hugo Graf
 *
 */
public class FailedParsedTemplateState extends ParsedTemplateState {

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
