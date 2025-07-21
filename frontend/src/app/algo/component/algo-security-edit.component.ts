import {Component, OnInit} from '@angular/core';
import {AlgoSecurity} from '../model/algo.security';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {AlgoSecurityService} from '../service/algo.security.service';
import {AlgoAssetclass} from '../model/algo.assetclass';
import {AppHelper} from '../../lib/helper/app.helper';
import {AlgoAssetclassSecurityBaseEdit} from './algo.assetclass.security.base.edit';
import {combineLatest, Observable} from 'rxjs';
import {Portfolio} from '../../entities/portfolio';
import {Security} from '../../entities/security';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {PortfolioService} from '../../portfolio/service/portfolio.service';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {AppSettings} from '../../shared/app.settings';
import {BusinessSelectOptionsHelper} from '../../securitycurrency/component/business.select.options.helper';

/**
 * Project: Grafioschtrader
 */
@Component({
    selector: 'algo-security-edit',
    template: `
    <p-dialog header="{{'ALGO_ASSETCLASS' | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`,
    standalone: false
})
export class AlgoSecurityEditComponent extends AlgoAssetclassSecurityBaseEdit<AlgoSecurity> implements OnInit {

  constructor(private portfolioService: PortfolioService,
              private securityService: SecurityService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              algoSecurityService: AlgoSecurityService) {
    super('ALGO_SECURITY', translateService, gps,
      messageToastService, algoSecurityService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF(AppSettings.SECURITY.toLowerCase(), true,
        {dataproperty: 'security.idSecuritycurrency'}),
      ...this.getFieldDefinition()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected initialize(): void {
    const securitiesObservable: Observable<Security[]> = this.securityService.getUnusedSecurityForAlgo(
      (<AlgoAssetclass>this.algoCallParam.parentObject).idAlgoAssetclassSecurity);
    const allSecurityaccountsObservable: Observable<Portfolio[]> = this.portfolioService.getPortfoliosForTenantOrderByName();
    this.valueChangedOnSecurityaccount1();
    combineLatest([securitiesObservable, allSecurityaccountsObservable]).subscribe(
      (data: [Security[], Portfolio[]]) => {
        this.configObject.security.referencedDataObject = data[0];
        this.algoCallParam.thisObject &&
        this.configObject.security.referencedDataObject.push((<AlgoSecurity>this.algoCallParam.thisObject).security);
        BusinessSelectOptionsHelper.securityCreateValueKeyHtmlSelectOptions(this.configObject.security.referencedDataObject,
          this.configObject.security);
        this.portfolios = data[1];
        this.setSecurityaccounts();
      });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): AlgoSecurity {
    const algoSecurity = new AlgoSecurity();
    if (this.algoCallParam.thisObject) {
      Object.assign(algoSecurity, this.algoCallParam.thisObject);
    }
    this.form.cleanMaskAndTransferValuesToBusinessObject(algoSecurity);
    algoSecurity.idAlgoSecurityParent = (<AlgoAssetclass>this.algoCallParam.parentObject).idAlgoAssetclassSecurity;
    return algoSecurity;
  }
}
