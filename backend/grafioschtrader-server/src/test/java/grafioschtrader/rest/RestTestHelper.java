package grafioschtrader.rest;

import java.util.List;

import grafiosch.test.rest.RestTestHelperBase;
import grafiosch.types.Language;
import grafioschtrader.entities.Assetclass;

/**
 * Grafioschtrader view on the shared integration test fixture. Everything generic — loading
 * {@code testdata/users.csv}, {@code users} / {@code ALL_USERS} / {@code LIMIT_USERS},
 * {@code getUserByNickname}, {@code inizializeUserTokens}, {@code getDiffPropertiesOfTwoObjects} — is inherited from
 * {@link RestTestHelperBase} and stays reachable through this class name, so existing call sites such as
 * {@code RestTestHelper.users} are unaffected.
 *
 * <p>
 * Only the nickname constants of {@code users.csv} and the Grafioschtrader specific lookups live here.
 */
public class RestTestHelper extends RestTestHelperBase {

  public static final String ADMIN = "admin";
  public static final String ALLEDIT = "alledit";
  public static final String USER = "user";
  public static final String LIMIT1 = "limit1";
  public static final String LIMIT2 = "limit2";

  /**
   * Picks the asset class matching a category / subcategory / instrument triple, because the generated ids differ per
   * database.
   *
   * @param assetclasses               the asset classes as returned by the endpoint
   * @param categoryType               the {@code AssetclassType} value
   * @param subCategoryDE              the German subcategory label, which is the stable identifier of a row
   * @param specialInvestmentInstrument the {@code SpecialInvestmentInstruments} value
   * @return the single matching asset class
   * @throws java.util.NoSuchElementException when no asset class matches
   */
  public static Assetclass getAssetclassBy(List<Assetclass> assetclasses, byte categoryType, String subCategoryDE,
      byte specialInvestmentInstrument) {
    return assetclasses.stream()
        .filter(a -> a.getCategoryType().getValue().equals(categoryType)
            && a.getSpecialInvestmentInstrument().getValue().equals(specialInvestmentInstrument)
            && a.getSubCategoryByLanguage(Language.GERMAN).equals(subCategoryDE))
        .findFirst().get();
  }
}
