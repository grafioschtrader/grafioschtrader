package grafioschtrader.config;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class ApplicationContextProvider implements ApplicationContextAware {

  private static ApplicationContext context;

  private ApplicationContextProvider() {
  }

  public static ApplicationContext getApplicationContext() {
    return context;
  }

  public static <T> T getBean(final String name, final Class<T> aClass) {
    return context.getBean(name, aClass);
  }

  @Override
  public void setApplicationContext(final ApplicationContext ctx) throws BeansException {
    context = ctx;
  }
}
