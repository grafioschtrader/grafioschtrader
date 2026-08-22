import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { ApplicationInfo } from '../../services/actuator.service';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

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
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [TranslateModule]
})
export class ApplicationInfoComponent {
  @Input() applicationInfo: ApplicationInfo;

  constructor(public translateService: TranslateService) {}
}
