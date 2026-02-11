import {Component, Input, OnInit} from '@angular/core';
import {SimpleEntityEditBase} from '../../edit/simple.entity.edit.base';
import {GTNetConfigEntity, SupplierConsumerLogTypes} from '../model/gtnet';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {MessageToastService} from '../../message/message.toast.service';
import {HelpIds} from '../../help/help.ids';
import {GTNetConfigEntityService} from '../service/gtnet.config.entity.service';
import {AppHelper} from '../../helper/app.helper';
import {DynamicFieldHelper} from '../../helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../helper/select.options.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {DataType} from '../../dynamic-form/models/data.type';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormComponent} from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {GTNetConfigEntityDisplay} from './gtnet-config-entity-table.component';

/**
 * Dialog component for editing GTNetConfigEntity.
 * Allows editing of supplierLog, consumerLog, and consumerUsage fields.
 */
@Component({
  selector: 'gtnet-config-entity-edit',
  standalone: true,
  imports: [
    DialogModule,
    DynamicFormComponent,
    TranslateModule
  ],
  template: `
    <p-dialog header="{{ 'GT_NET_CONFIG_ENTITY_EDIT' | translate }}" [visible]="visibleDialog"
              [style]="{width: '400px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <p class="big-size">{{gtNetConfigEntity.entityKind | translate}}</p>
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `,
  providers: [GTNetConfigEntityService]
})
export class GTNetConfigEntityEditComponent extends SimpleEntityEditBase<GTNetConfigEntity> implements OnInit {
  @Input() gtNetConfigEntity: GTNetConfigEntityDisplay;

  constructor(
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    gtNetConfigEntityService: GTNetConfigEntityService
  ) {
    super(HelpIds.HELP_GT_NET, 'GT_NET_CONFIG_ENTITY_EDIT', translateService, gps,
      messageToastService, gtNetConfigEntityService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF('supplierLog', true,
        {inputWidth: 200}),
      DynamicFieldHelper.createFieldSelectStringHeqF('consumerLog', true,
        {inputWidth: 200}),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'consumerUsage', false, 0, 255,
        {defaultValue: 0}),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    const logTypeOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
      this.translateService, SupplierConsumerLogTypes);
    this.configObject.supplierLog.valueKeyHtmlOptions = logTypeOptions;
    this.configObject.consumerLog.valueKeyHtmlOptions = logTypeOptions;
    this.form.transferBusinessObjectToForm(this.gtNetConfigEntity);
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): GTNetConfigEntity {
    const entity: GTNetConfigEntity = {
      idGtNetEntity: this.gtNetConfigEntity.idGtNetEntity,
      exchange: this.gtNetConfigEntity.exchange,
      supplierLog: value['supplierLog'],
      consumerLog: value['consumerLog'],
      consumerUsage: value['consumerUsage']
    };
    return entity;
  }
}


