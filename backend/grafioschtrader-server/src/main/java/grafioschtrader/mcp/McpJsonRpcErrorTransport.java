package grafioschtrader.mcp;

import java.util.List;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import reactor.core.publisher.Mono;

/**
 * Wraps the stateless WebMVC transport of Spring AI so that a request for a method the server does not implement is
 * answered with the JSON-RPC error {@code -32601 Method not found} instead of HTTP 500.
 *
 * <p>
 * The SDK handler reports an unknown method as a failed {@code Mono}, and the transport turns every failure into HTTP
 * 500 with a body that is not a JSON-RPC response. A client of a newer protocol revision probes with
 * {@code server/discover} and falls back to {@code initialize} only on {@code Method not found}; ChatGPT receiving the
 * 500 instead gave up on every query. With this wrapper the error carries the request id and arrives with HTTP 200, as
 * JSON-RPC prescribes for the protocol versions the SDK implements.
 * </p>
 */
public class McpJsonRpcErrorTransport implements McpStatelessServerTransport {

  private final McpStatelessServerTransport delegate;

  public McpJsonRpcErrorTransport(McpStatelessServerTransport delegate) {
    this.delegate = delegate;
  }

  @Override
  public void setMcpHandler(McpStatelessServerHandler mcpHandler) {
    delegate.setMcpHandler(new McpStatelessServerHandler() {

      @Override
      public Mono<McpSchema.JSONRPCResponse> handleRequest(McpTransportContext transportContext,
          McpSchema.JSONRPCRequest request) {
        return mcpHandler.handleRequest(transportContext, request).onErrorResume(McpError.class,
            e -> e.getJsonRpcError() == null ? Mono.error(e)
                : Mono.just(McpSchema.JSONRPCResponse.error(request.id(), e.getJsonRpcError())));
      }

      @Override
      public Mono<Void> handleNotification(McpTransportContext transportContext,
          McpSchema.JSONRPCNotification notification) {
        return mcpHandler.handleNotification(transportContext, notification);
      }
    });
  }

  @Override
  public Mono<Void> closeGracefully() {
    return delegate.closeGracefully();
  }

  @Override
  public List<String> protocolVersions() {
    return delegate.protocolVersions();
  }
}
