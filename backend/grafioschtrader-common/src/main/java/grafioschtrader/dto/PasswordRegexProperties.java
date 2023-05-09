package grafioschtrader.dto;

import java.util.Map;

public class PasswordRegexProperties {
   public String regex;
   public Map<String, String> languageErrorMsgMap;
   public Boolean forceRegex;
  
   public PasswordRegexProperties(){
   }
   
   public PasswordRegexProperties(String regex, Map<String, String> languageErrorMsgMap, Boolean forceRegex) {
    this.regex = regex;
    this.languageErrorMsgMap = languageErrorMsgMap;
    this.forceRegex = forceRegex;
  }
   
   
}
