package grafioschtrader.tax.swiss.ech0196;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;

import uk.org.okapibarcode.backend.Pdf417;
import uk.org.okapibarcode.graphics.Color;
import uk.org.okapibarcode.output.Java2DRenderer;

/**
 * Generates eCH-0196 v2.2.0 compliant barcodes: PDF417 Structured Append (ISO/IEC 15438) for encoding the full XML
 * content and CODE128C for page identification, as specified in "Beilage 2 zu eCH-0196 V2.2.0 – Barcode Generierung –
 * Technische Wegleitung".
 *
 * <p>The XML is ZLIB-compressed (Deflater.BEST_COMPRESSION) before being split into PDF417 Structured Append symbols
 * with 13 data columns, EC level 4, and byte compaction mode.</p>
 */
@Service
public class Ech0196BarcodeGenerator {

  /** PDF417 data columns per eCH-0196 spec. */
  private static final int PDF417_DATA_COLUMNS = 13;
  /** PDF417 rows per eCH-0196 spec. */
  private static final int PDF417_ROWS = 35;
  /** PDF417 error correction level per eCH-0196 spec. */
  private static final int PDF417_EC_LEVEL = 4;
  /** Magnification factor for rendering PDF417 symbols to PNG. */
  private static final double PDF417_MAGNIFICATION = 2.0;

  private static final int CODE128C_WIDTH = 300;
  private static final int CODE128C_HEIGHT = 34;

  /**
   * Generates PDF417 Structured Append barcodes encoding the full XML content per eCH-0196 v2.2.0. The XML is
   * ZLIB-compressed, then automatically split into structured append segments by OkapiBarcode. Each segment carries
   * Macro PDF417 metadata (file name, file ID, segment index, total count).
   *
   * @param xmlBytes    the full eCH-0196 XML document bytes
   * @param statementId the unique statement identifier used as PDF Macro file name
   * @return list of PNG image bytes, one per structured append segment
   */
  public List<byte[]> generatePdf417Barcodes(byte[] xmlBytes, String statementId) throws Exception {
    byte[] compressedXml = zlibCompress(xmlBytes);

    Pdf417 template = new Pdf417();
    template.setDataColumns(PDF417_DATA_COLUMNS);
    template.setRows(PDF417_ROWS);
    template.setPreferredEccLevel(PDF417_EC_LEVEL);
    template.setForceByteCompaction(true);
    template.setStructuredAppendFileName(statementId);

    List<Pdf417> symbols = Pdf417.createStructuredAppendSymbols(compressedXml, template);

    List<byte[]> barcodeImages = new ArrayList<>();
    for (Pdf417 symbol : symbols) {
      barcodeImages.add(renderSymbolToPng(symbol));
    }
    return barcodeImages;
  }

  /**
   * Generates a CODE128C barcode for page identification per eCH-0196 v2.2.0. The input must be a 16-digit numeric
   * string structured as: 196 + version(2) + clearing-nr(5) + page(3) + barcode-flag(1) + orientation(1) +
   * reading-direction(1).
   *
   * @param numericDocId 16-digit even-length numeric string
   * @return PNG image bytes
   */
  public byte[] generateCode128CBarcode(String numericDocId) throws Exception {
    Code128Writer writer = new Code128Writer();
    BitMatrix bitMatrix = writer.encode(numericDocId, BarcodeFormat.CODE_128, CODE128C_WIDTH, CODE128C_HEIGHT,
        Map.of(EncodeHintType.FORCE_CODE_SET, "C"));

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(MatrixToImageWriter.toBufferedImage(bitMatrix), "PNG", baos);
    return baos.toByteArray();
  }

  /**
   * ZLIB-compresses data using Deflater with BEST_COMPRESSION, as required by eCH-0196 v2.2.0.
   */
  private byte[] zlibCompress(byte[] data) {
    Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
    try {
      deflater.setInput(data);
      deflater.finish();
      ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
      byte[] buffer = new byte[4096];
      while (!deflater.finished()) {
        int count = deflater.deflate(buffer);
        baos.write(buffer, 0, count);
      }
      return baos.toByteArray();
    } finally {
      deflater.end();
    }
  }

  /**
   * Renders an OkapiBarcode symbol to PNG image bytes.
   */
  private byte[] renderSymbolToPng(uk.org.okapibarcode.backend.Symbol symbol) throws Exception {
    int width = (int) Math.ceil(symbol.getWidth() * PDF417_MAGNIFICATION);
    int height = (int) Math.ceil(symbol.getHeight() * PDF417_MAGNIFICATION);

    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
    Graphics2D g2d = image.createGraphics();
    g2d.setColor(java.awt.Color.WHITE);
    g2d.fillRect(0, 0, width, height);

    Java2DRenderer renderer = new Java2DRenderer(g2d, PDF417_MAGNIFICATION, Color.WHITE, Color.BLACK);
    renderer.render(symbol);
    g2d.dispose();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(image, "PNG", baos);
    return baos.toByteArray();
  }
}
