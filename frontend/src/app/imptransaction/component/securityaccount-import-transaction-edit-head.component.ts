import { afterNextRender, Component, inject, Injector, OnInit, input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';

import { HelpIds } from '../../lib/help/help.ids';
import { ImportTransactionHeadService } from '../service/import.transaction.head.service';
import { ImportTransactionHead } from '../../entities/import.transaction.head';
import { AppHelper } from '../../lib/helper/app.helper';
import { CallParam } from '../../shared/maintree/types/dialog.visible';
import { Securityaccount } from '../../entities/securityaccount';
import { SimpleEntityEditBase } from '../../lib/edit/simple.entity.edit.base';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { BaseSettings } from '../../lib/base.settings';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';

@Component({
  selector: 'securityaccount-import-transaction-edit-head',
  template: ` <p-dialog
    header="{{ 'IMPORT_SET' | translate }}"
    [visible]="visibleDialog()"
    [style]="{ width: '400px' }"
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
export class SecurityaccountImportTransactionEditHeadComponent
  extends SimpleEntityEditBase<ImportTransactionHead>
  implements OnInit
{
  private gpsGT: GlobalparameterGTService = inject(GlobalparameterGTService);

  private readonly injector = inject(Injector);

  readonly callParam = input<CallParam>(undefined);

  constructor() {
    super(HelpIds.HELP_PORTFOLIO_SECURITYACCOUNT, 'IMPORT_SET', inject(ImportTransactionHeadService));
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 6, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputString('name', 'IMPORT_TRANSACTION_NAME', 40, true),
      DynamicFieldHelper.createFieldTextareaInputString('note', 'NOTE', BaseSettings.FID_MAX_LETTERS, false),
      ...(this.gpsGT.getTenantGtImportPlatformId() != null
        ? [DynamicFieldHelper.createFieldCheckboxHeqF('useGtPlatform')]
        : []),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.form().setDefaultValuesAndEnableSubmit();
    const callParam = this.callParam();
    if (callParam.thisObject) {
      this.form().transferBusinessObjectToForm(callParam.thisObject);
    }
    afterNextRender(() => this.configObject.name.elementRef?.nativeElement.focus(), { injector: this.injector });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): ImportTransactionHead {
    const importTransactionHead = new ImportTransactionHead();
    const callParam = this.callParam();
    if (callParam.thisObject) {
      Object.assign(importTransactionHead, callParam.thisObject);
    } else {
      importTransactionHead.securityaccount = <Securityaccount>callParam.parentObject;
    }
    this.form().cleanMaskAndTransferValuesToBusinessObject(importTransactionHead);
    return importTransactionHead;
  }
}
