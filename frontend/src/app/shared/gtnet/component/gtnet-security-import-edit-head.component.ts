import { afterNextRender, Component, inject, Injector, OnInit, input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';

import { HelpIds } from '../../../lib/help/help.ids';
import { SimpleEntityEditBase } from '../../../lib/edit/simple.entity.edit.base';
import { DynamicFieldHelper } from '../../../lib/helper/dynamic.field.helper';
import { DYNAMIC_FORM_IMPORTS } from '../../../lib/dynamic-form/dynamic-form.imports';
import { TranslateHelper } from '../../../lib/helper/translate.helper';
import { AppHelper } from '../../../lib/helper/app.helper';
import { BaseSettings } from '../../../lib/base.settings';

import { GTNetSecurityImpHead } from '../model/gtnet-security-imp-head';
import { GTNetSecurityImpHeadService } from '../service/gtnet-security-imp-head.service';

/**
 * Dialog component for creating and editing GTNet security import headers.
 */
@Component({
  selector: 'gtnet-security-import-edit-head',
  template: `
    <p-dialog
      header="{{ 'GTNET_SECURITY_IMP_HEAD' | translate }}"
      [visible]="visibleDialog()"
      [style]="{ width: '400px' }"
      (onShow)="onShow($event)"
      (onHide)="onHide($event)"
      [modal]="true">
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm"
        (submitBt)="submit($event)" />
    </p-dialog>
  `,
  imports: [DialogModule, ...DYNAMIC_FORM_IMPORTS, TranslateModule]
})
export class GTNetSecurityImportEditHeadComponent extends SimpleEntityEditBase<GTNetSecurityImpHead> implements OnInit {
  readonly entity = input<GTNetSecurityImpHead>(undefined);

  private readonly injector = inject(Injector);

  constructor() {
    super(HelpIds.HELP_BASEDATA_GT_NET_IMPORT_SECURITY, 'GTNET_SECURITY_IMP_HEAD', inject(GTNetSecurityImpHeadService));
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
    this.form().setDefaultValuesAndEnableSubmit();
    const entity = this.entity();
    if (entity) {
      this.form().transferBusinessObjectToForm(entity);
    }
    afterNextRender(() => this.configObject.name.elementRef?.nativeElement.focus(), { injector: this.injector });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): GTNetSecurityImpHead {
    const head = new GTNetSecurityImpHead();
    const entity = this.entity();
    if (entity) {
      Object.assign(head, entity);
    }
    this.form().cleanMaskAndTransferValuesToBusinessObject(head);
    return head;
  }
}
