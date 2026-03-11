package grafioschtrader.tax.swiss.ech0196;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Generates a PDF from eCH-0196 XML using Apache FOP with an XSLT stylesheet.
 */
@Service
public class Ech0196PdfGenerator {

  private static final String XSLT_PATH = "xslt/ech0196-to-pdf.xsl";

  /**
   * Transforms eCH-0196 XML to PDF.
   *
   * @param xmlBytes     the eCH-0196 XML document
   * @param barcodeImage optional barcode PNG bytes (may be null)
   * @return the rendered PDF bytes
   */
  public byte[] generatePdf(byte[] xmlBytes, byte[] barcodeImage) throws Exception {
    FopFactory fopFactory = FopFactory.newInstance(new java.io.File(".").toURI());
    ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
    Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, pdfOut);

    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Source xsltSource = new StreamSource(new ClassPathResource(XSLT_PATH).getInputStream());
    Transformer transformer = transformerFactory.newTransformer(xsltSource);

    if (barcodeImage != null) {
      String barcodeDataUri = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(barcodeImage);
      transformer.setParameter("barcodeImageUri", barcodeDataUri);
    }

    Source xmlSource = new StreamSource(new ByteArrayInputStream(xmlBytes));
    Result result = new SAXResult(fop.getDefaultHandler());
    transformer.transform(xmlSource, result);

    return pdfOut.toByteArray();
  }
}
