package grafiosch.gtnet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import grafiosch.gtnet.handler.GTNetMessageHandler;
import grafiosch.gtnet.handler.GTNetMessageHandlerRegistry;

/**
 * Fails the start-up when the protocol registration is incomplete.
 *
 * <p>
 * A code registered without its handler, without the model its category requires, without the answers a request needs
 * or without a label used to surface only at runtime — as an error envelope the peer could not act on, or as a raw
 * number in the message list. All of that is decidable the moment the application context is up, so it is decided
 * there, and every violation is reported at once rather than one per restart.
 * </p>
 *
 * <p>
 * The check is skipped when the context holds no handlers. That is not a loophole but the JUnit context: {@code
 * GTforTest} excludes {@code grafiosch.gtnet.handler.*} from the component scan, so a context without a single handler
 * is one that was never meant to serve GTNet. The registry is therefore looked up from the refreshed context rather
 * than injected — with the package excluded there is no bean definition at all, and neither {@code @Lazy} nor an
 * optional dependency can stand in for a definition that does not exist.
 * </p>
 */
@Component
public class GTNetProtocolStartupValidator implements ApplicationListener<ContextRefreshedEvent> {

  private static final Logger log = LoggerFactory.getLogger(GTNetProtocolStartupValidator.class);

  /** The languages every user-facing code must be labelled in. */
  private static final List<Locale> REQUIRED_LOCALES = List.of(Locale.ENGLISH, Locale.GERMAN);

  private final GTNetMessageCodeRegistry messageCodeRegistry;

  private final MessageSource messageSource;

  /**
   * @param messageCodeRegistry the protocol registry to check
   * @param messageSource       the bundle every code is labelled from
   */
  public GTNetProtocolStartupValidator(GTNetMessageCodeRegistry messageCodeRegistry, MessageSource messageSource) {
    this.messageCodeRegistry = messageCodeRegistry;
    this.messageSource = messageSource;
  }

  @Override
  public void onApplicationEvent(ContextRefreshedEvent event) {
    validate(event.getApplicationContext().getBeanProvider(GTNetMessageHandlerRegistry.class).getIfAvailable());
  }

  /**
   * Runs every check over the registered protocol.
   *
   * @param handlerRegistry the handlers this context discovered, null when it scanned none
   * @throws IllegalStateException listing all violations, when the registration is incomplete
   */
  public void validate(GTNetMessageHandlerRegistry handlerRegistry) {
    if (handlerRegistry == null || handlerRegistry.getHandlerCount() == 0) {
      log.info("GTNet protocol validation skipped - this context registers no message handlers");
      return;
    }
    List<String> violations = new ArrayList<>();
    for (GTNetProtocolDescriptor descriptor : messageCodeRegistry.getAllDescriptors()) {
      checkHandler(descriptor, handlerRegistry, violations);
      checkModel(descriptor, violations);
      checkResponses(descriptor, violations);
      checkNlsKey(descriptor, violations);
    }
    checkHandlersWithoutDescriptor(handlerRegistry, violations);

    if (!violations.isEmpty()) {
      throw new IllegalStateException("GTNet protocol registration is incomplete:" + System.lineSeparator()
          + String.join(System.lineSeparator(), violations.stream().map(violation -> "  - " + violation).toList()));
    }
    log.info("GTNet protocol validated: {} codes, {} handlers", messageCodeRegistry.getAllDescriptors().size(),
        handlerRegistry.getHandlerCount());
  }

  /**
   * A code that can arrive on its own needs a handler, and that handler must agree with the descriptor about what kind
   * of message it is.
   */
  private void checkHandler(GTNetProtocolDescriptor descriptor, GTNetMessageHandlerRegistry handlerRegistry,
      List<String> violations) {
    GTNetMessageHandler handler = handlerRegistry.findHandler(descriptor.value()).orElse(null);
    if (descriptor.inboundDispatch()) {
      if (handler == null) {
        violations.add(descriptor.name() + " is dispatched inbound but has no handler");
      } else if (handler.getCategory() != descriptor.category()) {
        violations.add(descriptor.name() + " is declared " + descriptor.category() + " but its handler "
            + handler.getClass().getSimpleName() + " reports " + handler.getCategory());
      }
    } else if (handler != null) {
      violations.add(descriptor.name() + " declares no inbound dispatch but " + handler.getClass().getSimpleName()
          + " handles it");
    }
  }

  /** A form can only be built from a model. */
  private void checkModel(GTNetProtocolDescriptor descriptor, List<String> violations) {
    if (descriptor.formEligible() && descriptor.model() == null) {
      violations.add(descriptor.name() + " is form eligible but carries no payload model");
    }
  }

  /** A request that stays open needs answers, and every answer must be a registered response. */
  private void checkResponses(GTNetProtocolDescriptor descriptor, List<String> violations) {
    if (descriptor.requiresResponse() && descriptor.validResponses().isEmpty()) {
      violations.add(descriptor.name() + " requires a response but declares no valid response code");
    }
    for (GTNetMessageCode response : descriptor.validResponses()) {
      GTNetProtocolDescriptor responseDescriptor = messageCodeRegistry.getDescriptor(response.getValue());
      if (responseDescriptor == null) {
        violations.add(descriptor.name() + " names the unregistered response " + response.name());
      } else if (responseDescriptor.category() != MessageCategory.RESPONSE && !descriptor.threadable()) {
        violations.add(descriptor.name() + " names " + response.name() + " as an answer, but that code is "
            + responseDescriptor.category());
      }
    }
  }

  /**
   * A code that can end up in a stored message row is rendered by its own name, so it needs a label in both languages.
   * A code that is neither sent by a person, nor dispatched, nor an answer to anything never reaches a row and is
   * exempt.
   */
  private void checkNlsKey(GTNetProtocolDescriptor descriptor, List<String> violations) {
    if (!descriptor.userInitiable() && !descriptor.inboundDispatch()
        && !messageCodeRegistry.isRegisteredResponse(descriptor.value())) {
      return;
    }
    for (Locale locale : REQUIRED_LOCALES) {
      try {
        messageSource.getMessage(descriptor.name(), null, locale);
      } catch (NoSuchMessageException e) {
        violations.add(descriptor.name() + " has no NLS key for language " + locale.getLanguage());
      }
    }
  }

  /** A handler for a code nobody registered can never be reached, because dispatch resolves the code first. */
  private void checkHandlersWithoutDescriptor(GTNetMessageHandlerRegistry handlerRegistry, List<String> violations) {
    for (Byte codeValue : handlerRegistry.getRegisteredCodeValues()) {
      if (messageCodeRegistry.getDescriptor(codeValue) == null) {
        violations.add("handler " + handlerRegistry.getHandler(codeValue).getClass().getSimpleName()
            + " claims the unregistered message code " + codeValue);
      }
    }
  }
}
