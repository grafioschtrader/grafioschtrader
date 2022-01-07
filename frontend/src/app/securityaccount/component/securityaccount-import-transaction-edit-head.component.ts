import {Component, Input, OnInit} from '@angular/core';
import {HelpIds} from '../../shared/help/help.ids';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {ImportTransactionHeadService} from '../service/import.transaction.head.service';
import {ImportTransactionHead} from '../../entities/import.transaction.head';
import {AppHelper} from '../../shared/helper/app.helper';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {Securityaccount} from '../../entities/securityaccount';
import {SimpleEntityEditBase} from '../../shared/edit/simple.entity.edit.base';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {AppSettings} from '../../shared/app.settings';


@Component({
  selector: 'securityaccount-import-transaction-edit-head',
  template: `
    <p-dialog header="{{'IMPORT_SET' | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '400px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`
})
export class SecurityaccountImportTransactionEditHeadComponent extends SimpleEntityEditBase<ImportTransactionHead> implements OnInit {

  @Input() callParam: CallParam;

  constructor(translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              importTransactionHeadService: ImportTransactionHeadService) {
    super(HelpIds.HELP_PORTFOLIO_SECURITYACCOUNT, 'IMPORT_SET', translateService, gps,
      messageToastService, importTransactionHeadService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      6, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputString('name', 'IMPORT_TRANSACTION_NAME', 40, true),
      DynamicFieldHelper.createFieldTextareaInputString('note', 'NOTE', AppSettings.FID_MAX_LETTERS, false),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected initialize(): void {
    this.form.setDefaultValuesAndEnableSubmit();
    if (this.callParam.thisObject) {
      this.form.transferBusinessObjectToForm(this.callParam.thisObject);
    }
    setTimeout(() => this.configObject.name.elementRef.nativeElement.focus());
  }

  protected getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): ImportTransactionHead {
    const importTransactionHead = new ImportTransactionHead();
    if (this.callParam.thisObject) {
      Object.assign(importTransactionHead, this.callParam.thisObject);
    } else {
      importTransactionHead.securityaccount = <Securityaccount>this.callParam.parentObject;
    }
    this.form.cleanMaskAndTransferValuesToBusinessObject(importTransactionHead);
    return importTransactionHead;
  }
}
