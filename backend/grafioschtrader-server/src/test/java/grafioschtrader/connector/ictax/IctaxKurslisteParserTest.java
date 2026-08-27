package grafioschtrader.connector.ictax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.IctaxExchangeRate;
import grafioschtrader.entities.IctaxPayment;
import grafioschtrader.entities.IctaxSecurityTaxData;
import grafioschtrader.tax.swiss.ictax.IctaxKurslisteParser;
import grafioschtrader.tax.swiss.ictax.ParsedExchangeRate;

/**
 * Pure parser tests (no Spring context) verifying that superseded payment rows are dropped and that KEP coupons are
 * flagged as capital gains.
 */
class IctaxKurslisteParserTest {

  /**
   * Mirrors the structure observed for CH0237935652/2025: each valid coupon is accompanied by deleted="1"
   * correction/breakdown rows, and the valid KEP coupon carries sign="KEP" instead of capitalGain="1".
   */
  private static final String XML = """
      <?xml version="1.0" encoding="ISO-8859-1"?>
      <kursliste>
        <fund id="1" valorNumber="23793565" isin="CH0237935652" securityGroup="FUND" country="CH" currency="CHF" nominalValue="0">
          <yearend id="9" quotationType="PIECE" taxValue="159.02" taxValueCHF="159.02"/>
          <payment id="100" deleted="1" paymentDate="2025-04-15" currency="CHF" paymentValueCHF="0.6594891684" exDate="2025-04-11"/>
          <payment id="101" deleted="1" paymentDate="2025-04-15" currency="CHF" paymentValueCHF="0.0005108316" sign="(G)" exDate="2025-04-11" capitalGain="1"/>
          <payment id="102" paymentDate="2025-04-15" currency="CHF" paymentValueCHF="0.66" exDate="2025-04-11" coupon="62"/>
          <payment id="200" deleted="1" paymentDate="2025-07-17" currency="CHF" paymentValueCHF="0.4" sign="(G)" exDate="2025-07-15" capitalGain="1"/>
          <payment id="201" paymentDate="2025-07-17" currency="CHF" paymentValueCHF="0.4" sign="KEP" exDate="2025-07-15" coupon="65"/>
          <payment id="202" paymentDate="2025-07-17" currency="CHF" paymentValueCHF="0.44" exDate="2025-07-15" coupon="64"/>
        </fund>
        <exchangeRateYearEnd currency="USD" year="2025" value="0.79225" valueMiddle="0.8306517857"/>
        <exchangeRateYearEnd currency="JPY" year="2025" denomination="100" value="0.5054" valueMiddle="0.5553194821"/>
        <exchangeRateYearEnd currency="XXX" year="2025" valueMiddle="1.5"/>
      </kursliste>
      """;

  @Test
  @DisplayName("Deleted rows are dropped; KEP coupon is flagged as capital gain")
  void parsesValidPaymentsOnly() throws Exception {
    IctaxKurslisteParser parser = new IctaxKurslisteParser();
    List<IctaxSecurityTaxData> result = parser
        .parseFull(new ByteArrayInputStream(XML.getBytes(StandardCharsets.ISO_8859_1)), 1).securities();

    assertEquals(1, result.size());
    List<IctaxPayment> payments = result.getFirst().getPayments();

    // Only the three non-deleted coupons survive (0.66, 0.40 KEP, 0.44).
    assertEquals(3, payments.size(), "deleted=1 rows must not be imported");

    IctaxPayment kep = payments.stream().filter(p -> Double.valueOf(0.4).equals(p.getPaymentValueChf())).findFirst()
        .orElseThrow();
    assertTrue(Boolean.TRUE.equals(kep.getCapitalGain()), "sign=\"KEP\" coupon must be flagged capitalGain");

    // The two genuine taxable coupons are not flagged.
    assertEquals(2, payments.stream().filter(p -> !Boolean.TRUE.equals(p.getCapitalGain())).count());
  }

  @Test
  @DisplayName("Year-end exchange rates are read, denomination divided out and the override preferred")
  void parsesExchangeRates() throws Exception {
    IctaxKurslisteParser parser = new IctaxKurslisteParser();
    List<ParsedExchangeRate> rates = parser
        .parseFull(new ByteArrayInputStream(XML.getBytes(StandardCharsets.ISO_8859_1)), 1).exchangeRates();

    // XXX carries only an annual mean and no year-end rate, which makes it useless for a tax value.
    assertEquals(2, rates.size(), "a row without a year-end rate must be dropped");

    ParsedExchangeRate usd = rates.stream().filter(r -> "USD".equals(r.currency())).findFirst().orElseThrow();
    assertEquals(0.79225, usd.yearEndRate());
    assertEquals(0.8306517857, usd.annualMeanRate());
    assertEquals(1, usd.denomination(), "a missing denomination means the rate is quoted per single unit");
    assertEquals((short) 2025, usd.year());

    // The Kursliste quotes JPY per 100 units; only the entity divides that out.
    ParsedExchangeRate jpy = rates.stream().filter(r -> "JPY".equals(r.currency())).findFirst().orElseThrow();
    assertEquals(100, jpy.denomination());
    IctaxExchangeRate jpyEntity = new IctaxExchangeRate(1, jpy.currency(), jpy.denomination(), jpy.yearEndRate(),
        jpy.annualMeanRate());
    assertEquals(0.005054, jpyEntity.getEffectiveYearEndRateChfPerUnit(), 1e-12);

    jpyEntity.setYearEndRateOverride(0.5055);
    assertEquals(0.005055, jpyEntity.getEffectiveYearEndRateChfPerUnit(), 1e-12,
        "a manual override replaces the published rate");
  }

  @Test
  @DisplayName("Large Kursliste parses despite a strict jdk.xml.maxGeneralEntitySizeLimit")
  void parsesDocumentLargerThanEntitySizeLimit() throws Exception {
    String previous = System.getProperty("jdk.xml.maxGeneralEntitySizeLimit");
    System.setProperty("jdk.xml.maxGeneralEntitySizeLimit", "1000"); // simulate strict env
    try {
      StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<kursliste>\n");
      for (int i = 0; i < 5000; i++) { // well over 1000 chars total
        sb.append("  <fund id=\"").append(i).append("\" valorNumber=\"").append(i).append("\" isin=\"CH000000000")
            .append(i % 10).append("\" securityGroup=\"FUND\" country=\"CH\" currency=\"CHF\"/>\n");
      }
      sb.append("</kursliste>\n");

      IctaxKurslisteParser parser = new IctaxKurslisteParser();
      List<IctaxSecurityTaxData> result = parser
          .parseFull(new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.ISO_8859_1)), 1).securities();
      assertEquals(5000, result.size());
    } finally {
      if (previous == null) {
        System.clearProperty("jdk.xml.maxGeneralEntitySizeLimit");
      } else {
        System.setProperty("jdk.xml.maxGeneralEntitySizeLimit", previous);
      }
    }
  }
}
