package grafiosch.gtnet;

/**
 * The outcome of a synchronous GTNet exchange, from the caller's point of view.
 *
 * <p>
 * The HTTP status of a GTNet call is 200 for every protocol outcome, so "a body came back" says nothing about whether
 * the peer served the request. A refusal such as {@code GT_NET_LASTPRICE_MAX_LIMIT_EXCEEDED_S}, a deferred
 * acknowledgement and an error all arrive the same way as an answer, and a caller that only tests for a payload reads
 * every one of them as "no data received" — which is indistinguishable from a peer that legitimately had nothing.
 * </p>
 *
 * @param <T> the type of the payload a successful answer carries
 */
public sealed interface GTNetResponseResult<T> {

  /**
   * The peer answered with one of the codes the caller declared it accepts.
   *
   * @param payload     the deserialized payload, null when the answer carries none
   * @param messageCode the accepted code that came back
   */
  record Success<T>(T payload, byte messageCode) implements GTNetResponseResult<T> {
  }

  /**
   * The peer understood the request and declined to serve it — a limit, a missing grant, a cooling-off period.
   *
   * @param messageCode  the refusing code
   * @param errorMsgCode the stable reason, null when the refusal code carries the reason on its own
   */
  record Refused<T>(byte messageCode, String errorMsgCode) implements GTNetResponseResult<T> {
  }

  /** The peer took the request but has not decided; the answer arrives later as a message of its own. */
  record Deferred<T>() implements GTNetResponseResult<T> {
  }

  /**
   * Nothing usable came back: no response, an unexpected code, or a payload that could not be read.
   *
   * @param reason what went wrong, for the log
   */
  record Failed<T>(String reason) implements GTNetResponseResult<T> {
  }

  /**
   * The payload of a successful answer, or null for every other outcome. For the many call sites that only need the
   * data and handle the rest by logging.
   *
   * @return the payload, or null
   */
  default T payloadOrNull() {
    return this instanceof Success<T> success ? success.payload() : null;
  }

  /**
   * Whether the peer accepted and answered the request.
   *
   * @return true only for {@link Success}
   */
  default boolean isSuccess() {
    return this instanceof Success<T>;
  }
}
