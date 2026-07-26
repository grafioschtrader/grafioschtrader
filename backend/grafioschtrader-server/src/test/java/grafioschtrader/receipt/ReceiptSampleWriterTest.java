package grafioschtrader.receipt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.receipt.TransactionReceiptPdfGenerator.ReceiptContext;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.TransactionType;

/**
 * Writes sample receipt PDFs to <code>target/receipt-samples</code> (overridable with the system property
 * <code>receipt.sample.dir</code>) for visual inspection of the layout. The machine readability of the receipts is
 * covered by {@link TransactionReceiptRoundTripTest}; this test only ensures generation does not throw and provides
 * the documents a developer looks at when adjusting the layout.
 */
class ReceiptSampleWriterTest {

  @Test
  void writeSamples() throws Exception {
    Path dir = Path.of(System.getProperty("receipt.sample.dir", "target/receipt-samples"));
    Files.createDirectories(dir);
    TransactionReceiptPdfGenerator generator = new TransactionReceiptPdfGenerator();
    ReceiptContext ctx = new ReceiptContext("hugo", "Hauptdepot", "Konto CHF");

    Transaction buy = tx(TransactionType.ACCUMULATE, sec("NL0009690239", "VanEck Global Real Estate ETF", "CHF",
        AssetclassType.EQUITIES), 61.0, 35.68, 9.85, 3.26, -2189.59);
    Files.write(dir.resolve("buy_de.pdf"), generator.generate(buy, Locale.GERMAN, ctx));
    Files.write(dir.resolve("buy_en.pdf"), generator.generate(buy, Locale.ENGLISH, ctx));

    Transaction dividend = tx(TransactionType.DIVIDEND, sec("NL0009690239", "VanEck Global Real Estate ETF", "EUR",
        AssetclassType.EQUITIES), 500.0, 0.42, null, 31.5, 164.63);
    dividend.setCurrencyExRate(0.9223);
    dividend.setExDate(LocalDate.of(2026, 6, 3));
    Files.write(dir.resolve("dividend_de.pdf"), generator.generate(dividend, Locale.GERMAN, ctx));
  }

  private Transaction tx(TransactionType type, Security security, Double units, Double quotation, Double tc, Double tt,
      double amount) {
    Transaction t = new Transaction();
    t.setIdTransaction(4711);
    t.setTransactionType(type);
    t.setSecuritycurrency(security);
    Cashaccount ca = new Cashaccount();
    ca.setCurrency("CHF");
    ca.setName("Konto CHF");
    t.setCashaccount(ca);
    t.setUnits(units);
    t.setQuotation(quotation);
    t.setTransactionCost(tc);
    t.setTaxCost(tt);
    t.setCashaccountAmount(amount);
    t.setTransactionTime(LocalDateTime.of(2026, 3, 9, 14, 5, 33));
    return t;
  }

  private Security sec(String isin, String name, String currency, AssetclassType type) {
    Security s = new Security();
    s.setIsin(isin);
    s.setName(name);
    s.setCurrency(currency);
    Assetclass ac = new Assetclass();
    ac.setCategoryType(type);
    s.setAssetClass(ac);
    return s;
  }
}
