This Snap version of Girinoscope requires explicit permissions to access serial ports.
To do so, open the Girinoscope page in the Snap Store software,
click the permission button and give serial port access to the installed application.
If it is a simple checkbox (displayed as a slide to unlock button), it should be ok.
However, if it is a combobox with no choices, you need to enable the hotplug support for it to work as intended:

First, check your _snapd_ version.

``` bash
apt-cache policy snapd
```

If the installed version is greater or equal to 2.39,
you can then enable the hotplug support.

``` bash
sudo snap set system experimental.hotplug=true
sudo systemctl restart snapd
```

In case the serial port still cannot be opened, you can try to connect it manually to the application:

``` bash
snap connect girinoscope:serial-port :pl2303serialport
```

Mine was called `pl2303serialport` (not the best adapter for the job since it lacks the DTR/RTS wire).
To know the exact name of your serial port and its connection status,
your can query the available connections:

``` bash
snap connections system | grep serial
```

Finally, if your _snapd_ version is too old or you don’t want to enable the experimental hotplug support,
you could also install the Snap in _devmode_.
The application won’t be confined anymore though.

``` bash
snap install girinoscope --devmode
