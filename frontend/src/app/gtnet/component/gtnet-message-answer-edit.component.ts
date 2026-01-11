import {Component, Input, OnInit, AfterViewInit, OnDestroy} from '@angular/core';
import {SimpleEntityEditBase} from '../../lib/edit/simple.entity.edit.base';
import {
  getResponseCodesForRequest,
  GTNetMessageAnswer,
  GTNetMessageAnswerCallParam,
  REQUEST_CODES_FOR_AUTO_RESPONSE
} from '../model/gtnet.message.answer';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {HelpIds} from '../../lib/help/help.ids';
import {AppSettings} from '../../shared/app.settings';
import {GTNetMessageAnswerService} from '../service/gtnet.message.answer.service';
import {AppHelper} from '../../lib/helper/app.helper';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {Subscription} from 'rxjs';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormComponent} from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import {GTNetMessageCodeType} from '../model/gtnet.message';
import {MenuItem} from 'primeng/api';

/**
 * Type filter for context-aware variable filtering.
 */
type EvalExType = 'STRING' | 'NUMBER' | 'ANY';

/**
 * Context information about cursor position within an expression.
 */
interface CursorContext {
  insideFunction: boolean;
  functionName: string | null;
  parameterIndex: number;
  expectedType: EvalExType;
}

/**
 * Variables grouped by EvalEx data type.
 */
const STRING_VARIABLES = ['MyTimezone', 'RemoteTimezone', 'RemoteDomainRemoteName', 'Message'];
const NUMBER_VARIABLES = [
  'hour', 'dayOfWeek', 'dailyCount', 'dailyLimit',
  'MyDailyRequestLimit', 'MyMaxLimitLastPrice', 'MyMaxLimitHistorical',
  'RemoteDailyRequestLimit', 'RemoteMaxLimitLastPrice', 'RemoteMaxLimitHistorical',
  'TimezoneOffsetHours', 'TotalConnections', 'ConnectionsLastPrice', 'ConnectionsHistorical'
];

/**
 * Function parameter type mapping for context-aware filtering.
 * Maps function names to their parameter types by position.
 */
const FUNCTION_PARAM_TYPES: { [func: string]: EvalExType[] } = {
  'STR_STARTS_WITH': ['STRING', 'STRING'],
  'STR_ENDS_WITH': ['STRING', 'STRING'],
  'STR_CONTAINS': ['STRING', 'STRING'],
  'STR_MATCHES': ['STRING', 'STRING'],
  'STR_LENGTH': ['STRING'],
  'STR_LOWER': ['STRING'],
  'STR_UPPER': ['STRING'],
  'STR_TRIM': ['STRING'],
  'STR_LEFT': ['STRING', 'NUMBER'],
  'STR_RIGHT': ['STRING', 'NUMBER'],
  'STR_SUBSTRING': ['STRING', 'NUMBER', 'NUMBER']
};

/**
 * Edit component for GTNetMessageAnswer entities.
 * Allows configuration of automatic response rules for incoming GTNet messages.
 */
@Component({
  selector: 'gtnet-message-answer-edit',
  standalone: true,
  imports: [
    DialogModule,
    DynamicFormComponent,
    TranslateModule
  ],
  template: `
    <p-dialog header="{{'GT_NET_MESSAGE_ANSWER' | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '600px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `
})
export class GTNetMessageAnswerEditComponent extends SimpleEntityEditBase<GTNetMessageAnswer> implements OnInit {
  @Input() callParam: GTNetMessageAnswerCallParam;

  private requestMsgCodeSubscription: Subscription;
  private currentTypeFilter: EvalExType = 'ANY';
  private textareaEventsBound = false;

  constructor(translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    gtNetMessageAnswerService: GTNetMessageAnswerService) {
    super(HelpIds.HELP_GT_NET, AppHelper.toUpperCaseWithUnderscore(AppSettings.GT_NET_MESSAGE_ANSWER), translateService, gps,
      messageToastService, gtNetMessageAnswerService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF('requestMsgCode', true),
      DynamicFieldHelper.createFieldSelectStringHeqF('responseMsgCode', true),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'priority', true, 1, 99,
        {defaultValue: 1}),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('responseMsgConditional', 256, false,
        {inputWidth: 500, contextMenuItems: this.buildContextMenuItems()}),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('responseMsgMessage', 1000, false,
        {inputWidth: 500}),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'waitDaysApply', true, 0, 365,
        {defaultValue: 0}),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    // Setup request code options (only RR codes that support auto-response)
    this.configObject.requestMsgCode.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
      this.translateService, GTNetMessageCodeType,
      REQUEST_CODES_FOR_AUTO_RESPONSE.map(code => GTNetMessageCodeType[code]), false);

    // Setup response code options (empty initially, updated when request code changes)
    this.configObject.responseMsgCode.valueKeyHtmlOptions = [];

    // Subscribe to request code changes to update response code options
    this.requestMsgCodeSubscription = this.configObject.requestMsgCode.formControl.valueChanges.subscribe(
      (requestCode: string) => {
        this.updateResponseCodeOptions(requestCode);
      }
    );

    // Load existing entity or set defaults
    const entity = this.callParam?.gtNetMessageAnswer ?? new GTNetMessageAnswer();
    if (entity.requestMsgCode) {
      // Convert numeric code to string name for form
      const requestCodeName = typeof entity.requestMsgCode === 'number'
        ? GTNetMessageCodeType[entity.requestMsgCode]
        : entity.requestMsgCode;
      this.updateResponseCodeOptions(requestCodeName as string);
    }
    this.form.transferBusinessObjectToForm(entity);

    // Bind cursor tracking for context-aware menu filtering
    this.bindTextareaEvents();
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): GTNetMessageAnswer {
    const entity = new GTNetMessageAnswer();
    if (this.callParam?.gtNetMessageAnswer) {
      Object.assign(entity, this.callParam.gtNetMessageAnswer);
    }

    // Send enum names as strings - backend Jackson will deserialize to GTNetMessageCodeType
    entity.requestMsgCode = value['requestMsgCode'];
    entity.responseMsgCode = value['responseMsgCode'];
    entity.priority = value['priority'];
    entity.responseMsgConditional = value['responseMsgConditional'] || null;
    entity.responseMsgMessage = value['responseMsgMessage'] || null;
    entity.waitDaysApply = value['waitDaysApply'];

    return entity;
  }

  override onHide(event): void {
    this.requestMsgCodeSubscription?.unsubscribe();
    this.textareaEventsBound = false;
    this.currentTypeFilter = 'ANY';
    super.onHide(event);
  }

  /**
   * Updates the response code options based on the selected request code.
   */
  private updateResponseCodeOptions(requestCodeName: string): void {
    if (!requestCodeName) {
      this.configObject.responseMsgCode.valueKeyHtmlOptions = [];
      return;
    }

    const requestCode = GTNetMessageCodeType[requestCodeName as keyof typeof GTNetMessageCodeType];
    const responseCodes = getResponseCodesForRequest(requestCode);

    this.configObject.responseMsgCode.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
      this.translateService, GTNetMessageCodeType,
      responseCodes.map(code => GTNetMessageCodeType[code]), false);
  }

  /**
   * Builds the context menu items for the responseMsgConditional textarea.
   * Provides variable insertion for EvalEx expressions grouped by category.
   * Filters variables based on the expected type at cursor position.
   */
  private buildContextMenuItems(typeFilter: EvalExType = 'ANY'): MenuItem[] {
    const showVar = (varName: string): boolean => {
      if (typeFilter === 'ANY') return true;
      if (typeFilter === 'STRING') return STRING_VARIABLES.includes(varName);
      if (typeFilter === 'NUMBER') return NUMBER_VARIABLES.includes(varName);
      return true;
    };

    const createVarItem = (varName: string, descKey: string, type: EvalExType): MenuItem | null => {
      if (!showVar(varName)) return null;
      return {
        label: varName,
        title: this.translateService.instant(descKey) + ` (${type})`,
        command: () => this.insertVariable(varName)
      };
    };

    const filterItems = (items: (MenuItem | null)[]): MenuItem[] =>
      items.filter((item): item is MenuItem => item !== null);

    const menuItems: MenuItem[] = [];

    // My Server variables
    const myServerItems = filterItems([
      createVarItem('MyDailyRequestLimit', 'MY_DAILY_REQUEST_LIMIT_DESC', 'NUMBER'),
      createVarItem('MyTimezone', 'MY_TIMEZONE_DESC', 'STRING'),
      createVarItem('MyMaxLimitLastPrice', 'MY_MAX_LIMIT_LAST_PRICE_DESC', 'NUMBER'),
      createVarItem('MyMaxLimitHistorical', 'MY_MAX_LIMIT_HISTORICAL_DESC', 'NUMBER')
    ]);
    if (myServerItems.length > 0) {
      menuItems.push({label: this.translateService.instant('EVALEX_MY_SERVER'), items: myServerItems});
    }

    // Remote Server variables
    const remoteServerItems = filterItems([
      createVarItem('RemoteDailyRequestLimit', 'REMOTE_DAILY_REQUEST_LIMIT_DESC', 'NUMBER'),
      createVarItem('RemoteTimezone', 'REMOTE_TIMEZONE_DESC', 'STRING'),
      createVarItem('RemoteDomainRemoteName', 'REMOTE_DOMAIN_REMOTE_NAME_DESC', 'STRING'),
      createVarItem('RemoteMaxLimitLastPrice', 'REMOTE_MAX_LIMIT_LAST_PRICE_DESC', 'NUMBER'),
      createVarItem('RemoteMaxLimitHistorical', 'REMOTE_MAX_LIMIT_HISTORICAL_DESC', 'NUMBER')
    ]);
    if (remoteServerItems.length > 0) {
      menuItems.push({label: this.translateService.instant('EVALEX_REMOTE_SERVER'), items: remoteServerItems});
    }

    // Message variables
    const messageItems = filterItems([
      createVarItem('Message', 'MESSAGE_DESC', 'STRING')
    ]);
    if (messageItems.length > 0) {
      menuItems.push({label: this.translateService.instant('EVALEX_MESSAGE'), items: messageItems});
    }

    // Connections variables
    const connectionItems = filterItems([
      createVarItem('TotalConnections', 'TOTAL_CONNECTIONS_DESC', 'NUMBER'),
      createVarItem('ConnectionsLastPrice', 'CONNECTIONS_LAST_PRICE_DESC', 'NUMBER'),
      createVarItem('ConnectionsHistorical', 'CONNECTIONS_HISTORICAL_DESC', 'NUMBER')
    ]);
    if (connectionItems.length > 0) {
      menuItems.push({label: this.translateService.instant('EVALEX_CONNECTIONS'), items: connectionItems});
    }

    // Calculated variables
    const calculatedItems = filterItems([
      createVarItem('TimezoneOffsetHours', 'TIMEZONE_OFFSET_HOURS_DESC', 'NUMBER'),
      createVarItem('hour', 'HOUR_DESC', 'NUMBER'),
      createVarItem('dayOfWeek', 'DAY_OF_WEEK_DESC', 'NUMBER'),
      createVarItem('dailyCount', 'DAILY_COUNT_DESC', 'NUMBER'),
      createVarItem('dailyLimit', 'DAILY_LIMIT_DESC', 'NUMBER')
    ]);
    if (calculatedItems.length > 0) {
      menuItems.push({label: this.translateService.instant('EVALEX_CALCULATED'), items: calculatedItems});
    }

    // String Functions (always shown, type filter doesn't apply to functions)
    if (typeFilter === 'ANY') {
      menuItems.push({
        label: this.translateService.instant('EVALEX_STRING_FUNCTIONS'),
        items: [
          {label: 'STR_STARTS_WITH(?, ?)', title: this.translateService.instant('STR_STARTS_WITH_DESC'),
            command: () => this.insertFunction('STR_STARTS_WITH(?, ?)')},
          {label: 'STR_ENDS_WITH(?, ?)', title: this.translateService.instant('STR_ENDS_WITH_DESC'),
            command: () => this.insertFunction('STR_ENDS_WITH(?, ?)')},
          {label: 'STR_CONTAINS(?, ?)', title: this.translateService.instant('STR_CONTAINS_DESC'),
            command: () => this.insertFunction('STR_CONTAINS(?, ?)')},
          {label: 'STR_MATCHES(?, ?)', title: this.translateService.instant('STR_MATCHES_DESC'),
            command: () => this.insertFunction('STR_MATCHES(?, ?)')},
          {label: 'STR_LENGTH(?)', title: this.translateService.instant('STR_LENGTH_DESC'),
            command: () => this.insertFunction('STR_LENGTH(?)')},
          {label: 'STR_LOWER(?)', title: this.translateService.instant('STR_LOWER_DESC'),
            command: () => this.insertFunction('STR_LOWER(?)')},
          {label: 'STR_UPPER(?)', title: this.translateService.instant('STR_UPPER_DESC'),
            command: () => this.insertFunction('STR_UPPER(?)')},
          {label: 'STR_TRIM(?)', title: this.translateService.instant('STR_TRIM_DESC'),
            command: () => this.insertFunction('STR_TRIM(?)')},
          {label: 'STR_LEFT(?, ?)', title: this.translateService.instant('STR_LEFT_DESC'),
            command: () => this.insertFunction('STR_LEFT(?, ?)')},
          {label: 'STR_RIGHT(?, ?)', title: this.translateService.instant('STR_RIGHT_DESC'),
            command: () => this.insertFunction('STR_RIGHT(?, ?)')},
          {label: 'STR_SUBSTRING(?, ?, ?)', title: this.translateService.instant('STR_SUBSTRING_DESC'),
            command: () => this.insertFunction('STR_SUBSTRING(?, ?, ?)')}
        ]
      });
    }

    return menuItems;
  }

  /**
   * Inserts a variable name at the current cursor position in the responseMsgConditional textarea.
   */
  private insertVariable(varName: string): void {
    const textarea = document.getElementById('responseMsgConditional') as HTMLTextAreaElement;
    if (textarea) {
      const start = textarea.selectionStart;
      const end = textarea.selectionEnd;
      const currentValue = this.configObject.responseMsgConditional.formControl.value || '';
      const newValue = currentValue.substring(0, start) + varName + currentValue.substring(end);
      this.configObject.responseMsgConditional.formControl.setValue(newValue);
      // Restore cursor position after variable
      setTimeout(() => {
        textarea.selectionStart = textarea.selectionEnd = start + varName.length;
        textarea.focus();
      });
    }
  }

  /**
   * Inserts a function template at the current cursor position.
   * Places cursor at the first '?' placeholder for easy variable insertion.
   */
  private insertFunction(template: string): void {
    const textarea = document.getElementById('responseMsgConditional') as HTMLTextAreaElement;
    if (textarea) {
      const start = textarea.selectionStart;
      const end = textarea.selectionEnd;
      const currentValue = this.configObject.responseMsgConditional.formControl.value || '';
      const newValue = currentValue.substring(0, start) + template + currentValue.substring(end);
      this.configObject.responseMsgConditional.formControl.setValue(newValue);
      setTimeout(() => {
        // Position cursor at first placeholder '?'
        const placeholderPos = newValue.indexOf('?', start);
        if (placeholderPos >= 0) {
          textarea.selectionStart = placeholderPos;
          textarea.selectionEnd = placeholderPos + 1;
        } else {
          textarea.selectionStart = textarea.selectionEnd = start + template.length;
        }
        textarea.focus();
      });
    }
  }

  /**
   * Analyzes cursor position within an expression to determine the context.
   * Detects if cursor is inside a function call and which parameter position.
   */
  private getCursorContext(text: string, cursorPos: number): CursorContext {
    const beforeCursor = text.substring(0, cursorPos);

    // Find unclosed function call by counting parentheses
    let parenDepth = 0;
    let funcStart = -1;
    let commaCount = 0;

    for (let i = beforeCursor.length - 1; i >= 0; i--) {
      const char = beforeCursor[i];
      if (char === ')') {
        parenDepth++;
      } else if (char === '(') {
        if (parenDepth === 0) {
          funcStart = i;
          break;
        }
        parenDepth--;
      } else if (char === ',' && parenDepth === 0) {
        commaCount++;
      }
    }

    if (funcStart === -1) {
      return {insideFunction: false, functionName: null, parameterIndex: 0, expectedType: 'ANY'};
    }

    // Extract function name (word before opening paren)
    const funcMatch = beforeCursor.substring(0, funcStart).match(/(\w+)\s*$/);
    const functionName = funcMatch ? funcMatch[1] : null;

    // Determine expected type based on function and parameter index
    const expectedType = this.getExpectedType(functionName, commaCount);

    return {insideFunction: true, functionName, parameterIndex: commaCount, expectedType};
  }

  /**
   * Determines the expected EvalEx type for a function parameter at the given position.
   */
  private getExpectedType(functionName: string | null, paramIndex: number): EvalExType {
    if (!functionName) return 'ANY';

    const funcUpper = functionName.toUpperCase();
    const types = FUNCTION_PARAM_TYPES[funcUpper];
    if (!types || paramIndex >= types.length) return 'ANY';
    return types[paramIndex];
  }

  /**
   * Handles cursor position changes in the textarea to update context menu filtering.
   */
  private onTextareaInput(): void {
    const textarea = document.getElementById('responseMsgConditional') as HTMLTextAreaElement;
    if (textarea) {
      const ctx = this.getCursorContext(textarea.value, textarea.selectionStart);
      if (ctx.expectedType !== this.currentTypeFilter) {
        this.currentTypeFilter = ctx.expectedType;
        // Rebuild menu items with new filter
        this.configObject.responseMsgConditional.contextMenuItems = this.buildContextMenuItems(this.currentTypeFilter);
      }
    }
  }

  /**
   * Binds event listeners to the textarea for cursor tracking.
   */
  private bindTextareaEvents(): void {
    if (this.textareaEventsBound) return;

    setTimeout(() => {
      const textarea = document.getElementById('responseMsgConditional') as HTMLTextAreaElement;
      if (textarea) {
        const handler = () => this.onTextareaInput();
        textarea.addEventListener('click', handler);
        textarea.addEventListener('keyup', handler);
        textarea.addEventListener('contextmenu', handler);
        this.textareaEventsBound = true;
      }
    }, 100);
  }
}
