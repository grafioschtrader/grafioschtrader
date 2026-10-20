package grafioschtrader.mcp;

/**
 * Stable codes of a failed tool call. A failure of the tool itself is a tool result with {@code isError = true} and the
 * content {@code {code, message, ...}}; only the authentication failures of the security chain are HTTP answers.
 */
public enum McpErrorCode {
  /** A parameter is missing, invalid or out of bounds; extra field {@code parameter}. */
  INVALID_ARGUMENT,
  /** An id passed by the client does not belong to the home tenant; extra field {@code parameter}. */
  NOT_OWNED,
  /** The report rejected the request for a business reason. */
  DOMAIN_ERROR,
  /** The daily budget of tool calls is used up; extra field {@code resetAt} in UTC. */
  QUOTA_EXCEEDED,
  /** The request bucket of the user is empty; extra field {@code retryAfterSeconds}. */
  RATE_LIMITED,
  /** A heavy report of the same user is still running. */
  BUSY,
  /** Unexpected failure, logged with a correlation id; extra field {@code correlationId}. */
  INTERNAL
}
