package grafioschtrader.tax.swiss.ech0196.model;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

/**
 * JAXB adapter that marshals Double values to strings with exactly 2 decimal places for xs:decimal XML attributes.
 */
public class Double2DecimalAdapter extends XmlAdapter<String, Double> {

  @Override
  public Double unmarshal(String v) {
    return v == null ? null : Double.parseDouble(v);
  }

  @Override
  public String marshal(Double v) {
    return v == null ? null : String.format("%.2f", v);
  }
}
