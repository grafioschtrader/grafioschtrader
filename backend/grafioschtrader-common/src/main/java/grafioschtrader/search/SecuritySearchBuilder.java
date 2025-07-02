package grafioschtrader.search;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.entities.MultilanguageString;
import grafiosch.entities.MultilanguageString_;
import grafiosch.entities.User;
import grafiosch.types.Language;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Assetclass_;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Security_;
import grafioschtrader.entities.Securitycurrency_;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.Stockexchange_;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.MapJoin;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * JPA Criteria API Specification implementation for comprehensive security searching.
 * <p>
 * This class builds dynamic queries to search for securities based on a wide variety of criteria while optionally
 * excluding those already present in watchlists or correlation sets. It implements the Spring Data JPA Specification
 * pattern to provide type-safe and composable query building for {@link Security} entities.
 * </p>
 * <p>
 * The search supports extensive filtering capabilities including:
 * <ul>
 * <li><strong>Identification:</strong> ISIN, name, ticker symbol</li>
 * <li><strong>Classification:</strong> Asset class type, investment instrument type, subcategory</li>
 * <li><strong>Trading:</strong> Stock exchange, currency, active date ranges</li>
 * <li><strong>Access:</strong> Tenant privacy, leverage factor, derived securities</li>
 * <li><strong>Data feeds:</strong> Historical and intraday connector IDs</li>
 * <li><strong>Exclusions:</strong> Items already in watchlists or correlation sets</li>
 * </ul>
 * </p>
 * <p>
 *
 * SQL equivalent examples:
 * 
 * SELECT s.name FROM security s where s.name LIKE "%Gold%" AND s.id_securitycurrency NOT IN (Select
 * w.id_securitycurrency FROM watchlist_sec_cur w where w.id_watchlist = 7)
 *
 * SELECT s.name FROM security s where s.name LIKE "%Gold%" AND NOT EXISTS (Select null FROM watchlist_sec_cur w where
 * w.id_watchlist = 7 AND s.id_securitycurrency = w.id_securitycurrency)
 *
 * https://stackoverflow.com/questions/13859780/building-a-query-using-not-exists-in-jpa-criteria-api
 *
 *
 */
public class SecuritySearchBuilder extends SecuritycurrencySearchBuilder implements Specification<Security> {

  private static final long serialVersionUID = 1L;

  /** Tenant ID for privacy security filtering and access control */
  final Integer idTenant;

  /** Specific list of security IDs to include in results (optional) */
  final List<Integer> idSecurityList;

  /**
   * Constructs a new SecuritySearchBuilder with the specified search parameters.
   * 
   * @param idWatchlist            the ID of a watchlist to exclude securities from, or null if not filtering by
   *                               watchlist
   * @param idCorrelationSet       the ID of a correlation set to exclude securities from, or null if not filtering by
   *                               correlation set
   * @param securitycurrencySearch the search criteria containing various filter options (must not be null)
   * @param idTenant               the tenant ID for privacy filtering and access control (must not be null)
   * @param idSecurityList         specific security IDs to include, or null to search all accessible securities
   */
  public SecuritySearchBuilder(final Integer idWatchlist, Integer idCorrelationSet,
      final SecuritycurrencySearch securitycurrencySearch, final Integer idTenant, List<Integer> idSecurityList) {
    super(idWatchlist, idCorrelationSet, securitycurrencySearch);
    this.idTenant = idTenant;
    this.idSecurityList = idSecurityList;
  }

  /**
   * Builds the JPA Criteria API predicate for searching securities.
   * <p>
   * This method constructs a comprehensive compound predicate based on the provided search criteria. The filtering
   * logic follows this priority:
   * </p>
   * <ol>
   * <li><strong>Exclusion filters:</strong> Excludes securities already in specified watchlist or correlation set</li>
   * <li><strong>ID list filter:</strong> Restricts to specific security IDs if provided</li>
   * <li><strong>ISIN exact match:</strong> If ISIN is provided, only this filter is applied (short-circuit)</li>
   * <li><strong>Comprehensive filtering:</strong> Applies all other criteria including derived securities, tenant
   * privacy, name/ticker matching, connectors, currency, dates, asset class, and stock exchange</li>
   * </ol>
   * <p>
   * The method uses helper methods to organize filtering logic for dates, asset classes, and stock exchanges.
   * Multilanguage support is provided for asset class subcategories using the current user's language preference.
   * </p>
   * 
   * @param securityRoot the root entity for the query (Security)
   * @param query        the criteria query being built
   * @param builder      the criteria builder for constructing predicates and expressions
   * @return a compound predicate that combines all specified search criteria with AND logic
   */
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
      if (securitycurrencySearch.getLeverageFactor() != null && securitycurrencySearch.getLeverageFactor() != 0) {
        mainPredicates.add(builder.and(
            builder.equal(securityRoot.get(Security_.leverageFactor), securitycurrencySearch.getLeverageFactor())));
      }
      if (securitycurrencySearch.isOnlyTenantPrivate() != null) {
        if (securitycurrencySearch.isOnlyTenantPrivate()) {
          // Only records where idTenantPrivate matches idTenant
          mainPredicates.add(builder.equal(securityRoot.get(Security_.idTenantPrivate), idTenant));
        } else {
          // Only records where idTenantPrivate is null
          mainPredicates.add(builder.isNull(securityRoot.get(Security_.idTenantPrivate)));
        }
      } else {
        // isOnlyTenantPrivate is null, no filtering based on idTenantPrivate
        mainPredicates.add(builder.or(builder.equal(securityRoot.get(Security_.idTenantPrivate), idTenant),
            builder.isNull(securityRoot.get(Security_.idTenantPrivate))));
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

  /**
   * Adds predicates to filter securities that are active on a specific date.
   * <p>
   * A security is considered active on a date if:
   * <ul>
   * <li>The date is on or after the security's active-from date</li>
   * <li>The date is on or before the security's active-to date</li>
   * </ul>
   * </p>
   * 
   * @param securityRoot   the root entity for the query
   * @param builder        the criteria builder for constructing predicates
   * @param mainPredicates the list to add the date predicates to
   * 
   * @see SecuritycurrencySearch#getActiveDate()
   */
  private void addActiveDate(final Root<Security> securityRoot, final CriteriaBuilder builder,
      final List<Predicate> mainPredicates) {
    if (securitycurrencySearch.getActiveDate() != null) {
      mainPredicates.add(builder.and(builder.lessThanOrEqualTo(securityRoot.<Date>get(Security_.activeFromDate),
          securitycurrencySearch.getActiveDate())));
      mainPredicates.add(builder.and(builder.greaterThanOrEqualTo(securityRoot.<Date>get(Security_.activeToDate),
          securitycurrencySearch.getActiveDate())));
    }
  }

  /**
   * Adds predicates to filter securities whose active period overlaps with a specified date range.
   * <p>
   * A security's active period overlaps with the search range if:
   * <ul>
   * <li>The security's active-from date is on or before the search range's end date</li>
   * <li>The security's active-to date is on or after the search range's start date</li>
   * </ul>
   * This ensures that any security that was active for any part of the specified period is included.
   * </p>
   * 
   * @param securityRoot   the root entity for the query
   * @param builder        the criteria builder for constructing predicates
   * @param mainPredicates the list to add the date range predicates to
   */
  private void addFromToActiveDate(final Root<Security> securityRoot, final CriteriaBuilder builder,
      final List<Predicate> mainPredicates) {
    if (securitycurrencySearch.getMaxFromDate() != null && securitycurrencySearch.getMinToDate() != null) {
      mainPredicates.add(builder.and(builder.lessThanOrEqualTo(securityRoot.<Date>get(Security_.activeFromDate),
          securitycurrencySearch.getMinToDate())));
      mainPredicates.add(builder.and(builder.greaterThanOrEqualTo(securityRoot.<Date>get(Security_.activeToDate),
          securitycurrencySearch.getMaxFromDate())));
    }
  }

  /**
   * Adds predicates to filter securities by asset class properties.
   * <p>
   * This method handles filtering by:
   * <ul>
   * <li><strong>Asset class type:</strong> EQUITIES, FIXED_INCOME, etc.</li>
   * <li><strong>Investment instrument:</strong> DIRECT_INVESTMENT, ETF, MUTUAL_FUND, etc.</li>
   * <li><strong>Subcategory:</strong> Localized subcategory text using the current user's language</li>
   * </ul>
   * </p>
   * <p>
   * <strong>Multilanguage Support:</strong> When filtering by subcategory, the method automatically uses the current
   * authenticated user's language preference from the security context.
   * </p>
   * 
   * @param securityRoot   the root entity for the query
   * @param builder        the criteria builder for constructing predicates
   * @param mainPredicates the list to add the asset class predicates to
   */
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

  /**
   * Adds predicates to filter securities by stock exchange properties.
   * <p>
   * This method handles filtering by:
   * <ul>
   * <li><strong>Specific exchange:</strong> Filter by stock exchange ID</li>
   * <li><strong>Market value availability:</strong> Exclude exchanges with no market value when requested</li>
   * <li><strong>Geographic location:</strong> Filter by stock exchange country code</li>
   * </ul>
   * </p>
   * 
   * @param securityRoot   the root entity for the query
   * @param builder        the criteria builder for constructing predicates
   * @param mainPredicates the list to add the stock exchange predicates to
   */
  private void addStockexchangePredicate(final Root<Security> securityRoot, final CriteriaBuilder builder,
      final List<Predicate> mainPredicates) {
    if (securitycurrencySearch.getIdStockexchange() != null
        || securitycurrencySearch.getStockexchangeCountryCode() != null || securitycurrencySearch.isNoMarketValue()) {
      final Join<Security, Stockexchange> joinStockexchane = securityRoot.join(Security_.stockexchange);
      if (securitycurrencySearch.getIdStockexchange() != null) {
        mainPredicates.add(builder.equal(joinStockexchane.get(Stockexchange_.idStockexchange),
            securitycurrencySearch.getIdStockexchange()));
      }
      if (securitycurrencySearch.isNoMarketValue()) {
        mainPredicates.add(builder.equal(joinStockexchane.get(Stockexchange_.noMarketValue), false));
      }
      if (securitycurrencySearch.getStockexchangeCountryCode() != null) {
        mainPredicates.add(builder.equal(joinStockexchane.get(Stockexchange_.countryCode),
            securitycurrencySearch.getStockexchangeCountryCode()));
      }
    }
  }

}
