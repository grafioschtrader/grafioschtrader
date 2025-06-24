## Purpose
The purpose of this module is to perform integration tests on `grafiosch-base` and `grafiosch-server-base`. It also shows which classes must be extended in order for an application to be built on these modules.

## Why not in `grafiosch-server-base`?
Certain entities, such as `Tenant`, are defined abstractly as `TenantBase`. We are certain that this entity will be expanded depending on the application. There are, of course, different ways to expand a JPA entity. However, we wanted to avoid creating an additional table. Accordingly, the implementation of the repository is also kept abstract. This must also be expanded accordingly.
