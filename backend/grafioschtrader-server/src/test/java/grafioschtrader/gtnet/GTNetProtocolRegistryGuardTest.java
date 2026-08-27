package grafioschtrader.gtnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.type.filter.AssignableTypeFilter;

import grafiosch.gtnet.CoreProtocolDescriptors;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCodeRegistry;
import grafiosch.gtnet.GTNetProtocolDescriptor;
import grafiosch.gtnet.GTNetProtocolStartupValidator;
import grafiosch.gtnet.handler.GTNetMessageHandler;
import grafiosch.gtnet.handler.GTNetMessageHandlerRegistry;
import grafiosch.gtnet.model.msg.DataRequestMsg;

/**
 * Sweeps the whole protocol the way the start-up guard does, but without a Spring context.
 *
 * <p>
 * The handlers are discovered by scanning the same two packages Spring scans and are instantiated through their
 * shortest constructor with null arguments. Nothing the guard asks of a handler — which codes it claims and which
 * category it reports — touches an injected field, so an uninitialised instance answers exactly as the running bean
 * would. That is what makes it a complete sweep rather than the spot check its predecessor performed on six of the
 * twenty application codes.
 * </p>
 */
class GTNetProtocolRegistryGuardTest {

  /** The two packages that hold {@code @Component} message handlers, library first. */
  private static final List<String> HANDLER_PACKAGES = List.of("grafiosch.gtnet.handler.impl",
      "grafioschtrader.gtnet.handler.impl");

  @Test
  @DisplayName("Core and application protocol together pass every start-up check")
  void theRegisteredProtocolIsComplete() {
    validatorFor(fullRegistry()).validate(handlerRegistry());
  }

  @Test
  @DisplayName("Every code carries the payload model its category needs")
  void formEligibleCodesCarryAModel() {
    for (GTNetProtocolDescriptor descriptor : fullRegistry().getAllDescriptors()) {
      if (descriptor.formEligible()) {
        assertThat(descriptor.model()).as("%s is offered as a form", descriptor.name()).isNotNull();
      }
    }
  }

  @Test
  @DisplayName("Only the codes that stay open are delete protected")
  void theOpenRequestsAreTheOnesThatWaitForAnAnswer() {
    // The list the repository used to hardcode held three of these five: a pending token refresh and a pending
    // exchange sync were invisible to the reply gate and to the delete protection.
    assertThat(fullRegistry().requestCodesRequiringResponse()).containsExactlyInAnyOrder(
        GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_SEL_RR_S.getValue(),
        GNetCoreMessageCode.GT_NET_TOKEN_REFRESH_SEL_RR_C.getValue(),
        GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_SEL_RR_C.getValue(),
        GNetCoreMessageCode.GT_NET_DATA_REQUEST_SEL_RR_C.getValue(),
        GTNetMessageCodeType.GT_NET_EXCHANGE_SYNC_SEL_RR_C.getValue());
  }

  @Test
  @DisplayName("A form without a model is reported")
  void reportsAFormEligibleCodeWithoutAModel() {
    GTNetMessageCodeRegistry registry = fullRegistry();
    registry.register(GTNetProtocolDescriptor.request(TestCode.NO_MODEL).noInboundDispatch().formModel(null).build());

    assertThatThrownBy(() -> validatorFor(registry).validate(handlerRegistry()))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("NO_MODEL")
        .hasMessageContaining("no payload model");
  }

  @Test
  @DisplayName("An open request without answers is reported")
  void reportsARequestThatWaitsForAnAnswerItNeverDeclared() {
    GTNetMessageCodeRegistry registry = fullRegistry();
    registry
        .register(GTNetProtocolDescriptor.request(TestCode.NO_ANSWERS).noInboundDispatch().requiresResponse().build());

    assertThatThrownBy(() -> validatorFor(registry).validate(handlerRegistry()))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("NO_ANSWERS")
        .hasMessageContaining("no valid response code");
  }

  @Test
  @DisplayName("A dispatched code without a handler is reported")
  void reportsADispatchedCodeWithoutAHandler() {
    GTNetMessageCodeRegistry registry = fullRegistry();
    registry.register(GTNetProtocolDescriptor.announcement(TestCode.NO_HANDLER).userInitiable().build());

    assertThatThrownBy(() -> validatorFor(registry).validate(handlerRegistry()))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("NO_HANDLER")
        .hasMessageContaining("no handler");
  }

  @Test
  @DisplayName("A code that can reach a message row without a label is reported")
  void reportsAUserVisibleCodeWithoutAnNlsKey() {
    GTNetMessageCodeRegistry registry = fullRegistry();
    registry.register(GTNetProtocolDescriptor.response(TestCode.NO_LABEL).userInitiable().build());

    assertThatThrownBy(() -> validatorFor(registry).validate(handlerRegistry()))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("NO_LABEL").hasMessageContaining("no NLS key");
  }

  @Test
  @DisplayName("The data request payload is a set of entity kinds and nothing else")
  void theDataRequestModelIsWhatTheProtocolSays() {
    // The Javadoc used to promise terms the payload never carried - bidirectionality, rate limits, and a modify
    // response in the free slot 51.
    assertThat(DataRequestMsg.class.getFields()).extracting(field -> field.getName()).containsExactly("entityKinds");
  }

  /** The registry an application start builds: the core protocol plus the Grafioschtrader payload codes. */
  private static GTNetMessageCodeRegistry fullRegistry() {
    GTNetMessageCodeRegistry registry = new GTNetMessageCodeRegistry();
    GTProtocolDescriptors.all().forEach(registry::register);
    assertThat(registry.getAllDescriptors())
        .hasSize(CoreProtocolDescriptors.all().size() + GTProtocolDescriptors.all().size());
    return registry;
  }

  private static GTNetProtocolStartupValidator validatorFor(GTNetMessageCodeRegistry registry) {
    return new GTNetProtocolStartupValidator(registry, messageSource());
  }

  /** The handler registry a running context would hold. */
  private static GTNetMessageHandlerRegistry handlerRegistry() {
    return new GTNetMessageHandlerRegistry(discoverHandlers());
  }

  /** Instantiates every handler component of both modules, the way the component scan would. */
  private static List<GTNetMessageHandler> discoverHandlers() {
    ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AssignableTypeFilter(GTNetMessageHandler.class));
    List<GTNetMessageHandler> handlers = new ArrayList<>();
    for (String basePackage : HANDLER_PACKAGES) {
      for (BeanDefinition definition : scanner.findCandidateComponents(basePackage)) {
        handlers.add(instantiate(definition.getBeanClassName()));
      }
    }
    assertThat(handlers).as("no handler found below %s", HANDLER_PACKAGES).isNotEmpty();
    return handlers;
  }

  /**
   * Builds a handler through whichever constructor takes the fewest arguments, filling them with null. The guard only
   * asks a handler which codes it claims and which category it is, and no handler computes either from a collaborator,
   * so an instance whose dependencies are null answers exactly as the wired bean does.
   */
  private static GTNetMessageHandler instantiate(String className) {
    try {
      Constructor<?> constructor = Arrays.stream(Class.forName(className).getDeclaredConstructors())
          .min(Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
      constructor.setAccessible(true);
      return (GTNetMessageHandler) constructor.newInstance(new Object[constructor.getParameterCount()]);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("Cannot instantiate handler " + className, e);
    }
  }

  /** The same two basenames in the same order as {@code MessageConfig}, application bundle first. */
  private static MessageSource messageSource() {
    ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
    messageSource.setBasenames("classpath:message/messages", "classpath:i18n/messages");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    return messageSource;
  }

  /** Codes that exist only to make the guard report something; none of them is part of the protocol. */
  private enum TestCode implements grafiosch.gtnet.GTNetMessageCode {

    NO_MODEL((byte) 120), NO_ANSWERS((byte) 121), NO_HANDLER((byte) 122), NO_LABEL((byte) 123);

    private final byte value;

    TestCode(byte value) {
      this.value = value;
    }

    @Override
    public byte getValue() {
      return value;
    }
  }
}
