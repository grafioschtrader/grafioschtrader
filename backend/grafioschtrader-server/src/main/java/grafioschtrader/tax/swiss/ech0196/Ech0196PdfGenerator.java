package grafioschtrader.tax.swiss.ech0196;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Generates a PDF from eCH-0196 XML using Apache FOP, then appends landscape PDF417 barcode pages using PDFBox. Per
 * eCH-0196 v2.2.0 spec, barcode pages are A4 landscape with up to 6 PDF417 segments placed side by side, each rotated
 * 90° and stretching from top to bottom.
 */
@Service
public class Ech0196PdfGenerator {

  private static final String XSLT_PATH = "xslt/ech0196-to-pdf.xsl";
  private static final float PAGE_MARGIN = 30f;
  /** Horizontal gap between barcode columns. */
  private static final float COLUMN_GAP = 8f;
  /** Maximum PDF417 segments per landscape barcode page per eCH-0196 spec. */
  private static final int SEGMENTS_PER_PAGE = 6;
  /** Space reserved at top for page header text. */
  private static final float HEADER_HEIGHT = 18f;
  /** Space for segment label above each barcode column. */
  private static final float LABEL_HEIGHT = 10f;
  /** Height reserved at page bottom for CODE128C barcode + clearance. */
  private static final float BOTTOM_RESERVED = 45f;

  /**
   * Transforms eCH-0196 XML to PDF with CODE128C on every content page and appended landscape PDF417 barcode pages.
   *
   * @param xmlBytes      the eCH-0196 XML document
   * @param code128CImage CODE128C barcode PNG for page identification (rendered in header via XSLT)
   * @param pdf417Images  list of PDF417 barcode PNG images encoding the XML content
   * @return the final PDF bytes with content pages and appended barcode pages
   */
  public byte[] generatePdf(byte[] xmlBytes, byte[] code128CImage, List<byte[]> pdf417Images) throws Exception {
    byte[] contentPdf = generateContentPdf(xmlBytes, code128CImage);
    return appendBarcodePages(contentPdf, pdf417Images, code128CImage);
  }

  private byte[] generateContentPdf(byte[] xmlBytes, byte[] code128CImage) throws Exception {
    FopFactory fopFactory = FopFactory.newInstance(new java.io.File(".").toURI());
    ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
    Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, pdfOut);

    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Source xsltSource = new StreamSource(new ClassPathResource(XSLT_PATH).getInputStream());
    Transformer transformer = transformerFactory.newTransformer(xsltSource);

    if (code128CImage != null) {
      String dataUri = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(code128CImage);
      transformer.setParameter("code128CImageUri", dataUri);
    }

    Source xmlSource = new StreamSource(new ByteArrayInputStream(xmlBytes));
    Result result = new SAXResult(fop.getDefaultHandler());
    transformer.transform(xmlSource, result);

    return pdfOut.toByteArray();
  }

  private byte[] appendBarcodePages(byte[] contentPdf, List<byte[]> pdf417Images, byte[] code128CImage)
      throws Exception {
    if (pdf417Images == null || pdf417Images.isEmpty()) {
      return contentPdf;
    }

    try (PDDocument document = Loader.loadPDF(contentPdf)) {
      PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
      PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

      int totalSegments = pdf417Images.size();
      int totalBarcodePages = (totalSegments + SEGMENTS_PER_PAGE - 1) / SEGMENTS_PER_PAGE;

      int barcodeIdx = 0;
      int barcodePageNum = 0;

      while (barcodeIdx < totalSegments) {
        barcodePageNum++;
        PDRectangle landscape = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
        PDPage page = new PDPage(landscape);
        document.addPage(page);

        float pageWidth = landscape.getWidth();   // 842
        float pageHeight = landscape.getHeight(); // 595

        // Available area for barcode columns
        float contentWidth = pageWidth - 2 * PAGE_MARGIN;
        float barcodeTop = pageHeight - PAGE_MARGIN - HEADER_HEIGHT - LABEL_HEIGHT;
        float barcodeBottom = PAGE_MARGIN + BOTTOM_RESERVED;
        float availableHeight = barcodeTop - barcodeBottom;

        // How many segments on this page
        int remaining = totalSegments - barcodeIdx;
        int segmentsOnPage = Math.min(remaining, SEGMENTS_PER_PAGE);

        // Column width for this page
        float columnWidth = (contentWidth - (segmentsOnPage - 1) * COLUMN_GAP) / segmentsOnPage;

        try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
          // Page header
          cs.beginText();
          cs.setFont(fontBold, 9);
          cs.newLineAtOffset(PAGE_MARGIN, pageHeight - PAGE_MARGIN);
          cs.showText("Barcode-Seite " + barcodePageNum + " von " + totalBarcodePages);
          cs.endText();

          // Place barcodes side by side, each rotated 90° stretching top to bottom
          for (int col = 0; col < segmentsOnPage; col++) {
            PDImageXObject barcodeImg = PDImageXObject.createFromByteArray(document,
                pdf417Images.get(barcodeIdx), "pdf417_" + barcodeIdx);

            float xCol = PAGE_MARGIN + col * (columnWidth + COLUMN_GAP);

            // Segment label (not rotated, above the barcode column)
            cs.beginText();
            cs.setFont(fontRegular, 7);
            cs.newLineAtOffset(xCol, barcodeTop + 2);
            cs.showText("Segment " + (barcodeIdx + 1) + "/" + totalSegments);
            cs.endText();

            // Calculate rotated barcode dimensions:
            // Original image: imgW (wide) × imgH (short)
            // After 90° CCW rotation: imgH (horizontal) × imgW (vertical)
            float imgPixelW = barcodeImg.getWidth();
            float imgPixelH = barcodeImg.getHeight();
            float aspectRatio = imgPixelW / imgPixelH;

            // The barcode's original width becomes the vertical span after rotation
            // The barcode's original height becomes the horizontal span after rotation
            // Scale so the vertical span fills availableHeight
            float drawW = availableHeight;             // pre-rotation width → vertical on page
            float drawH = drawW / aspectRatio;         // pre-rotation height → horizontal on page

            // Cap horizontal span to column width
            if (drawH > columnWidth) {
              drawH = columnWidth;
              drawW = drawH * aspectRatio;
            }

            // Center horizontally within column
            float xOffset = (columnWidth - drawH) / 2;

            // Draw rotated barcode using transformation matrix
            // 90° CCW rotation: (x,y) → (-y, x)
            // Matrix(a,b,c,d,e,f) = (0, 1, -1, 0, tx, ty)
            // Maps (0,0)→(tx,ty), (drawW,0)→(tx, ty+drawW), (0,drawH)→(tx-drawH, ty)
            // We want bottom-left at (xCol+xOffset, barcodeBottom), so tx = xCol+xOffset+drawH
            float tx = xCol + xOffset + drawH;
            float ty = barcodeBottom;

            cs.saveGraphicsState();
            cs.transform(new Matrix(0, 1, -1, 0, tx, ty));
            cs.drawImage(barcodeImg, 0, 0, drawW, drawH);
            cs.restoreGraphicsState();

            barcodeIdx++;
          }

          // CODE128C at bottom-right of barcode page
          if (code128CImage != null) {
            PDImageXObject code128Img = PDImageXObject.createFromByteArray(document, code128CImage, "code128c");
            float c128Width = 130;
            float c128Height = c128Width * code128Img.getHeight() / code128Img.getWidth();
            cs.drawImage(code128Img, pageWidth - PAGE_MARGIN - c128Width, PAGE_MARGIN + 3, c128Width, c128Height);
          }
        }
      }

      ByteArrayOutputStream resultOut = new ByteArrayOutputStream();
      document.save(resultOut);
      return resultOut.toByteArray();
    }
  }
}
