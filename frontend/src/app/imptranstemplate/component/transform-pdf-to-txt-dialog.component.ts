import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import { Component, OnInit, inject } from '@angular/core';
import { Subscription } from 'rxjs';
import { SimpleFormEditBase } from '../../lib/edit/simple.form.edit.base';
import { AppHelper } from '../../lib/helper/app.helper';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { HelpIds } from '../../lib/help/help.ids';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ImportTransactionPlatformService } from '../service/import.transaction.platform.service';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { ProcessedAction } from '../../lib/types/processed.action';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';

@Component({
  selector: 'transform-pdf-to-txt-dialog',
  template: ` <p-dialog
    header="{{ 'TRANSFORM_PDF_TO_TXT' | translate }}"
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
export class TransformPdfToTxtDialogComponent extends SimpleFormEditBase implements OnInit {
  private importTransactionPlatformService: ImportTransactionPlatformService = inject(ImportTransactionPlatformService);
  public translateService: TranslateService = inject(TranslateService);

  private transformSubscription?: Subscription;
  constructor() {
    super(HelpIds.HELP_BASEDATA_IMPORT_TRANSACTION_TEMPLATE_GROUP);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 3, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFileUpload(DataType.File, 'fileToUpload', 'PDF_ORIGINAL_FORM', 'pdf', true, {
        handleChangeFileInputFN: this.handleFileInput.bind(this)
      }),
      DynamicFieldHelper.createFieldTextareaInputString('templateAsTxt', 'PDF_TEMPLATE_AS_TXT', 4096, false, {
        textareaRows: 30,
        readonly: true
      }),
      DynamicFieldHelper.createFunctionButton('COPY_TO_CLIPBOARD', (e) => this.copyToClipboard(e)),
      DynamicFieldHelper.createSubmitButton('EXIT')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  async copyToClipboard(event: unknown) {
    await navigator.clipboard.writeText(requireFormControl(this.configObject.templateAsTxt).value);
  }

  handleFileInput(files: FileList) {
    this.transformSubscription?.unsubscribe();
    this.transformSubscription = this.importTransactionPlatformService
      .uploadAndTransformPDFToTxt(files.item(0))
      .pipe(this.untilDialogClosed())
      .subscribe((txt: string) => {
        requireFormControl(this.configObject.templateAsTxt).setValue(txt);
      });
  }

  submit(value: { [name: string]: any }): void {
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE, null));
  }

  protected override initialize(): void {}
}
