package grafioschtrader.tax.swiss.ech0196;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafioschtrader.dto.TaxStatementExportRequest;
import grafioschtrader.entities.IctaxPayment;
import grafioschtrader.entities.IctaxSecurityTaxData;
import grafioschtrader.entities.Security;
import grafioschtrader.reports.SecurityDividendsReport;
import grafioschtrader.reportviews.securitydividends.SecurityDividendsGrandTotal;
import grafioschtrader.reportviews.securitydividends.SecurityDividendsPosition;
import grafioschtrader.reportviews.securitydividends.SecurityDividendsYearGroup;
import grafioschtrader.reportviews.securitydividends.UnitsCounter;
import grafioschtrader.tax.swiss.ech0196.model.Ech0196Client;
import grafioschtrader.tax.swiss.ech0196.model.Ech0196Depot;
import grafioschtrader.tax.swiss.ech0196.model.Ech0196Institution;
import grafioschtrader.tax.swiss.ech0196.model.Ech0196ListOfSecurities;
import grafioschtrader.tax.swiss.ech0196.model.Ech0196Payment;
import grafioschtrader.tax.swiss.ech0196.model.Ech0196Security;
import grafioschtrader.tax.swiss.ech0196.model.Ech0196Stock;
import grafioschtrader.tax.swiss.ech0196.model.Ech0196TaxStatement;
import grafioschtrader.tax.swiss.ech0196.model.Ech0196TaxValue;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Maps GT dividend report data and ICTax tax data to an eCH-0196 v2.2.0 JAXB model. All securities across all security
 * accounts are merged into a single depot element, as the tax authorities view the portfolio as one virtual depot.
 */
@Service
public class Ech0196MappingService {
 

  @Autowired
  private SecurityDividendsReport securityDividendsReport;

  /**
   * Builds an eCH-0196 tax statement from the dividend report for a specific year.
   *
   * @param request    export parameters (year, canton, institution/client details)
   * @param grandTotal the computed dividend report containing all years
   * @return a fully populated JAXB model ready for XML marshalling
   */
  public Ech0196TaxStatement buildTaxStatement(TaxStatementExportRequest request,
      SecurityDividendsGrandTotal grandTotal) {
    int taxYear = request.getTaxYear();
    SecurityDividendsYearGroup yearGroup = findYearGroup(grandTotal, taxYear);

    Ech0196TaxStatement stmt = new Ech0196TaxStatement();
    stmt.setId("GT-" + UUID.randomUUID().toString());
    stmt.setCreationDate(LocalDateTime.now());
    stmt.setTaxPeriod(taxYear);
    stmt.setPeriodFrom(LocalDate.of(taxYear, 1, 1));
    stmt.setPeriodTo(LocalDate.of(taxYear, 12, 31));
    stmt.setCountry("CH");
    stmt.setCanton(request.getCanton().toUpperCase());
    stmt.setMinorVersion(2);

    stmt.setInstitution(buildInstitution(request));
    stmt.setClients(List.of(buildClient(request)));

    if (yearGroup != null) {
      List<SecurityDividendsPosition> positions = yearGroup.getSecurityDividendsPositions();
      boolean isFallbackYear = (yearGroup.year != taxYear);
      if (isFallbackYear) {
        // The report has no year group for the requested year (no transactions in that year).
        // We use the last available year's positions (which carry forward) but must load
        // ICTax data for the actual requested tax year, not the fallback year.
        securityDividendsReport.enrichWithIctaxData(yearGroup, (short) taxYear);
      }

      Ech0196ListOfSecurities listOfSecurities = buildListOfSecurities(positions, taxYear);
      stmt.setListOfSecurities(listOfSecurities);
      stmt.setTotalTaxValue(listOfSecurities.getTotalTaxValue());
      stmt.setTotalGrossRevenueA(listOfSecurities.getTotalGrossRevenueA());
      stmt.setTotalGrossRevenueB(listOfSecurities.getTotalGrossRevenueB());
      stmt.setTotalWithHoldingTaxClaim(listOfSecurities.getTotalWithHoldingTaxClaim());
    } else {
      stmt.setTotalTaxValue(0.0);
      stmt.setTotalGrossRevenueA(0.0);
      stmt.setTotalGrossRevenueB(0.0);
      stmt.setTotalWithHoldingTaxClaim(0.0);
    }

    return stmt;
  }

  /**
   * Finds the year group matching the requested tax year. If no exact match exists (e.g., no transactions in that
   * year), falls back to the latest available year group whose positions carry forward into the tax year.
   */
  private SecurityDividendsYearGroup findYearGroup(SecurityDividendsGrandTotal grandTotal, int taxYear) {
    SecurityDividendsYearGroup exact = grandTotal.getSecurityDividendsYearGroup().stream()
        .filter(yg -> yg.year == taxYear).findFirst().orElse(null);
    if (exact != null) {
      return exact;
    }
    return grandTotal.getSecurityDividendsYearGroup().stream()
        .filter(yg -> yg.year < taxYear)
        .max(java.util.Comparator.comparingInt(yg -> yg.year)).orElse(null);
  }

  private Ech0196Institution buildInstitution(TaxStatementExportRequest request) {
    Ech0196Institution inst = new Ech0196Institution();
    inst.setName(request.getInstitutionName());
    if (request.getInstitutionLei() != null && !request.getInstitutionLei().isBlank()) {
      inst.setLei(request.getInstitutionLei());
    }
    return inst;
  }

  private Ech0196Client buildClient(TaxStatementExportRequest request) {
    Ech0196Client client = new Ech0196Client();
    client.setClientNumber(request.getClientNumber());
    if (request.getClientFirstName() != null && !request.getClientFirstName().isBlank()) {
      client.setFirstName(request.getClientFirstName());
    }
    if (request.getClientLastName() != null && !request.getClientLastName().isBlank()) {
      client.setLastName(request.getClientLastName());
    }
    if (request.getClientTin() != null && !request.getClientTin().isBlank()) {
      client.setTin(request.getClientTin());
    }
    return client;
  }

  private Ech0196ListOfSecurities buildListOfSecurities(List<SecurityDividendsPosition> positions, int taxYear) {
    Ech0196ListOfSecurities list = new Ech0196ListOfSecurities();
    LocalDate dec31 = LocalDate.of(taxYear, 12, 31);

    double totalTaxValue = 0.0;
    double totalGrossRevenueA = 0.0;
    double totalGrossRevenueB = 0.0;
    double totalWithHoldingTaxClaim = 0.0;

    List<Ech0196Security> securities = new ArrayList<>();
    AtomicInteger positionCounter = new AtomicInteger(1);

    for (SecurityDividendsPosition position : positions) {
      if (position.excludedFromTax) {
        continue;
      }
      if (position.security.isMarginInstrument()) {
        continue;
      }
      if (position.unitsAtEndOfYear <= 0 && (position.ictaxPayments == null || position.ictaxPayments.isEmpty())
          && position.taxableAmountMC == 0.0) {
        continue;
      }

      Ech0196Security sec = buildSecurity(position, positionCounter.getAndIncrement(), dec31);
      securities.add(sec);

      // Tax value: prefer ICTax, fall back to valueAtEndOfYearMC from the report
      double positionTaxValue = position.ictaxTotalTaxValueChf != null
          ? position.ictaxTotalTaxValueChf
          : (position.valueAtEndOfYearMC != null ? position.valueAtEndOfYearMC : 0.0);
      totalTaxValue += positionTaxValue;

      // Yield: prefer ICTax pre-calculated total, fall back to taxableAmountMC from the report
      double positionYield;
      if (position.ictaxTotalPaymentValueChf != null && position.ictaxTotalPaymentValueChf != 0.0) {
        positionYield = position.ictaxTotalPaymentValueChf;
      } else {
        positionYield = position.taxableAmountMC;
      }

      if (positionYield != 0.0) {
        if (isSwissCountry(position)) {
          totalGrossRevenueA += positionYield;
          totalWithHoldingTaxClaim += round2(positionYield * 0.35);
        } else {
          totalGrossRevenueB += positionYield;
        }
      }
    }

    Ech0196Depot depot = new Ech0196Depot();
    depot.setDepotNumber("1");
    depot.setSecurities(securities);
    list.setDepots(List.of(depot));

    list.setTotalTaxValue(round2(totalTaxValue));
    list.setTotalGrossRevenueA(round2(totalGrossRevenueA));
    list.setTotalGrossRevenueB(round2(totalGrossRevenueB));
    list.setTotalWithHoldingTaxClaim(round2(totalWithHoldingTaxClaim));
    list.setTotalLumpSumTaxCredit(0.0);
    list.setTotalNonRecoverableTax(0.0);
    list.setTotalAdditionalWithHoldingTaxUSA(0.0);
    list.setTotalGrossRevenueIUP(0.0);
    list.setTotalGrossRevenueConversion(0.0);

    return list;
  }

  private Ech0196Security buildSecurity(SecurityDividendsPosition position, int positionId, LocalDate dec31) {
    Security security = position.security;
    IctaxSecurityTaxData ictaxData = getIctaxData(position);

    Ech0196Security sec = new Ech0196Security();
    sec.setPositionId(positionId);
    sec.setIsin(security.getIsin());
    sec.setSecurityName(truncate(security.getName(), 60));
    sec.setCurrency(security.getCurrency());
    sec.setQuotationType(mapQuotationType(security));
    sec.setSecurityCategory(mapSecurityCategory(security));
    sec.setCountry(resolveCountry(position, ictaxData));

    if (ictaxData != null && ictaxData.getValorNumber() != null) {
      sec.setValorNumber(ictaxData.getValorNumber());
    }

    if (security.getDenomination() != null) {
      sec.setNominalValue(security.getDenomination().doubleValue());
    }

    if (position.unitsAtEndOfYear > 0) {
      sec.setTaxValue(buildTaxValue(position, dec31));
    }

    if (position.ictaxPayments != null && !position.ictaxPayments.isEmpty()) {
      sec.setPayments(buildPayments(position));
    }

    List<Ech0196Stock> stocks = buildStocks(position, dec31.getYear());
    if (stocks != null) {
      sec.setStocks(stocks);
    }

    return sec;
  }

  private Ech0196TaxValue buildTaxValue(SecurityDividendsPosition position, LocalDate dec31) {
    Ech0196TaxValue tv = new Ech0196TaxValue();
    tv.setReferenceDate(dec31);
    tv.setQuotationType(mapQuotationType(position.security));
    tv.setQuantity(position.unitsAtEndOfYear);
    tv.setBalanceCurrency("CHF");

    if (position.ictaxTaxValuePerUnitChf != null) {
      tv.setKursliste(Boolean.TRUE);
      tv.setUnitPrice(position.ictaxTaxValuePerUnitChf);
      if (position.ictaxTotalTaxValueChf != null) {
        tv.setValue(position.ictaxTotalTaxValueChf);
        tv.setBalance(position.ictaxTotalTaxValueChf);
      }
    } else if (position.valueAtEndOfYearMC != null) {
      // No ICTax data — use the report's year-end market value as fallback
      tv.setKursliste(Boolean.FALSE);
      tv.setValue(position.valueAtEndOfYearMC);
      tv.setBalance(position.valueAtEndOfYearMC);
    }

    return tv;
  }

  private List<Ech0196Payment> buildPayments(SecurityDividendsPosition position) {
    List<Ech0196Payment> payments = new ArrayList<>();
    boolean isSwiss = isSwissCountry(position);

    for (IctaxPayment ictaxPmt : position.ictaxPayments) {
      Ech0196Payment pmt = new Ech0196Payment();
      pmt.setPaymentDate(ictaxPmt.getPaymentDate() != null ? ictaxPmt.getPaymentDate() : LocalDate.of(1900, 1, 1));
      if (ictaxPmt.getExDate() != null) {
        pmt.setExDate(ictaxPmt.getExDate());
      }
      pmt.setQuotationType(mapQuotationType(position.security));
      pmt.setQuantity(ictaxPmt.getComputedUnitsAtDate());
      pmt.setAmountCurrency(ictaxPmt.getCurrency() != null ? ictaxPmt.getCurrency() : position.security.getCurrency());
      pmt.setKursliste(Boolean.TRUE);

      if (ictaxPmt.getPaymentValue() != null) {
        pmt.setAmountPerUnit(ictaxPmt.getPaymentValue());
      }
      if (ictaxPmt.getExchangeRate() != null) {
        pmt.setExchangeRate(ictaxPmt.getExchangeRate());
      }

      if (ictaxPmt.getPaymentValueChf() != null) {
        double paymentChf = ictaxPmt.getComputedTotalPaymentChf();
        pmt.setAmount(paymentChf);
        if (isSwiss) {
          pmt.setGrossRevenueA(paymentChf);
          pmt.setWithHoldingTaxClaim(round2(paymentChf * 0.35));
        } else {
          pmt.setGrossRevenueB(paymentChf);
        }
      }

      payments.add(pmt);
    }
    return payments;
  }

  /**
   * Builds stock mutation entries (Bestandesmutationen) for a security position.
   * Generates an opening position entry at Jan 1 if units were held at prior year-end,
   * plus one mutation entry per buy/sell transaction within the tax year.
   */
  private List<Ech0196Stock> buildStocks(SecurityDividendsPosition position, int taxYear) {
    if (position.unitsCounter == null) {
      return null;
    }
    List<Ech0196Stock> stocks = new ArrayList<>();
    LocalDate jan1 = LocalDate.of(taxYear, 1, 1);
    String quotationType = mapQuotationType(position.security);
    String currency = position.security.getCurrency();

    // Opening position: units held at start of year (before any transactions in this year)
    double unitsAtStartOfYear = position.unitsCounter.getUnitsAtDate(jan1.minusDays(1));
    if (unitsAtStartOfYear > 0) {
      Ech0196Stock opening = new Ech0196Stock();
      opening.setReferenceDate(jan1);
      opening.setMutation(false);
      opening.setQuotationType(quotationType);
      opening.setQuantity(unitsAtStartOfYear);
      opening.setBalanceCurrency(currency);
      stocks.add(opening);
    }

    // Mutations: each buy/sell transaction within the tax year
    for (UnitsCounter.UnitMutation m : position.unitsCounter.getMutations()) {
      if (m.date().getYear() == taxYear) {
        Ech0196Stock mutation = new Ech0196Stock();
        mutation.setReferenceDate(m.date());
        mutation.setMutation(true);
        mutation.setQuotationType(quotationType);
        mutation.setQuantity(m.delta());
        mutation.setBalanceCurrency(currency);
        stocks.add(mutation);
      }
    }

    return stocks.isEmpty() ? null : stocks;
  }

  private IctaxSecurityTaxData getIctaxData(SecurityDividendsPosition position) {
    if (position.ictaxPayments != null && !position.ictaxPayments.isEmpty()) {
      IctaxPayment firstPayment = position.ictaxPayments.get(0);
      return firstPayment.getIctaxSecurityTaxData();
    }
    return null;
  }

  private boolean isSwissCountry(SecurityDividendsPosition position) {
    IctaxSecurityTaxData ictaxData = getIctaxData(position);
    if (ictaxData != null && ictaxData.getCountry() != null) {
      return "CH".equalsIgnoreCase(ictaxData.getCountry());
    }
    return false;
  }

  private String resolveCountry(SecurityDividendsPosition position, IctaxSecurityTaxData ictaxData) {
    if (ictaxData != null && ictaxData.getCountry() != null && !ictaxData.getCountry().isBlank()) {
      return ictaxData.getCountry().toUpperCase();
    }
    return "XX";
  }

  private String mapQuotationType(Security security) {
    if (security.isBondDirectInvestment()) {
      return "PERCENT";
    }
    return "PIECE";
  }

  /**
   * Maps GT AssetclassType and SpecialInvestmentInstruments to eCH-0196 securityCategory.
   */
  private String mapSecurityCategory(Security security) {
    AssetclassType categoryType = security.getAssetClass().getCategoryType();
    SpecialInvestmentInstruments sii = security.getAssetClass().getSpecialInvestmentInstrument();

    if (sii == SpecialInvestmentInstruments.ETF || sii == SpecialInvestmentInstruments.MUTUAL_FUND
        || sii == SpecialInvestmentInstruments.PENSION_FUNDS) {
      return "FUND";
    }
    if (categoryType == null) {
      return "OTHER";
    }
    return switch (categoryType) {
    case EQUITIES -> "SHARE";
    case FIXED_INCOME, CONVERTIBLE_BOND -> "BOND";
    case COMMODITIES -> "COINBULL";
    case CURRENCY_PAIR -> "CURRNOTE";
    case CREDIT_DERIVATIVE -> "DEVT";
    default -> "OTHER";
    };
  }

  private static double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  private static String truncate(String s, int maxLen) {
    if (s == null) {
      return "";
    }
    return s.length() > maxLen ? s.substring(0, maxLen) : s;
  }
}
