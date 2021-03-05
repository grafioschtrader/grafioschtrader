package grafioschtrader.reports;

import java.lang.reflect.InvocationTargetException;

import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.entities.Security;
import grafioschtrader.entities.User;
import grafioschtrader.types.Language;

public class SecurityGroupByAssetclassSubCategoryReport extends SecurityGroupByBaseReport<String> {

  private Language language;

  public SecurityGroupByAssetclassSubCategoryReport() {
    super(null);
    language = ((User) SecurityContextHolder.getContext().getAuthentication().getDetails()).getLanguage();
  }

  @Override
  protected String getGroupValue(Security security)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    return security.getAssetClass().getSubCategoryByLanguage(language);
  }

}
