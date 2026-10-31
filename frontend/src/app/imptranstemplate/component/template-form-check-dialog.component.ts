import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import { Component, OnInit, signal, input, inject } from '@angular/core';
import { Subscription } from 'rxjs';
import { SimpleFormEditBase } from '../../lib/edit/simple.form.edit.base';
import { HelpIds } from '../../lib/help/help.ids';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AppHelper } from '../../lib/helper/app.helper';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { ImportTransactionTemplateService } from '../service/import.transaction.template.service';
import { FormTemplateCheck, type FormTemplateCheckResult } from './form.template.check';
import { ImportTransactionPlatform } from '../../entities/import.transaction.platform';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { TemplateFormCheckDialogResultSuccessComponent } from './template-form-check-dialog-result-success.component';
import { TemplateFormCheckDialogResultFailedComponent } from './template-form-check-dialog-result-failed.component';

@Component({
  selector: 'template-form-check-dialog',
  template: ` <p-dialog
    header="{{ 'CHECK_TEMPLATE_FORM' | translate }}"
    [visible]="visibleDialog()"
    showEffect="fade"
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

    @if (formTemplateCheck(); as result) {
      @if (result.importTransactionPos) {
        <template-form-check-dialog-result-success [formTemplateCheck]="result" />
      }

      @if (result.failedParsedTemplateStateList) {
        <template-form-check-dialog-result-failed
          [failedParsedTemplateStateList]="result.failedParsedTemplateStateList" />
      }
    }
  </p-dialog>`,
  imports: [
    DialogModule,
    ...DYNAMIC_FORM_IMPORTS,
    TranslateModule,
    TemplateFormCheckDialogResultSuccessComponent,
    TemplateFormCheckDialogResultFailedComponent
  ]
})
export class TemplateFormCheckDialogComponent extends SimpleFormEditBase implements OnInit {
  private importTransactionTemplateService: ImportTransactionTemplateService = inject(ImportTransactionTemplateService);
  public translateService: TranslateService = inject(TranslateService);

  readonly importTransactionPlatform = input.required<ImportTransactionPlatform>();

  readonly formTemplateCheck = signal<FormTemplateCheckResult | null>(null);
  private checkSubscription?: Subscription;

  constructor() {
    super(HelpIds.HELP_BASEDATA_IMPORT_TRANSACTION_TEMPLATE_GROUP);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 3, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldTextareaInputString('templateAsTxt', 'PDF_FORM_AS_TXT', 4096, true, {
        textareaRows: 20
      }),
      DynamicFieldHelper.createFunctionButton('CHECK_TEMPLATE_FORM', (e) => this.checkFormAgainstTemplate(e)),
      DynamicFieldHelper.createSubmitButton('EXIT')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  checkFormAgainstTemplate(event: unknown) {
    const idPlatform = this.importTransactionPlatform().idTransactionImportPlatform;
    if (idPlatform == null) return;
    const ftc = new FormTemplateCheck(idPlatform, requireFormControl(this.configObject.templateAsTxt).value);
    this.checkSubscription?.unsubscribe();
    this.checkSubscription = this.importTransactionTemplateService
      .checkFormAgainstTemplate(ftc)
      .pipe(this.untilDialogClosed())
      .subscribe((formTemplateCheck) => {
        this.formTemplateCheck.set(formTemplateCheck);
      });
  }

  submit(value: { [name: string]: any }): void {
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE, null));
  }

  protected override initialize(): void {
    this.formTemplateCheck.set(null);
  }
}
