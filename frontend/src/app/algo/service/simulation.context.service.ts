import { Injectable } from '@angular/core';
import { GlobalSessionNames } from '../../lib/global.session.names';
import { ManageClientService } from '../../lib/manageclient/service/manage-client.service';
import { AppSettings } from '../../shared/app.settings';

/**
 * Tells whether the session currently operates in a simulation environment.
 *
 * Why this is not simply `MAIN_ID_TENANT != null`: that flag is set by every tenant switch, so an advisor working in a
 * managed client would be reported as being in a simulation and would lose the strategy editing that is perfectly
 * legitimate there. The entered environment is therefore remembered under its own key, and it is only believed while it
 * still matches the tenant of the session — a later switch makes a left-over entry answer false on its own.
 *
 * The value is written only after the backend has authorized the switch into a SIMULATION_COPY child of the user's home
 * tenant, so it never claims a context the server would refuse.
 */
@Injectable()
export class SimulationContextService {
  constructor(private manageClientService: ManageClientService) {}

  /**
   * @returns true when the session operates in a simulation environment
   */
  isInSimulation(): boolean {
    const simulationIdTenant = sessionStorage.getItem(AppSettings.SIMULATION_ID_TENANT);
    return simulationIdTenant !== null && simulationIdTenant === sessionStorage.getItem(GlobalSessionNames.ID_TENANT);
  }

  /**
   * Remembers the environment that was entered. Called only after a successful switch response.
   *
   * @param idTenant - The simulation environment the session switched into
   */
  enter(idTenant: number): void {
    sessionStorage.setItem(AppSettings.SIMULATION_ID_TENANT, idTenant.toString());
  }

  /** Forgets the entered environment, when returning to the home tenant. */
  leave(): void {
    sessionStorage.removeItem(AppSettings.SIMULATION_ID_TENANT);
  }

  /**
   * Leaves an environment the session may no longer use — it was deleted, or a replay of it has just started — by
   * returning to the user's own tenant. Doing nothing instead would leave a session whose every request is refused,
   * which reads as an application that has stopped working rather than as an environment that is busy.
   *
   * @returns true when a return was started, false when the session is not in an environment
   */
  recoverToHome(): boolean {
    const mainIdTenant = sessionStorage.getItem(GlobalSessionNames.MAIN_ID_TENANT);
    if (!this.isInSimulation() || !mainIdTenant) {
      return false;
    }
    this.manageClientService.switchAndReload(+mainIdTenant, true, () => this.leave());
    return true;
  }
}
