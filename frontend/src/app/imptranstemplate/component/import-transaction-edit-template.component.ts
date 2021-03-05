import {Component, Input, OnInit} from '@angular/core';
import {SimpleEntityEditBase} from '../../shared/edit/simple.entity.edit.base';
import {AppHelper} from '../../shared/helper/app.helper';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {HelpIds} from '../../shared/help/help.ids';
import {ImportTransactionTemplateService} from '../service/import.transaction.template.service';
import {ImportTransactionTemplate} from '../../entities/import.transaction.template';
import {ImportTransactionPlatform} from '../../entities/import.transaction.platform';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {ErrorMessageRules, RuleEvent} from '../../dynamic-form/error/error.message.rules';
import {InputType} from '../../dynamic-form/models/input.type';
import {DataType} from '../../dynamic-form/models/data.type';
import {Validators} from '@angular/forms';
import {TemplateFormatType} from '../../shared/types/template.format.type';
import {AuditHelper} from '../../shared/helper/audit.helper';
import {ProposeChangeEntityWithEntity} from '../../entities/proposechange/propose.change.entity.whit.entity';
import {Auditable} from '../../entities/auditable';
import {AppSettings} from '../../shared/app.settings';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';


@Component({
  selector: 'import-transaction-edit-template',
  template: `
    <p-dialog header="{{'IMPORTTRANSACTIONTEMPLATE' | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '600px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                    (submit)="submit($event)">
      </dynamic-form>
    </p-dialog>`
})
export class ImportTransactionEditTemplateComponent extends SimpleEntityEditBase<ImportTransactionTemplate> implements OnInit {

  @Input() callParam: CallParam;
  @Input() proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity;

  constructor(translateService: TranslateService,
              globalparameterService: GlobalparameterService,
              messageToastService: MessageToastService,
              public importTransactionTemplateService: ImportTransactionTemplateService) {
    super(HelpIds.HELP_BASEDATA_IMPORT_TRANSACTION_TEMPLATE, 'IMPORT_SET', translateService, globalparameterService,
      messageToastService, importTransactionTemplateService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.globalparameterService,
      3, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputStringHeqF('templatePurpose',  50, true),
      DynamicFieldHelper.createFieldSelectString('templateFormatType', 'TEMPLATE_FORMAT', true),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateNumeric, 'validSince',  true),
      DynamicFieldHelper.createFieldSelectStringHeqF('templateLanguage', true, {inputWidth: 10}),
      DynamicFieldHelper.createFieldTextareaInputString('templateAsTxt', 'PDF_TEMPLATE_AS_TXT', 4096, true,
        {textareaRows: 30}),
      ...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this)
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);

  }

  protected initialize(): void {
    this.importTransactionTemplateService.getPossibleLanguagesForTemplate().subscribe(data => {
        this.configObject.templateFormatType.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
          TemplateFormatType);
        this.configObject.templateLanguage.valueKeyHtmlOptions = data;
        this.form.setDefaultValuesAndEnableSubmit();
        AuditHelper.transferToFormAndChangeButtonForProposaleEdit(this.translateService, this.globalparameterService,
          <Auditable> this.callParam.thisObject, this.form, this.configObject, this.proposeChangeEntityWithEntity);
        this.configObject.templatePurpose.elementRef.nativeElement.focus();
      }
    );
  }


  protected getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): ImportTransactionTemplate {
    const importTransactionTemplate = new ImportTransactionTemplate();
    if (this.callParam.thisObject) {
      Object.assign(importTransactionTemplate, this.callParam.thisObject);
    } else {
      importTransactionTemplate.idTransactionImportPlatform =
        (<ImportTransactionPlatform>this.callParam.parentObject).idTransactionImportPlatform;
    }
    AuditHelper.copyProposeChangeEntityToEntityAfterEdit(this, importTransactionTemplate, this.proposeChangeEntityWithEntity);
    this.form.cleanMaskAndTransferValuesToBusinessObject(importTransactionTemplate);
    return importTransactionTemplate;
  }

}
