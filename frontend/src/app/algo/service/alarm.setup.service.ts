import { Injectable } from '@angular/core';
import { MenuItem, MenuItemCommandEvent } from '@openng/optimus-ui/api';
import { Securitycurrency } from '../../entities/securitycurrency';
import { AlgoCallParam, AlgoStrategyDefinitionForm } from '../model/algo.dialog.visible';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { AlgoSecurity, AlgoSecurityStrategyImplType } from '../model/algo.security';
import { Security } from '../../entities/security';
import { AlgoSecurityService } from './algo.security.service';
import { ProcessedAction } from '../../lib/types/processed.action';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { BaseSettings } from '../../lib/base.settings';
import { SimulationContextService } from './simulation.context.service';

@Injectable()
export class AlarmSetupService {
  visibleDialog: boolean;
  algoCallParam: AlgoCallParam;
  assit: AlgoSecurityStrategyImplType;

  constructor(
    private algoSecurityService: AlgoSecurityService,
    private simulationContext: SimulationContextService,
    private gps: GlobalparameterService
  ) {}

  /**
   * The alert entry of an instrument, offered only where adding one can actually succeed.
   *
   * Opening the dialog is itself a write: {@link showStrategyEdit} asks the backend for the standalone alert node of
   * the instrument, which persists that node on the first visit. Inside a simulation environment the strategy hierarchy
   * is read only and for a read-only user the node may not be created at all, so in both cases the entry is left out
   * rather than shown and then refused.
   *
   * @param securitycurrency - The instrument the entry would add an alert for
   * @returns the menu entries, or an empty array when alerts cannot be set up here
   */
  getMenuItem(securitycurrency: Securitycurrency): MenuItem[] {
    return this.gps.useAlert() && !this.simulationContext.isInSimulation() && !this.gps.isReadOnlyUser()
      ? [
          { separator: true },
          {
            label: 'ADD_ALERT' + BaseSettings.DIALOG_MENU_SUFFIX,
            command: (e: MenuItemCommandEvent) => this.showStrategyEdit(securitycurrency)
          }
        ]
      : [];
  }

  showStrategyEdit(securitycurrency: Securitycurrency): void {
    const algoSecurity: AlgoSecurity = new AlgoSecurity();
    algoSecurity.security = <Security>securitycurrency;

    this.algoSecurityService
      .getAlgoSecurityStrategyImplTypeByIdSecuritycurrency(securitycurrency.idSecuritycurrency)
      .subscribe((assit: AlgoSecurityStrategyImplType) => {
        this.assit = assit;
        const algoStrategyDefinitionForm = new AlgoStrategyDefinitionForm();
        algoStrategyDefinitionForm.unusedAlgoStrategyMap = new Map();
        algoStrategyDefinitionForm.unusedAlgoStrategyMap.set(
          assit.algoSecurity.idAlgoAssetclassSecurity,
          assit.possibleStrategyImplSet
        );
        this.algoCallParam = new AlgoCallParam(assit.algoSecurity, null, algoStrategyDefinitionForm);
        this.visibleDialog = true;
      });
  }

  handleCloseDialog(processedActionData: ProcessedActionData): void {
    this.visibleDialog = false;
    if (this.assit.wasCreated && processedActionData.action !== ProcessedAction.CREATED) {
      // An AlgoSecurity was specially created on the backend. As this is not used, it can be deleted again.
      this.algoSecurityService.deleteEntity(this.assit.algoSecurity.idAlgoAssetclassSecurity).subscribe();
    }
  }
}
