package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertySelectiveUpdatableOrWhenNull;
import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.User;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.repository.RepositoryHelper;
import grafiosch.types.Language;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.types.SpecialInvestmentInstruments;

public class AssetclassJpaRepositoryImpl extends BaseRepositoryImpl<Assetclass>
    implements AssetclassJpaRepositoryCustom {

  @Autowired
  private AssetclassJpaRepository assetclassJpaRepository;

  @Override
  public List<Assetclass> getPossibleAssetclassForExistingSecurityOrAll(Integer idSecuritycurrency) {
    List<Assetclass> assetklassList = Collections.emptyList();
    if (idSecuritycurrency != null) {
      assetklassList = assetclassJpaRepository.getPossibleAssetclassForExistingSecurity(idSecuritycurrency);
    }
    return assetklassList.isEmpty() ? assetclassJpaRepository.findAll() : assetklassList;
  }

  @Override
  public Assetclass saveOnlyAttributes(final Assetclass assetclass, final Assetclass existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {

    SpecialInvestmentInstruments[] siis = Assetclass.possibleInstrumentsMap.get(assetclass.getCategoryType());
    if (!Arrays.asList(siis).contains(assetclass.getSpecialInvestmentInstrument())) {
      throw new IllegalArgumentException("The combination of assetcalss and finance instrument is not accepted!");
    }
    return RepositoryHelper.saveOnlyAttributes(assetclassJpaRepository, assetclass, existingEntity,
        updatePropertyLevelClasses);
  }

  @Override
  public Set<Class<? extends Annotation>> getUpdatePropertyLevels(final Assetclass existingAssetclass) {
    int numberOfSecurity = assetclassJpaRepository.assetclassHasSecurity(existingAssetclass.getIdAssetClass());
    return numberOfSecurity == 0 ? Set.of(PropertySelectiveUpdatableOrWhenNull.class, PropertyAlwaysUpdatable.class)
        : Set.of(PropertyAlwaysUpdatable.class);
  }

  @Override
  public List<ValueKeyHtmlSelectOptions> getSubcategoryForLanguage() {
    final List<ValueKeyHtmlSelectOptions> dropdownValues = new ArrayList<>();
    Language language = ((User) SecurityContextHolder.getContext().getAuthentication().getDetails()).getLanguage();
    List<String> subCategories = assetclassJpaRepository.findAll().stream()
        .map(assetclass -> assetclass.getSubCategoryByLanguage(language)).distinct().sorted()
        .collect(Collectors.toList());
    subCategories.forEach(sb -> dropdownValues.add(new ValueKeyHtmlSelectOptions(sb, sb)));
    return dropdownValues;
  }

}
