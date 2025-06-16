package grafioschtrader.reports;

import java.lang.reflect.InvocationTargetException;

import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.entities.User;
import grafiosch.types.Language;
import grafioschtrader.entities.Security;

/**
 * Specialized security position report that groups portfolio holdings by asset class subcategory with
 * internationalization support. Provides detailed portfolio allocation analysis at the subcategory level, displaying
 * results in the user's preferred language for enhanced accessibility and regional compliance requirements.
 * 
 * <p>
 * This report extends the basic grouping function to focus specifically on subcategories of asset classes that offer a
 * more detailed classification than the main asset classes. Within the EQUITIES asset class, subcategories could
 * include, for example, “emerging markets,” “developed countries,” etc.
 * </p>
 */
public class SecurityGroupByAssetclassSubCategoryReport extends SecurityGroupByBaseReport<String> {

  /** This subgroup is defined per language, so the grouping must also be based on the user's language. */
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
