package grafioschtrader.tax.swiss.ech0196;

import java.io.ByteArrayOutputStream;

import org.springframework.stereotype.Service;

import grafioschtrader.tax.swiss.ech0196.model.Ech0196TaxStatement;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;

/**
 * Marshals an {@link Ech0196TaxStatement} JAXB object to UTF-8 XML bytes.
 */
@Service
public class Ech0196XmlGenerator {

  private static final String SCHEMA_LOCATION =
      "http://www.ech.ch/xmlns/eCH-0196/2 http://www.ech.ch/xmlns/eCH-0196/2/eCH-0196-2-2.xsd";

  public byte[] generateXml(Ech0196TaxStatement taxStatement) throws Exception {
    JAXBContext context = JAXBContext.newInstance(Ech0196TaxStatement.class);
    Marshaller marshaller = context.createMarshaller();
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
    marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
    marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, SCHEMA_LOCATION);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    marshaller.marshal(taxStatement, baos);
    return baos.toByteArray();
  }
}
