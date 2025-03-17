package grafioschtrader.repository;

import java.util.List;

import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.entities.Assetclass;

public interface AssetclassJpaRepositoryCustom extends BaseRepositoryCustom<Assetclass> {
  List<ValueKeyHtmlSelectOptions> getSubcategoryForLanguage();

  List<Assetclass> getPossibleAssetclassForExistingSecurityOrAll(Integer idSecuritycurrency);
}
