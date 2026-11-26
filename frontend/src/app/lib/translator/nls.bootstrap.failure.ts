/** Static colors of the overlay; it can appear before any component exists, so it cannot rely on the theme tokens. */
interface NlsFailurePalette {
  page: string;
  card: string;
  border: string;
  text: string;
  buttonText: string;
  button: string;
  buttonBorder: string;
}

const NLS_FAILURE_PALETTES: Record<'light' | 'dark', NlsFailurePalette> = {
  light: {
    page: '#f5f5f5',
    card: '#fff',
    border: '#d0d0d0',
    text: '#222',
    buttonText: '#fff',
    button: '#0b5ed7',
    buttonBorder: '#0a58ca'
  },
  dark: {
    page: '#18181b',
    card: '#27272a',
    border: '#3f3f46',
    text: '#f4f4f5',
    buttonText: '#fff',
    button: '#2563eb',
    buttonBorder: '#3b82f6'
  }
};

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
  // The pre-paint script of index.html has already set the scheme on <html>.
  const p = NLS_FAILURE_PALETTES[document.documentElement.getAttribute('data-bs-theme') === 'dark' ? 'dark' : 'light'];
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
    'background:' + p.page,
    'color:' + p.text,
    "font-family:system-ui,-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif"
  ].join(';');

  const panel = document.createElement('div');
  panel.style.cssText = [
    'max-width:34rem',
    'margin:1rem',
    'padding:1.75rem 2rem',
    'background:' + p.card,
    'border:1px solid ' + p.border,
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
    'color:' + p.buttonText,
    'background:' + p.button,
    'border:1px solid ' + p.buttonBorder,
    'border-radius:4px'
  ].join(';');
  button.addEventListener('click', () => (onRetry ? onRetry() : location.reload()));

  panel.append(heading, message, button);
  overlay.append(panel);
  document.body.append(overlay);
  button.focus();
}
