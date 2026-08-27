package grafiosch.m2m.rest;

import java.io.IOException;

/**
 * Raised while reading an M2M request body that grows past {@code g.gnet.max.body.bytes}.
 *
 * <p>
 * A chunked request declares no {@code Content-Length}, so its size is only known while it is being read. Aborting the
 * read is what stops an unbounded body from being buffered; the message converter reports the abort as an unreadable
 * body, and {@link GTNetM2MExceptionAdvice} recognizes this cause in the chain to answer with {@code PAYLOAD_TOO_LARGE}
 * rather than the generic {@code ENVELOPE_INVALID}.
 * </p>
 */
public class GTNetBodyTooLargeException extends IOException {

  private static final long serialVersionUID = 1L;

  public GTNetBodyTooLargeException(long limitBytes) {
    super("The message body exceeds the accepted size of " + limitBytes + " bytes");
  }
}
