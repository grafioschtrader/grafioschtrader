import {Component} from '@angular/core';
import {BaseInputComponent} from '../base.input.component';
import {ReactiveFormsModule} from '@angular/forms';
import {AutoCompleteModule} from 'primeng/autocomplete';
import {TooltipModule} from 'primeng/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {FilterOutPipe} from '../../pipe/FilterOutPipe';

@Component({
    selector: 'form-input-suggestion',
    template: `
    <ng-container [formGroup]="group">
      <p-autoComplete #input
                      [id]="config.field" [formControlName]="config.field"
                      [inputStyleClass]="'form-control ' + (isRequired? 'required-input': '')"
                      class="p-autocomplete"
                      [style]="{'width':'100%'}" [inputStyle]="{'width':'100%'}"
                      [suggestions]="config.suggestions" (completeMethod)="callSuggestionsFN($event)"
                      pTooltip="{{config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP'}}">
      </p-autoComplete>
    </ng-container>
  `,
    imports: [
        ReactiveFormsModule,
        AutoCompleteModule,
        TooltipModule,
        TranslateModule,
        FilterOutPipe
    ],
    standalone: true
})

export class FormInputSuggestionComponent extends BaseInputComponent {

  public callSuggestionsFN(event): void {
    this.config.suggestionsFN(event);
  }

}
