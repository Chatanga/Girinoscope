This confined version of Girinoscope has trouble accessing the serial ports.
It is not yet clear to me what should I do and what is supported at this time.
There are however 2 workarounds which could be applied to fix this issue:

## 1) Quick and dirty

Simply install the Snap in _devmode_. The application won’t be confined anymore though.
Confinment is not the only benefit of a Snap, but it’s unfortunate to have to leave it aside.

``` bash
snap install girinoscope --devmode
```

## 2) Somewhat better, but a bit convoluted

This option is better — the application will be kept confined —, but a bit convoluted.
It relies on the [hotplug support](https://snapcraft.io/docs/hotplug-support/) for USB serial adapters
which is still under devlopment.

Check your snapd version. The installed version need to be >= 2.39.

``` bash
apt-cache policy snapd
```

Enable hotplug support:

``` bash
sudo snap set system experimental.hotplug=true
sudo systemctl restart snapd
```

Plug your serial to USB adaptor and get its name:

``` bash
snap connections system | grep serial
```

Manually connect it (mine was called 'pl2303serialport') to the application:

``` bash
snap connect girinoscope:serial-port :pl2303serialport
```

The updated connections should display something like this:

``` bash
serial-port               girinoscope:serial-port                    :pl2303serialport          manual
```

