/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.Assetclass;
import grafioschtrader.rest.UpdateCreateJpaRepository;

public interface AssetclassJpaRepository
    extends JpaRepository<Assetclass, Integer>, AssetclassJpaRepositoryCustom, UpdateCreateJpaRepository<Assetclass> {

  @Query("SELECT DISTINCT(a) FROM Watchlist w JOIN w.securitycurrencyList s JOIN s.assetClass a WHERE w.idTenant = ?1 "
      + "AND w.idWatchlist = ?2 AND a.specialInvestmentInstrument NOT IN (4,10)")
  List<Assetclass> getInvestableAssetclassesByWatchlist(Integer idTenant, Integer idWatchlist);

  @Query(value = "SELECT a.*, s.* FROM assetclass a, multilinguestrings s "
      + "WHERE a.category_type = ?1 AND a.spec_invest_instrument = ?2 "
      + "AND s.text = ?3 AND s.language = ?4 and a.sub_category_nls = s.id_string", nativeQuery = true)
  Assetclass findByCategorySpecInvestmentSubCategory(Byte categoryType, Byte specInvestment, String subCategory,
      String language);

  @Query(value = "SELECT (CASE WHEN EXISTS(SELECT NULL FROM security s WHERE s.id_asset_class = a.id_asset_class)"
      + " THEN 1 ELSE 0 END) AS has_security FROM assetclass a WHERE a.id_asset_class = ?1", nativeQuery = true)
  int assetclassHasSecurity(Integer idAssetClass);

  @Query(value = "SELECT a.id_asset_class, (CASE WHEN EXISTS(SELECT NULL FROM security s WHERE s.id_asset_class = a.id_asset_class)"
      + " THEN 1 ELSE 0 END) AS has_security FROM assetclass a", nativeQuery = true)
  List<Object[]> assetclassesHasSecurity();

  @Query(nativeQuery = true)
  List<Assetclass> getUnusedAssetclassForAlgo(Integer idTenant, Integer idAlgoAssetclassSecurity);

}
