import {Component, Input} from '@angular/core';
import {ApplicationInfo} from '../../service/actuator.service';
import {TranslateService} from '@ngx-translate/core';

@Component({
  selector: 'application-info',
  template: `
    <div *ngIf="applicationInfo else serverDown">
      <h3>{{applicationInfo.name}}</h3>
      Version: {{applicationInfo.version}}
    </div>
    <ng-template #serverDown>
      <div class="alert alert-danger">
        <h3>{{'BACKEND_SERVER_DOWN' | translate}}</h3>
      </div>
    </ng-template>
  `,
})
export class ApplicationInfoComponent {
  @Input() applicationInfo: ApplicationInfo;

  constructor(public translateService: TranslateService) {
  }

}
