import {Component, Input} from '@angular/core';
import {ApplicationInfo} from '../../services/actuator.service';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {CommonModule} from '@angular/common';

@Component({
  selector: 'application-info',
  template: `
    @if (applicationInfo) {
      <div>
        <h3>{{ applicationInfo.name }}</h3>
        Version: {{ applicationInfo.version }}
      </div>
    } @else {
      <div class="alert alert-danger">
        <h3>{{ 'BACKEND_SERVER_DOWN' | translate }}</h3>
      </div>
    }
  `,
  standalone: true,
  imports: [CommonModule, TranslateModule]
})
export class ApplicationInfoComponent {
  @Input() applicationInfo: ApplicationInfo;

  constructor(public translateService: TranslateService) {
  }

}
