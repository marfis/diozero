---
parent: Internals
nav_order: 5
permalink: /internals/bbbsetup.html
redirect_from:
  - /en/latest/BeagleBoneBlackSetup/index.html
  - /en/stable/BeagleBoneBlackSetup/index.html
---

# BeagleBone Green and Black Setup
{: .no_toc }

General instructions for getting up and running with a BeagleBone Green or Black.

[Useful site](http://elinux.org/BeagleBoardDebian) for running Debian on BeagleBone boards.
[Google Group](https://groups.google.com/forum/#!categories/beagleboard/beaglebone-black).

## Sections
{: .no_toc .text-delta }

1. TOC
{:toc}

## Install and Boot from SD Card

Download the latest [Debian IoT SD image](https://beagleboard.org/latest-images) for BeagleBone.
I would recommend using the `Buster IoT (without graphical desktop) for BeagleBone and PocketBeagle via microSD card` image.
Burn to a microSD card using a tool like Win32 DiskImager or [Etcher](http://etcher.io)
and boot the BeagleBone from the microSD card with a network cable attached.

Locate the BeagleBone on your network and ssh onto it using the username `debian`.

## Flash the Onboard eMMC

To copy the Debian image from the microSD card onto the onboard eMMC, edit the `/boot/uEnv.txt`
file on the microSD card and remove the '#' on this line:

```
cmdline=init=/opt/scripts/tools/eMMC/init-eMMC-flasher-v3.sh
```

Enabling this line will cause the BeagleBone to copy the microSD Debian installation onto the onboard eMMC every time it boots.

Now reboot. Booting the BeagleBone with this configuration option enabled will initiate
the eMMC flashing process. The LEDs will cycle for a few minutes (cylon sweep pattern) -
this may take 5-6 minutes. When complete the 4 LEDS will all light up for a moment before turning off.
Obviously do not interrupt this process as you may corrupt the eMMC.

Once complete disconnect the power from the BeagleBone and remove the microSD card.
Note that the flashing process will repeat if the microSD card is left in.

With the microSD card removed, power-on the BeagleBone - it should now boot from eMMC.
The microSD card should be reformatted as it is no longer needed and accidentally booting
from it will reflash the eMMC thereby losing any changes that have subsequently been made.

## Setup the debian User Account

Install your ssh key: `ssh-copy-id debian@<bb-ip-address>`

Change password from 'temppwd': `passwd debian`

Edit `/etc/sudoers.d/admin` and change to:

```
%admin ALL=(ALL:ALL) NOPASSWD: ALL
```

Add the debian user to group `gpio` (if not already a member): `sudo usermod -a -G gpio debian`

Edit `~/.bashrc` and enable colour prompt: Remove the '#' from `force_color_prompt=yes`

Disable the graphical desktop since we are using the headless image:

```
sudo systemctl disable graphical.target
```

## Disable and Remove Bonescript / Cloud9 / Nodered

I don't use these services and disabling / removing them can save quite a bit of disk space and memory as well as significantly speeding up updates.
(I need to doublecheck these commands).

```
sudo systemctl stop bonescript.socket
sudo systemctl disable bonescript.socket
sudo systemctl stop bonescript.service
sudo systemctl disable bonescript.service
sudo systemctl stop bonescript-autorun.service
sudo systemctl disable bonescript-autorun.service
sudo systemctl stop nodered.socket
sudo systemctl disable nodered.socket
sudo systemctl stop nodered.service
sudo systemctl disable nodered.service
sudo systemctl stop cloud9.socket
sudo systemctl disable cloud9.socket
sudo systemctl stop cloud9.service
sudo systemctl disable cloud9.service
sudo systemctl stop nginx.service
sudo systemctl disable nginx.service
sudo apt -y remove c9-core-installer bonescript nodejs bb-node-red-installer
sudo apt -y purge c9-core-installer bonescript nodejs bb-node-red-installer
sudo apt autoremove && sudo apt autoclean
```

## Create the System Update / Upgrade Script

Create `/usr/local/bin/update`:

```
#!/bin/sh

apt update && apt -y --auto-remove full-upgrade
apt -y autoclean
```

Make it executable: `chmod +x /usr/local/bin/update`

Run it (`sudo /usr/local/bin/update`) and reboot (`sudo reboot`).

## Install Essential Development Tools and Libraries

Run: `sudo apt update && sudo apt -y install git gcc make build-essential i2c-tools libi2c-dev unzip zip vim gpiod libgpiod-dev libgpiod2`

## Install Java

OpenJDK 11 no longer works for me on my BeagleBone Black so I am forced to revert to OpenJDK 8.
It appears that the latest Debian OpenJDK 11 ARM build is compiled with CPU flags that are not compatible with the BeagleBone Black.
Unfortunately OpenJDK 8 isn't available in Debian Buster by default, however, it is available in the AdoptOpenJDK repository:

```
sudo apt -y install software-properties-common
wget -qO - https://adoptopenjdk.jfrog.io/adoptopenjdk/api/gpg/key/public | sudo apt-key add -
sudo add-apt-repository --yes https://adoptopenjdk.jfrog.io/adoptopenjdk/deb/
sudo apt update && sudo apt -y install adoptopenjdk-8-hotspot
sudo update-java-alternatives -s adoptopenjdk-8-hotspot-armhf
```

Alternatively install OpenJDK 8 via the Debian Stretch security updates channel:

```
sudo apt -y install software-properties-common
sudo apt-add-repository 'deb http://security.debian.org/debian-security stretch/updates main'
sudo apt update && sudo apt -y install openjdk-8-jdk
```

## Locale / Timezone

Run: `sudo dpkg-reconfigure locales`

Select `en_GB.UTF-8`

Run: `sudo dpkg-reconfigure tzdata`

Select: `Europe / London`

Install and enable ntp:

```
sudo apt update && sudo apt install ntp ntpdate
sudo systemctl enable ntp
```

Check ntp: `ntpq -p`

If need be, manually set the date / time: `sudo date -s "07:41 04/07/2017 BST" "+%H:%M %d/%m/%Y %Z"`

## Install ZSH and Oh My Zsh

Run:

```
sudo apt -y install zsh
chsh -s /usr/bin/zsh
sh -c "$(curl -fsSL https://raw.github.com/ohmyzsh/ohmyzsh/master/tools/install.sh)"
```

Make a minor tweak to the robbyrussell theme to show the hostname in the command prompt:

```
cd ~/.oh-my-zsh/themes
cp robbyrussell.zsh-theme robbyrussell_tweak.zsh-theme
```

Edit `robbyrussell_tweak.zsh-theme` and change the `PROMPT` value to include this prefix `%{$fg_bold[white]%}%M%{$reset_color%} `:

```
PROMPT="%{$fg_bold[white]%}%M%{$reset_color%} %(?:%{$fg_bold[green]%}➜ :%{$fg_bold[red]%}➜ )"
```

Update the ZSH config `~/.zshrc`:

```
export PATH=$PATH:/sbin:/usr/sbin:/usr/local/sbin
export JAVA_HOME=/usr/lib/jvm/adoptopenjdk-8-hotspot-armhf

ZSH_THEME="robbyrussell_tweak"
```

My own preference is to add this to the end of the `.zshrc` file:

```
# Allow multiple terminal sessions to all append to one zsh command history
setopt APPEND_HISTORY
# Do not enter command lines into the history list if they are duplicates of the previous event
setopt HIST_IGNORE_DUPS
# Remove command lines from the history list when the first character on the line is a space
setopt HIST_IGNORE_SPACE
# Remove the history (fc -l) command from the history list when invoked
setopt HIST_NO_STORE
```

## Disable Unused Capes (HDMI, Wireless)

Edit `/boot/uEnv.txt` and remove the preceeding `#` from these lines so that it looks like this:

```
disable_uboot_overlay_video=1
disable_uboot_overlay_audio=1
disable_uboot_overlay_wireless=1
```

## Remove the Additional Login Messages

Edit ```/etc/issue``` (note the blank line):

```
Debian GNU/Linux 10 \n \l

```

Edit ```/etc/issue.net``` (no blank line):

```
Debian GNU/Linux 10
```

## Kernel Update

```
cd /opt/scripts
git pull
cd tools
sudo ./update_kernel.sh [<OPTIONS>]
```

With no options it updates the current kernel.

To upgrade to 5.4:

```
sudo ./update_kernel.sh --lts-5_4
```

## I<sup>2</sup>C Clock Frequency

Check your current I<sup>2</sup>C bus frequencies:

```
> dmesg | grep i2c
[    0.315717] omap_i2c 4802a000.i2c: bus 1 rev0.11 at 100 kHz
[    0.317272] omap_i2c 4819c000.i2c: bus 2 rev0.11 at 100 kHz
[    1.337313] i2c /dev entries driver
[    1.642672] input: tps65217_pwr_but as /devices/platform/ocp/44e0b000.i2c/i2c-0/0-0024/tps65217-pwrbutton/input/input0
[    1.644722] omap_i2c 44e0b000.i2c: bus 0 rev0.11 at 400 kHz
```

Check which dtb file you are using:

```
sudo /opt/scripts/tools/version.sh
```

Look for a line like this:

```
UBOOT: Booted Device-Tree:[am335x-boneblack-uboot-univ.dts]
```

Take a copy of your dtb file and compile to dts text format (make sure to check your kernel version):

```
cp /boot/dtbs/4.19.94-ti-r42/am335x-boneblack-uboot-univ.dtb ~/.
dtc -I dtb -O dts -o am335x-boneblack-uboot-univ.dts am335x-boneblack-uboot-univ.dtb
```

Edit the .dts file, look for the aliases section and make a note of the I2C entries. Mine had this:

```
		i2c0 = "/ocp/i2c@44e0b000";
		i2c1 = "/ocp/i2c@4802a000";
		i2c2 = "/ocp/i2c@4819c000";
```

The i2c-0 bus is not accessible on the header pins while the i2c-1 bus is utilised for reading
EEPROMS on cape add-on boards and may interfere with that function when used for other digital
I/O operations.

Then locate the i2c@xxx sections in the file to view the status ("disabled" or "okay") and clock
frequency (a hex number). Valid I<sup>2</sup>C clock frequencies (depending on the device):

* 100 kHz (100,000) : 0x186A0
* 400 kHz (400,000) : 0x61A80
* 1,000 kHz (1,000,000) : 0xF4240
* 3,400 kHz (3,400,000) : 0x33E140
* 5,000 kHz (5,000,000) : 0x4C4B40

Note the O/S only enumerates the I<sup>2</sup>C buses that are enabled (status = "okay").

My DTS file:

i2c0 (i2c@44e0b000)

```
status = "okay";
clock-frequency = < 0x61a80 >;
```

i2c1 (i2c@4802a000)

```
status = "okay";
clock-frequency = < 0x186a0 >;
```

i2c2 (/ocp/i2c@4819c000)

```
status = "okay";
clock-frequency = < 0x186a0 >;
```

Since i2c2 (/dev/i2c-2) is the bus for general usage, update the clock-frequency value to 400kHz (`clock-frequency = < 0x61a80 >;`)

Make a backup of the original dtb file. Compile the dts file back to .dtb format and copy back to /boot:
```
dtc -I dts -O dtb -o am335x-boneblack-uboot-univ.dtb am335x-boneblack-uboot-univ.dts
sudo cp am335x-boneblack-uboot-univ.dtb /boot/dtbs/4.19.94-ti-r42/.
```

Reboot and check the I<sup>2</sup>C bus speeds:

```
> dmesg | grep i2c
[    0.315730] omap_i2c 4802a000.i2c: bus 1 rev0.11 at 100 kHz
[    0.317282] omap_i2c 4819c000.i2c: bus 2 rev0.11 at 400 kHz
[    1.337240] i2c /dev entries driver
[    1.638674] input: tps65217_pwr_but as /devices/platform/ocp/44e0b000.i2c/i2c-0/0-0024/tps65217-pwrbutton/input/input0
[    1.640742] omap_i2c 44e0b000.i2c: bus 0 rev0.11 at 400 kHz
```

### I2CDetect

Run `i2cdetect -y -r 2` as write quick isn't supported.

## Networking Configuration

Note work-in-progress.

Whatever I do I cannot get connman to work with a USB WiFi adapter, it simply refuses to detect any WiFi networks.

```
connmanctl> tether wifi off
Error disabling wifi tethering: Already disabled
connmanctl> enable wifi
Error wifi: Already enabled
connmanctl> agent on
Agent registered
connmanctl> scan wifi
Scan completed for wifi
connmanctl> services
*AO Wired                ethernet_a0f6fd4c0e73_cable
connmanctl> exit
```

My workaround so far is to use Network Manager via the CLI instead.
Run these commands with the ethernet cable connected and the USB WiFi adapter plugged in:

```
sudo apt -y install network-manager
sudo systemctl enable network-manager
sudo systemctl start network-manager
nmcli d wifi
sudo nmcli r wifi on
nmcli d wifi list
sudo nmcli d wifi connect <<SSID>> password <<password>>
```

Make sure that `/etc/network/interfaces` doesn't include config for eth0 / wlan0.
Alternatively set `managed=true` in /etc/NetworkManager/NetworkManager.conf and add this to `/etc/network/interfaces`:

```
auto eth0
iface eth0 inet dhcp
```

Verify that the board is now connected to the WiFi using `iwconfig wlan0` and make a note of the IP address.
If it is connected (wlan0 has been allocated an IP address), stop connman to double-check that it can be disabled.
Note that your current SSH connection might get terminated, if it does log back in using the wlan0 IP address.

```
sudo systemctl stop connman.service
ip addr
```

If everything is ok, reboot and confirm that the network connections are all ok. Now stop and disable connman.service:

```
sudo systemctl stop connman.service
sudo systemctl disable connman.service
```

### Setup Static IP Address

In ```/etc/network/interfaces```:

```
connmanctl config ethernet_a0f6fd4c0e73_cable --ipv4 manual 192.168.1.16 255.255.255.0 192.168.1.254 --nameservers 192.168.1.254
```

### On other Pi-like Debian Jessie Distributions

Edit ```/etc/network/interfaces```:

```
# The primary network interface
auto eth0
iface eth0 inet manual
```

Run:

```
sudo apt install dhcpcd5
```

Edit `/etc/dhcpcd.conf`:

```
interface eth0
  static ip_address=192.168.1.16/24
  static routers=192.168.1.254
  static domain_name_servers=192.168.1.254
```

Enable the service: `sudo systemctl enable dhcpcd`

## Pinout

Taken from [beagleboard.org](https://beagleboard.org/Support/bone101).

With the power / ethernet connection at the top:

```
TBD
```

Some good info [here](https://vadl.github.io/beagleboneblack/2016/07/29/setting-up-bbb-gpio)

![Universal Cape Pinout](https://vadl.github.io/images/bbb/bbb_headers.png)

* [P8 pins (PDF)](https://github.com/derekmolloy/boneDeviceTree/raw/master/docs/BeagleboneBlackP8HeaderTable.pdf)
* [P9 pins (PDF)](https://github.com/derekmolloy/boneDeviceTree/raw/master/docs/BeagleboneBlackP9HeaderTable.pdf)
