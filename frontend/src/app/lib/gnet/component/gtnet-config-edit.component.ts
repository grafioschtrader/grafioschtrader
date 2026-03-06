import {Component, Input, OnInit} from '@angular/core';
import {SimpleEntityEditBase} from '../../edit/simple.entity.edit.base';
import {GTNetConfig} from '../model/gtnet';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {MessageToastService} from '../../message/message.toast.service';
import {HelpIds} from '../../help/help.ids';
import {GTNetConfigService} from '../service/gtnet.config.service';
import {AppHelper} from '../../helper/app.helper';
import {DynamicFieldHelper} from '../../helper/dynamic.field.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {DataType} from '../../dynamic-form/models/data.type';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormComponent} from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';

/**
 * Dialog for editing the connection timeout of a GTNetConfig entity.
 * Only the connectionTimeout field (5-40 seconds, nullable) is editable.
 */
@Component({
  selector: 'gtnet-config-edit',
  standalone: true,
  imports: [
    DialogModule,
    DynamicFormComponent,
    TranslateModule
  ],
  template: `
    <p-dialog header="{{'GT_NET_CONFIG_EDIT' | translate}}" [visible]="visibleDialog"
              [style]="{width: '450px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm" (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `
})
export class GTNetConfigEditComponent extends SimpleEntityEditBase<GTNetConfig> implements OnInit {
  @Input() gtNetConfig: GTNetConfig;

  constructor(
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    private gtNetConfigService: GTNetConfigService
  ) {
    super(HelpIds.HELP_GT_NET, 'GT_NET_CONFIG_EDIT', translateService, gps,
      messageToastService, gtNetConfigService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'connectionTimeout', false, 5, 40),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.form.transferBusinessObjectToForm(this.gtNetConfig);
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): GTNetConfig {
    const config = {...this.gtNetConfig};
    config.connectionTimeout = value['connectionTimeout'] ?? null;
    return config;
  }
}
