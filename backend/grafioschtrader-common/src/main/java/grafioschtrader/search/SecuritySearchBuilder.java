package grafioschtrader.search;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.MapJoin;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Assetclass_;
import grafioschtrader.entities.MultilanguageString;
import grafioschtrader.entities.MultilanguageString_;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Security_;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.Stockexchange_;
import grafioschtrader.entities.User;
import grafioschtrader.types.Language;

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

  public SecuritySearchBuilder(final Integer idWatchlist, Integer idCorrelationSet,
      final SecuritycurrencySearch securitycurrencySearch, final Integer idTenant) {
    this.idWatchlist = idWatchlist;
    this.idCorrelationSet = idCorrelationSet;
    this.securitycurrencySearch = securitycurrencySearch;
    this.idTenant = idTenant;
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

    if (securitycurrencySearch.isin != null) {
      mainPredicates.add(builder.and(builder.equal(securityRoot.get(Security_.isin), securitycurrencySearch.isin)));
    } else {
      if (securitycurrencySearch.excludeDerivedSecurity) {
        mainPredicates.add(builder.and(builder.isNull(securityRoot.get(Security_.idLinkSecuritycurrency))));
      }

      if (securitycurrencySearch.shortSecurity != null) {
        mainPredicates.add(builder
            .and(builder.equal(securityRoot.get(Security_.shortSecurity), securitycurrencySearch.shortSecurity)));
      }

      if (securitycurrencySearch.onlyTenantPrivate) {
        mainPredicates.add(builder.and(builder.equal(securityRoot.get(Security_.idTenantPrivate), idTenant)));
      } else {
        mainPredicates.add(builder.and(builder.isNull(securityRoot.get(Security_.idTenantPrivate))));
      }

      if (securitycurrencySearch.name != null) {
        mainPredicates.add(builder.and(builder.like(builder.lower(securityRoot.get(Security_.name)),
            "%" + securitycurrencySearch.name.toLowerCase() + "%")));
      }

      if (securitycurrencySearch.tickerSymbol != null) {
        mainPredicates.add(builder.and(builder.like(securityRoot.get(Security_.tickerSymbol),
            "%" + securitycurrencySearch.tickerSymbol.toUpperCase() + "%")));
      }

      if (securitycurrencySearch.idConnectorHistory != null) {
        mainPredicates.add(builder.and(
            builder.equal(securityRoot.get(Security_.idConnectorHistory), securitycurrencySearch.idConnectorHistory)));
      }

      if (securitycurrencySearch.idConnectorIntra != null) {
        mainPredicates.add(builder
            .and(builder.equal(securityRoot.get(Security_.idConnectorIntra), securitycurrencySearch.idConnectorIntra)));
      }

      if (securitycurrencySearch.currency != null) {
        mainPredicates
            .add(builder.and(builder.equal(securityRoot.get(Security_.currency), securitycurrencySearch.currency)));
      }

      addActiveDate(securityRoot, builder, mainPredicates);
      addAssetclassPredicate(securityRoot, builder, mainPredicates);
      addStockexchangePredicate(securityRoot, builder, mainPredicates);

    }

    final Predicate[] predicatesArray = new Predicate[mainPredicates.size()];
    return builder.and(mainPredicates.toArray(predicatesArray));

  }

  private void addActiveDate(final Root<Security> securityRoot, final CriteriaBuilder builder,
      final List<Predicate> mainPredicates) {
    if (securitycurrencySearch.activeDate != null) {
      mainPredicates.add(builder.and(builder.lessThanOrEqualTo(securityRoot.<Date>get(Security_.activeFromDate),
          securitycurrencySearch.getActiveDate())));
      mainPredicates.add(builder.and(builder.greaterThanOrEqualTo(securityRoot.<Date>get(Security_.activeToDate),
          securitycurrencySearch.getActiveDate())));
    }
  }

  private void addAssetclassPredicate(final Root<Security> securityRoot, final CriteriaBuilder builder,
      final List<Predicate> mainPredicates) {
    if (securitycurrencySearch.assetclassType != null || securitycurrencySearch.specialInvestmentInstruments != null
        || securitycurrencySearch.getSubCategoryNLS() != null) {
      final Join<Security, Assetclass> joinAssetclass = securityRoot.join(Security_.assetClass);
      if (securitycurrencySearch.assetclassType != null) {
        mainPredicates.add(builder.equal(joinAssetclass.get(Assetclass_.categoryType),
            securitycurrencySearch.assetclassType.getValue()));
      }
      if (securitycurrencySearch.specialInvestmentInstruments != null) {
        mainPredicates.add(builder.equal(joinAssetclass.get(Assetclass_.specialInvestmentInstrument),
            securitycurrencySearch.specialInvestmentInstruments.getValue()));
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
    if (securitycurrencySearch.idStockexchange != null || securitycurrencySearch.stockexchangeCounrtyCode != null) {
      final Join<Security, Stockexchange> joinStockexchane = securityRoot.join(Security_.stockexchange);
      if (securitycurrencySearch.idStockexchange != null) {
        mainPredicates.add(builder.equal(joinStockexchane.get(Stockexchange_.idStockexchange),
            securitycurrencySearch.idStockexchange));
      }
      if (securitycurrencySearch.stockexchangeCounrtyCode != null) {
        mainPredicates.add(builder.equal(joinStockexchane.get(Stockexchange_.countryCode),
            securitycurrencySearch.stockexchangeCounrtyCode));
      }
    }

  }

}
