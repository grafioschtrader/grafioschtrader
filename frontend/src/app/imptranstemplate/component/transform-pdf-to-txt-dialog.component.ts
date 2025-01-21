import {Component, OnInit} from '@angular/core';
import {SimpleEditBase} from '../../shared/edit/simple.edit.base';
import {AppHelper} from '../../shared/helper/app.helper';
import {DataType} from '../../dynamic-form/models/data.type';
import {HelpIds} from '../../shared/help/help.ids';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {ImportTransactionPlatformService} from '../service/import.transaction.platform.service';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {ProcessedAction} from '../../shared/types/processed.action';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';


@Component({
    selector: 'transform-pdf-to-txt-dialog',
    template: `
    <p-dialog header="{{'TRANSFORM_PDF_TO_TXT' | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '600px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`,
    standalone: false
})
export class TransformPdfToTxtDialogComponent extends SimpleEditBase implements OnInit {

  constructor(private importTransactionPlatformService: ImportTransactionPlatformService,
              public translateService: TranslateService,
              gps: GlobalparameterService) {
    super(HelpIds.HELP_BASEDATA_IMPORT_TRANSACTION_TEMPLATE_GROUP, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      3, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFileUpload(DataType.File, 'fileToUpload', 'PDF_ORIGINAL_FORM',
        'pdf', true, {handleChangeFileInputFN: this.handleFileInput.bind(this)}),
      DynamicFieldHelper.createFieldTextareaInputString('templateAsTxt', 'PDF_TEMPLATE_AS_TXT', 4096, false,
        {textareaRows: 30, readonly: true}),
      DynamicFieldHelper.createFunctionButton('COPY_TO_CLIPBOARD', (e) => this.copyToClipboard(e)),
      DynamicFieldHelper.createSubmitButton('EXIT')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);

  }

  async copyToClipboard(event)  {
    await navigator.clipboard.writeText(this.configObject.templateAsTxt.formControl.value);
  }

  handleFileInput(files: FileList) {
    this.importTransactionPlatformService.uploadAndTransformPDFToTxt(files.item(0)).subscribe((txt: string) => {
        this.configObject.templateAsTxt.formControl.setValue(txt);
      }
    );
  }

  submit(value: { [name: string]: any }): void {
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE, null));
  }

  protected override initialize(): void {

  }
}
