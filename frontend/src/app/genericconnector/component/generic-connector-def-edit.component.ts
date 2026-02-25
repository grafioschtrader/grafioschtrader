import {Component, Input, OnInit} from '@angular/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GenericConnectorDefService} from '../service/generic.connector.def.service';
import {AppHelper} from '../../lib/helper/app.helper';
import {GenericConnectorDef} from '../../entities/generic.connector.def';
import {RateLimitType} from '../../shared/types/rate.limit.type';
import {SimpleEntityEditBase} from '../../lib/edit/simple.entity.edit.base';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {ProposeChangeEntityWithEntity} from '../../lib/proposechange/model/propose.change.entity.whit.entity';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {AppSettings} from '../../shared/app.settings';
import {AppHelpIds} from '../../shared/help/help.ids';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';
import {FieldsetModule} from 'primeng/fieldset';
import {YamlEditorComponent} from '../../algo/component/yaml-editor.component';
import {HttpClient} from '@angular/common/http';
import {MultilanguageString} from '../../lib/entities/multilanguage.string';

@Component({
  selector: 'generic-connector-def-edit',
  template: `
    <p-dialog header="{{'GENERIC_CONNECTOR_DEF' | translate}}" [visible]="visibleDialog"
              [style]="{width: '900px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm" (submitBt)="submit($event)">
      </dynamic-form>

      <p-fieldset [legend]="'TOKEN_CONFIG_YAML' | translate" [toggleable]="true" [collapsed]="true"
                  styleClass="mt-3">
        <yaml-editor [height]="'300px'" [(value)]="tokenConfigYamlValue"
                     [schema]="tokenConfigSchema"></yaml-editor>
      </p-fieldset>
    </p-dialog>`,
  standalone: true,
  imports: [DialogModule, DynamicFormModule, TranslateModule, FieldsetModule, YamlEditorComponent]
})
export class GenericConnectorDefEditComponent extends SimpleEntityEditBase<GenericConnectorDef> implements OnInit {
  @Input() callParam: GenericConnectorDef;
  @Input() proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity;

  tokenConfigYamlValue = '';
  tokenConfigSchema: any;

  constructor(private genericConnectorDefService: GenericConnectorDefService,
              private httpClient: HttpClient,
              translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService) {
    super(AppHelpIds.HELP_BASEDATA_GENERIC_CONNECTOR, AppSettings.GENERIC_CONNECTOR_DEF.toUpperCase(),
      translateService, gps, messageToastService, genericConnectorDefService);
    this.loadTokenConfigSchema();
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputStringHeqF('shortId', 32, true),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('readableName', 100, true),
      DynamicFieldHelper.createFieldTextareaInputString('en', 'DESCRIPTION', 2000, false,
        {labelSuffix: 'EN', dataproperty: 'descriptionNLS.map.en'}),
      DynamicFieldHelper.createFieldTextareaInputString('de', 'DESCRIPTION', 2000, false,
        {labelSuffix: 'DE', dataproperty: 'descriptionNLS.map.de'}),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('domainUrl', 255, true),
      DynamicFieldHelper.createFieldCheckboxHeqF('needsApiKey'),
      DynamicFieldHelper.createFieldSelectStringHeqF('rateLimitType', true),
      DynamicFieldHelper.createFieldInputNumberHeqF('rateLimitRequests', false, 5, 0, false),
      DynamicFieldHelper.createFieldInputNumberHeqF('rateLimitPeriodSec', false, 5, 0, false),
      DynamicFieldHelper.createFieldInputNumberHeqF('rateLimitConcurrent', false, 3, 0, false),
      DynamicFieldHelper.createFieldInputNumberHeqF('intradayDelaySeconds', false, 5, 0, false),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('regexUrlPattern', 255, false),
      DynamicFieldHelper.createFieldCheckboxHeqF('supportsSecurity'),
      DynamicFieldHelper.createFieldCheckboxHeqF('supportsCurrency'),
      DynamicFieldHelper.createFieldCheckboxHeqF('needHistoryGapFiller'),
      DynamicFieldHelper.createFieldCheckboxHeqF('gbxDividerEnabled'),
      ...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this)
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.configObject.rateLimitType.valueKeyHtmlOptions =
      SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService, RateLimitType);
    this.form.setDefaultValuesAndEnableSubmit();
    AuditHelper.transferToFormAndChangeButtonForProposaleEdit(this.translateService, this.gps, this.callParam,
      this.form, this.configObject, this.proposeChangeEntityWithEntity);
    this.configObject.shortId.elementRef.nativeElement.focus();
    this.tokenConfigYamlValue = this.callParam?.tokenConfigYaml || '';
  }

  protected override getNewOrExistingInstanceBeforeSave(value: {[name: string]: any}): GenericConnectorDef {
    const entity = new GenericConnectorDef();
    this.copyFormToPublicBusinessObject(entity, this.callParam, this.proposeChangeEntityWithEntity);
    this.form.cleanMaskAndTransferValuesToBusinessObject(entity);
    const values: any = {};
    this.form.cleanMaskAndTransferValuesToBusinessObject(values, true);
    entity.descriptionNLS = entity.descriptionNLS || new MultilanguageString();
    entity.descriptionNLS.map = entity.descriptionNLS.map || {de: null, en: null};
    entity.descriptionNLS.map.en = values.en;
    entity.descriptionNLS.map.de = values.de;
    entity.tokenConfigYaml = this.tokenConfigYamlValue?.trim() || null;
    return entity;
  }

  private loadTokenConfigSchema(): void {
    this.httpClient.get('assets/schemas/token-config-schema.json').subscribe({
      next: (schema: any) => this.tokenConfigSchema = schema,
      error: () => console.warn('Failed to load token config schema')
    });
  }
}
