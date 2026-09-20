package grafioschtrader.service;

import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.Role;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.TaxCountry;
import grafioschtrader.repository.TaxCountryJpaRepository;
import grafioschtrader.validation.TaxCountryCodeValidator;

/** Single validated write boundary for country creation and administrator-authored simulation models. */
@Service
public class TaxCountryModelService {
  private final TaxCountryJpaRepository countries;
  private final TaxEvalExEstimator estimator;

  public TaxCountryModelService(TaxCountryJpaRepository countries, TaxEvalExEstimator estimator) {
    this.countries = countries;
    this.estimator = estimator;
  }

  public static void requireAdmin() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getDetails() instanceof User user) || user.getMostPrivilegedRole() != Role.ROLE_ADMIN)
      throw new SecurityException("Admin access required");
  }

  public static void requireIctax(TaxCountry country) {
    if (country == null || !"CH".equals(country.getCountryCode()))
      throw new DataViolationException("country.code", "gt.tax.ictax.unsupported", null);
  }

  @Transactional
  public TaxCountry create(TaxCountry request) {
    requireAdmin();
    String code = request.getCountryCode();
    if (code == null || !TaxCountryCodeValidator.CODES.contains(code))
      throw new DataViolationException("country.code", "gt.tax.country.invalid", null);
    if (request.getIdTaxCountry() != null || countries.existsByCountryCode(code))
      throw new DataViolationException("country.code", "gt.tax.country.duplicate", null);
    TaxCountry country = new TaxCountry();
    country.setCountryCode(code);
    country.setTaxModelYaml(validated(request.getTaxModelYaml()));
    return countries.save(country);
  }

  public String read(int id) {
    requireAdmin();
    return country(id).getTaxModelYaml();
  }

  @Transactional
  public String update(int id, String yaml) {
    requireAdmin();
    TaxCountry country = country(id);
    country.setTaxModelYaml(validated(yaml));
    return countries.save(country).getTaxModelYaml();
  }

  private TaxCountry country(int id) {
    return countries.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown tax country"));
  }

  private String validated(String yaml) {
    if (yaml == null || yaml.isBlank())
      return null;
    List<String> errors = estimator.validate(yaml);
    if (!errors.isEmpty())
      throw new DataViolationException("tax.model.yaml", "gt.tax.model.invalid",
          new Object[] { String.join("; ", errors) });
    return yaml;
  }
}
