package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.entities.Assetclass;

public interface AssetclassJpaRepositoryCustom extends BaseRepositoryCustom<Assetclass> {
  List<ValueKeyHtmlSelectOptions> getSubcategoryForLanguage();
}
