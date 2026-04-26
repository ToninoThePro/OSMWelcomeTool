# Roadmap Dati & Networking

Backlog essenziale e allineato alla codebase corrente.

## Priorita attive

- [ ] Gestire rate limiting/retry API OSM in modo esplicito (429, timeout).
- [ ] Valutare cache runtime guidata da `settings.cacheEnabled` (oggi flag solo salvata).
- [ ] Migliorare sync incrementale usando `lastKnownChangesetId` dove possibile.
- [ ] Preparare export semplice utenti accolti (CSV/JSON).
