import {Component, Input, OnInit, ViewChild} from '@angular/core';
import {SimpleEntityEditBase} from '../../lib/edit/simple.entity.edit.base';
import {
  AcceptRequestTypes,
  GTNet,
  GTNetCallParam,
  GTNetEntity,
  GTNetExchangeKindType,
  GTNetServerOnlineStatusTypes,
  GTNetServerStateTypes
} from '../model/gtnet';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {HelpIds} from '../../lib/help/help.ids';
import {AppSettings} from '../../shared/app.settings';
import {GTNetService} from '../service/gtnet.service';
import {AppHelper} from '../../lib/helper/app.helper';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {Subscription} from 'rxjs';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {DialogModule} from 'primeng/dialog';
import {ButtonModule} from 'primeng/button';
import {DynamicFormComponent} from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {EditableTableComponent} from '../../lib/datashowbase/editable-table.component';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {ColumnConfig, EditInputType, TranslateValue} from '../../lib/datashowbase/column.config';
import {ShowRecordConfigBase} from '../../lib/datashowbase/show.record.config.base';
import {Helper} from '../../lib/helper/helper';

/**
 * Add or modify a GTNet entity with EditableTableComponent for entity configurations.
 * Almost all attributes can be changed when entering data for your own server. When settings like dailyRequestLimit,
 * acceptRequest, serverState, or maxLimit are changed during update, these changes are automatically broadcast
 * to all connected peers via GT_NET_SETTINGS_UPDATED_ALL_C.
 * Only domainRemoteName can be entered for a remote instance. The other values are set by the remote instance via GTNetMessage.
 *
 * This component uses EditableTableComponent in batch mode to edit all three entity kinds
 * (HISTORICAL_PRICES, LAST_PRICE, SECURITY_METADATA) in a single table.
 */
@Component({
  selector: 'gtnet-edit',
  standalone: true,
  imports: [
    DialogModule,
    ButtonModule,
    DynamicFormComponent,
    TranslateModule,
    EditableTableComponent
  ],
  template: `
    <p-dialog header="{{'GT_NET_NET_AND_MESSAGE' | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '750px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>

      @if (callParam?.isMyEntry && entityFieldsReady) {
        <h4 style="margin-top: 1rem; margin-bottom: 0.5rem;">{{ 'ENTITY_CONFIGURATIONS' | translate }}</h4>
        <editable-table #entityTable
          [data]="gtNetEntities"
          [fields]="entityFields"
          dataKey="entityKind"
          [batchMode]="true"
          [startInEditMode]="true"
          [showEditColumn]="false"
          [selectionMode]="null"
          [contextMenuEnabled]="false"
          [valueGetterFn]="getEntityValueByPath.bind(this)"
          [baseLocale]="baseLocale"
          [scrollable]="false"
          [containerClass]="''"
          [stripedRows]="false">
        </editable-table>

        <div style="margin-top: 1rem; text-align: right;">
          <p-button [label]="'SAVE' | translate" icon="pi pi-check" (click)="submitAll()" />
        </div>
      }
    </p-dialog>
  `
})
export class GTNetEditComponent extends SimpleEntityEditBase<GTNet> implements OnInit {
  @Input() callParam: GTNetCallParam;
  @ViewChild('entityTable') entityTable: EditableTableComponent<GTNetEntity>;

  private readonly BASE_SETTING = 'BASE_SETTING';
  private domainRemoteNameSubscribe: Subscription;

  // Entity table configuration
  entityFields: ColumnConfig[] = [];
  /** Entity data with string enum values for dropdown binding */
  gtNetEntities: any[] = [];
  entityFieldsReady = false;
  baseLocale: { language: string; dateFormat: string };

  // Dropdown options for entity table
  private acceptRequestOptions: ValueKeyHtmlSelectOptions[] = [];
  private serverStateOptions: ValueKeyHtmlSelectOptions[] = [];

  constructor(translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    private gtNetService: GTNetService) {
    super(HelpIds.HELP_GT_NET, AppSettings.GT_NET.toUpperCase(), translateService, gps,
      messageToastService, gtNetService);
    this.baseLocale = {
      language: gps.getUserLang(),
      dateFormat: gps.getCalendarTwoNumberDateFormat().toLocaleLowerCase()
    };
  }

  ngOnInit(): void {
    const isUpdate = this.callParam.gtNet && !!this.callParam?.gtNet.idGtNet;
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      5, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('domainRemoteName', 128, false, {
        fieldsetName: this.BASE_SETTING,
        disabled: isUpdate
      }),
      ...this.editOwnInstance(isUpdate),
      // Only add submit button if NOT isMyEntry (entity table has its own submit)
      ...(this.callParam.isMyEntry ? [] : [DynamicFieldHelper.createSubmitButton()])
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);

    // Setup entity table fields if this is my entry
    if (this.callParam.isMyEntry) {
      this.setupEntityFields();
    }
  }

  private editOwnInstance(isUpdate: boolean): FieldConfig[] {
    return this.callParam.isMyEntry ? [
      DynamicFieldHelper.createFieldSelectStringHeqF('timeZone', true, {fieldsetName: this.BASE_SETTING}),
      DynamicFieldHelper.createFieldCheckboxHeqF('spreadCapability', {
        defaultValue: true,
        fieldsetName: this.BASE_SETTING
      }),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'dailyRequestLimit', true, 0, 9999,
        {defaultValue: 1000, fieldsetName: this.BASE_SETTING}),
      DynamicFieldHelper.createFieldCheckboxHeqF('serverBusy', {fieldsetName: this.BASE_SETTING}),
      DynamicFieldHelper.createFieldCheckboxHeqF('allowServerCreation', {fieldsetName: this.BASE_SETTING}),
      DynamicFieldHelper.createFieldSelectStringHeqF('serverOnline', true,
        {
          defaultValue: GTNetServerOnlineStatusTypes[GTNetServerOnlineStatusTypes.SOS_UNKNOWN],
          fieldsetName: this.BASE_SETTING,
          disabled: isUpdate
        })
    ] : [];
  }

  /**
   * Sets up the entity table fields for editing GTNetEntity configurations.
   */
  private setupEntityFields(): void {
    // Create dropdown options
    this.acceptRequestOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
      this.translateService, AcceptRequestTypes);
    this.serverStateOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
      this.translateService, GTNetServerStateTypes);

    // entityKind column - ReadOnly, translated
    const entityKindCol = ShowRecordConfigBase.createColumnConfig(
      DataType.String, 'entityKind', 'ENTITY_KIND', true, false,
      {translateValues: TranslateValue.NORMAL, width: 180});
    entityKindCol.cec = {inputType: EditInputType.ReadOnly};
    this.entityFields.push(entityKindCol);

    // acceptRequest column - Select dropdown with per-row filtering
    const acceptRequestCol = ShowRecordConfigBase.createColumnConfigFeqH(
      DataType.String, 'acceptRequest',  true, false,
      {translateValues: TranslateValue.NORMAL, width: 150});
    acceptRequestCol.cec = {
      inputType: EditInputType.Select,
      optionsProviderFn: this.getAcceptRequestOptions.bind(this)
    };
    this.entityFields.push(acceptRequestCol);

    // serverState column - Select dropdown
    const serverStateCol = ShowRecordConfigBase.createColumnConfigFeqH(
      DataType.String, 'serverState', true, false,
      {translateValues: TranslateValue.NORMAL, width: 150});
    serverStateCol.cec = {
      inputType: EditInputType.Select,
      valueKeyHtmlOptions: this.serverStateOptions
    };
    this.entityFields.push(serverStateCol);

    // maxLimit column - Number input
    const maxLimitCol = ShowRecordConfigBase.createColumnConfig(
      DataType.NumericInteger, 'maxLimit', 'GT_NET_MAX_LIMIT', true, false,
      {width: 100});
    maxLimitCol.cec = {
      inputType: EditInputType.Number,
      min: 10,
      max: 999
    };
    this.entityFields.push(maxLimitCol);

    // Translate headers
    this.translateEntityHeaders();
  }

  /**
   * Translates entity table headers.
   */
  private translateEntityHeaders(): void {
    const headerKeys = this.entityFields.map(f => f.headerKey);
    this.translateService.get(headerKeys).subscribe(translations => {
      this.entityFields.forEach(field => {
        field.headerTranslated = translations[field.headerKey] || field.headerKey;
      });
      this.entityFieldsReady = true;
    });
  }

  protected override initialize(): void {
    if (this.callParam.isMyEntry) {
      this.gps.getTimezones().subscribe((timezones: ValueKeyHtmlSelectOptions[]) => {
        this.configObject.timeZone.valueKeyHtmlOptions = timezones;
        this.configObject.serverOnline.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
          GTNetServerOnlineStatusTypes);
        const gtNet = this.callParam.gtNet ?? new GTNet();
        this.form.transferBusinessObjectToForm(gtNet);

        // Initialize entity table data
        this.initializeEntities();
      });
    }
  }

  /**
   * Initializes the entity table with 3 rows (one per entity kind).
   */
  private initializeEntities(): void {
    const existingEntities = this.callParam.gtNet?.gtNetEntities || [];

    // Create 3 rows, one for each entity kind
    this.gtNetEntities = [
      this.findOrCreateEntity(existingEntities, GTNetExchangeKindType.HISTORICAL_PRICES),
      this.findOrCreateEntity(existingEntities, GTNetExchangeKindType.LAST_PRICE),
      this.findOrCreateEntity(existingEntities, GTNetExchangeKindType.SECURITY_METADATA)
    ];

    // Create translated value store for entityKind display
    this.createEntityKindTranslations();
  }

  /**
   * Finds an existing entity or creates a new one with default values.
   */
  private findOrCreateEntity(existing: GTNetEntity[], kind: GTNetExchangeKindType): any {
    const found = existing.find(e => e.entityKind === kind);
    if (found) {
      // Convert numeric enum values to string keys for dropdown binding
      return {
        ...found,
        acceptRequest: typeof found.acceptRequest === 'number'
          ? AcceptRequestTypes[found.acceptRequest]
          : found.acceptRequest,
        serverState: typeof found.serverState === 'number'
          ? GTNetServerStateTypes[found.serverState]
          : found.serverState,
        entityKind: typeof found.entityKind === 'number'
          ? GTNetExchangeKindType[found.entityKind]
          : found.entityKind
      };
    }

    // Default values for new entity
    return {
      idGtNet: this.callParam.gtNet?.idGtNet,
      entityKind: GTNetExchangeKindType[kind],
      acceptRequest: AcceptRequestTypes[AcceptRequestTypes.AC_OPEN],
      serverState: GTNetServerStateTypes[GTNetServerStateTypes.SS_NONE],
      maxLimit: 300
    };
  }

  /**
   * Creates translations for entityKind values.
   */
  private createEntityKindTranslations(): void {
    const entityKindField = this.entityFields.find(f => f.field === 'entityKind');
    if (entityKindField) {
      // Get translation keys for entity kinds
      const keys = Object.keys(GTNetExchangeKindType).filter(k => isNaN(Number(k)));
      this.translateService.get(keys).subscribe(translations => {
        entityKindField.translatedValueMap = {};
        keys.forEach(key => {
          entityKindField.translatedValueMap[key] = translations[key] || key;
        });
        // Also add $ suffixed field for sorting
        this.gtNetEntities.forEach(entity => {
          const kindStr = typeof entity.entityKind === 'number'
            ? GTNetExchangeKindType[entity.entityKind]
            : entity.entityKind;
          (entity as any)['entityKind$'] = translations[kindStr] || kindStr;
        });
      });
    }
  }

  /**
   * Value getter for entity table.
   */
  getEntityValueByPath(row: GTNetEntity, field: ColumnConfig): any {
    const value = Helper.getValueByPath(row, field.field);

    // Handle translated values
    if (field.translateValues && field.translatedValueMap) {
      const key = typeof value === 'number' ? GTNetExchangeKindType[value] : value;
      return field.translatedValueMap[key] || key;
    }

    return value;
  }

  /**
   * Provides acceptRequest options based on entity kind.
   * SECURITY_METADATA does not support AC_PUSH_OPEN mode.
   */
  getAcceptRequestOptions(row: any): ValueKeyHtmlSelectOptions[] {
    const entityKind = row.entityKind;
    const isSecurityMetadata = entityKind === GTNetExchangeKindType.SECURITY_METADATA
      || entityKind === GTNetExchangeKindType[GTNetExchangeKindType.SECURITY_METADATA];

    if (isSecurityMetadata) {
      // Filter out AC_PUSH_OPEN for SECURITY_METADATA
      return this.acceptRequestOptions.filter(opt =>
        opt.key !== AcceptRequestTypes[AcceptRequestTypes.AC_PUSH_OPEN]);
    }
    return this.acceptRequestOptions;
  }

  /**
   * Submits both the base form and entity table data.
   */
  submitAll(): void {
    // Get form values
    const formValue = this.form.value;

    // Create the GTNet entity with all data
    const gtNet = this.getNewOrExistingInstanceBeforeSave(formValue);

    // Use the service to save
    this.activateWaitStateInButton();
    this.serviceEntityUpdate.update(gtNet).subscribe({
      next: returnEntity => {
        this.messageToastService.showMessageI18n(
          InfoLevelType.SUCCESS,
          'MSG_RECORD_SAVED',
          {i18nRecord: this.i18nRecord}
        );
        this.closeDialog.emit(new ProcessedActionData(ProcessedAction.CREATED, returnEntity));
      },
      error: () => {
        this.deactivateWaitStateInButton();
      }
    });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): GTNet {
    const gtNet: GTNet = new GTNet();
    Object.assign(gtNet, this.callParam.gtNet);

    // Only set domainRemoteName from form value during create (field is disabled during update)
    if (value['domainRemoteName'] !== undefined) {
      gtNet.domainRemoteName = value['domainRemoteName'];
    }
    gtNet.timeZone = this.gps.getStandardTimeZone();

    if (this.callParam.isMyEntry) {
      gtNet.timeZone = value['timeZone'];
      gtNet.spreadCapability = value['spreadCapability'];
      gtNet.dailyRequestLimit = value['dailyRequestLimit'];
      gtNet.serverBusy = value['serverBusy'];
      gtNet.allowServerCreation = value['allowServerCreation'];
      gtNet.serverOnline = GTNetServerOnlineStatusTypes[value['serverOnline']] as any;

      // Get entity configurations from the table
      const entities = this.entityTable?.getData() || this.gtNetEntities;
      gtNet.gtNetEntities = entities.map(entity => this.convertEntityForSave(entity));
    }

    return gtNet;
  }

  /**
   * Converts entity from table format (string enums) to save format (numeric enums).
   */
  private convertEntityForSave(entity: GTNetEntity): GTNetEntity {
    return {
      idGtNetEntity: entity.idGtNetEntity,
      idGtNet: entity.idGtNet || this.callParam.gtNet?.idGtNet,
      entityKind: typeof entity.entityKind === 'string'
        ? GTNetExchangeKindType[entity.entityKind as keyof typeof GTNetExchangeKindType]
        : entity.entityKind,
      serverState: typeof entity.serverState === 'string'
        ? GTNetServerStateTypes[entity.serverState as keyof typeof GTNetServerStateTypes]
        : entity.serverState,
      acceptRequest: typeof entity.acceptRequest === 'string'
        ? AcceptRequestTypes[entity.acceptRequest as keyof typeof AcceptRequestTypes]
        : entity.acceptRequest,
      maxLimit: entity.maxLimit
    };
  }

  override onHide(event): void {
    this.domainRemoteNameSubscribe && this.domainRemoteNameSubscribe.unsubscribe();
    super.onHide(event);
  }
}
