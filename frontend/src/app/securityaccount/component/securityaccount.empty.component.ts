import { Component, Injectable, ChangeDetectionStrategy } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

@Injectable()
@Component({
  template: `<h4>{{ 'BEFORE_FIRST_SECURITYACCOUNT' | translate }}</h4>`,
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [TranslatePipe]
})
export class SecurityaccountEmptyComponent {}
