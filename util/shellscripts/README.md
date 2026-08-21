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