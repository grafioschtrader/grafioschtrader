import {Component, Input, OnInit} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';

import {HelpIds} from '../../lib/help/help.ids';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {SimpleEntityEditBase} from '../../lib/edit/simple.entity.edit.base';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {AppHelper} from '../../lib/helper/app.helper';
import {BaseSettings} from '../../lib/base.settings';

import {GTNetSecurityImpHead} from '../model/gtnet-security-imp-head';
import {GTNetSecurityImpHeadService} from '../service/gtnet-security-imp-head.service';

/**
 * Dialog component for creating and editing GTNet security import headers.
 */
@Component({
  selector: 'gtnet-security-import-edit-head',
  template: `
    <p-dialog header="{{'GTNET_SECURITY_IMP_HEAD' | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '400px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `,
  standalone: false
})
export class GTNetSecurityImportEditHeadComponent extends SimpleEntityEditBase<GTNetSecurityImpHead> implements OnInit {

  @Input() entity: GTNetSecurityImpHead;

  constructor(
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    gtNetSecurityImpHeadService: GTNetSecurityImpHeadService
  ) {
    super(
      HelpIds.HELP_BASEDATA_GTNET,
      'GTNET_SECURITY_IMP_HEAD',
      translateService,
      gps,
      messageToastService,
      gtNetSecurityImpHeadService
    );
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 6, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputString('name', 'NAME', 40, true),
      DynamicFieldHelper.createFieldTextareaInputString('note', 'NOTE', BaseSettings.FID_MAX_LETTERS, false),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.form.setDefaultValuesAndEnableSubmit();
    if (this.entity) {
      this.form.transferBusinessObjectToForm(this.entity);
    }
    setTimeout(() => this.configObject.name.elementRef.nativeElement.focus());
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): GTNetSecurityImpHead {
    const head = new GTNetSecurityImpHead();
    if (this.entity) {
      Object.assign(head, this.entity);
    }
    this.form.cleanMaskAndTransferValuesToBusinessObject(head);
    return head;
  }
}
