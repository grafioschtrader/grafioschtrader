package grafioschtrader.tax.swiss.ech0196.model;

import java.time.LocalDate;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

/**
 * JAXB adapter for marshalling LocalDate to xs:date format (yyyy-MM-dd).
 */
public class LocalDateAdapter extends XmlAdapter<String, LocalDate> {

  @Override
  public LocalDate unmarshal(String v) {
    return v == null ? null : LocalDate.parse(v);
  }

  @Override
  public String marshal(LocalDate v) {
    return v == null ? null : v.toString();
  }
}
