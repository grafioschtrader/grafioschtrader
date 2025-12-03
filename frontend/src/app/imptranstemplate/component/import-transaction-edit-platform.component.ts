import {Component, Input, OnInit} from '@angular/core';
import {SimpleEntityEditBase} from '../../lib/edit/simple.entity.edit.base';
import {ImportTransactionPlatform} from '../../entities/import.transaction.platform';
import {AppHelper} from '../../lib/helper/app.helper';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {HelpIds} from '../../lib/help/help.ids';
import {ImportTransactionPlatformService} from '../service/import.transaction.platform.service';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {Auditable} from '../../lib/entities/auditable';
import {ProposeChangeEntityWithEntity} from '../../lib/proposechange/model/propose.change.entity.whit.entity';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';

/**
 * Dialog for editing the transaction import template group
 */
@Component({
    selector: 'import-transaction-edit-platform',
    template: `
    <p-dialog header="{{'IMPORT_TRANSACTION_PLATFORM' | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`,
    standalone: true,
    imports: [DialogModule, DynamicFormModule, TranslateModule]
})
export class ImportTransactionEditPlatformComponent extends SimpleEntityEditBase<ImportTransactionPlatform> implements OnInit {

  @Input() callParam: CallParam;
  @Input() platformTransactionImportHtmlOptions: ValueKeyHtmlSelectOptions[];
  @Input() proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity;

  constructor(translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              importTransactionPlatformService: ImportTransactionPlatformService) {
    super(HelpIds.HELP_BASEDATA_IMPORT_TRANSACTION_TEMPLATE_GROUP, 'IMPORT_TRANSACTION_PLATFORM', translateService, gps,
      messageToastService, importTransactionPlatformService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      6, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputString('name', 'IMPORT_SET_NAME', 32, true),
      DynamicFieldHelper.createFieldSelectString('idCsvImportImplementation', 'TRANSACTION_IMPLEMENTATION', false,
        {valueKeyHtmlOptions: this.platformTransactionImportHtmlOptions}),
      ...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this)
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.form.setDefaultValuesAndEnableSubmit();
    AuditHelper.transferToFormAndChangeButtonForProposaleEdit(this.translateService, this.gps,
      <Auditable>this.callParam.thisObject, this.form, this.configObject, this.proposeChangeEntityWithEntity);
    setTimeout(() => this.configObject.name.elementRef.nativeElement.focus());
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): ImportTransactionPlatform {
    const importTransactionPlatform = new ImportTransactionPlatform();
    if (this.callParam.thisObject) {
      Object.assign(importTransactionPlatform, this.callParam.thisObject);
    }
    this.form.cleanMaskAndTransferValuesToBusinessObject(importTransactionPlatform);
    return importTransactionPlatform;
  }
}
