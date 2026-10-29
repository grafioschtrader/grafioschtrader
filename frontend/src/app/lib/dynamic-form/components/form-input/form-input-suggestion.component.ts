import { Component } from '@angular/core';
import { BaseInputComponent } from '../base.input.component';
import { ReactiveFormsModule } from '@angular/forms';
import { AutoCompleteModule } from '@openng/optimus-ui/autocomplete';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { FilterOutPipe } from '../../pipe/FilterOutPipe';

@Component({
  selector: 'form-input-suggestion',
  template: `
    @let config = this.config();
    @let formConfig = this.formConfig();
    @let group = this.group();
    @let rev = notifier?.revision();
    <ng-container [formGroup]="group">
      <p-autoComplete
        #input
        [id]="config.field"
        [formControlName]="config.field"
        [inputStyleClass]="'form-control ' + (isRequired ? 'required-input' : '')"
        class="p-autocomplete"
        [style]="{ width: '100%' }"
        [inputStyle]="{ width: '100%' }"
        [suggestions]="config.suggestions"
        (completeMethod)="callSuggestionsFN($event)"
        pTooltip="{{ config.labelKey + '_TOOLTIP' | translate | filterOut: config.labelKey + '_TOOLTIP' }}" />
    </ng-container>
  `,
  imports: [ReactiveFormsModule, AutoCompleteModule, TooltipModule, TranslateModule, FilterOutPipe]
})
export class FormInputSuggestionComponent extends BaseInputComponent {
  public callSuggestionsFN(event: { query: string }): void {
    this.config().suggestionsFN(event);
  }
}
