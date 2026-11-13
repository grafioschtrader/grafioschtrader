import { Component, OnInit, computed, inject, input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { SimpleEntityEditBase } from '../../edit/simple.entity.edit.base';
import { SignalDynamicFormComponent } from '../../dynamic-form/signal/signal-dynamic-form.component';
import { AppHelper } from '../../helper/app.helper';
import { DynamicFieldHelper } from '../../helper/dynamic.field.helper';
import { SelectOptionsHelper } from '../../helper/select.options.helper';
import { TranslateHelper } from '../../helper/translate.helper';
import { HelpIds } from '../../help/help.ids';
import { UDFMetadata } from '../model/udf.metadata';
import { UDFMetadataSubscription } from '../model/udf.metadata.subscription';
import { UDFMetadataSubscriptionService } from '../service/udf.metadata.subscription.service';

interface SubscriptionFormModel {
  uiOrder: number | null;
}

@Component({
  selector: 'udf-subscription-edit',
  template: `
    <p-dialog
      [header]="i18nRecord | translate"
      [visible]="visibleDialog()"
      [modal]="true"
      [style]="{ width: '500px' }"
      (onShow)="onShow($event)"
      (onHide)="onHide($event)">
      <dl>
        @for (row of displayRows(); track $index) {
          <dt>{{ row.label | translate }}</dt>
          <dd>{{ row.value }}</dd>
        }
      </dl>
      @if (noticeKey(); as notice) {
        <p class="bg-info">{{ notice | translate }}</p>
      }
      <signal-dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [source]="source()"
        #form="signalDynamicForm"
        (submitBt)="submit($event)" />
    </p-dialog>
  `,
  imports: [DialogModule, TranslateModule, SignalDynamicFormComponent]
})
export class UDFSubscriptionEditComponent
  extends SimpleEntityEditBase<UDFMetadataSubscription, SignalDynamicFormComponent<SubscriptionFormModel>>
  implements OnInit
{
  readonly field = input.required<UDFMetadata>();
  readonly excludeUiOrders = input.required<number[]>();
  readonly rows = input<{ label: string; value: string | null }[]>([]);

  /**
   * Translation key of a notice of the application about what adopting this field entails, shown above the save
   * button; absent or null for none.
   */
  readonly noticeKey = input<string | null>();
  readonly displayRows = computed(() => [
    { label: 'FIELD_DESCRIPTION', value: this.field().description },
    { label: 'FIELD_DESCRIPTION_HELP', value: this.field().descriptionHelp },
    { label: 'SHARE_MODE', value: this.translateService.instant(this.field().shareMode) },
    ...this.rows()
  ]);
  readonly source = computed(() =>
    Object.assign(new UDFMetadataSubscription(), {
      idUDFMetadataSubscription: this.field().idUDFMetadataSubscription,
      idUDFMetadata: this.field().idUDFMetadata,
      uiOrder: this.field().idUDFMetadataSubscription == null ? null : this.field().uiOrder
    })
  );

  constructor() {
    super(
      HelpIds.HELP_BASEDATA_UDF_METADATA_GENERAL,
      'UDF_METADATA_SUBSCRIPTION',
      inject(UDFMetadataSubscriptionService)
    );
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectNumberHeqF('uiOrder', true),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config, false);
    this.configObject.uiOrder.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsForNumberRange(
      1,
      99,
      this.excludeUiOrders()
    );
  }

  protected override initialize(): void {}

  protected override getNewOrExistingInstanceBeforeSave(): UDFMetadataSubscription {
    return this.copyFormToPrivateBusinessObject(new UDFMetadataSubscription(), this.source());
  }
}
