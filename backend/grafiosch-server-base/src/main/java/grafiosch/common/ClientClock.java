package grafiosch.common;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import grafiosch.BaseConstants;

/**
 * Calendar dates in the requesting browser's time zone. Background jobs and callers without a valid zone use UTC.
 * Resolve today on the request thread and pass it explicitly to asynchronous work.
 */
public final class ClientClock {

  private ClientClock() {
  }

  public static ZoneId zone() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
      String zone = attributes.getRequest().getHeader(BaseConstants.TIME_ZONE_HEADER);
      if (zone != null) {
        try {
          return ZoneId.of(zone);
        } catch (DateTimeException ignored) {
          // Older clients and invalid headers must not prevent a request from being handled.
        }
      }
    }
    return ZoneOffset.UTC;
  }

  public static LocalDate today() {
    return LocalDate.now(zone());
  }
}
