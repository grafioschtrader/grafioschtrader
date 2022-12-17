package grafioschtrader.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {

  @Override
  public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException e) throws IOException, ServletException {
    HttpServletResponse httpResponse = response;
    httpResponse.setContentType("text/plain");
    httpResponse.setStatus(HttpStatus.UNAUTHORIZED.value());

    httpResponse.getWriter().append(e instanceof LockedException ? "login.ipaddress.locked" : "login.failure");
  }
}
