import { Component, Input, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { FieldsetModule } from '@openng/optimus-ui/fieldset';
import { ButtonModule } from '@openng/optimus-ui/button';

import { AppHelper } from '../../lib/helper/app.helper';
import { AppSettings } from '../../shared/app.settings';
import { AuditHelper } from '../../lib/helper/audit.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { DynamicFormModule } from '../../lib/dynamic-form/dynamic-form.module';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { HelpIds } from '../../lib/help/help.ids';
import { MessageToastService } from '../../lib/message/message.toast.service';
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
    header="{{ i18nRecord | translate }}"
    [visible]="visibleDialog"
    [style]="{ width: '900px' }"
    (onShow)="onShow($event)"
    (onHide)="onHide($event)"
    [modal]="true">
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)">
    </dynamic-form>

    <yaml-editor
      [height]="'380px'"
      [(value)]="ruleYamlValue"
      [schema]="ruleSchema"
      [fieldCompletions]="ruleCompletions"></yaml-editor>

    @if (validationErrors.length > 0) {
      <div class="text-red-600 mt-2">
        @for (error of validationErrors; track error) {
          <div>{{ error }}</div>
        }
      </div>
    }

    @if (idTradingCalendarRuleSet) {
      <p-fieldset
        [legend]="'PREVIEW_CLOSURES' | translate"
        [toggleable]="true"
        [collapsed]="true"
        styleClass="mt-3"
        (onBeforeToggle)="loadPreview()">
        <div style="display: flex; gap: 0.5rem; align-items: center; margin-bottom: 0.5rem;">
          <p-button [label]="'PREVIOUS_YEAR' | translate" (click)="changePreviewYear(-1)">
            <i class="pi pi-angle-left" pButtonIcon></i>
          </p-button>
          <strong>{{ previewYear }}</strong>
          <p-button [label]="'NEXT_YEAR' | translate" (click)="changePreviewYear(1)">
            <i class="pi pi-angle-right" pButtonIcon></i>
          </p-button>
        </div>
        @if (previewError) {
          <div class="text-red-600">{{ previewError }}</div>
        } @else {
          <trading-calendar-rule-set-preview [closures]="previewClosures"> </trading-calendar-rule-set-preview>
        }
      </p-fieldset>
    }
  </p-dialog>`,
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [
    DialogModule,
    DynamicFormModule,
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
  @Input() callParam: TradingCalendarRuleSet;
  @Input() proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity;

  ruleYamlValue = '';
  ruleSchema: any;
  ruleCompletions: { [fieldName: string]: YamlFieldCompletion[] };
  validationErrors: string[] = [];

  previewYear: number = new Date().getFullYear();
  previewClosures: { [date: string]: string } = {};
  previewError: string = null;

  constructor(
    private tradingCalendarRuleSetService: TradingCalendarRuleSetService,
    private httpClient: HttpClient,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService
  ) {
    super(
      HelpIds.HELP_BASEDATA_TRADING_CALENDAR_RULE,
      AppHelper.toUpperCaseWithUnderscore(AppSettings.TRADING_CALENDAR_RULE_SET),
      translateService,
      gps,
      messageToastService,
      tradingCalendarRuleSetService
    );
  }

  get idTradingCalendarRuleSet(): number {
    return this.callParam?.idTradingCalendarRuleSet;
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
    this.ruleYamlValue = this.callParam?.ruleYaml ?? '';
    this.httpClient
      .get('assets/schemas/trading-calendar-rule-schema.json')
      .subscribe((schema) => (this.ruleSchema = schema));

    this.tradingCalendarRuleSetService.getRuleSetOptions().subscribe((options: ValueKeyHtmlSelectOptions[]) => {
      // A set must not extend itself, so the edited one is removed from the choices. Longer cycles are refused by
      // the backend, which is the only place that can see the whole chain.
      this.configObject.idExtendsRuleSet.valueKeyHtmlOptions = options.filter(
        (option) => !this.idTradingCalendarRuleSet || +option.key !== this.idTradingCalendarRuleSet
      );
      this.form.setDefaultValuesAndEnableSubmit();
      AuditHelper.transferToFormAndChangeButtonForProposaleEdit(
        this.translateService,
        this.gps,
        this.callParam,
        this.form,
        this.configObject,
        this.proposeChangeEntityWithEntity
      );
    });
  }

  /**
   * Checks the YAML before saving and only continues when it is free of errors. The backend refuses invalid rules on
   * save as well, but reporting them here lists every problem at once instead of the first one as a toast.
   */
  override submit(value: { [name: string]: any }): void {
    this.validationErrors = [];
    this.tradingCalendarRuleSetService.validateYaml(this.ruleYamlValue).subscribe({
      next: (errors) => {
        this.validationErrors = errors;
        if (errors.length === 0) {
          super.submit(value);
        } else {
          this.configObject.submit.disabled = false;
        }
      },
      error: () => super.submit(value)
    });
  }

  changePreviewYear(offset: number): void {
    this.previewYear += offset;
    this.loadPreview();
  }

  /**
   * Fetches the closures for the previewed year, sending the edited YAML so the effect of an unsaved change is
   * visible. The inherited rules always come from the stored sets.
   */
  loadPreview(): void {
    if (!this.idTradingCalendarRuleSet) {
      return;
    }
    this.previewError = null;
    this.tradingCalendarRuleSetService
      .previewClosures(this.idTradingCalendarRuleSet, this.previewYear, this.ruleYamlValue)
      .subscribe({
        next: (closures) => (this.previewClosures = closures),
        error: () => (this.previewError = this.translateService.instant('PREVIEW_CLOSURES_FAILED'))
      });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): TradingCalendarRuleSet {
    const ruleSet = new TradingCalendarRuleSet();
    this.copyFormToPublicBusinessObject(ruleSet, this.callParam, this.proposeChangeEntityWithEntity);
    ruleSet.mic = ruleSet.mic ? ruleSet.mic.toUpperCase() : null;
    ruleSet.ruleYaml = this.ruleYamlValue;
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
