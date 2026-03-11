package grafioschtrader.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.dto.SecurityActionTreeData;
import grafioschtrader.entities.SecurityAction;
import grafioschtrader.entities.SecurityActionApplication;
import grafioschtrader.entities.SecurityTransfer;
import grafioschtrader.service.SecurityActionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for security actions (admin ISIN changes) and security transfers (user account transfers).
 */
@RestController
@RequestMapping("/api/securityaction")
@Tag(name = "SecurityAction", description = "ISIN change events and security account transfers")
public class SecurityActionResource {

  @Autowired
  private SecurityActionService securityActionService;

  @Operation(summary = "Returns tree data for the SecurityAction TreeTable")
  @GetMapping("/tree")
  public ResponseEntity<SecurityActionTreeData> getTree() {
    return ResponseEntity.ok(securityActionService.getTreeData());
  }

  @Operation(summary = "Admin creates an ISIN change event")
  @PostMapping
  public ResponseEntity<SecurityAction> createSecurityAction(@RequestBody SecurityAction securityAction) {
    return ResponseEntity.ok(securityActionService.createSecurityAction(securityAction));
  }

  @Operation(summary = "Admin deletes an ISIN change event (only if no applications exist)")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteSecurityAction(@PathVariable Integer id) {
    securityActionService.deleteSecurityAction(id);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "User applies an ISIN change to their holdings")
  @PostMapping("/{id}/apply")
  public ResponseEntity<SecurityActionApplication> applySecurityAction(@PathVariable Integer id) {
    return ResponseEntity.ok(securityActionService.applySecurityAction(id));
  }

  @Operation(summary = "User reverses an applied ISIN change")
  @PostMapping("/{id}/reverse")
  public ResponseEntity<Void> reverseSecurityAction(@PathVariable Integer id) {
    securityActionService.reverseSecurityAction(id);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "User creates a security transfer between accounts")
  @PostMapping("/transfer")
  public ResponseEntity<SecurityTransfer> createTransfer(@RequestBody SecurityTransfer securityTransfer) {
    return ResponseEntity.ok(securityActionService.createTransfer(securityTransfer));
  }

  @Operation(summary = "User reverses/deletes a security transfer")
  @DeleteMapping("/transfer/{id}")
  public ResponseEntity<Void> reverseTransfer(@PathVariable Integer id) {
    securityActionService.reverseTransfer(id);
    return ResponseEntity.noContent().build();
  }
}
