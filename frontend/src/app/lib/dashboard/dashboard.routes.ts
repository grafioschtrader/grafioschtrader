import { inject } from '@angular/core';
import { CanActivateFn, Routes, Router } from '@angular/router';
import { GlobalparameterService } from '../services/globalparameter.service';
import { BaseSettings } from '../base.settings';
import { DashboardComponent } from './dashboard.component';
import { authGuard } from '../services/guards.definition';

const dashboardGuard: CanActivateFn = () =>
  inject(GlobalparameterService).useDashboard()
    ? true
    : inject(Router).createUrlTree(['/' + BaseSettings.MAINVIEW_KEY]);

/** Guard a componentless parent because Angular does not allow canMatch on a redirect route. */
export const DASHBOARD_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canMatch: [() => inject(GlobalparameterService).useDashboard()],
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: '/' + BaseSettings.MAINVIEW_KEY + '/' + BaseSettings.DASHBOARD_KEY
      }
    ]
  },
  {
    path: BaseSettings.DASHBOARD_KEY,
    component: DashboardComponent,
    canActivate: [authGuard, dashboardGuard],
    canDeactivate: [(component: DashboardComponent) => component.canLeave()]
  }
];
