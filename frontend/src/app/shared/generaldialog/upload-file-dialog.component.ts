import {Component, Input, OnInit} from '@angular/core';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {AppHelper} from '../../lib/helper/app.helper';
import {SimpleEditBase} from '../../lib/edit/simple.edit.base';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {Subscription} from 'rxjs';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {AppSettings} from '../app.settings';
import {FileUploadParam, SupportedCSVFormat, UploadHistoryquotesSuccess} from './model/file.upload.param';

/**
 * General dialog for importing one or more file/s. Optional it allows to set some separators.
 */
@Component({
    selector: 'upload-file-dialog',
    template: `
    <p-dialog header="{{fileUploadParam.title | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '400px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`,
    standalone: false
})
export class UploadFileDialogComponent extends SimpleEditBase implements OnInit {

  // Input from parent view
  @Input() fileUploadParam: FileUploadParam;
  decimalSeparatorSub: Subscription;

  fileToUpload: File = null;

  constructor(public translateService: TranslateService,
              private messageToastService: MessageToastService,
              private userSettingsService: UserSettingsService,
              gps: GlobalparameterService) {
    super(null, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      5, this.helpLink.bind(this));

    this.helpId = this.fileUploadParam.helpId;

    this.config = [
      ...this.fileUploadParam.additionalFieldConfig ? this.fileUploadParam.additionalFieldConfig.fieldConfig : [],
      ...this.getCSVFormatsFields(),
      DynamicFieldHelper.createFileUpload(this.fileUploadParam.multiple ? DataType.Files : DataType.File, 'fileToUpload',
        this.fileUploadParam.multiple ? 'FILES' : 'FILE', this.fileUploadParam.acceptFileType, true),
      DynamicFieldHelper.createSubmitButton('UPLOAD')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  handleFileInput(files: FileList) {
    this.fileToUpload = files.item(0);
  }

  submit(value: { [name: string]: any }): void {
    const files: FileList = this.configObject.fileToUpload.elementRef.nativeElement.files;
    const formData = new FormData();

    for (let i = 0; i < files.length; i++) {
      formData.append('file', files.item(i), files.item(i).name);
    }

    if (this.fileUploadParam.supportedCSVFormats) {
      const supportedCSVFormat = new SupportedCSVFormat();
      this.form.cleanMaskAndTransferValuesToBusinessObject(supportedCSVFormat);
      this.userSettingsService.saveObject(AppSettings.HIST_SUPPORTED_CSV_FORMAT, supportedCSVFormat);
      Object.keys(supportedCSVFormat).forEach(e => formData.append(e, supportedCSVFormat[e]));
    }

    this.fileUploadParam.additionalFieldConfig && this.fileUploadParam.additionalFieldConfig.submitPrepareFN(value, formData,
      this.fileUploadParam.additionalFieldConfig.fieldConfig);

    this.fileUploadParam.uploadService.uploadFiles(this.fileUploadParam.entityId, formData).subscribe({next:
      response => {
        if (response.hasOwnProperty('duplicatedInImport')) {
          const uhs: UploadHistoryquotesSuccess = response;
          this.messageToastService.showMessageI18nEnableHtml(uhs.validationErrors + uhs.notOverridden > 0 ?
            uhs.duplicatedInImport > 0 ? InfoLevelType.WARNING : InfoLevelType.ERROR
            : InfoLevelType.SUCCESS, 'UPLOAD_HISTORYQUOTE_SUCCESS', uhs);
        }
        this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED));
      }, error: () => this.configObject.submit.disabled = false});
  }

  valueChangedOnDecimalSeparator(): void {
    this.decimalSeparatorSub = this.configObject.decimalSeparator.formControl.valueChanges.subscribe((data: string) => {
      const thousandSeparators: string[] = this.fileUploadParam.supportedCSVFormats.thousandSeparators.filter(separator => separator !==
        this.configObject.decimalSeparator.formControl.value);

      this.configObject.thousandSeparator.valueKeyHtmlOptions =
        SelectOptionsHelper.createHtmlOptionsFromStringArray(thousandSeparators);
    });
  }

  override onHide(event) {
    super.onHide(event);
    this.decimalSeparatorSub && this.decimalSeparatorSub.unsubscribe();
  }

  protected override initialize(): void {
    if (this.fileUploadParam.supportedCSVFormats) {
      this.configObject.decimalSeparator.valueKeyHtmlOptions =
        SelectOptionsHelper.createHtmlOptionsFromStringArray(this.fileUploadParam.supportedCSVFormats.decimalSeparators);
      this.configObject.dateFormat.valueKeyHtmlOptions =
        SelectOptionsHelper.createHtmlOptionsFromStringArray(this.fileUploadParam.supportedCSVFormats.dateFormats);

      const supportedCSVFormat: SupportedCSVFormat = this.userSettingsService.retrieveObject(this.fileUploadParam.persistenceCSVKey);
      this.valueChangedOnDecimalSeparator();
      if (supportedCSVFormat) {
        this.form.transferBusinessObjectToForm(supportedCSVFormat);
      }
    }
  }

  private getCSVFormatsFields(): FieldConfig[] {
    const fieldConfig: FieldConfig[] = [];
    if (this.fileUploadParam.supportedCSVFormats) {
      fieldConfig.push(DynamicFieldHelper.createFieldSelectStringHeqF('decimalSeparator', true,
        {inputWidth: 3}));
      fieldConfig.push(DynamicFieldHelper.createFieldSelectStringHeqF('thousandSeparator', true,
        {inputWidth: 3}));
      fieldConfig.push(DynamicFieldHelper.createFieldSelectStringHeqF('dateFormat', true,
        {inputWidth: 15}));
    }
    return fieldConfig;
  }
}




