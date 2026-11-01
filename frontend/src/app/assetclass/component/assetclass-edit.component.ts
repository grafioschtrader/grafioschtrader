import { afterNextRender, Component, inject, Injector, OnInit, input, computed, signal } from '@angular/core';
import { Assetclass } from '../../entities/assetclass';
import { AppHelper } from '../../lib/helper/app.helper';
import { AssetclassService } from '../service/assetclass.service';
import { TranslateModule } from '@ngx-translate/core';
import { HelpIds } from '../../lib/help/help.ids';
import { AuditHelper } from '../../lib/helper/audit.helper';
import { AssetclassCallParam } from './assetclass.call.param';
import type { ProposeChangeEntityWithEntity } from '../../lib/proposechange/model/propose.change.entity.whit.entity';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { AppSettings } from '../../shared/app.settings';
import { SimpleEntityEditBase } from '../../lib/edit/simple.entity.edit.base';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import { AssetclassType } from '../../shared/types/assetclass.type';
import { SpecialInvestmentInstruments } from '../../shared/types/special.investment.instruments';
import { forkJoin } from 'rxjs';
import { disabled, metadata, SchemaPathTree } from '@angular/forms/signals';
import { FIELD_DISPLAY, SignalValueLink } from '../../lib/dynamic-form/signal/signal-form.model';
import { AuditNoteModel } from '../../lib/helper/audit.helper';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';

import { DialogModule } from '@openng/optimus-ui/dialog';
import { SignalDynamicFormComponent } from '../../lib/dynamic-form/signal/signal-dynamic-form.component';

interface AssetclassFormModel extends AuditNoteModel {
  categoryType: string | null;
  specialInvestmentInstrument: string | null;
  en: string;
  de: string;
}

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
      <signal-dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [source]="callParam().assetclass"
        [values]="startValues()"
        [rules]="rules"
        [valueLinks]="valueLinks"
        #form="signalDynamicForm"
        (submitBt)="submit($event)" />
    </p-dialog>
  `,
  imports: [DialogModule, SignalDynamicFormComponent, TranslateModule]
})
export class AssetclassEditComponent
  extends SimpleEntityEditBase<Assetclass, SignalDynamicFormComponent<AssetclassFormModel>>
  implements OnInit
{
  readonly callParam = input.required<AssetclassCallParam>();
  readonly proposeChangeEntityWithEntity = input<ProposeChangeEntityWithEntity>();

  private readonly gpsGT = inject(GlobalparameterGTService);
  private readonly choices = signal<{
    mapping: Record<string, SpecialInvestmentInstruments[]>;
    categories: ValueKeyHtmlSelectOptions[];
    instruments: ValueKeyHtmlSelectOptions[];
  } | null>(null);
  private readonly suggestionsEN = signal<string[]>([]);
  private readonly suggestionsDE = signal<string[]>([]);
  readonly auditMode = computed(() =>
    AuditHelper.auditEditMode(this.gps, this.callParam().assetclass, this.proposeChangeEntityWithEntity())
  );
  readonly startValues = computed(() => AuditHelper.noteStartValues(this.proposeChangeEntityWithEntity()));
  readonly rules = (p: SchemaPathTree<AssetclassFormModel>): void => {
    AuditHelper.auditRules(p, this.config, () => ({
      mode: this.auditMode(),
      proposeChangeEntityWithEntity: this.proposeChangeEntityWithEntity()
    }));
    disabled(p.categoryType, { when: () => this.callParam().hasSecurity });
    disabled(p.specialInvestmentInstrument, { when: () => this.callParam().hasSecurity });
    metadata(p.categoryType, FIELD_DISPLAY, () => ({ valueKeyHtmlOptions: this.choices()?.categories ?? [] }));
    metadata(p.specialInvestmentInstrument, FIELD_DISPLAY, ({ valueOf }) => ({
      valueKeyHtmlOptions: this.instrumentOptions(valueOf(p.categoryType))
    }));
    metadata(p.en, FIELD_DISPLAY, () => ({ suggestions: this.suggestionsEN() }));
    metadata(p.de, FIELD_DISPLAY, () => ({ suggestions: this.suggestionsDE() }));
  };
  readonly valueLinks: SignalValueLink<AssetclassFormModel>[] = [
    {
      sources: ['categoryType'],
      target: 'specialInvestmentInstrument',
      derive: (values, current) => {
        if (this.callParam().assetclass || !values.categoryType || !this.choices()) return current;
        const options = this.instrumentOptions(values.categoryType);
        return options[0]?.key ?? null;
      }
    }
  ];

  private instrumentOptions(category: string | null): ValueKeyHtmlSelectOptions[] {
    const choices = this.choices();
    if (!choices) return [];
    if (this.callParam().assetclass || !category) return choices.instruments;
    return choices.instruments.filter((option) =>
      choices.mapping[category]?.includes(option.key as SpecialInvestmentInstruments)
    );
  }

  private readonly injector = inject(Injector);

  constructor() {
    super(HelpIds.HELP_BASEDATA_ASSETCLASS, AppSettings.ASSETCLASS.toUpperCase(), inject(AssetclassService));
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
      ...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this.form)
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config, false);
  }

  public filterSuggestionsEN(event: { query: string }) {
    const query: string = event.query.toLocaleLowerCase();
    this.suggestionsEN.set(
      this.callParam().subCategorySuggestionsEN.filter((suggestion) => suggestion.toLocaleLowerCase().startsWith(query))
    );
  }

  public filterSuggestionsDE(event: { query: string }) {
    const query: string = event.query.toLocaleLowerCase();
    this.suggestionsDE.set(
      this.callParam().subCategorySuggestionsDE.filter((suggestion) => suggestion.toLocaleLowerCase().startsWith(query))
    );
  }

  protected override initialize(): void {
    this.suggestionsEN.set(this.callParam().subCategorySuggestionsEN);
    this.suggestionsDE.set(this.callParam().subCategorySuggestionsDE);
    AuditHelper.applyAuditButtons(this.form(), this.auditMode());
    const categories = Object.keys(AssetclassType).filter(
      (key) => isNaN(Number(key)) && !['CURRENCY_CASH', 'CURRENCY_FOREIGN'].includes(key)
    );
    const instruments = Object.keys(SpecialInvestmentInstruments).filter((key) => isNaN(Number(key)));
    forkJoin({
      mapping: this.gpsGT.getPossibleAssetclassInstrumentMap(),
      texts: this.translateService.get([...categories, ...instruments])
    })
      .pipe(this.untilDialogClosed())
      .subscribe(({ mapping, texts }) => {
        const options = (keys: string[]) =>
          keys
            .map((key) => new ValueKeyHtmlSelectOptions(key, texts[key]))
            .sort((a, b) => a.value.localeCompare(b.value));
        this.choices.set({ mapping, categories: options(categories), instruments: options(instruments) });
      });
    if (!this.callParam().assetclass)
      afterNextRender(() => this.configObject.categoryType.baseInputComponent?.focus(), { injector: this.injector });
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
