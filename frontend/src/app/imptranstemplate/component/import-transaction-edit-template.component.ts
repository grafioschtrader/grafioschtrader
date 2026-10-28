import { Component, OnInit, input, inject } from '@angular/core';
import { SimpleEntityEditBase } from '../../lib/edit/simple.entity.edit.base';
import { AppHelper } from '../../lib/helper/app.helper';
import { TranslateModule } from '@ngx-translate/core';
import { HelpIds } from '../../lib/help/help.ids';
import { ImportTransactionTemplateService } from '../service/import.transaction.template.service';
import { ImportTransactionTemplate, TemplateCategory } from '../../entities/import.transaction.template';
import { ImportTransactionPlatform } from '../../entities/import.transaction.platform';
import { CallParam } from '../../shared/maintree/types/dialog.visible';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { TemplateFormatType } from '../../shared/types/template.format.type';
import { AuditHelper } from '../../lib/helper/audit.helper';
import { ProposeChangeEntityWithEntity } from '../../lib/proposechange/model/propose.change.entity.whit.entity';
import { Auditable } from '../../lib/entities/auditable';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';

/**
 * Edit import transaction template in a dialog
 */
@Component({
  selector: 'import-transaction-edit-template',
  template: ` <p-dialog
    header="{{ 'IMPORT_TRANSACTION_TEMPLATE' | translate }}"
    [visible]="visibleDialog()"
    [style]="{ width: '600px' }"
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
export class ImportTransactionEditTemplateComponent
  extends SimpleEntityEditBase<ImportTransactionTemplate>
  implements OnInit
{
  public importTransactionTemplateService: ImportTransactionTemplateService = inject(ImportTransactionTemplateService);

  readonly callParam = input<CallParam>(undefined);
  readonly proposeChangeEntityWithEntity = input<ProposeChangeEntityWithEntity>(undefined);

  constructor() {
    super(
      HelpIds.HELP_BASEDATA_IMPORT_TRANSACTION_TEMPLATE_TEMPLATE,
      'IMPORT_SET',
      inject(ImportTransactionTemplateService)
    );
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 3, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputStringHeqF('templatePurpose', 50, true),
      DynamicFieldHelper.createFieldSelectStringHeqF('templateCategory', true),
      DynamicFieldHelper.createFieldSelectStringHeqF('templateFormatType', true),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateNumeric, 'validSince', true),
      DynamicFieldHelper.createFieldSelectStringHeqF('templateLanguage', true, {
        inputWidth: 10
      }),
      DynamicFieldHelper.createFieldTextareaInputString('templateAsTxt', 'PDF_TEMPLATE_AS_TXT', 4096, true, {
        textareaRows: 30
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
    this.importTransactionTemplateService
      .getPossibleLanguagesForTemplate()
      .pipe(this.untilDialogClosed())
      .subscribe((data) => {
        this.configObject.templateFormatType.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
          this.translateService,
          TemplateFormatType
        );
        this.configObject.templateCategory.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
          this.translateService,
          TemplateCategory
        );
        this.configObject.templateLanguage.valueKeyHtmlOptions = data;
        this.configObject.templatePurpose.elementRef.nativeElement.focus();
      });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): ImportTransactionTemplate {
    const importTransactionTemplate = new ImportTransactionTemplate();
    const callParam = this.callParam();
    if (callParam.thisObject) {
      Object.assign(importTransactionTemplate, callParam.thisObject);
    } else {
      importTransactionTemplate.idTransactionImportPlatform = (<ImportTransactionPlatform>(
        callParam.parentObject
      )).idTransactionImportPlatform;
    }
    AuditHelper.copyProposeChangeEntityToEntityAfterEdit(
      this,
      importTransactionTemplate,
      this.proposeChangeEntityWithEntity()
    );
    this.form().cleanMaskAndTransferValuesToBusinessObject(importTransactionTemplate);
    return importTransactionTemplate;
  }
}
