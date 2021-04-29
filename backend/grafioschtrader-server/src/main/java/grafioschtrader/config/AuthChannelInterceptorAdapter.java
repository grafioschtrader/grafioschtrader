package grafioschtrader.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import grafioschtrader.exceptions.RequestLimitAndSecurityBreachException;
import grafioschtrader.security.TokenAuthenticationService;

@Component
@ConditionalOnExpression("${gt.use.websocket:false}")
public class AuthChannelInterceptorAdapter implements ChannelInterceptor {

  @Autowired
  TokenAuthenticationService tokenAuthenticationService;

  @Override
  public Message<?> preSend(final Message<?> message, final MessageChannel channel) {
    try {
      StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
      if (accessor.getCommand() != StompCommand.DISCONNECT && accessor.getCommand() != StompCommand.UNSUBSCRIBE) {
        Authentication auth = tokenAuthenticationService.generateAuthenticationFromStompHeader(message, accessor);
        SecurityContextHolder.getContext().setAuthentication(auth);
        accessor.setUser(auth);
      }
    } catch (RequestLimitAndSecurityBreachException lee) {
      lee.printStackTrace();
    }

    return message;
  }

}
