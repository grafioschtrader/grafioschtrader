import {Component, Input, OnInit} from '@angular/core';
import {SimpleEditBase} from '../../shared/edit/simple.edit.base';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {HelpIds} from '../../shared/help/help.ids';
import {TranslateService} from '@ngx-translate/core';
import {AppHelper} from '../../shared/helper/app.helper';
import {ProcessedAction} from '../../shared/types/processed.action';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {ImportTransactionTemplateService} from '../service/import.transaction.template.service';
import {FormTemplateCheck} from './form.template.check';
import {ImportTransactionPlatform} from '../../entities/import.transaction.platform';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';

@Component({
  selector: 'template-form-check-dialog',
  template: `
    <p-dialog header="{{'CHECK_TEMPLATE_FORM' | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" showEffect="fade" [style]="{width: '600px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>

      <ng-container *ngIf="formTemplateCheck">
        <template-form-check-dialog-result-success *ngIf="formTemplateCheck.importTransactionPos"
                                                   [formTemplateCheck]="formTemplateCheck">
        </template-form-check-dialog-result-success>

        <template-form-check-dialog-result-failed *ngIf="formTemplateCheck.failedParsedTemplateStateList"
                                                  [failedParsedTemplateStateList]="formTemplateCheck.failedParsedTemplateStateList">
        </template-form-check-dialog-result-failed>
      </ng-container>
    </p-dialog>`
})
export class TemplateFormCheckDialogComponent extends SimpleEditBase implements OnInit {
  @Input() importTransactionPlatform: ImportTransactionPlatform;

  formTemplateCheck: FormTemplateCheck;

  constructor(private importTransactionTemplateService: ImportTransactionTemplateService,
              public translateService: TranslateService,
              gps: GlobalparameterService) {
    super(HelpIds.HELP_BASEDATA_IMPORT_TRANSACTION_TEMPLATE_GROUP, gps);
  }


  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      3, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldTextareaInputString('templateAsTxt', 'PDF_FORM_AS_TXT', 4096, true,
        {textareaRows: 20}),
      DynamicFieldHelper.createFunctionButton('CHECK_TEMPLATE_FORM', (e) => this.checkFormAgainstTemplate(e)),
      DynamicFieldHelper.createSubmitButton('EXIT')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  checkFormAgainstTemplate(event) {
    const ftc = new FormTemplateCheck(this.importTransactionPlatform.idTransactionImportPlatform,
      this.configObject.templateAsTxt.formControl.value);
    this.importTransactionTemplateService.checkFormAgainstTemplate(ftc).subscribe(formTemplateCheck => {
      this.formTemplateCheck = formTemplateCheck;
    });
  }

  submit(value: { [name: string]: any }): void {
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE, null));
  }

  protected initialize(): void {

  }

}
