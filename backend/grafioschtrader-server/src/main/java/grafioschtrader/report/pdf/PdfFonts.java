package grafioschtrader.report.pdf;

import java.io.IOException;
import java.io.InputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/** One pair of embedded, subsetted Unicode fonts per document. */
public record PdfFonts(PDType0Font regular, PDType0Font bold) {
  public static PdfFonts load(PDDocument document) throws IOException {
    return new PdfFonts(load(document, "Regular"), load(document, "Bold"));
  }

  private static PDType0Font load(PDDocument document, String weight) throws IOException {
    try (InputStream font = PdfFonts.class.getResourceAsStream("/fonts/NotoSans-" + weight + ".ttf")) {
      if (font == null) {
        throw new IOException("Missing bundled report font: " + weight);
      }
      return PDType0Font.load(document, font, true);
    }
  }
}
