import { Routes } from '@angular/router';
import { mobileAuthGuard } from './service/mobile.auth.guard';

/** Start page after sign-in. */
export const MOBILE_HOME = '/tabs/dashboard';

/** Routes of the mobile application; the hash location keeps every deep link inside index.html. */
export const MOBILE_ROUTES: Routes = [
  { path: '', redirectTo: 'tabs/dashboard', pathMatch: 'full' },
  { path: 'login', loadComponent: () => import('./page/login.page').then((m) => m.LoginPage) },
  {
    path: 'tabs',
    canActivate: [mobileAuthGuard],
    loadComponent: () => import('./page/tabs.page').then((m) => m.TabsPage),
    children: [
      { path: 'dashboard', loadComponent: () => import('./page/dashboard.page').then((m) => m.DashboardPage) },
      { path: 'watchlists', loadComponent: () => import('./watchlist/watchlist.page').then((m) => m.WatchlistPage) },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
    ]
  },
  {
    path: 'widget/:instanceId',
    canActivate: [mobileAuthGuard],
    loadComponent: () => import('./page/widget.page').then((m) => m.WidgetPage)
  },
  { path: '**', redirectTo: 'tabs/dashboard' }
];
