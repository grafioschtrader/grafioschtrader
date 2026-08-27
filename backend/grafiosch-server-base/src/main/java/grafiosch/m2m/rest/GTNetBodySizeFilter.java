package grafiosch.m2m.rest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetProtocolLimits;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The outer bound on an inbound M2M request body.
 *
 * <p>
 * No Spring Boot property caps a JSON body: {@code server.max-http-request-header-size} is headers only,
 * {@code server.tomcat.max-http-form-post-size} applies to form encoding and
 * {@code spring.servlet.multipart.max-request-size} to multipart. The cap therefore lives in a filter, ahead of the
 * message converter, so an over-sized body is refused before Jackson materializes it into memory.
 * </p>
 *
 * <p>
 * The refusal is HTTP 200 with a {@link GNetCoreMessageCode#GT_NET_ERROR_S} envelope rather than HTTP 413, for the same
 * reason the endpoint carries no {@code @Valid}: {@code BaseDataClient} discards the body of every non-2xx, so a 413
 * would reach the sender as a failed delivery with no reason. The envelope is written as a literal, because a filter
 * runs outside any transaction and an error path must not need the database.
 * </p>
 */
@Component
public class GTNetBodySizeFilter extends OncePerRequestFilter {

  /** Only the machine-to-machine endpoints are capped; ordinary API traffic keeps the container's own limits. */
  private static final String M2M_PATH_PREFIX = grafiosch.rest.RequestMappings.M2M_API;

  private final GTNetProtocolLimits protocolLimits;

  public GTNetBodySizeFilter(GTNetProtocolLimits protocolLimits) {
    this.protocolLimits = protocolLimits;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().contains(M2M_PATH_PREFIX);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long maxBodyBytes = protocolLimits.getMaxBodyBytes();
    long declaredLength = request.getContentLengthLong();
    if (declaredLength > maxBodyBytes) {
      logger.warn("Refused an M2M body of " + declaredLength + " bytes, the limit is " + maxBodyBytes);
      writePayloadTooLarge(response);
      return;
    }
    if (declaredLength < 0) {
      // A chunked request declares no length, so the only way to bound it is to count while it is read and abort.
      filterChain.doFilter(new BoundedBodyRequestWrapper(request, maxBodyBytes), response);
      return;
    }
    filterChain.doFilter(request, response);
  }

  /**
   * Wraps a request whose body size is not known in advance and aborts the read once the cap is passed.
   */
  private static class BoundedBodyRequestWrapper extends HttpServletRequestWrapper {

    private final long maxBodyBytes;

    BoundedBodyRequestWrapper(HttpServletRequest request, long maxBodyBytes) {
      super(request);
      this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      ServletInputStream delegate = super.getInputStream();
      return new ServletInputStream() {

        private long read;

        @Override
        public int read() throws IOException {
          int value = delegate.read();
          if (value != -1 && ++read > maxBodyBytes) {
            throw new GTNetBodyTooLargeException(maxBodyBytes);
          }
          return value;
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws IOException {
          int count = delegate.read(buffer, off, len);
          if (count > 0 && (read += count) > maxBodyBytes) {
            throw new GTNetBodyTooLargeException(maxBodyBytes);
          }
          return count;
        }

        @Override
        public boolean isFinished() {
          return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
          return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
          delegate.setReadListener(readListener);
        }
      };
    }

    @Override
    public BufferedReader getReader() throws IOException {
      return new BufferedReader(new InputStreamReader(getInputStream(),
          getCharacterEncoding() == null ? StandardCharsets.UTF_8.name() : getCharacterEncoding()));
    }
  }

  /**
   * Writes the refusal envelope. Hand-built so that the filter needs neither a repository nor a serializer, and
   * complete enough for the sender to read: a receiving peer tolerates a null {@code sourceGtNet}.
   *
   * @param response the servlet response to write to
   * @throws IOException when the response cannot be written
   */
  private void writePayloadTooLarge(HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.OK.value());
    response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    response.getWriter()
        .write("{\"messageCode\":" + GNetCoreMessageCode.GT_NET_ERROR_S.getValue()
            + ",\"errorMsgCode\":\"PAYLOAD_TOO_LARGE\",\"serverBusy\":false,\"visibility\":0,"
            + "\"message\":\"The message body exceeds the accepted size\"}");
  }
}
