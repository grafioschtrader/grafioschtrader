/**
 * Blocking error screen shown when the user interface texts cannot be loaded from the server.
 *
 * Since GitHub issue #214 the backend is the only source of those texts, so a failed load leaves nothing to render:
 * every label, button and menu entry would appear as a raw translation key, which looks like a broken application
 * rather than an unreachable server. The application initializer therefore stops bootstrapping and calls this instead.
 *
 * Deliberately plain DOM and not an Angular component: it has to render at a moment when bootstrapping has been
 * halted on purpose. Deliberately bilingual and hard coded: translations are precisely what is missing, so the one
 * screen that reports their absence cannot itself depend on them.
 */
export function showNlsBootstrapFailure(onRetry?: () => void): void {
  const overlay = document.createElement('div');
  overlay.id = 'gt-nls-failure';
  overlay.setAttribute('role', 'alertdialog');
  overlay.style.cssText = [
    'position:fixed',
    'inset:0',
    'z-index:99999',
    'display:flex',
    'align-items:center',
    'justify-content:center',
    'background:#f5f5f5',
    'color:#222',
    "font-family:system-ui,-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif"
  ].join(';');

  const panel = document.createElement('div');
  panel.style.cssText = [
    'max-width:34rem',
    'margin:1rem',
    'padding:1.75rem 2rem',
    'background:#fff',
    'border:1px solid #d0d0d0',
    'border-radius:6px',
    'box-shadow:0 2px 10px rgba(0,0,0,.12)',
    'text-align:center',
    'line-height:1.5'
  ].join(';');

  const heading = document.createElement('h2');
  heading.style.cssText = 'margin:0 0 .75rem;font-size:1.25rem';
  heading.textContent = 'Server unavailable — Server nicht erreichbar';

  const message = document.createElement('p');
  message.style.cssText = 'margin:0 0 1.5rem';
  message.textContent =
    'Grafioschtrader could not load its texts from the server. Please check that the server is ' +
    'running and try again.\n\nGrafioschtrader konnte seine Texte nicht vom Server laden. Bitte prüfen Sie, ob der ' +
    'Server läuft, und versuchen Sie es erneut.';
  message.style.whiteSpace = 'pre-line';

  const button = document.createElement('button');
  button.type = 'button';
  button.textContent = 'Retry — Erneut versuchen';
  button.style.cssText = [
    'padding:.55rem 1.4rem',
    'font-size:1rem',
    'cursor:pointer',
    'color:#fff',
    'background:#0b5ed7',
    'border:1px solid #0a58ca',
    'border-radius:4px'
  ].join(';');
  button.addEventListener('click', () => (onRetry ? onRetry() : location.reload()));

  panel.append(heading, message, button);
  overlay.append(panel);
  document.body.append(overlay);
  button.focus();
}
