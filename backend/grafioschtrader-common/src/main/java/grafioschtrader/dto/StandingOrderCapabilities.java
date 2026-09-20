package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Tenant-specific standing-order behavior needed by the edit and table views. */
@Schema(description = "Standing-order capabilities of the currently selected tenant")
public record StandingOrderCapabilities(boolean simulationTenant, boolean cashStandingOrderSupported,
    boolean securityStandingOrderSupported, int minQuoteTolerance, int maxQuoteTolerance) {
}
