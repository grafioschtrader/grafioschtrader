package grafioschtrader.dto;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import grafioschtrader.GlobalConstants;

public class PasswordRegexProperties implements IPropertiesSelfCheck {
  public String regex;
  public Map<String, String> languageErrorMsgMap = new HashMap<>();
  public Boolean forceRegex;

  public PasswordRegexProperties() {
  }

  public PasswordRegexProperties(String regex, Map<String, String> languageErrorMsgMap, Boolean forceRegex) {
    this.regex = regex;
    this.languageErrorMsgMap = languageErrorMsgMap;
    this.forceRegex = forceRegex;
  }

  public String checkForValid() {
    Set<String> gtLangSet = new HashSet<>(GlobalConstants.GT_LANGUAGE_CODES);
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
