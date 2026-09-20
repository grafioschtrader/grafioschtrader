import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { AlgoSecurity } from '../model/algo.security';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { AlgoSecurityService } from '../service/algo.security.service';
import { AlgoAssetclass } from '../model/algo.assetclass';
import { AppHelper } from '../../lib/helper/app.helper';
import { AlgoAssetclassSecurityBaseEdit } from './algo.assetclass.security.base.edit';
import { combineLatest, Observable } from 'rxjs';
import { Portfolio } from '../../entities/portfolio';
import { Security } from '../../entities/security';
import { SecurityService } from '../../securitycurrency/service/security.service';
import { PortfolioService } from '../../portfolio/service/portfolio.service';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { createAlgoSecurityOptions } from './algo-security-options';

import { DialogModule } from '@openng/optimus-ui/dialog';
import { DynamicFormModule } from '../../lib/dynamic-form/dynamic-form.module';

@Component({
  selector: 'algo-security-edit',
  template: ` <p-dialog
    header="{{ 'ALGO_SECURITY' | translate }}"
    [visible]="visibleDialog"
    [style]="{ width: '700px', maxWidth: '95vw' }"
    (onShow)="onShow($event)"
    (onHide)="onHide($event)"
    [modal]="true">
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)">
    </dynamic-form>
  </p-dialog>`,
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [DialogModule, DynamicFormModule, TranslateModule]
})
export class AlgoSecurityEditComponent extends AlgoAssetclassSecurityBaseEdit<AlgoSecurity> implements OnInit {
  constructor(
    private portfolioService: PortfolioService,
    private securityService: SecurityService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    algoSecurityService: AlgoSecurityService
  ) {
    super('ALGO_SECURITY', translateService, gps, messageToastService, algoSecurityService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldDropdownNumber('security', 'ALGO_SECURITY_SELECTION', true, {
        dataproperty: 'security.idSecuritycurrency',
        filter: true
      }),
      ...this.getFieldDefinition()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected initialize(): void {
    const parentAssetclass = <AlgoAssetclass>this.algoCallParam.parentObject;
    let securitiesObservable: Observable<Security[]>;
    if (parentAssetclass.name != null && this.algoCallParam.idWatchlist != null) {
      securitiesObservable = this.securityService.getUnusedSecurityForAlgoCustom(
        this.algoCallParam.idWatchlist,
        parentAssetclass.idAlgoAssetclassSecurity
      );
    } else {
      securitiesObservable = this.securityService.getUnusedSecurityForAlgo(parentAssetclass.idAlgoAssetclassSecurity);
    }
    const allSecurityaccountsObservable: Observable<Portfolio[]> =
      this.portfolioService.getPortfoliosForTenantOrderByName();
    this.valueChangedOnSecurityaccount1();
    combineLatest([
      securitiesObservable,
      allSecurityaccountsObservable,
      this.translateService.get([
        'ACTIVE_FROM_DATE',
        'ACTIVE_TO_DATE',
        'ALGO_SECURITY_STARTS_AFTER_REFERENCE',
        'ALGO_SECURITY_ENDED'
      ])
    ]).subscribe((data) => {
      this.configObject.security.referencedDataObject = data[0];
      this.algoCallParam.thisObject &&
        this.configObject.security.referencedDataObject.push((<AlgoSecurity>this.algoCallParam.thisObject).security);
      const dateFields = {
        activeFromDate: ShowRecordConfigBase.createColumnConfig(
          DataType.DateString,
          'activeFromDate',
          'ACTIVE_FROM_DATE'
        ),
        activeToDate: ShowRecordConfigBase.createColumnConfig(DataType.DateString, 'activeToDate', 'ACTIVE_TO_DATE')
      };
      this.configObject.security.groupItem = createAlgoSecurityOptions(
        this.configObject.security.referencedDataObject,
        this.algoCallParam.referenceDate,
        (security, field) =>
          AppHelper.getValueByPathWithField(this.gps, this.translateService, security, dateFields[field], field),
        {
          activeFrom: data[2].ACTIVE_FROM_DATE,
          activeTo: data[2].ACTIVE_TO_DATE,
          startsAfterReference: data[2].ALGO_SECURITY_STARTS_AFTER_REFERENCE,
          ended: data[2].ALGO_SECURITY_ENDED
        }
      );
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
