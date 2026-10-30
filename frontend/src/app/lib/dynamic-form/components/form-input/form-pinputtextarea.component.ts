import { Component } from '@angular/core';
import { BaseInputComponent } from '../base.input.component';
import { ReactiveFormsModule } from '@angular/forms';

import { Textarea } from '@openng/optimus-ui/textarea';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { FilterOutPipe } from '../../pipe/FilterOutPipe';

@Component({
  selector: 'form-pinputtextarea',
  template: `
    @let config = this.config();
    @let formConfig = this.formConfig();
    @let group = this.group();
    @let rev = notifier?.revision();
    <ng-container [formGroup]="group">
      <textarea
        pTextarea
        [rows]="config.textareaRows"
        [formControlName]="fieldName"
        [id]="fieldName"
        [class.required-input]="isRequired && !config.readonly"
        [readonly]="config.readonly"
        [disabled]="config.disabled ?? false"
        [maxlength]="config.maxLength ?? null"
        pTooltip="{{
          config.labelHelpText?.startsWith('*')
            ? config.labelHelpText.slice(1)
            : (config.labelKey + '_TOOLTIP' | translate | filterOut: config.labelKey + '_TOOLTIP')
        }}"
        #input
        class="form-control">
      </textarea>

      @if (config.contextMenuItems) {
        <p-contextMenu [target]="input" [model]="config.contextMenuItems" appendTo="body" />
      }
    </ng-container>
  `,
  imports: [ReactiveFormsModule, Textarea, ContextMenuModule, TooltipModule, TranslateModule, FilterOutPipe]
})
export class FormPInputTextareaComponent extends BaseInputComponent {}
