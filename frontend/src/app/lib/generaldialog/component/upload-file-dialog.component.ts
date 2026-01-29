import {ChangeDetectorRef, Component, Input, OnInit} from '@angular/core';

import {ProcessedActionData} from '../../types/processed.action.data';
import {ProcessedAction} from '../../types/processed.action';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {DialogModule} from 'primeng/dialog';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {AppHelper} from '../../helper/app.helper';
import {SimpleEditBase} from '../../edit/simple.edit.base';
import {DynamicFieldHelper} from '../../helper/dynamic.field.helper';
import {DynamicFormModule} from '../../dynamic-form/dynamic-form.module';
import {TranslateHelper} from '../../helper/translate.helper';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {SelectOptionsHelper} from '../../helper/select.options.helper';
import {Subscription} from 'rxjs';
import {InfoLevelType} from '../../message/info.leve.type';
import {MessageToastService} from '../../message/message.toast.service';
import {UserSettingsService} from '../../services/user.settings.service';
import {FileUploadParam, SupportedCSVFormat, UploadHistoryquotesSuccess} from '../model/file.upload.param';
import {BaseSettings} from '../../base.settings';

/**
 * Generic file upload dialog component for importing single or multiple files with optional CSV format
 * configuration. This reusable component provides a standardized interface for file uploads across the
 * application, supporting custom validation, additional form fields, and CSV format settings.
 *
 * The dialog automatically handles:
 * - File selection with MIME type filtering
 * - Optional CSV format settings (decimal/thousand separators, date formats)
 * - Additional custom form fields
 * - Format preference persistence in browser storage
 * - FormData preparation and multipart upload
 * - Upload result feedback with success/error messaging
 *
 * This component extends SimpleEditBase to inherit standard dialog behavior including visibility
 * management, form configuration, and lifecycle handling.
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
    standalone: true,
    imports: [DialogModule, DynamicFormModule, TranslateModule]
})
export class UploadFileDialogComponent extends SimpleEditBase implements OnInit {

  /** Configuration parameters passed from parent component defining dialog behavior and upload settings */
  @Input() fileUploadParam: FileUploadParam;

  /** Subscription to decimal separator value changes, used to update thousand separator options dynamically */
  decimalSeparatorSub: Subscription;

  /** Currently selected file for upload (used in single-file mode, kept for backward compatibility) */
  fileToUpload: File = null;

  /**
   * Creates a new upload file dialog component.
   *
   * @param translateService - Angular translation service for internationalization of labels and messages
   * @param messageToastService - Service for displaying toast notifications with upload results and errors
   * @param userSettingsService - Service for persisting and retrieving user preferences (CSV format settings)
   * @param gps - Global parameter service providing application-wide configuration and user settings
   */
  constructor(public translateService: TranslateService,
              private messageToastService: MessageToastService,
              private userSettingsService: UserSettingsService,
              private cdr: ChangeDetectorRef,
              gps: GlobalparameterService) {
    super(null, gps);
  }

  /**
   * Initializes the component by building the dynamic form configuration. Creates form fields including
   * optional additional fields, CSV format selectors (if configured), and the file upload control.
   * Prepares all field configurations and translations for rendering.
   */
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

  /**
   * Legacy method for handling single file selection. Kept for backward compatibility but superseded
   * by direct FileList access from form controls.
   *
   * @param files - FileList containing selected files from the file input element
   */
  handleFileInput(files: FileList) {
    this.fileToUpload = files.item(0);
  }

  /**
   * Handles form submission by preparing FormData with selected files and form values, then initiating
   * the upload via the configured upload service. Processes upload results and displays appropriate
   * success or error messages. For CSV uploads with format settings, saves user preferences for future use.
   *
   * The method:
   * 1. Extracts all selected files from the file input element
   * 2. Creates FormData and appends files with 'file' key
   * 3. If CSV format support is enabled, appends separator and date format settings
   * 4. Calls optional additional field preparation function to process custom fields
   * 5. Invokes the upload service and handles the response
   * 6. Displays detailed feedback for historical quote uploads (success counts, validation errors)
   * 7. Closes the dialog and emits UPDATED action on success
   *
   * @param value - Form values object containing all field values from the dynamic form
   */
  submit(value: { [name: string]: any }): void {
    const files: FileList = this.configObject.fileToUpload.elementRef.nativeElement.files;
    const formData = new FormData();

    for (let i = 0; i < files.length; i++) {
      formData.append('file', files.item(i), files.item(i).name);
    }

    if (this.fileUploadParam.supportedCSVFormats) {
      const supportedCSVFormat = new SupportedCSVFormat();
      this.form.cleanMaskAndTransferValuesToBusinessObject(supportedCSVFormat);
      this.userSettingsService.saveObject(BaseSettings.CSV_EXPORT_FORMAT, supportedCSVFormat);
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
            : InfoLevelType.SUCCESS, 'UPLOAD_SUCCESS', uhs);
        }
        this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED));
      }, error: () => this.configObject.submit.disabled = false});
  }

  /**
   * Sets up reactive listener for decimal separator changes to dynamically update thousand separator options.
   * This ensures that decimal and thousand separators cannot use the same character, preventing ambiguous
   * CSV parsing. When the user changes the decimal separator, the thousand separator dropdown is filtered
   * to exclude the chosen decimal separator.
   */
  valueChangedOnDecimalSeparator(): void {
    this.decimalSeparatorSub = this.configObject.decimalSeparator.formControl.valueChanges.subscribe((data: string) => {
      const thousandSeparators: string[] = this.fileUploadParam.supportedCSVFormats.thousandSeparators.filter(separator => separator !==
        this.configObject.decimalSeparator.formControl.value);

      this.configObject.thousandSeparator.valueKeyHtmlOptions =
        SelectOptionsHelper.createHtmlOptionsFromStringArray(thousandSeparators);
    });
  }

  /**
   * Lifecycle hook called when the dialog is closed. Cleans up the decimal separator change subscription
   * to prevent memory leaks. Delegates to parent class for standard cleanup operations.
   *
   * @param event - PrimeNG dialog hide event
   */
  override onHide(event) {
    super.onHide(event);
    this.decimalSeparatorSub && this.decimalSeparatorSub.unsubscribe();
  }

  /**
   * Initializes dialog state when shown. For dialogs with CSV format support, this method:
   * - Populates dropdown options for decimal separators, thousand separators, and date formats
   * - Retrieves previously saved format preferences from browser storage
   * - Pre-fills format fields with saved values if available
   * - Sets up decimal/thousand separator interdependency handling
   *
   * This provides a better user experience by remembering format preferences across sessions.
   *
   * @protected
   */
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
      this.cdr.detectChanges();
    }
  }

  /**
   * Builds form field configurations for CSV format settings if CSV format support is enabled.
   * Creates three dropdown fields for decimal separator, thousand separator, and date format selection.
   * These fields are only added to the form when the fileUploadParam includes supportedCSVFormats configuration.
   *
   * @returns Array of field configurations for CSV format options, or empty array if CSV support is not enabled
   * @private
   */
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




