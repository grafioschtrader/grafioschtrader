import { afterNextRender, Component, inject, Injector, OnInit, input } from '@angular/core';
import { Assetclass } from '../../entities/assetclass';
import { AppHelper } from '../../lib/helper/app.helper';
import { AssetclassService } from '../service/assetclass.service';
import { TranslateModule } from '@ngx-translate/core';
import { HelpIds } from '../../lib/help/help.ids';
import { AuditHelper } from '../../lib/helper/audit.helper';
import { AssetclassCallParam } from './assetclass.call.param';
import { ProposeChangeEntityWithEntity } from '../../lib/proposechange/model/propose.change.entity.whit.entity';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { FormHelper } from '../../lib/dynamic-form/components/FormHelper';
import { AppSettings } from '../../shared/app.settings';
import { AssetClassTypeSpecInstrument } from '../../udfmetasecurity/components/asset.class.type.spec.instrument';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';

/**
 * Edit asset classes in a dialog
 */
@Component({
  selector: 'assetclass-edit',
  template: `
    <p-dialog
      header="{{ i18nRecord | translate }}"
      [visible]="visibleDialog()"
      [style]="{ width: '500px' }"
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
export class AssetclassEditComponent extends AssetClassTypeSpecInstrument<Assetclass> implements OnInit {
  readonly callParam = input.required<AssetclassCallParam>();
  readonly proposeChangeEntityWithEntity = input<ProposeChangeEntityWithEntity>();

  private readonly injector = inject(Injector);

  constructor() {
    super(
      AppSettings.CATEGORY_TYPE,
      'specialInvestmentInstrument',
      HelpIds.HELP_BASEDATA_ASSETCLASS,
      AppSettings.ASSETCLASS.toUpperCase(),
      inject(AssetclassService)
    );
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldSelectString(AppSettings.CATEGORY_TYPE, AppSettings.ASSETCLASS.toUpperCase(), true),
      DynamicFieldHelper.createFieldSuggestionInputString('en', 'SUB_ASSETCLASS', 64, true, {
        dataproperty: 'subCategoryNLS.map.en',
        labelSuffix: 'EN',
        suggestionsFN: this.filterSuggestionsEN.bind(this)
      }),
      DynamicFieldHelper.createFieldSuggestionInputString('de', 'SUB_ASSETCLASS', 64, true, {
        dataproperty: 'subCategoryNLS.map.de',
        labelSuffix: 'DE',
        suggestionsFN: this.filterSuggestionsDE.bind(this)
      }),
      DynamicFieldHelper.createFieldSelectString('specialInvestmentInstrument', 'FINANCIAL_INSTRUMENT', true),
      ...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this)
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  public filterSuggestionsEN(event: any) {
    const query: string = event.query.toLocaleLowerCase();
    this.configObject.en.suggestions = this.callParam().subCategorySuggestionsEN.filter((suggestion) =>
      suggestion.toLocaleLowerCase().startsWith(query)
    );
  }

  public filterSuggestionsDE(event: any) {
    const query: string = event.query.toLocaleLowerCase();
    this.configObject.de.suggestions = this.callParam().subCategorySuggestionsDE.filter((suggestion) =>
      suggestion.toLocaleLowerCase().startsWith(query)
    );
  }

  protected override canChangeValues(): boolean {
    return !this.callParam().assetclass;
  }

  protected override initializeOthers(): void {
    this.configObject.en.suggestions = this.callParam().subCategorySuggestionsEN;
    const callParam = this.callParam();
    AuditHelper.transferToFormAndChangeButtonForProposaleEdit(
      this.translateService,
      this.gps,
      callParam.assetclass,
      this.form(),
      this.configObject,
      this.proposeChangeEntityWithEntity()
    );
    FormHelper.disableEnableFieldConfigs(callParam.hasSecurity, [
      this.configObject.categoryType,
      this.configObject.specialInvestmentInstrument
    ]);
    if (!callParam.assetclass) {
      afterNextRender(() => this.configObject.categoryType.baseInputComponent?.focus(), { injector: this.injector });
    }
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): Assetclass {
    const assetclass: Assetclass = new Assetclass();
    this.copyFormToPublicBusinessObject(assetclass, this.callParam().assetclass, this.proposeChangeEntityWithEntity());

    const values: any = {};
    this.form().cleanMaskAndTransferValuesToBusinessObject(values, true);
    assetclass.subCategoryNLS.map.de = values.de;
    assetclass.subCategoryNLS.map.en = values.en;
    return assetclass;
  }
}
