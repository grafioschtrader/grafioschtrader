import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FormField } from '@angular/forms/signals';
import { TranslateModule } from '@ngx-translate/core';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { Textarea } from '@openng/optimus-ui/textarea';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { FilterOutPipe } from '../../pipe/FilterOutPipe';
import { SignalBaseInputComponent } from './signal-base-input.component';
import { SignalValueBridgeDirective } from './signal-value-bridge.directive';

/**
 * Optimus textarea of a signal dynamic form. It goes through the SignalValueBridgeDirective as well: the pTextarea
 * directive subscribes to `valueChanges` of its NgControl, which the NgControl stand-in of `[formField]` lacks.
 */
@Component({
  selector: 'signal-form-pinputtextarea',
  template: `
    @let config = this.view();
    <span signalValueBridge #bridge="signalValueBridge" [formField]="field()" style="display: contents">
      <textarea
        pTextarea
        #input
        [ngModel]="bridge.value()"
        (ngModelChange)="bridge.value.set($event)"
        [ngModelOptions]="{ standalone: true }"
        [disabled]="bridge.disabled()"
        [readonly]="bridge.readonly()"
        [attr.maxlength]="bridge.maxLength() ?? null"
        (blur)="bridge.touch.emit()"
        [rows]="config.textareaRows"
        [id]="fieldName"
        [class.required-input]="isRequired()"
        pTooltip="{{
          config.labelHelpText?.startsWith('*')
            ? config.labelHelpText.slice(1)
            : (config.labelKey + '_TOOLTIP' | translate | filterOut: config.labelKey + '_TOOLTIP')
        }}"
        class="form-control">
      </textarea>
    </span>

    @if (config.contextMenuItems) {
      <p-contextMenu [target]="input" [model]="config.contextMenuItems" appendTo="body" />
    }
  `,
  imports: [
    FormField,
    FormsModule,
    SignalValueBridgeDirective,
    Textarea,
    ContextMenuModule,
    TooltipModule,
    TranslateModule,
    FilterOutPipe
  ]
})
export class SignalFormPInputTextareaComponent extends SignalBaseInputComponent<string> {}
