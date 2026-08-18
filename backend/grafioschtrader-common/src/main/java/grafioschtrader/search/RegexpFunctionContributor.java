package grafioschtrader.search;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * Makes the MariaDB function {@code REGEXP_INSTR(subject, pattern)} available to the JPA Criteria API.
 * <p>
 * The Criteria API has no operator for a regular expression match, and MariaDB — unlike MySQL 8 — does not provide
 * {@code REGEXP_LIKE()}. {@code REGEXP_INSTR} is the portable-within-MariaDB equivalent: it returns the 1-based
 * position of the first match or 0 when the pattern does not match, so {@code REGEXP_INSTR(name, pattern) > 0} is a
 * boolean match test.
 * </p>
 * <p>
 * The only consumer is {@link SecuritySearchBuilder}, which offers an opt-in regular expression search on the
 * instrument name. The function is registered through the {@link FunctionContributor} SPI (declared in
 * {@code META-INF/services/org.hibernate.boot.model.FunctionContributor}) rather than being relied upon as an
 * unregistered function name.
 * </p>
 */
public class RegexpFunctionContributor implements FunctionContributor {

  /** Name under which the function is registered and which must be used in {@code CriteriaBuilder.function(...)}. */
  public static final String REGEXP_INSTR = "regexp_instr";

  @Override
  public void contributeFunctions(FunctionContributions functionContributions) {
    functionContributions.getFunctionRegistry().registerPattern(REGEXP_INSTR, "regexp_instr(?1, ?2)",
        functionContributions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.INTEGER));
  }
}
