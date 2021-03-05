package grafioschtrader.rest.helper;

import java.util.regex.Pattern;

import grafioschtrader.exceptions.GeneralNotTranslatedWithArgumentsException;

public interface RestHelper {
   public static void isDemoAccount(String demoAccountPattern, String userName) {
     Pattern pattern = Pattern.compile(demoAccountPattern);
     if(pattern.matcher(userName).matches()) {
       throw new GeneralNotTranslatedWithArgumentsException("gt.demo.func.not.available", null);
     }
   }
}
