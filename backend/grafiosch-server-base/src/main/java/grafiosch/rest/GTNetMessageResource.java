package grafiosch.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.common.UserAccessHelper;
import grafiosch.dynamic.model.ClassDescriptorInputAndShow;
import grafiosch.dynamic.model.DynamicModelHelper;
import grafiosch.dynamic.model.FieldDescriptorInputAndShow;
import grafiosch.entities.GTNetMessage;
import grafiosch.entities.User;
import grafiosch.gtnet.ExchangeKindTypeRegistry;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.GTNetMessageCodeRegistry;
import grafiosch.gtnet.GTNetProtocolDescriptor;
import grafiosch.gtnet.GTNetProtocolDescriptorDTO;
import grafiosch.gtnet.IExchangeKindType;
import grafiosch.gtnet.MessageVisibility;
import grafiosch.repository.GTNetMessageJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;

@RestController
@RequestMapping(RequestMappings.GTNET_MESSAGE_MAP)
@Tag(name = RequestMappings.GTNET_MESSAGE, description = "Controller for gtnet message")
public class GTNetMessageResource extends UpdateCreateDeleteAudit<GTNetMessage> {

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepository;

  @Autowired
  private ExchangeKindTypeRegistry exchangeKindTypeRegistry;

  @Autowired
  private GTNetMessageCodeRegistry messageCodeRegistry;

  @Operation(summary = "Returns the protocol descriptor of every registered message code", description = """
      One entry per GTNet message code: its category, the codes that answer it, whether an administrator may send it,
      whether it carries a form, and whether it may appear in an auto-answer rule. This is the authority the client
      builds its reply gate and its rule dialog from.""", tags = { RequestMappings.GTNET_MESSAGE })
  @GetMapping(value = "/protocol", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<GTNetProtocolDescriptorDTO>> getProtocolDescriptors() {
    List<GTNetProtocolDescriptorDTO> descriptors = messageCodeRegistry.getAllDescriptors().stream()
        .sorted((a, b) -> Byte.compare(a.value(), b.value())).map(GTNetProtocolDescriptorDTO::of).toList();
    return new ResponseEntity<>(descriptors, HttpStatus.OK);
  }

  @Operation(summary = "Returns all form defintion of messages", description = "", tags = {
      RequestMappings.GTNET_MESSAGE })
  @GetMapping(value = "/msgformdefinition", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<GTNetMessageCode, ClassDescriptorInputAndShow>> getAllFormDefinitions() {
    Map<GTNetMessageCode, ClassDescriptorInputAndShow> formDefs = new LinkedHashMap<>();
    for (GTNetProtocolDescriptor descriptor : messageCodeRegistry.getFormEligibleDescriptors()) {
      formDefs.put(descriptor.code(), DynamicModelHelper.getFormDefinitionOfModelClass(descriptor.model()));
    }
    populateExchangeKindEnumValues(formDefs);
    return new ResponseEntity<>(formDefs, HttpStatus.OK);
  }

  /**
   * Populates enumValues for fields with interface-based EnumSet types (e.g., entityKinds). DynamicModelHelper creates
   * EnumSet descriptors with empty enumValues for interface bounds; this method fills them with the actual registered
   * exchange kind names.
   */
  private void populateExchangeKindEnumValues(Map<GTNetMessageCode, ClassDescriptorInputAndShow> formDefs) {
    String interfaceName = IExchangeKindType.class.getSimpleName();
    for (ClassDescriptorInputAndShow cdias : formDefs.values()) {
      for (FieldDescriptorInputAndShow fdias : cdias.fieldDescriptorInputAndShows) {
        if (interfaceName.equals(fdias.enumType) && (fdias.enumValues == null || fdias.enumValues.length == 0)) {
          fdias.enumValues = exchangeKindTypeRegistry.getAllKinds().stream().map(IExchangeKindType::name)
              .toArray(String[]::new);
        }
      }
    }
  }

  @Operation(summary = "Marks a message as read", description = """
      Sets the hasBeenRead flag of a message the caller is allowed to see. The flag is instance-wide rather than per
      user, so changing it is an administrative act. An id that does not exist, or one whose visibility the caller may
      not see, is answered with 404 instead of a silent success.""", tags = { RequestMappings.GTNET_MESSAGE })
  @PatchMapping(value = "/{idGtNetMessage}/markasread")
  @Transactional
  public ResponseEntity<Void> markAsRead(@PathVariable Integer idGtNetMessage) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    int updated = gtNetMessageJpaRepository.markAsRead(idGtNetMessage,
        MessageVisibility.visibleTo(UserAccessHelper.isAdmin(user)));
    return updated > 0 ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
  }

  @Operation(summary = "Returns admin messages (messageCode=30) based on user role", description = "For admins: returns both ALL_USERS and ADMIN_ONLY messages. For non-admins: returns only ALL_USERS messages.", tags = {
      RequestMappings.GTNET_MESSAGE })
  @GetMapping(value = "/admin", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<GTNetMessage>> getAdminMessages() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<GTNetMessage> messages;

    if (UserAccessHelper.isAdmin(user)) {
      messages = gtNetMessageJpaRepository.findAdminMessagesForAdmin();
    } else {
      messages = gtNetMessageJpaRepository.findAdminMessagesByVisibility(MessageVisibility.ALL_USERS.getValue());
    }
    return new ResponseEntity<>(messages, HttpStatus.OK);
  }

  @Operation(summary = "Returns admin message (messageCode=30) counts per GTNet domain based on user role", description = "For admins: returns counts for both ALL_USERS and ADMIN_ONLY messages. For non-admins: returns counts for only ALL_USERS messages.", tags = {
      RequestMappings.GTNET_MESSAGE })
  @GetMapping(value = "/admin/count", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<Integer, Integer>> getAdminMessageCounts() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Map<Integer, Integer> counts;

    if (UserAccessHelper.isAdmin(user)) {
      counts = gtNetMessageJpaRepository.countAdminMessagesForAdmin();
    } else {
      counts = gtNetMessageJpaRepository.countAdminMessagesByVisibility(MessageVisibility.ALL_USERS.getValue());
    }
    return new ResponseEntity<>(counts, HttpStatus.OK);
  }

  @Override
  protected UpdateCreateJpaRepository<GTNetMessage> getUpdateCreateJpaRepository() {
    return gtNetMessageJpaRepository;
  }

}
