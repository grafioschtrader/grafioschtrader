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

  /**
   * Selective import: stream-parses the full XML and returns only entries whose ISIN is in the target set.
   *
   * @param xmlBytes    full XML file content as byte array
   * @param targetIsins ISINs to extract
   * @param idTaxUpload the upload ID to link extracted data to
   * @return list of extracted tax data entries matching target ISINs
   */
  public List<IctaxSecurityTaxData> parseSelective(byte[] xmlBytes, Collection<String> targetIsins,
      int idTaxUpload) throws XMLStreamException {
    Set<String> isinSet = Set.copyOf(targetIsins);
    List<IctaxSecurityTaxData> allData = parseFull(new ByteArrayInputStream(xmlBytes), idTaxUpload);
    return allData.stream().filter(d -> isinSet.contains(d.getIsin())).toList();
  }

  /**
   * Full import: stream-parses the entire XML file and extracts all securities.
   *
   * @param xmlStream   input stream for the full XML file
   * @param idTaxUpload the upload ID to link extracted data to
   * @return list of all extracted tax data entries
   */
  public List<IctaxSecurityTaxData> parseFull(InputStream xmlStream, int idTaxUpload) throws XMLStreamException {
    XMLInputFactory factory = createSecureXmlFactory();
    XMLStreamReader reader = factory.createXMLStreamReader(new InputStreamReader(xmlStream, XML_CHARSET));
    List<IctaxSecurityTaxData> results = parseSecurities(reader, idTaxUpload);
    reader.close();
    return results;
  }

  private List<IctaxSecurityTaxData> parseSecurities(XMLStreamReader reader, int idTaxUpload)
      throws XMLStreamException {
    List<IctaxSecurityTaxData> results = new ArrayList<>();
    IctaxSecurityTaxData currentData = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        if (SECURITY_ELEMENTS.contains(localName)) {
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
          IctaxPayment payment = new IctaxPayment();
          payment.setIctaxSecurityTaxData(currentData);
          payment.setPaymentDate(getAttrDate(reader, "paymentDate"));
          payment.setExDate(getAttrDate(reader, "exDate"));
          payment.setCurrency(getAttr(reader, "currency"));
          payment.setPaymentValue(getAttrDouble(reader, "paymentValue"));
          payment.setExchangeRate(getAttrDouble(reader, "exchangeRate"));
          payment.setPaymentValueChf(getAttrDouble(reader, "paymentValueCHF"));
          String capitalGain = getAttr(reader, "capitalGain");
          payment.setCapitalGain("1".equals(capitalGain));
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
    return results;
  }

  private XMLInputFactory createSecureXmlFactory() {
    XMLInputFactory factory = XMLInputFactory.newInstance();
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
    return factory;
  }

  private String getAttr(XMLStreamReader reader, String name) {
    return reader.getAttributeValue(null, name);
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
