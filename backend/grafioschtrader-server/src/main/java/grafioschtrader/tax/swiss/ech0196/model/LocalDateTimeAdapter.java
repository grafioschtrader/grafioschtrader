package grafioschtrader.tax.swiss.ech0196.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

/**
 * JAXB adapter for marshalling LocalDateTime to xs:dateTime format.
 */
public class LocalDateTimeAdapter extends XmlAdapter<String, LocalDateTime> {

  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  @Override
  public LocalDateTime unmarshal(String v) {
    return v == null ? null : LocalDateTime.parse(v, FORMATTER);
  }

  @Override
  public String marshal(LocalDateTime v) {
    return v == null ? null : v.format(FORMATTER);
  }
}
