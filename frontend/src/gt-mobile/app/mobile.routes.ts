import { Routes } from '@angular/router';
import { mobileAuthGuard } from './service/mobile.auth.guard';

/** Routes of the mobile application; the hash location keeps every deep link inside index.html. */
export const MOBILE_ROUTES: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'login', loadComponent: () => import('./page/login.page').then((m) => m.LoginPage) },
  {
    path: 'dashboard',
    canActivate: [mobileAuthGuard],
    loadComponent: () => import('./page/dashboard.page').then((m) => m.DashboardPage)
  },
  {
    path: 'widget/:instanceId',
    canActivate: [mobileAuthGuard],
    loadComponent: () => import('./page/widget.page').then((m) => m.WidgetPage)
  },
  { path: '**', redirectTo: 'dashboard' }
];
