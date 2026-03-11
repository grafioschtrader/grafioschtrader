package grafioschtrader.tax.swiss.ech0196;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.datamatrix.DataMatrixWriter;

/**
 * Generates a DataMatrix barcode encoding a tax statement reference.
 */
@Service
public class Ech0196BarcodeGenerator {

  private static final int BARCODE_SIZE = 200;

  /**
   * Generates a DataMatrix barcode PNG encoding the statement ID.
   *
   * @param statementId the unique statement identifier
   * @return PNG image bytes
   */
  public byte[] generateBarcode(String statementId) throws Exception {
    DataMatrixWriter writer = new DataMatrixWriter();
    BitMatrix bitMatrix = writer.encode(statementId, BarcodeFormat.DATA_MATRIX, BARCODE_SIZE, BARCODE_SIZE,
        Map.of(EncodeHintType.MARGIN, 1));
    BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(image, "PNG", baos);
    return baos.toByteArray();
  }
}
