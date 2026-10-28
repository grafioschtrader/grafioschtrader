import { Component, input } from '@angular/core';
import { ApplicationInfo } from '../../services/actuator.service';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'application-info',
  template: `
    @let applicationInfoValue = applicationInfo();

    @if (applicationInfoValue) {
      <div>
        <h3>{{ applicationInfoValue.name }}</h3>
        Version: {{ applicationInfoValue.version }}
      </div>
    } @else {
      <div class="alert alert-danger">
        <h3>{{ 'BACKEND_SERVER_DOWN' | translate }}</h3>
      </div>
    }
  `,
  imports: [TranslateModule]
})
export class ApplicationInfoComponent {
  readonly applicationInfo = input<ApplicationInfo>(undefined);

  constructor(public translateService: TranslateService) {}
}
