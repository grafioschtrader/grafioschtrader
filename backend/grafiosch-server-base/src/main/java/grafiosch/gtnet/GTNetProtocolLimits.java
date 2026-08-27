package grafiosch.gtnet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The bounds an inbound GTNet message must respect before it is persisted, budgeted or dispatched.
 *
 * <p>
 * The database carries hard bounds of its own — {@code gt_net_message.message} is {@code varchar(1000)},
 * {@code gt_net_message_param.param_name} is {@code varchar(32)} and {@code param_value} is {@code varchar(255)} — but
 * reaching them produces a {@code DataException} and an HTTP 500 rather than a protocol rejection the peer can read.
 * These limits are checked first so that an over-sized or malformed envelope is answered with
 * {@link GNetCoreMessageCode#GT_NET_ERROR_S} and a stable {@code errorMsgCode}.
 * </p>
 *
 * <p>
 * The values are Spring properties rather than {@code globalparameters} rows: they are protocol constants of the
 * library, not something an administrator tunes per instance. The property names are dotted, so they are bound
 * individually with {@link Value} instead of through {@code @ConfigurationProperties}, whose relaxed binding would read
 * {@code g.gnet.max.body.bytes} as a nested object graph.
 * </p>
 */
@Component
public class GTNetProtocolLimits {

  /**
   * Outer bound on the JSON request body of the M2M endpoint, enforced before Jackson materializes the envelope.
   * {@code spring.servlet.multipart.max-*-size} does not apply to {@code application/json}, so this is enforced by a
   * servlet filter on the M2M path.
   */
  private final int maxBodyBytes;

  /** Maximum number of entries in the envelope's parameter map. */
  private final int maxParams;

  /** Maximum serialized size of the envelope's optional JSON payload. */
  private final int maxPayloadBytes;

  /**
   * How far the envelope timestamp may lie from the receiver's clock in either direction. Outside it the message is
   * refused with {@code CLOCK_SKEW_EXCEEDED} rather than stored with an implausible timestamp.
   */
  private final int maxClockSkewMinutes;

  /**
   * How long the token a refresh replaces stays accepted. The answerer commits the new token while handling the
   * request, so a response lost in transit would otherwise break both directions at once; within this window the
   * initiator can retry with either token.
   */
  private final int tokenOverlapDays;

  /**
   * Binds the five limits. The property names are dotted, so each is bound individually with {@link Value} rather than
   * through {@code @ConfigurationProperties}, whose relaxed binding would read {@code g.gnet.max.body.bytes} as a
   * nested object graph. Constructor injection keeps the bean constructible in a plain unit test.
   *
   * @param maxBodyBytes        outer bound on the JSON request body
   * @param maxParams           maximum number of envelope parameters
   * @param maxPayloadBytes     maximum serialized payload size
   * @param maxClockSkewMinutes accepted deviation of the envelope timestamp from the local clock
   * @param tokenOverlapDays    how long a replaced inbound token stays acceptable
   */
  public GTNetProtocolLimits(@Value("${g.gnet.max.body.bytes:4194304}") int maxBodyBytes,
      @Value("${g.gnet.max.params:32}") int maxParams,
      @Value("${g.gnet.max.payload.bytes:2097152}") int maxPayloadBytes,
      @Value("${g.gnet.max.clock.skew.minutes:5}") int maxClockSkewMinutes,
      @Value("${g.gnet.token.overlap.days:7}") int tokenOverlapDays) {
    this.maxBodyBytes = maxBodyBytes;
    this.maxParams = maxParams;
    this.maxPayloadBytes = maxPayloadBytes;
    this.maxClockSkewMinutes = maxClockSkewMinutes;
    this.tokenOverlapDays = tokenOverlapDays;
  }

  public int getMaxBodyBytes() {
    return maxBodyBytes;
  }

  public int getMaxParams() {
    return maxParams;
  }

  public int getMaxPayloadBytes() {
    return maxPayloadBytes;
  }

  public int getMaxClockSkewMinutes() {
    return maxClockSkewMinutes;
  }

  public int getTokenOverlapDays() {
    return tokenOverlapDays;
  }
}
