## Simplify installation and updating
This **shell scripts** simplify the installation of GT and reduce the update to a few steps.

### Install and setup scripts and enviroment
To use the scripts properly some steps must be done.

#### Install the scripts
Copy the scripts to the home directory of user **grafioschtrader** and make them executable.
```
cp ~/build/grafioschtrader/util/shellscripts/*.sh .
chmod +x *.sh
```
Please adjust the settings of `gtvar.sh` to your needs.

#### Systemd for GT
The user **grafioschtrader** must be able to start and stop the **systemd** for an update. The configuration file `/etc/sudoers.d/grafioschtrader` is required with the follow content:
```
Cmnd_Alias MYSERVICE = \
    /bin/systemctl stop grafioschtrader.service, \
    /bin/systemctl start grafioschtrader.service

grafioschtrader ALL = (root) NOPASSWD: MYSERVICE
```

### Times of the daily data download
`gtcronrandom.sh` gives this installation its own times for the daily price and dividend download, so
that not every Grafioschtrader instance queries the free data providers in the same minute. It is
called by `gtupbackend.sh` out of the updated clone - it does not have to be copied to the home
directory - and moves the whole morning chain to a random slot between 05:00 and 08:00 local time,
but only while all of its properties are still at their delivered values. A time you set yourself is
never overwritten; `GT_CRON_RANDOMIZE=off` switches the mechanism off. The background is described in
[backend/README.md](../../backend/README.md#times-of-the-daily-data-download).

### Update GT with a gtupdate.sh
The scripts in this directory simplify the updating of GT. For an **update** of GT execute the script `./gtupdate.sh` as user **grafioschtrader**. It can take a few minutes but also more than a quarter of an hour, depending on the performance of your system. It will to every thing which is needed for an update.

Build failures return a non-zero status. The backend keeps the deployed JAR until Maven succeeds and one
non-empty executable JAR is available. On a build failure it starts the retained JAR again. A failure to start
the service is reported separately; inspect the service journal and `/var/log/grafioschtrader.log`.

For the installer's first build, both component helpers accept `GT_INSTALL_BUILD_ONLY=1`. They publish their
artifacts without starting or stopping the service, including on failure; the installer owns the first-start
and migration boundary. Normal `gtupdate.sh` runs keep their existing service behavior. Backend artifacts use
a private umask, while frontend assets remain readable by the web server.

The frontend stages and checks its output before replacing GT's files. Other sites under the document root
are left alone. `basehref` must be a non-empty relative path without `..`, and its resolved destination must
stay beneath `docroot`. Only the GT directory needs to belong to the GT user; when its parent is not writable,
temporary staging takes place inside the GT directory. Publication preserves the directory itself and restores
its old contents if a file move fails. This is not an atomic switch for concurrent HTTP requests.

Sequential builds stop after a failed frontend build. Parallel builds wait for both jobs and report failure
if either fails, retaining a successfully deployed component even if the other component failed. The parallel
logs are `frontbuild.log` and `backbuild.log` under `builddir`. Restoring application files never rolls back
database migrations.

The shipped launcher now uses `exec` and logs stderr. Existing installations keep their own launcher, so apply
that change manually while retaining the local Jasypt password, Java path and heap settings.

Likewise, `merger.sh` preserves an installation's existing database initialization value. To adopt the combined
UTC/collation initialization on an existing host, set this value in its `application.properties` before updating:

```properties
spring.datasource.hikari.connection-init-sql=SET time_zone = '+00:00' /*M!110200 , character_set_collations = 'utf8mb3=utf8mb3_general_ci,utf8mb4=utf8mb4_general_ci' */
```

Remove any conflicting override of this key from local configuration. It affects new database connections and
future table creation; it does not repair collations of existing columns. Fresh configurations receive the
combined value from the shipped template.

The [prerequisite checks](../installer/test/README.md) cover failure recovery and disposable-database acceptance.
