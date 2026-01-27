/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.entities.Assetclass;

public interface AssetclassJpaRepository
    extends JpaRepository<Assetclass, Integer>, AssetclassJpaRepositoryCustom, UpdateCreateJpaRepository<Assetclass> {

  /**
   * Retrieves all asset classes in the specified watchlist that are investable, excluding CFDs and non-investable
   * indices. CFD may not support algorithmic trading.
   *
   * @param idTenant    the tenant ID owning the watchlist
   * @param idWatchlist the watchlist ID
   * @return a list of Assetclass entities with specialInvestmentInstrument not in (4, 10)
   */
  @Query("""
      SELECT DISTINCT(a) FROM Watchlist w JOIN w.securitycurrencyList s JOIN s.assetClass a WHERE w.idTenant = ?1 AND w.idWatchlist = ?2
      AND a.specialInvestmentInstrument NOT IN (4,10)""")
  List<Assetclass> getInvestableAssetclassesByWatchlist(Integer idTenant, Integer idWatchlist);

  /**
   * Finds an asset class by category type, special investment instrument, and localized sub-category.
   *
   * @param categoryType   the asset class category type code
   * @param specInvestment the special investment instrument code
   * @param subCategory    the sub-category text to match
   * @param language       the language code for the sub-category localization
   * @return the matching Assetclass entity, or null if none found
   */
  @Query(value = """
      SELECT a.*, s.* FROM assetclass a, multilinguestrings s WHERE a.category_type = ?1 AND a.spec_invest_instrument = ?2 AND s.text = ?3
      AND s.language = ?4 and a.sub_category_nls = s.id_string""", nativeQuery = true)
  Assetclass findByCategorySpecInvestmentSubCategory(Byte categoryType, Byte specInvestment, String subCategory,
      String language);

  /**
   * Checks whether any Security exists for the given asset class.
   *
   * @param idAssetClass the asset class ID to check
   * @return 1 if at least one Security references the asset class, 0 otherwise
   */
  @Query(value = """
      SELECT (CASE WHEN EXISTS(SELECT NULL FROM security s WHERE s.id_asset_class = a.id_asset_class)THEN 1 ELSE 0 END) AS has_security
      FROM assetclass a WHERE a.id_asset_class = ?1""", nativeQuery = true)
  int assetclassHasSecurity(Integer idAssetClass);

  /**
   * Determines for each asset class whether it has any associated Security records. Used to decide whether an asset
   * class can still be deleted.
   *
   * @return a list of Object arrays, where each array contains: [0] = asset class ID (Integer), [1] = has_security flag
   *         (Integer, 1 if true, 0 if false)
   */
  @Query(value = """
      SELECT a.id_asset_class, (CASE WHEN EXISTS(SELECT NULL FROM security s WHERE s.id_asset_class = a.id_asset_class)
      THEN 1 ELSE 0 END) AS has_security FROM assetclass a""", nativeQuery = true)
  List<Object[]> assetclassesHasSecurity();

  /**
   * Retrieves all asset classes not yet assigned to the specified algorithmic asset‐class, excluding margin instruments
   * (CFD, spec_invest_instrument = 4).
   *
   * @param idTenant                 the tenant ID owning the algorithmic asset‐class
   * @param idAlgoAssetclassSecurity the algorithmic asset‐class security ID
   * @return a list of Assetclass entities available for assignment
   */
  @Query(nativeQuery = true)
  List<Assetclass> getUnusedAssetclassForAlgo(Integer idTenant, Integer idAlgoAssetclassSecurity);

  //@formatter:off
  /**
   * Finds asset classes matching the category and instrument type of an existing security
   * that has transaction history. If a security has a transaction, the category and type of instrument can no
   * longer be changed. Return of all asset classes that are still possible, if
   * transaction exists.
   * <p>
   * The method:
   * <ul>
   *   <li>Determines the asset class of the given Security (if it has any transactions)</li>
   *   <li>Returns all asset classes with the same category_type and spec_invest_instrument</li>
   * </ul>
   *
   * @param idSecuritycurrency the ID of the Security to base the search on
   * @return a list of Assetclass entities compatible with the security’s classification
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<Assetclass> getPossibleAssetclassForExistingSecurity(Integer idSecuritycurrency);

  /**
   * Finds all asset classes matching the given category type and special investment instrument. Used for GTNet asset
   * class matching where we first find candidates by type, then filter by subCategoryNLS and scheme in Java code.
   *
   * @param categoryType               the asset class category type code (byte value of AssetclassType)
   * @param specialInvestmentInstrument the special investment instrument code (byte value of SpecialInvestmentInstruments)
   * @return list of matching asset classes
   */
  List<Assetclass> findByCategoryTypeAndSpecialInvestmentInstrument(byte categoryType, byte specialInvestmentInstrument);

}
