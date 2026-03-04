package grafioschtrader.connector.instrument.ecb;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.EcbExchangeRates;
import grafioschtrader.repository.EcbExchangeRatesRepository;

/**
 * Loading historical exchange rates from the European Central Bank (ECB). Should be executed preferably daily but not
 * necessarily on weekends.
 */
public class EcbLoader {

  static final String ECB_BASE_URL = "https://www.ecb.europa.eu/stats/eurofxref/";
  static final String ECB_SINGLE_DAY_EXTEND = "eurofxref-daily.xml";
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private final Logger log = LoggerFactory.getLogger(this.getClass());

  public void update(EcbExchangeRatesRepository ecbExchangeRatesRepository) {
    String volumeRequestUrlPart = ECB_SINGLE_DAY_EXTEND;

    LocalDate youngestDate = ecbExchangeRatesRepository.getMaxDate();
    LocalDate yesterDay = LocalDate.now().minusDays(1);
    if (youngestDate == null || ChronoUnit.DAYS.between(youngestDate, yesterDay) > 88) {
      volumeRequestUrlPart = "eurofxref-hist.xml";
    } else if (ChronoUnit.DAYS.between(youngestDate, yesterDay) > 1) {
      volumeRequestUrlPart = "eurofxref-hist-90d.xml";
    }
    log.info("Demand this '{}' period from ECB.", volumeRequestUrlPart);
    try {
      readXMLDataAndSave(ecbExchangeRatesRepository, getFromDate(youngestDate), volumeRequestUrlPart);
    } catch (Exception e) {
      log.error("Could not read ECB Data", e);
    }
  }

  private LocalDate getFromDate(LocalDate youngestDate) {
    if (youngestDate == null) {
      return LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY, DATE_FORMAT);
    } else {
      return youngestDate.plusDays(1);
    }
  }

  private void readXMLDataAndSave(EcbExchangeRatesRepository ecbExchangeRatesRepository, LocalDate fromDate,
      String volumeRequestUrlPart) throws Exception {
    HttpURLConnection connection = null;
    try {
      URL feedUrl = new URI(ECB_BASE_URL + volumeRequestUrlPart).toURL();
      connection = (HttpURLConnection) feedUrl.openConnection();
      InputStream input = connection.getInputStream();
      XMLInputFactory factory = XMLInputFactory.newInstance();

      factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
      factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);

      XMLStreamReader reader = factory.createXMLStreamReader(new InputStreamReader(input, StandardCharsets.UTF_8));
      ecbExchangeRatesRepository.saveAll(readCubes(reader, fromDate));
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  private List<EcbExchangeRates> readCubes(XMLStreamReader reader, LocalDate fromDate) throws XMLStreamException {

    List<EcbExchangeRates> ecbExchangeRatesList = new ArrayList<>();
    LocalDate readDate = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event != XMLStreamConstants.START_ELEMENT || !"Cube".equals(reader.getLocalName())) {
        continue;
      }

      String currency = reader.getAttributeValue(null, "currency");
      if (currency != null) {
        if (readDate != null) {
          String rateStr = reader.getAttributeValue(null, "rate");
          double rate = Double.valueOf(rateStr);
          EcbExchangeRates ecbExchangeRates = new EcbExchangeRates(readDate, currency, rate);
          ecbExchangeRatesList.add(ecbExchangeRates);
        }
      } else {
        String time = reader.getAttributeValue(null, "time");
        if (time != null) {
          readDate = LocalDate.parse(time, DATE_FORMAT);
          if (readDate.isBefore(fromDate)) {
            readDate = null;
          }
        }
      }
    }
    return ecbExchangeRatesList;
  }

}
