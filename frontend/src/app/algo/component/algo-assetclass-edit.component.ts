import {Component, OnInit} from '@angular/core';
import {AlgoAssetclass} from '../model/algo.assetclass';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {AlgoAssetclassService} from '../service/algo.assetclass.service';
import {AppHelper} from '../../shared/helper/app.helper';
import {PortfolioService} from '../../portfolio/service/portfolio.service';
import {AssetclassService} from '../../assetclass/service/assetclass.service';
import {combineLatest, Observable} from 'rxjs';
import {Assetclass} from '../../entities/assetclass';
import {Portfolio} from '../../entities/portfolio';
import {AlgoTop} from '../model/algo.top';
import {FormHelper} from '../../dynamic-form/components/FormHelper';
import {AlgoAssetclassSecurityBaseEdit} from './algo.assetclass.security.base.edit';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {AppSettings} from '../../shared/app.settings';


@Component({
  selector: 'algo-assetclass-edit',
  template: `
    <p-dialog header="{{'ALGO_ASSETCLASS' | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`
})
export class AlgoAssetclassEditComponent extends AlgoAssetclassSecurityBaseEdit<AlgoAssetclass> implements OnInit {

  constructor(private portfolioService: PortfolioService,
              private assetclassService: AssetclassService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              algoAssetclassService: AlgoAssetclassService) {
    super('ALGO_ASSETCLASS', translateService, gps,
      messageToastService, algoAssetclassService);
  }


  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF(AppSettings.ASSETCLASS_KEY, true,
        {dataproperty: 'assetclass.idAssetClass'}),
      ...this.getFieldDefinition()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);

  }

  protected initialize(): void {
    const allSecurityaccountsObservable: Observable<Portfolio[]> = this.portfolioService.getPortfoliosForTenantOrderByName();
    this.valueChangedOnSecurityaccount1();
    combineLatest([this.getAssetclassObserver(), allSecurityaccountsObservable]).subscribe(
      (data: [Assetclass | Assetclass[], Portfolio[]]) => {
        this.configObject.assetclass.referencedDataObject = Array.isArray(data[0]) ? data[0] : [data[0]];
        this.configObject.assetclass.valueKeyHtmlOptions = SelectOptionsHelper.assetclassCreateValueKeyHtmlSelectOptions(
          this.gps, this.translateService, this.configObject.assetclass.referencedDataObject);
        this.portfolios = data[1];
        this.setSecurityaccounts();
        this.disableEnableInputForExisting(this.algoCallParam.thisObject != null);
      });
  }

  protected getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): AlgoAssetclass {
    const algoAssetclass: AlgoAssetclass = new AlgoAssetclass();
    if (this.algoCallParam.thisObject) {
      Object.assign(algoAssetclass, this.algoCallParam.thisObject);
    }
    this.form.cleanMaskAndTransferValuesToBusinessObject(algoAssetclass);
    algoAssetclass.idAlgoAssetclassParent = (<AlgoTop>this.algoCallParam.parentObject).idAlgoAssetclassSecurity;
    return algoAssetclass;
  }

  private getAssetclassObserver(): Observable<Assetclass> | Observable<Assetclass[]> {
    if (this.algoCallParam.thisObject) {
      return this.assetclassService.getAssetclass((<AlgoAssetclass>this.algoCallParam.thisObject).assetclass.idAssetClass);
    } else {
      return this.assetclassService.getUnusedAssetclassForAlgo((<AlgoTop>this.algoCallParam.parentObject).idAlgoAssetclassSecurity);
    }
  }

  private disableEnableInputForExisting(disable: boolean): void {
    FormHelper.disableEnableFieldConfigs(disable, [this.configObject.assetclass]);
    if (!disable) {
      this.configObject.assetclass.elementRef.nativeElement.focus();
    } else {
      this.configObject.idSecurityaccount1.elementRef.nativeElement.focus();
    }
  }
}
