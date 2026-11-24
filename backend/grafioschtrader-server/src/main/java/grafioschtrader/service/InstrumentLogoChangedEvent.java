package grafioschtrader.service;

/**
 * Spring application event published whenever an {@link grafioschtrader.entities.InstrumentLogo} is persisted, so
 * {@link InstrumentLogoService} invalidates its in-memory metadata snapshot without introducing a circular bean
 * dependency with the repository implementation.
 */
public record InstrumentLogoChangedEvent(Integer idInstrumentLogo) {
}
