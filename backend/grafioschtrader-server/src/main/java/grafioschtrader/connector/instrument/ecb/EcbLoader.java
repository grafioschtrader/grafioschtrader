package grafioschtrader.connector.instrument.ecb;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.entities.EcbExchangeRates;
import grafioschtrader.repository.EcbExchangeRatesRepository;

/**
 * Loading historical exchange rates from the European Central Bank (ECB).
 * Should be executed preferably daily but not necessarily on weekends.
 */
public class EcbLoader {

  private static final String SOURCE_URL = "https://www.ecb.europa.eu/stats/eurofxref/";
  private final Logger log = LoggerFactory.getLogger(this.getClass());

  public void update(EcbExchangeRatesRepository ecbExchangeRatesRepository) {
    String volumeRequestUrlPart = "eurofxref-daily.xml";

    Date youngestDate = ecbExchangeRatesRepository.getMaxDate();
    Date yesterDay = DateHelper.setTimeToZeroAndAddDay(new Date(), -1);
    if (youngestDate == null || DateHelper.getDateDiff(youngestDate, yesterDay, TimeUnit.DAYS) > 88) {
      volumeRequestUrlPart = "eurofxref-hist.xml";
    } else if (DateHelper.getDateDiff(youngestDate, yesterDay, TimeUnit.DAYS) > 1) {
      volumeRequestUrlPart = "eurofxref-hist-90d.xml";
    }
    log.info("Demand this '{}' period from ECB.", volumeRequestUrlPart);
    try {
      readXMLDataAndSave(ecbExchangeRatesRepository, getFromDate(youngestDate), volumeRequestUrlPart);
    } catch (Exception e) {
      log.error("Could not read ECB Data", e);
    }
  }

  private Date getFromDate(Date youngestDate) throws ParseException {
    if (youngestDate == null) {
      SimpleDateFormat sdf = new SimpleDateFormat(GlobalConstants.STANDARD_DATE_FORMAT);
      return sdf.parse(GlobalConstants.OLDEST_TRADING_DAY);
    } else {
      return DateHelper.setTimeToZeroAndAddDay(youngestDate, 1);
    }
  }

  private void readXMLDataAndSave(EcbExchangeRatesRepository ecbExchangeRatesRepository, Date fromDate,
      String volumeRequestUrlPart) throws Exception {
    HttpURLConnection connection = null;
    try {
      URL feedUrl = new URI(SOURCE_URL + volumeRequestUrlPart).toURL();
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

  private List<EcbExchangeRates> readCubes(XMLStreamReader reader, Date fromDate)
      throws XMLStreamException, ParseException {

    SimpleDateFormat sdf = new SimpleDateFormat(GlobalConstants.STANDARD_DATE_FORMAT);
    List<EcbExchangeRates> ecbExchangeRatesList = new ArrayList<>();
    Date readDate = null;

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
          readDate = sdf.parse(time);
          if (readDate.before(fromDate)) {
            readDate = null;
          }
        }
      }
    }
    return ecbExchangeRatesList;
  }

}
