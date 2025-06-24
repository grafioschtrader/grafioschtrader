package grafiosch.dto;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Password validation configuration with regex patterns and localized error messages")
public class PasswordRegexProperties implements IPropertiesSelfCheck {
  
  @Schema(description = "Regular expression pattern defining password strength requirements")
  public String regex;
  
  @Schema(description = "Localized error messages by language code for password validation failures")
  public Map<String, String> languageErrorMsgMap = new HashMap<>();
  
  @Schema(description = "Forces immediate password change after login if existing password doesn't meet regex requirements")
  public Boolean forceRegex;

  public PasswordRegexProperties() {
  }

  /**
   * Creates a fully configured password regex properties instance.
   * 
   * @param regex regular expression pattern for password validation
   * @param languageErrorMsgMap map of language codes to localized error messages
   * @param forceRegex flag indicating whether to enforce regex validation
   */
  public PasswordRegexProperties(String regex, Map<String, String> languageErrorMsgMap, Boolean forceRegex) {
    this.regex = regex;
    this.languageErrorMsgMap = languageErrorMsgMap;
    this.forceRegex = forceRegex;
  }

  /**
   * Validates the password regex properties configuration for completeness and correctness.
   * 
   * <p>Checks for required fields, validates regex pattern syntax, and ensures
   * complete internationalization coverage for all supported languages.</p>
   * 
   * @return null if configuration is valid, error message key if validation fails
   */
  @Override
  public String checkForValid() {
    Set<String> gtLangSet = new HashSet<>(BaseConstants.G_LANGUAGE_CODES);
    if (this.regex == null || forceRegex == null || !languageErrorMsgMap.keySet().containsAll(gtLangSet)) {
      return "password.regex.prop.missing";
    }
    try {
      Pattern.compile(regex);
    } catch (PatternSyntaxException exception) {
      return "password.regex.pattern.wrong";
    }
    return null;
  }

}
