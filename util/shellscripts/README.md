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

### Update GT with a gtupdate.sh
The scripts in this directory simplify the updating of GT. For an **update** of GT execute the script `./gtupdate.sh` as user **grafioschtrader**. It can take a few minutes but also more than a quarter of an hour, depending on the performance of your system. It will to every thing which is needed for an update.