import { afterNextRender, Component, inject, Injector, OnInit, input } from '@angular/core';
import { SimpleEntityEditBase } from '../../lib/edit/simple.entity.edit.base';
import { ImportTransactionPlatform } from '../../entities/import.transaction.platform';
import { AppHelper } from '../../lib/helper/app.helper';
import { TranslateModule } from '@ngx-translate/core';
import { HelpIds } from '../../lib/help/help.ids';
import { ImportTransactionPlatformService } from '../service/import.transaction.platform.service';
import { CallParam } from '../../shared/maintree/types/dialog.visible';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { AuditHelper } from '../../lib/helper/audit.helper';
import { Auditable } from '../../lib/entities/auditable';
import { ProposeChangeEntityWithEntity } from '../../lib/proposechange/model/propose.change.entity.whit.entity';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';

/**
 * Dialog for editing the transaction import template group
 */
@Component({
  selector: 'import-transaction-edit-platform',
  template: ` <p-dialog
    header="{{ 'IMPORT_TRANSACTION_PLATFORM' | translate }}"
    [visible]="visibleDialog()"
    [style]="{ width: '500px' }"
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
export class ImportTransactionEditPlatformComponent
  extends SimpleEntityEditBase<ImportTransactionPlatform>
  implements OnInit
{
  private readonly injector = inject(Injector);

  readonly callParam = input.required<CallParam>();
  readonly platformTransactionImportHtmlOptions = input.required<ValueKeyHtmlSelectOptions[]>();
  readonly proposeChangeEntityWithEntity = input<ProposeChangeEntityWithEntity>();

  constructor() {
    super(
      HelpIds.HELP_BASEDATA_IMPORT_TRANSACTION_TEMPLATE_GROUP,
      'IMPORT_TRANSACTION_PLATFORM',
      inject(ImportTransactionPlatformService)
    );
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 6, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputString('name', 'IMPORT_SET_NAME', 32, true),
      DynamicFieldHelper.createFieldSelectString('idCsvImportImplementation', 'TRANSACTION_IMPLEMENTATION', false, {
        valueKeyHtmlOptions: this.platformTransactionImportHtmlOptions()
      }),
      ...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this)
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.form().setDefaultValuesAndEnableSubmit();
    AuditHelper.transferToFormAndChangeButtonForProposaleEdit(
      this.translateService,
      this.gps,
      <Auditable>this.callParam().thisObject,
      this.form(),
      this.configObject,
      this.proposeChangeEntityWithEntity()
    );
    afterNextRender(() => this.configObject.name.elementRef?.nativeElement.focus(), { injector: this.injector });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): ImportTransactionPlatform {
    const importTransactionPlatform = new ImportTransactionPlatform();
    const callParam = this.callParam();
    if (callParam.thisObject) {
      Object.assign(importTransactionPlatform, callParam.thisObject);
    }
    this.form().cleanMaskAndTransferValuesToBusinessObject(importTransactionPlatform);
    return importTransactionPlatform;
  }
}
