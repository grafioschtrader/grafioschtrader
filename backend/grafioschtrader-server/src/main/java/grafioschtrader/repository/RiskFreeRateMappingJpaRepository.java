package grafioschtrader.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.dto.RiskFreeInstrumentOption;
import grafioschtrader.entities.RiskFreeRateMapping;

public interface RiskFreeRateMappingJpaRepository
    extends JpaRepository<RiskFreeRateMapping, Integer>, UpdateCreateJpaRepository<RiskFreeRateMapping> {

  Optional<RiskFreeRateMapping> findByCurrency(String currency);

  /**
   * Lists ALL Security rows in the "Risk-free rate" assetclass (the one seeded by V0_35_5), including those already
   * mapped. Used twofold by the admin UI: the dropdown options for the instrument-picker column AND the lookup table
   * used to render the security's display name (column 2) and FRED series id (column 3) for already-mapped rows.
   *
   * <p>
   * Per-row "already-used" filtering is done client-side in {@code optionsProviderFn} so that a row being edited can
   * still see its own currently-selected instrument in the dropdown.
   *
   * <p>
   * Identifies the "Risk-free rate" assetclass by the English subcategory label rather than by id (since the id varies
   * across installations).
   *
   * <p>
   * Named query: {@code RiskFreeRateMapping.findAllRiskFreeInstruments} (defined in
   * {@code META-INF/jpa-named-queries.properties}). Returned columns are aliased to match the
   * {@link RiskFreeInstrumentOption} projection: {@code idSecuritycurrency}, {@code name}, {@code currency},
   * {@code urlHistoryExtend}.
   *
   * @return one option per risk-free Security, ordered by currency then name
   */
  @Query(nativeQuery = true)
  List<RiskFreeInstrumentOption> findAllRiskFreeInstruments();

  /**
   * Returns the ISO currency code of the Security identified by {@code idSecuritycurrency}, or {@code null} if no such
   * row exists. Used by the REST resource to validate that the currency of a posted mapping matches the underlying
   * security's currency before persisting.
   */
  @Query(nativeQuery = true,
      value = "SELECT s.currency FROM security s WHERE s.id_securitycurrency = ?1")
  String findCurrencyByIdSecuritycurrency(Integer idSecuritycurrency);
}
