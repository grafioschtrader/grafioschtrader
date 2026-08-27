package grafioschtrader.tax.swiss.ictax;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import grafioschtrader.entities.IctaxPayment;
import grafioschtrader.entities.IctaxSecurityTaxData;

/**
 * StAX-based parser for ICTax Kursliste XML files. Supports both selective import (using .ix index for specific ISINs)
 * and full import of all securities.
 */
public class IctaxKurslisteParser {

  private static final Charset XML_CHARSET = Charset.forName("ISO-8859-1");
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private static final Set<String> SECURITY_ELEMENTS = Set.of("share", "fund", "bond", "derivative", "coinBullion",
      "currencyNote", "mediumTermBond");

  /** Top level element holding the official year-end and annual mean rate of one currency. */
  private static final String EXCHANGE_RATE_ELEMENT = "exchangeRateYearEnd";

  /**
   * Selective import: stream-parses the full XML and keeps only securities whose ISIN is in the target set. The
   * exchange rates are always returned in full — they are per currency, not per security, and there are only a few
   * hundred of them.
   *
   * @param xmlBytes    full XML file content as byte array
   * @param targetIsins ISINs to extract
   * @param idTaxUpload the upload ID to link extracted data to
   * @return the securities matching the target ISINs and every exchange rate of the file
   */
  public KurslisteParseResult parseSelective(byte[] xmlBytes, Collection<String> targetIsins, int idTaxUpload)
      throws XMLStreamException {
    Set<String> isinSet = Set.copyOf(targetIsins);
    KurslisteParseResult all = parseFull(new ByteArrayInputStream(xmlBytes), idTaxUpload);
    return new KurslisteParseResult(all.securities().stream().filter(d -> isinSet.contains(d.getIsin())).toList(),
        all.exchangeRates());
  }

  /**
   * Full import: stream-parses the entire XML file and extracts all securities and all exchange rates in one pass.
   *
   * @param xmlStream   input stream for the full XML file
   * @param idTaxUpload the upload ID to link extracted data to
   * @return every tax data entry and every exchange rate of the file
   */
  public KurslisteParseResult parseFull(InputStream xmlStream, int idTaxUpload) throws XMLStreamException {
    XMLInputFactory factory = createSecureXmlFactory();
    XMLStreamReader reader = factory.createXMLStreamReader(new InputStreamReader(xmlStream, XML_CHARSET));
    KurslisteParseResult result = parseSecurities(reader, idTaxUpload);
    reader.close();
    return result;
  }

  private KurslisteParseResult parseSecurities(XMLStreamReader reader, int idTaxUpload) throws XMLStreamException {
    List<IctaxSecurityTaxData> results = new ArrayList<>();
    List<ParsedExchangeRate> exchangeRates = new ArrayList<>();
    IctaxSecurityTaxData currentData = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        if (EXCHANGE_RATE_ELEMENT.equals(localName)) {
          addExchangeRate(reader, exchangeRates);
        } else if (SECURITY_ELEMENTS.contains(localName)) {
          currentData = new IctaxSecurityTaxData();
          currentData.setIdTaxUpload(idTaxUpload);
          currentData.setPayments(new ArrayList<>());
          currentData.setIsin(getAttr(reader, "isin"));
          currentData.setValorNumber(getAttrInt(reader, "valorNumber"));
          currentData.setSecurityGroup(getAttr(reader, "securityGroup"));
          currentData.setInstitutionName(getAttr(reader, "institutionName"));
          currentData.setCountry(getAttr(reader, "country"));
          currentData.setCurrency(getAttr(reader, "currency"));
        } else if ("yearend".equals(localName) && currentData != null) {
          currentData.setTaxValueChf(getAttrDouble(reader, "taxValueCHF"));
          currentData.setQuotationType(getAttr(reader, "quotationType"));
        } else if ("payment".equals(localName) && currentData != null) {
          // Skip superseded entries: the Kursliste keeps corrected/breakdown rows flagged
          // deleted="1" alongside the current valid coupon. Importing them would double-count.
          if (isXmlTrue(getAttr(reader, "deleted"))) {
            continue;
          }
          IctaxPayment payment = new IctaxPayment();
          payment.setIctaxSecurityTaxData(currentData);
          payment.setPaymentDate(getAttrDate(reader, "paymentDate"));
          payment.setExDate(getAttrDate(reader, "exDate"));
          payment.setCurrency(getAttr(reader, "currency"));
          payment.setPaymentValue(getAttrDouble(reader, "paymentValue"));
          payment.setExchangeRate(getAttrDouble(reader, "exchangeRate"));
          payment.setPaymentValueChf(getAttrDouble(reader, "paymentValueCHF"));
          // Mark non-taxable capital-gain coupons. The valid KEP (Kapitaleinlage / return of
          // capital) coupon carries sign="KEP" and has no capitalGain attribute, so accept both.
          payment.setCapitalGain(isXmlTrue(getAttr(reader, "capitalGain")) || "KEP".equals(getAttr(reader, "sign")));
          currentData.getPayments().add(payment);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT) {
        String localName = reader.getLocalName();
        if (SECURITY_ELEMENTS.contains(localName) && currentData != null) {
          if (currentData.getIsin() != null && !currentData.getIsin().isEmpty()) {
            results.add(currentData);
          }
          currentData = null;
        }
      }
    }
    return new KurslisteParseResult(results, exchangeRates);
  }

  /**
   * Reads one {@code exchangeRateYearEnd} element. A row without a year-end rate carries no usable information and is
   * dropped; a missing denomination means the rates are quoted per single unit.
   */
  private void addExchangeRate(XMLStreamReader reader, List<ParsedExchangeRate> exchangeRates) {
    String currency = getAttr(reader, "currency");
    Double yearEndRate = getAttrDouble(reader, "value");
    if (currency == null || currency.isEmpty() || yearEndRate == null) {
      return;
    }
    Integer denomination = getAttrInt(reader, "denomination");
    Integer year = getAttrInt(reader, "year");
    exchangeRates.add(new ParsedExchangeRate(currency, year == null ? null : year.shortValue(),
        denomination == null || denomination == 0 ? 1 : denomination, yearEndRate,
        getAttrDouble(reader, "valueMiddle")));
  }

  private XMLInputFactory createSecureXmlFactory() {
    XMLInputFactory factory = XMLInputFactory.newInstance();
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
    // The full Kursliste is a very large, DTD-free document. With DTD and external entities
    // disabled above there is no entity-expansion risk, so the JDK general/total entity-size
    // limits (which count the streamed document entity "[xml]") only get in the way. Disable
    // them so multi-hundred-thousand-line files parse. Best-effort: ignore if a non-JDK StAX
    // implementation does not recognise the property name.
    setLimitProperty(factory, "jdk.xml.maxGeneralEntitySizeLimit", "0");
    setLimitProperty(factory, "jdk.xml.totalEntitySizeLimit", "0");
    return factory;
  }

  /** Best-effort: sets a JDK-specific size-limit property, ignoring StAX implementations that reject it. */
  private void setLimitProperty(XMLInputFactory factory, String name, String value) {
    try {
      factory.setProperty(name, value);
    } catch (IllegalArgumentException ex) {
      // Property not supported by the active StAX implementation; safe to ignore.
    }
  }

  private String getAttr(XMLStreamReader reader, String name) {
    return reader.getAttributeValue(null, name);
  }

  /** Interprets an xs:boolean attribute value; treats both "1" and "true" (case-insensitive) as true. */
  private boolean isXmlTrue(String value) {
    return "1".equals(value) || "true".equalsIgnoreCase(value);
  }

  private Integer getAttrInt(XMLStreamReader reader, String name) {
    String val = getAttr(reader, name);
    if (val == null || val.isEmpty()) {
      return null;
    }
    try {
      return Integer.parseInt(val);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private Double getAttrDouble(XMLStreamReader reader, String name) {
    String val = getAttr(reader, name);
    if (val == null || val.isEmpty()) {
      return null;
    }
    try {
      return Double.parseDouble(val);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private LocalDate getAttrDate(XMLStreamReader reader, String name) {
    String val = getAttr(reader, name);
    if (val == null || val.isEmpty()) {
      return null;
    }
    try {
      return LocalDate.parse(val, DATE_FORMAT);
    } catch (Exception e) {
      return null;
    }
  }

}
