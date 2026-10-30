import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import { switchMap, combineLatest, Observable } from 'rxjs';
import { Component, OnInit, inject } from '@angular/core';
import { AlgoSecurity } from '../model/algo.security';
import { TranslateModule } from '@ngx-translate/core';
import { AlgoSecurityService } from '../service/algo.security.service';
import { requireAlgoNodeId } from '../model/algo.top.asset.security';
import { AlgoAssetclass } from '../model/algo.assetclass';
import { AppHelper } from '../../lib/helper/app.helper';
import { AlgoAssetclassSecurityBaseEdit } from './algo.assetclass.security.base.edit';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { distinctUntilChanged } from 'rxjs/operators';
import { Security } from '../../entities/security';
import { SecurityService } from '../../securitycurrency/service/security.service';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { createAlgoSecurityOptions } from './algo-security-options';

import { DialogModule } from '@openng/optimus-ui/dialog';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';

@Component({
  selector: 'algo-security-edit',
  template: ` <p-dialog
    header="{{ 'ALGO_SECURITY' | translate }}"
    [visible]="visibleDialog()"
    [style]="{ width: '700px', maxWidth: '95vw' }"
    (onShow)="onShow($event)"
    (onHide)="onHide($event)"
    [modal]="true">
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)" />
  </p-dialog>`,
  imports: [DialogModule, ...DYNAMIC_FORM_IMPORTS, TranslateModule]
})
export class AlgoSecurityEditComponent extends AlgoAssetclassSecurityBaseEdit<AlgoSecurity> implements OnInit {
  private securityService: SecurityService = inject(SecurityService);

  constructor() {
    super('ALGO_SECURITY', inject(AlgoSecurityService));
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
    const parentAssetclass = <AlgoAssetclass>this.algoCallParam().parentObject;
    let securitiesObservable: Observable<Security[]>;
    const algoCallParam = this.algoCallParam();
    if (parentAssetclass.name != null && algoCallParam.idWatchlist != null) {
      securitiesObservable = this.securityService.getUnusedSecurityForAlgoCustom(
        algoCallParam.idWatchlist,
        requireAlgoNodeId(parentAssetclass)
      );
    } else {
      securitiesObservable = this.securityService.getUnusedSecurityForAlgo(requireAlgoNodeId(parentAssetclass));
    }
    const existing = <AlgoSecurity>algoCallParam.thisObject;
    const accountOptionsObservable: Observable<ValueKeyHtmlSelectOptions[]> = this.getAccountOptions(
      existing?.security?.idSecuritycurrency
    );
    this.valueChangedOnSecurityaccount1();
    combineLatest([
      securitiesObservable,
      accountOptionsObservable,
      this.translateService.get([
        'ACTIVE_FROM_DATE',
        'ACTIVE_TO_DATE',
        'ALGO_SECURITY_STARTS_AFTER_REFERENCE',
        'ALGO_SECURITY_ENDED'
      ])
    ])
      .pipe(this.untilDialogClosed())
      .subscribe((data) => {
        this.configObject.security.referencedDataObject = [
          ...data[0],
          ...(existing?.security ? [existing.security] : [])
        ];
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
          this.algoCallParam().referenceDate,
          (security, field) =>
            AppHelper.getValueByPathWithField(this.gps, this.translateService, security, dateFields[field], field),
          {
            activeFrom: data[2].ACTIVE_FROM_DATE,
            activeTo: data[2].ACTIVE_TO_DATE,
            startsAfterReference: data[2].ALGO_SECURITY_STARTS_AFTER_REFERENCE,
            ended: data[2].ALGO_SECURITY_ENDED
          }
        );
        this.setAccountOptions(data[1]);
        if (existing) {
          this.form().transferBusinessObjectToForm(existing);
          // A saved priority whose account no longer allows the type is cleared, the backend would refuse it.
          this.setAccountOptions(data[1]);
        }
        // The allowed accounts follow the instrument type of the selected security.
        requireFormControl(this.configObject.security)
          .valueChanges.pipe(
            distinctUntilChanged(),
            switchMap((id: number) => this.getAccountOptions(id || undefined)),
            this.untilDialogClosed()
          )
          .subscribe((options) => this.setAccountOptions(options));
      });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): AlgoSecurity {
    const algoSecurity = new AlgoSecurity();
    const algoCallParam = this.algoCallParam();
    if (algoCallParam.thisObject) {
      Object.assign(algoSecurity, algoCallParam.thisObject);
    }
    this.form().cleanMaskAndTransferValuesToBusinessObject(algoSecurity);
    algoSecurity.idAlgoSecurityParent = requireAlgoNodeId(algoCallParam.parentObject);
    return algoSecurity;
  }
}
