import {SimpleEditBase} from '../../edit/simple.edit.base';
import {MessageToastService} from '../../message/message.toast.service';
import {UDFDataService} from '../service/udf.data.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {AppHelper} from '../../helper/app.helper';
import {UDFData, UDFDataKey, UDFGeneralCallParam} from '../model/udf.metadata';
import {InfoLevelType} from '../../message/info.leve.type';
import {ProcessedActionData} from '../../types/processed.action.data';
import {ProcessedAction} from '../../types/processed.action';
import {DynamicFieldModelHelper} from '../../helper/dynamic.field.model.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {FieldDescriptorInputAndShowExtended} from '../../dynamicfield/field.descriptor.input.and.show';
import {Directive, Input} from '@angular/core';
import {DataType} from '../../dynamic-form/models/data.type';
import {Helper} from '../../helper/helper';
import {BaseSettings} from '../../base.settings';

/**
 * Abstract base component for editing user-defined field data values.
 * Provides common functionality for UDF data editing dialogs across different entity types.
 * Handles data retrieval, form population, validation, and submission of UDF field values.
 * Subclasses must implement entity-specific initialization logic.
 */
@Directive()
export abstract class BaseUDFDataEdit extends SimpleEditBase {
  /** Call parameters containing entity information, existing UDF data, and dialog configuration */
  @Input() uDFGeneralCallParam: UDFGeneralCallParam;

  /** Name of the primary key field for the entity being edited (e.g., 'idPortfolio', 'idWatchlist') */
  entityKeyName: string;

  /**
   * Creates the base UDF data edit component.
   *
   * @param messageToastService - Service for displaying user notifications
   * @param uDFDataService - Service for UDF data CRUD operations
   * @param translateService - Translation service for i18n support
   * @param helpId - Help context identifier for the help system
   * @param gps - Global parameter service providing user settings and system configuration
   * @protected
   */
  protected constructor(private messageToastService: MessageToastService,
    private uDFDataService: UDFDataService,
    public translateService: TranslateService,
    helpId: string,
    gps: GlobalparameterService) {
    super(helpId, gps);
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));
  }

  /**
   * Initializes the UDF data editing form with field descriptors.
   * Creates dynamic form configuration from UDF field descriptors and prepares entity key name.
   *
   * @param fdList - Array of field descriptors defining the UDF fields for this entity type
   * @protected
   */
  protected baseInit(fdList: FieldDescriptorInputAndShowExtended[]): void {
    this.config = DynamicFieldModelHelper.createConfigFieldsFromExtendedDescriptor(this.translateService, fdList, '', true);
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.entityKeyName = this.gps.getKeyNameByEntityName(this.uDFGeneralCallParam.entityName);
  }

  /**
   * Initializes the component by loading existing UDF data if not already provided.
   * If UDF data is passed in call parameters, uses that data directly; otherwise fetches from server.
   *
   * @protected
   */
  protected override initialize(): void {
    if (!this.uDFGeneralCallParam.udfData) {
      this.uDFDataService.getUDFDataByEntityAndIdEntity(this.uDFGeneralCallParam.entityName,
        <number>this.uDFGeneralCallParam.selectedEntity[this.entityKeyName]).subscribe(udfData => {
        this.prepareData(udfData?.jsonValues)
      });
    }
    this.prepareData(this.uDFGeneralCallParam.udfData);
  }

  /**
   * Populates the form with existing UDF data values.
   *
   * @param jsonValues - Object containing UDF field values to populate the form with
   * @private
   */
  private prepareData(jsonValues: any): void {
    jsonValues && this.form.transferBusinessObjectToForm(jsonValues);
  }

  /**
   * Handles form submission by saving UDF data values to the server.
   * Formats date fields before submission and displays success notification on completion.
   * Emits UPDATED action to notify parent components that data was saved.
   *
   * @param value - Form values containing UDF field data to save
   */
  submit(value: { [name: string]: any }): void {
    const udfData = new UDFData(new UDFDataKey(null, this.uDFGeneralCallParam.entityName,
      <number>this.uDFGeneralCallParam.selectedEntity[this.entityKeyName]), value);
    this.formatDate(value);
    this.uDFDataService.update(udfData, this.uDFGeneralCallParam.udfData ? 1 : null).subscribe({
      next: (uDFData: UDFData) => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED',
          {i18nRecord: 'UDF'});
        // Read only the watchlist, the watchlist was not changed
        this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED));
      }, error: () => this.configObject.submit.disabled = false
    });
  }

  /**
   * Formats date field values to native date string format before submission.
   * Ensures date fields are properly formatted for backend processing.
   *
   * @param value - Form values object containing fields to format
   * @private
   */
  private formatDate(value: { [name: string]: any }): void {
    this.config.filter(c => c.dataType === DataType.DateString).forEach(c => value[c.field] =
      Helper.formatDateStringAsString(c, BaseSettings.FORMAT_DATE_SHORT_NATIVE));
  }
}
