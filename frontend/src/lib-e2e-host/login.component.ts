import {Component} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Router} from '@angular/router';
import {LoginService} from '../app/lib/login/service/log-in.service';

/** Deliberately small host login: authentication behavior remains in the reusable LoginService. */
@Component({
  selector: 'lib-e2e-login',
  standalone: true,
  imports: [FormsModule],
  template: `
    <form class="container mt-4" (ngSubmit)="submit()">
      <h2>Sign in</h2>
      <label for="email">Email</label>
      <input id="email" class="form-control" name="email" type="email" [(ngModel)]="email" required>
      <label for="password" class="mt-2">Password</label>
      <input id="password" class="form-control" name="password" type="password" [(ngModel)]="password" required>
      <button class="btn btn-primary mt-3" type="submit">Sign in</button>
      @if (error) {
        <div class="alert alert-danger mt-3">{{ error }}</div>
      }
    </form>
  `,
})
export class LibE2ELoginComponent {
  email = '';
  password = '';
  error = '';

  constructor(private loginService: LoginService, private router: Router) {
  }

  submit(): void {
    this.error = '';
    this.loginService.login(this.email, this.password).subscribe({
      next: response => {
        this.loginService.afterSuccessfulLogin(response.headers.get('x-auth-token'), (response as any).body);
        this.router.navigate(['/mail/mailsendrecv']);
      },
      error: () => this.error = 'Login failed',
    });
  }
}
