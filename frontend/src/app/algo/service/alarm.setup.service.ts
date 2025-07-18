import {Injectable} from '@angular/core';
import {MenuItem, MenuItemCommandEvent} from 'primeng/api';
import {Securitycurrency} from '../../entities/securitycurrency';
import {AppSettings} from '../../shared/app.settings';
import {AlgoCallParam, AlgoStrategyDefinitionForm} from '../model/algo.dialog.visible';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {AlgoSecurity, AlgoSecurityStrategyImplType} from '../model/algo.security';
import {Security} from '../../entities/security';
import {AlgoSecurityService} from './algo.security.service';
import {ProcessedAction} from '../../lib/types/processed.action';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {BaseSettings} from '../../lib/base.settings';


@Injectable()
export class AlarmSetupService {
  visibleDialog: boolean;
  algoCallParam: AlgoCallParam;
  assit: AlgoSecurityStrategyImplType;

  constructor(private algoSecurityService: AlgoSecurityService, private gps: GlobalparameterService) {
  }

  getMenuItem(securitycurrency: Securitycurrency): MenuItem[] {
    return this.gps.useAlert()? [{separator: true}, {
      label: 'ADD_ALERT' + BaseSettings.DIALOG_MENU_SUFFIX,
      command: (e: MenuItemCommandEvent) => this.showStrategyEdit(securitycurrency)
    }]: [];
  }

  showStrategyEdit(securitycurrency: Securitycurrency): void {

    const algoSecurity: AlgoSecurity = new AlgoSecurity();
    algoSecurity.security = <Security>securitycurrency;

    this.algoSecurityService.getAlgoSecurityStrategyImplTypeByIdSecuritycurrency(securitycurrency.idSecuritycurrency)
      .subscribe((assit: AlgoSecurityStrategyImplType) => {
        this.assit = assit;
        const algoStrategyDefinitionForm = new AlgoStrategyDefinitionForm();
        algoStrategyDefinitionForm.unusedAlgoStrategyMap = new Map();
        algoStrategyDefinitionForm.unusedAlgoStrategyMap.set(assit.algoSecurity.idAlgoAssetclassSecurity, assit.possibleStrategyImplSet)
        this.algoCallParam = new AlgoCallParam(assit.algoSecurity, null, algoStrategyDefinitionForm);
        this.visibleDialog = true;
      })
  }

  handleCloseDialog(processedActionData: ProcessedActionData): void {
    this.visibleDialog = false;
    if(this.assit.wasCreated && processedActionData.action !== ProcessedAction.CREATED) {
       // An AlgoSecurity was specially created on the backend. As this is not used, it can be deleted again.
      this.algoSecurityService.deleteEntity(this.assit.algoSecurity.idAlgoAssetclassSecurity).subscribe();
    }
  }
}
