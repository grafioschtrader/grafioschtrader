import { Component, effect, OnInit, signal, viewChild, input, inject } from '@angular/core';
import { Subscription } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { FieldsetModule } from '@openng/optimus-ui/fieldset';
import { ButtonModule } from '@openng/optimus-ui/button';

import { AppHelper } from '../../lib/helper/app.helper';
import { AppSettings } from '../../shared/app.settings';
import { AuditHelper } from '../../lib/helper/audit.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { HelpIds } from '../../lib/help/help.ids';
import { ProposeChangeEntityWithEntity } from '../../lib/proposechange/model/propose.change.entity.whit.entity';
import { SimpleEntityEditBase } from '../../lib/edit/simple.entity.edit.base';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { YamlEditorComponent, YamlFieldCompletion } from '../../algo/component/yaml-editor.component';
import { TradingCalendarRuleSet } from '../../entities/trading.calendar.rule.set';
import { TradingCalendarRuleSetService } from '../service/trading.calendar.rule.set.service';
import { TradingCalendarRuleSetPreviewComponent } from './trading-calendar-rule-set-preview.component';

/**
 * Creates and edits a trading calendar rule set. The rules are written as YAML in a Monaco editor whose completions
 * and hover texts come from the JSON Schema, so the meaning of a rule type and of the fields it requires is available
 * without leaving the dialog.
 */
@Component({
  selector: 'trading-calendar-rule-set-edit',
  template: ` <p-dialog
    styleClass="big-dialog"
    header="{{ i18nRecord | translate }}"
    [visible]="visibleDialog()"
    [style]="{ width: '900px' }"
    (onShow)="onShow($event)"
    (onHide)="onHide($event)"
    [modal]="true">
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)" />

    <yaml-editor
      #yamlEditor
      format="CALENDAR"
      (syntaxValidChange)="updateSubmitState()"
      (validationPendingChange)="updateSubmitState()"
      [height]="'380px'"
      [value]="ruleYamlValue()"
      (valueChange)="ruleYamlValue.set($event)"
      [schema]="ruleSchema()"
      [fieldCompletions]="ruleCompletions" />

    @if (idTradingCalendarRuleSet) {
      <p-fieldset
        [legend]="'PREVIEW_CLOSURES' | translate"
        [toggleable]="true"
        [collapsed]="true"
        styleClass="mt-3"
        (onBeforeToggle)="onPreviewToggle($event.collapsed)">
        <div style="display: flex; gap: 0.5rem; align-items: center; margin-bottom: 0.5rem;">
          <p-button [label]="'PREVIOUS_YEAR' | translate" (click)="changePreviewYear(-1)">
            <i class="pi pi-angle-left" pButtonIcon></i>
          </p-button>
          <strong>{{ previewYear() }}</strong>
          <p-button [label]="'NEXT_YEAR' | translate" (click)="changePreviewYear(1)">
            <i class="pi pi-angle-right" pButtonIcon></i>
          </p-button>
        </div>
        @if (previewError()) {
          <div class="text-red-600">{{ previewError() }}</div>
        } @else {
          <trading-calendar-rule-set-preview [closures]="previewClosures()" />
        }
      </p-fieldset>
    }
  </p-dialog>`,
  imports: [
    DialogModule,
    ...DYNAMIC_FORM_IMPORTS,
    TranslateModule,
    FieldsetModule,
    ButtonModule,
    YamlEditorComponent,
    TradingCalendarRuleSetPreviewComponent
  ]
})
export class TradingCalendarRuleSetEditComponent
  extends SimpleEntityEditBase<TradingCalendarRuleSet>
  implements OnInit
{
  private tradingCalendarRuleSetService: TradingCalendarRuleSetService = inject(TradingCalendarRuleSetService);
  private httpClient: HttpClient = inject(HttpClient);

  readonly yamlEditor = viewChild(YamlEditorComponent);
  readonly callParam = input<TradingCalendarRuleSet | null>();
  readonly proposeChangeEntityWithEntity = input<ProposeChangeEntityWithEntity>(undefined);

  readonly ruleYamlValue = signal('');
  readonly ruleSchema = signal<any>(null);
  ruleCompletions: { [fieldName: string]: YamlFieldCompletion[] };

  readonly previewYear = signal(new Date().getFullYear());
  readonly previewClosures = signal<{ [date: string]: string }>({});
  readonly previewError = signal<string | null>(null);
  private previewSubscription?: Subscription;
  private optionsLoaded = false;

  constructor() {
    super(
      HelpIds.HELP_BASEDATA_TRADING_CALENDAR_RULE,
      AppHelper.toUpperCaseWithUnderscore(AppSettings.TRADING_CALENDAR_RULE_SET),
      inject(TradingCalendarRuleSetService)
    );
    effect(() => {
      this.ruleYamlValue();
      // A result belongs to the exact YAML that was sent, including while its response is still in flight.
      this.previewSubscription?.unsubscribe();
      this.previewClosures.set({});
      this.previewError.set(null);
    });
  }

  get idTradingCalendarRuleSet(): number | null | undefined {
    return this.callParam()?.idTradingCalendarRuleSet;
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldInputStringHeqF('name', 64, true, {
        minLength: 2
      }),
      DynamicFieldHelper.createFieldInputStringHeqF('mic', 4, false, {
        minLength: 4
      }),
      DynamicFieldHelper.createFieldSelectNumberHeqF('idExtendsRuleSet', false),
      ...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this)
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.ruleCompletions = TradingCalendarRuleSetEditComponent.buildRuleCompletions();
  }

  protected override initialize(): void {
    const callParam = this.callParam();
    this.ruleYamlValue.set(callParam?.ruleYaml ?? '');
    this.previewYear.set(new Date().getFullYear());
    this.previewClosures.set({});
    this.previewError.set(null);
    this.optionsLoaded = false;
    this.form().setDefaultValuesAndEnableSubmit();
    AuditHelper.transferToFormAndChangeButtonForProposaleEdit(
      this.translateService,
      this.gps,
      callParam,
      this.form(),
      this.configObject,
      this.proposeChangeEntityWithEntity()
    );
    this.updateSubmitState();
    this.httpClient
      .get('assets/schemas/trading-calendar-rule-schema.json')
      .pipe(this.untilDialogClosed())
      .subscribe((schema) => this.ruleSchema.set(schema));

    this.tradingCalendarRuleSetService
      .getRuleSetOptions()
      .pipe(this.untilDialogClosed())
      .subscribe((options: ValueKeyHtmlSelectOptions[]) => {
        // A set must not extend itself, so the edited one is removed from the choices. Longer cycles are refused by
        // the backend, which is the only place that can see the whole chain.
        this.configObject.idExtendsRuleSet.valueKeyHtmlOptions = options.filter(
          (option) =>
            this.idTradingCalendarRuleSet == null || option.key == null || +option.key !== this.idTradingCalendarRuleSet
        );
        this.optionsLoaded = true;
        this.updateSubmitState();
      });
  }

  /** Options and YAML validation can finish in either order; neither may enable submit before the other. */
  updateSubmitState(): void {
    const yamlEditor = this.yamlEditor();
    this.configObject.submit.disabled = !this.optionsLoaded || !yamlEditor?.syntaxValid || yamlEditor.validating;
  }

  /**
   * Checks the YAML before saving and only continues when it is free of errors. The backend refuses invalid rules on
   * save as well, but reporting them here lists every problem at once instead of the first one as a toast.
   */
  override async submit(value: { [name: string]: any }): Promise<void> {
    if (await this.yamlEditor().validateForSubmit()) {
      if (!this.destroyRef.destroyed && this.optionsLoaded) super.submit(value);
    } else this.updateSubmitState();
  }

  changePreviewYear(offset: number): void {
    this.previewYear.update((year) => year + offset);
    this.loadPreview();
  }

  /** The before-toggle event contains the old state: only expanding starts a preview. */
  onPreviewToggle(collapsed: boolean): void {
    this.previewSubscription?.unsubscribe();
    if (collapsed) this.loadPreview();
  }

  /**
   * Fetches the closures for the previewed year, sending the edited YAML so the effect of an unsaved change is
   * visible. The inherited rules always come from the stored sets.
   */
  loadPreview(): void {
    const id = this.idTradingCalendarRuleSet;
    if (!id) {
      return;
    }
    this.previewSubscription?.unsubscribe();
    this.previewError.set(null);
    this.previewClosures.set({});
    this.previewSubscription = this.tradingCalendarRuleSetService
      .previewClosures(id, this.previewYear(), this.ruleYamlValue())
      .pipe(this.untilDialogClosed())
      .subscribe({
        next: (closures) => this.previewClosures.set(closures),
        error: () => this.previewError.set(this.translateService.instant('PREVIEW_CLOSURES_FAILED'))
      });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): TradingCalendarRuleSet {
    const ruleSet = new TradingCalendarRuleSet();
    this.copyFormToPublicBusinessObject(ruleSet, this.callParam(), this.proposeChangeEntityWithEntity());
    ruleSet.mic = ruleSet.mic ? ruleSet.mic.toUpperCase() : null;
    ruleSet.ruleYaml = this.ruleYamlValue();
    return ruleSet;
  }

  /**
   * Value completions for the fields whose values are enum constants. The schema already documents the properties;
   * these add the allowed values with a one line explanation at the point where they are typed.
   */
  private static buildRuleCompletions(): {
    [fieldName: string]: YamlFieldCompletion[];
  } {
    const completion =
      (detail: string) =>
      (label: string, documentation: string): YamlFieldCompletion => ({
        label,
        insertText: label,
        detail,
        documentation,
        kind: 'Keyword'
      });
    const ruleType = completion('rule type');
    const observance = completion('observance');
    const weekday = completion('weekday');
    return {
      type: [
        ruleType('FIXED', 'Same month and day every year, for instance 1 January.'),
        ruleType(
          'NTH_WEEKDAY',
          'The nth weekday of a month, for instance the third Monday in January. Needs month, nth and dayOfWeek.'
        ),
        ruleType('WEEKLY', 'The same weekday all year, used for non standard weekends. Needs dayOfWeek.'),
        ruleType('EASTER_RELATIVE', 'An offset in days from Western Easter Sunday. Needs offset.'),
        ruleType('ORTHODOX_EASTER_RELATIVE', 'An offset in days from Orthodox Easter Sunday. Needs offset.'),
        ruleType(
          'HIJRI',
          'An Islamic date converted to the Gregorian calendar. Needs month and day, usually with offsetTolerance.'
        ),
        ruleType('EXPLICIT_DATES', 'A fixed list of dates that follow no pattern. Needs dates.')
      ],
      observance: [
        observance(
          'NONE',
          'A closure falling on a weekend is dropped. Correct wherever holidays are not made up, for instance in Switzerland.'
        ),
        observance('NEAREST_WEEKDAY', 'Saturday moves to Friday, Sunday to Monday.'),
        observance('SUNDAY_TO_MONDAY', 'Only a Sunday moves, to the following Monday.'),
        observance('NEXT_MONDAY', 'A weekend date moves to the following Monday.'),
        observance('NEXT_WEEKDAY', 'A weekend date moves to the next working day.')
      ],
      dayOfWeek: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].map((day) =>
        weekday(day, 'Weekday in English and upper case.')
      )
    };
  }
}
