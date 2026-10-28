import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  template: `<h4>{{ 'BEFORE_FIRST_SECURITYACCOUNT' | translate }}</h4>`,
  imports: [TranslatePipe]
})
export class SecurityaccountEmptyComponent {}
