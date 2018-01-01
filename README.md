# SmartThings-PingServer2 (Work in Progress)

The combination of this device handler, smartapp and node server script will allow you to setup presence devices in SmartThings that will activate based on your devices presence on the network.

This requires the installation of a local server running a node script that utilizes the [ping](https://www.npmjs.com/package/ping) wrapper for nodejs to handle the check.

Installation:
* Install ping (npm install ping -g).
* Copy the pingserver2.js file to a location on your machine and run it using node.
* Install the device handler in the SmartThings IDE.
* Install the smartapp in the SmartThings IDE.
* Configure the smartapp to point it to the server and add your device IP's in a comma separated list. Your devices will start to show up.

SmartApp Options:
* IP: The IP address of the pingserver.
* Port: The port of the ping server (default=4000).
* Timeout Delay: The amount of time (in minutes) a device has to not respond before it is marked offline (default=15).
* Ping Frequency: How often to ping the devices in seconds (default=15).
* Device List: A comma separated list of device IP's/names.

Notes:
* The ping frequency works well at 3 seconds as when testing my two iPhones would disconnect from the network when locked.  They would randomly connect/discconnect while in sleep mode.  15 seconds is quick enough to catch them when they connect for a few seconds.
* The timeout delay of 15 minutes seems to work best.




