package grafioschtrader.tax.swiss.ech0196.model;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

/**
 * JAXB adapter for marshalling Integer to xs:gYear format (e.g., "2025").
 */
public class GYearAdapter extends XmlAdapter<String, Integer> {

  @Override
  public Integer unmarshal(String v) {
    return v == null ? null : Integer.parseInt(v);
  }

  @Override
  public String marshal(Integer v) {
    return v == null ? null : String.valueOf(v);
  }
}
