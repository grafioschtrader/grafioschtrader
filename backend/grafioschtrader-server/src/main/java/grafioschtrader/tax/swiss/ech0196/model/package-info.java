@jakarta.xml.bind.annotation.XmlSchema(
    namespace = "http://www.ech.ch/xmlns/eCH-0196/2",
    elementFormDefault = jakarta.xml.bind.annotation.XmlNsForm.QUALIFIED,
    xmlns = {
        @jakarta.xml.bind.annotation.XmlNs(prefix = "eCH-0196", namespaceURI = "http://www.ech.ch/xmlns/eCH-0196/2")
    }
)
@XmlJavaTypeAdapter(value = Double2DecimalAdapter.class, type = Double.class)
package grafioschtrader.tax.swiss.ech0196.model;

import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
