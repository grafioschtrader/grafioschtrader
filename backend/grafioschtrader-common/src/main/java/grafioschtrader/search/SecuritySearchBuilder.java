package grafioschtrader.search;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Assetclass_;
import grafioschtrader.entities.MultilanguageString;
import grafioschtrader.entities.MultilanguageString_;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Security_;
import grafioschtrader.entities.Securitycurrency_;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.Stockexchange_;
import grafioschtrader.entities.User;
import grafioschtrader.types.Language;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.MapJoin;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/*
 *
 *
 * SELECT s.name FROM security s where s.name LIKE "%Gold%" AND s.id_securitycurrency NOT IN (Select
 * w.id_securitycurrency FROM watchlist_sec_cur w where w.id_watchlist = 7)
 *
 * SELECT s.name FROM security s where s.name LIKE "%Gold%" AND NOT EXISTS (Select null FROM watchlist_sec_cur w
 * where w.id_watchlist = 7 AND s.id_securitycurrency = w.id_securitycurrency)
 *
 *
 * https://stackoverflow.com/questions/13859780/building-a-query-using-not-exists-in-jpa-criteria-api
 *
 *
 */
public class SecuritySearchBuilder extends SecuritycurrencySearchBuilder implements Specification<Security> {

  private static final long serialVersionUID = 1L;

  final Integer idWatchlist;
  final Integer idCorrelationSet;
  final SecuritycurrencySearch securitycurrencySearch;
  final Integer idTenant;
  final List<Integer> idSecurityList;

  public SecuritySearchBuilder(final Integer idWatchlist, Integer idCorrelationSet,
      final SecuritycurrencySearch securitycurrencySearch, final Integer idTenant, List<Integer> idSecurityList) {
    this.idWatchlist = idWatchlist;
    this.idCorrelationSet = idCorrelationSet;
    this.securitycurrencySearch = securitycurrencySearch;
    this.idTenant = idTenant;
    this.idSecurityList = idSecurityList;
  }

  @Override
  public Predicate toPredicate(final Root<Security> securityRoot, final CriteriaQuery<?> query,
      final CriteriaBuilder builder) {
    final List<Predicate> mainPredicates = new ArrayList<>();

    if (idWatchlist != null) {
      mainPredicates
          .add(builder.not(builder.exists(subQueryForAddingWatchlist(idWatchlist, securityRoot, query, builder))));
    }

    if (idCorrelationSet != null) {
      mainPredicates.add(
          builder.not(builder.exists(subQueryForAddingCorrelationSet(idCorrelationSet, securityRoot, query, builder))));
    }

    if (idSecurityList != null) {
      Expression<Integer> exp = securityRoot.get(Securitycurrency_.idSecuritycurrency);
      mainPredicates.add(builder.and(exp.in(idSecurityList)));
    }

    if (securitycurrencySearch.getIsin() != null) {
      mainPredicates
          .add(builder.and(builder.equal(securityRoot.get(Security_.isin), securitycurrencySearch.getIsin())));
    } else {
      if (securitycurrencySearch.isExcludeDerivedSecurity()) {
        mainPredicates.add(builder.and(builder.isNull(securityRoot.get(Security_.idLinkSecuritycurrency))));
      }

      if (securitycurrencySearch.getLeverageFactor() != null && securitycurrencySearch.getLeverageFactor() != 0 ) {
        mainPredicates.add(builder
            .and(builder.equal(securityRoot.get(Security_.leverageFactor), securitycurrencySearch.getLeverageFactor())));
      }

      if (securitycurrencySearch.isOnlyTenantPrivate()) {
        mainPredicates.add(builder.and(builder.equal(securityRoot.get(Security_.idTenantPrivate), idTenant)));
      } else {
        mainPredicates.add(builder.and(builder.isNull(securityRoot.get(Security_.idTenantPrivate))));
      }

      if (securitycurrencySearch.getName() != null) {
        mainPredicates.add(builder.and(builder.like(builder.lower(securityRoot.get(Security_.name)),
            "%" + securitycurrencySearch.getName().toLowerCase() + "%")));
      }

      if (securitycurrencySearch.getTickerSymbol() != null) {
        mainPredicates.add(builder.and(builder.like(securityRoot.get(Security_.tickerSymbol),
            "%" + securitycurrencySearch.getTickerSymbol().toUpperCase() + "%")));
      }

      if (securitycurrencySearch.getIdConnectorHistory() != null) {
        mainPredicates.add(builder.and(builder.equal(securityRoot.get(Securitycurrency_.idConnectorHistory),
            securitycurrencySearch.getIdConnectorHistory())));
      }

      if (securitycurrencySearch.getIdConnectorIntra() != null) {
        mainPredicates.add(builder.and(builder.equal(securityRoot.get(Securitycurrency_.idConnectorIntra),
            securitycurrencySearch.getIdConnectorIntra())));
      }

      if (securitycurrencySearch.getCurrency() != null) {
        mainPredicates.add(
            builder.and(builder.equal(securityRoot.get(Security_.currency), securitycurrencySearch.getCurrency())));
      }

      addActiveDate(securityRoot, builder, mainPredicates);
      addFromToActiveDate(securityRoot, builder, mainPredicates);
      addAssetclassPredicate(securityRoot, builder, mainPredicates);
      addStockexchangePredicate(securityRoot, builder, mainPredicates);

    }

    final Predicate[] predicatesArray = new Predicate[mainPredicates.size()];
    return builder.and(mainPredicates.toArray(predicatesArray));

  }

  private void addActiveDate(final Root<Security> securityRoot, final CriteriaBuilder builder,
      final List<Predicate> mainPredicates) {
    if (securitycurrencySearch.getActiveDate() != null) {
      mainPredicates.add(builder.and(builder.lessThanOrEqualTo(securityRoot.<Date>get(Security_.activeFromDate),
          securitycurrencySearch.getActiveDate())));
      mainPredicates.add(builder.and(builder.greaterThanOrEqualTo(securityRoot.<Date>get(Security_.activeToDate),
          securitycurrencySearch.getActiveDate())));
    }
  }
  
  private void addFromToActiveDate(final Root<Security> securityRoot, final CriteriaBuilder builder,
      final List<Predicate> mainPredicates) {
    if(securitycurrencySearch.getMaxFromDate() != null && securitycurrencySearch.getMinToDate() != null) {
      mainPredicates.add(builder.and(builder.lessThanOrEqualTo(securityRoot.<Date>get(Security_.activeFromDate),
          securitycurrencySearch.getMinToDate())));
      mainPredicates.add(builder.and(builder.greaterThanOrEqualTo(securityRoot.<Date>get(Security_.activeToDate),
          securitycurrencySearch.getMaxFromDate())));
      
    }
  }
  

  private void addAssetclassPredicate(final Root<Security> securityRoot, final CriteriaBuilder builder,
      final List<Predicate> mainPredicates) {
    if (securitycurrencySearch.getAssetclassType() != null
        || securitycurrencySearch.getSpecialInvestmentInstruments() != null
        || securitycurrencySearch.getSubCategoryNLS() != null) {
      final Join<Security, Assetclass> joinAssetclass = securityRoot.join(Security_.assetClass);
      if (securitycurrencySearch.getAssetclassType() != null) {
        mainPredicates.add(builder.equal(joinAssetclass.get(Assetclass_.categoryType),
            securitycurrencySearch.getAssetclassType().getValue()));
      }
      if (securitycurrencySearch.getSpecialInvestmentInstruments() != null) {
        mainPredicates.add(builder.equal(joinAssetclass.get(Assetclass_.specialInvestmentInstrument),
            securitycurrencySearch.getSpecialInvestmentInstruments().getValue()));
      }
      if (securitycurrencySearch.getSubCategoryNLS() != null) {
        final Join<Assetclass, MultilanguageString> joinMultilanguage = joinAssetclass.join(Assetclass_.subCategoryNLS);
        MapJoin<MultilanguageString, String, String> mapJoin = joinMultilanguage.join(MultilanguageString_.map);
        Language language = ((User) SecurityContextHolder.getContext().getAuthentication().getDetails()).getLanguage();
        mainPredicates.add(builder.equal(mapJoin.key(), language.getKey()));
        mainPredicates.add(builder.equal(mapJoin.value(), securitycurrencySearch.getSubCategoryNLS()));
      }
    }
  }

  private void addStockexchangePredicate(final Root<Security> securityRoot, final CriteriaBuilder builder,
      final List<Predicate> mainPredicates) {

    if (securitycurrencySearch.getIdStockexchange() != null
        || securitycurrencySearch.getStockexchangeCounrtyCode() != null || securitycurrencySearch.isNoMarketValue()) {
      final Join<Security, Stockexchange> joinStockexchane = securityRoot.join(Security_.stockexchange);
      if (securitycurrencySearch.getIdStockexchange() != null) {
        mainPredicates.add(builder.equal(joinStockexchane.get(Stockexchange_.idStockexchange),
            securitycurrencySearch.getIdStockexchange()));
      }
      if (securitycurrencySearch.isNoMarketValue()) {
        mainPredicates.add(builder.equal(joinStockexchane.get(Stockexchange_.noMarketValue), false));
      }

      if (securitycurrencySearch.getStockexchangeCounrtyCode() != null) {
        mainPredicates.add(builder.equal(joinStockexchane.get(Stockexchange_.countryCode),
            securitycurrencySearch.getStockexchangeCounrtyCode()));
      }

    }
  }

}
