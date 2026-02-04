import {Component, Injectable} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';

@Injectable()
@Component({
  template: `<h4>{{'BEFORE_FIRST_SECURITYACCOUNT' | translate}}</h4>`,
  standalone: true,
  imports: [TranslatePipe]
})
export class SecurityaccountEmptyComponent {

}
