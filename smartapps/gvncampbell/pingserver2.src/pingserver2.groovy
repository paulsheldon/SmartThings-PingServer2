/**
 *  PingServer2
 *
 *  Copyright 2017 Gavin Campbell
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "PingServer2",
    namespace: "GvnCampbell",
    author: "Gavin Campbell",
    description: "Update presence devices via a REST API.",
    category: "My Apps",
    iconUrl: "https://github.com/GvnCampbell/SmartThings-PingServer2/blob/master/smartapps/gvncampbell/pingserver2.src/pingserver2.png?raw=true",
    iconX2Url: "https://github.com/GvnCampbell/SmartThings-PingServer2/blob/master/smartapps/gvncampbell/pingserver2.src/pingserver22.png?raw=true",
    iconX3Url: "https://github.com/GvnCampbell/SmartThings-PingServer2/blob/master/smartapps/gvncampbell/pingserver2.src/pingserver22.png?raw=true")

preferences {
    page(name: "config")
}

def config() {
    dynamicPage(name: "config", title: "PingServer2 Settings", install: true, uninstall: true) {
        section("Create your devices below.") {
            input(name: "deviceList", type: "text", title: "Device List (Comma Separated)", description: "Comma separated list of devices.", defaultValue: "", required: false, submitOnChange: true)
        }
		section("Server Information") {
            input(name: "ip", type: "text", title: "IP", description: "Server IP", required: false, submitOnChange: true)
            input(name: "port", type: "text", title: "Port", defaultValue: "4001", description: "Server Port", required: false, submitOnChange: true)

        }
        section("Other Settings") {
        	input(name: "timeout", type: "number", defaultValue: "14", range: "0..*", title: "Timeout Delay (minutes)", description: "Number of minutes for device to be offline before presence is set to away.", required: true)
            input(name: "pingDevicesInterval", type: "number", defaultValue: "3", range: "0..*", title: "Ping Interval (seconds)", description: "Interval in seconds to ping each device.", required: true)
            input(name: "offlineTimeout", type: "number", defaultValue: "120", range: "1..*", title: "Offline Timeout (minutes)", description: "Number of minutes without a server response before devices are marked as offline.", required: true)
            input(name: "debugLogging", type: "bool", defaultValue: "false", title: "Enable Debug Logging", description: "Enable extra logging for debugging.")
        }
    }
}

//************************************************************************************
//
//  OAuth Callback Methods
//
//************************************************************************************
mappings {
    path("/device/:dev/:attr/:cmd") {
        action: [
            GET: "updateDevice"
        ]
    }
}

def updateDevice() {
	def loggerPrefix = "[updateDevice] "
	logger loggerPrefix + "params.command: ${params}"

	def d = getChildDevice(app.id + '/' + params.dev)
    logger loggerPrefix + "d: " + d
    if (d) {
    	logger loggerPrefix + "Device found."
        sendEvent(d.deviceNetworkId, [name: params.attr, value: params.cmd])
    } else {
    	logger loggerPrefix + "Device not found."
    }
}

//************************************************************************************
//
//  SmartApp Config Methods
//
//************************************************************************************
def installed() {
	def loggerPrefix = "[installed] "
	logger loggerPrefix + "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	def loggerPrefix = "[updated] "
	logger loggerPrefix + "Updated with settings: ${settings}"
	initialize()
}

def initialize() {
	def loggerPrefix = "[initialize] "
    logger loggerPrefix + "Started."

    addDevices()

    if (!state.accessToken) {
        createAccessToken()
    	logger loggerPrefix + "Created access token: " + state.accessToken
    } else {
    	logger loggerPrefix + "Access token already created: " + state.accessToken
    }

    subscribe(location, null, locationHandler, [filterEvents:false])

    sendCommand("init", "")

	healthCheck(true, offlineTimeout)
	unschedule()
    poll()
    runEvery1Minute(poll)
    runEvery1Minute(healthCheck)
}

def locationHandler(evt) {
    def loggerPrefix = "[locationHandler] "

    def description = evt.description
    def hub = evt?.hubId

    def parsedEvent = parseLanMessage(description)
    parsedEvent << ["hub":hub]

    if (parsedEvent.headers && parsedEvent.body && parsedEvent?.data?.service == 'pingserver2') {
        logger loggerPrefix + "Legit response found."
        logger loggerPrefix + "parsedEvent.body: " + parsedEvent.body
        healthCheck(true)
		if (parsedEvent.data.status == 'error' && parsedEvent?.data?.message == 'init') {
            logger loggerPrefix + "Initialization requested."
            initialize()
        }

    }
}

//************************************************************************************
//
//  Child Device Methods
//
//************************************************************************************
def addDevices() {
  	def loggerPrefix = "[addDevices] "
 	logger loggerPrefix + "Started."

    if (deviceList) {
        deviceList.split(',').findAll { selected ->
            def dni = app.id + "/" + selected
            def d = getChildDevice(dni)
            if(!d) {
                d = addChildDevice("GvnCampbell", "PingServer2 Sensor", dni, null, ["label": "${selected}"])
                logger loggerPrefix + "Created ${d.displayName} with id $dni"
                d.refresh()
            } else {
                logger loggerPrefix + "Found ${d.displayName} with id $dni already exists, type: '$d.typeName'"
            }
            return selected
        }
    }
}

def sendCommand(cmd, qAppend) {
    def loggerPrefix = "[sendCommand] "
    logger loggerPrefix + "Started."

    //def encodedUsername = java.net.URLEncoder.encode(username, "UTF-8")
    //def encodedPassword = java.net.URLEncoder.encode(password, "UTF-8")
    def encodedToken = java.net.URLEncoder.encode(state.accessToken, "UTF-8")
    def encodedURL = java.net.URLEncoder.encode(getApiServerUrl() + "/api/smartapps/installations/" + app.id, "UTF-8")
    //def encodedAllDevices = getAllDevices().collect { s -> s.description }
    def encodedDeviceList = java.net.URLEncoder.encode(deviceList, "UTF-8")

    def q = "?token=" + encodedToken + "&url=" + encodedURL + "&dl=" + encodedDeviceList + "&to=" + (timeout*60) + "&pdi=" + pingDevicesInterval + qAppend
    logger loggerPrefix + "q: " + q
    put("/" + cmd, "", q)
}

//************************************************************************************
//
//  Device Communication Methods
//
//************************************************************************************
def poll() {
	def loggerPrefix = "[poll] "
    logger loggerPrefix + "Started."
    put("/ping", "", "")
}

private refresh() {
	def loggerPrefix = "[refresh] "
    logger loggerPrefix + "Started."
    sendCommand("init", "")
}

//************************************************************************************
//
//  Misc Methods
//
//************************************************************************************
private put(path, text, q = "") {
    def loggerPrefix = "[put] "
    logger loggerPrefix + "path: " + path
    logger loggerPrefix + "text: " + text
    logger loggerPrefix + "dni: " + dni
    logger loggerPrefix + "q: " + q
    logger loggerPrefix + "ip: " + ip
    logger loggerPrefix + "port: " + port

    def hubaction = new physicalgraph.device.HubAction([
        method: "PUT",
        path: path + "/" + q,
        body: text,
        headers: [ HOST: "$ip:$port", "Content-Type": "application/json" ]]
                                                      )
    logger loggerPrefix + "hubaction: " + hubaction
    sendHubCommand(hubaction)
}

private logger(text) {
    if (debugLogging) {
		log.debug text
	}
}

def healthCheck(boolean checkIn=false, timeout=null) {
	def loggerPrefix = "[healthCheck] "
    logger loggerPrefix + "Started."

    if (!state.healthCheckLastCheckIn) {
    	logger loggerPrefix + "healthCheckLastCheckIn Initialized."
    	state.healthCheckLastCheckIn = new Date().getTime()
    }
    if (!state.healthCheckTimeOut) {
    	logger loggerPrefix + "healthCheckTimeOut Initialized."
    	state.healthCheckLastCheckIn = 120
    }
    if (timeout) {
    	logger loggerPrefix + "healthCheckTimeOut updated."
    	state.healthCheckTimeOut = timeout
    }
	if (checkIn) {
    	logger loggerPrefix + "Check In."
    	state.healthCheckLastCheckIn = new Date().getTime()
	} else {
		def timeNow = new Date().getTime()
		def timeDiff = (timeNow - state.healthCheckLastCheckIn) / 1000 / 60 // in minutes
        logger loggerPrefix + "Time since last Check In (minutes): " + timeDiff
		if (timeDiff > state.healthCheckTimeOut) {
            def children = getChildDevices()
            children.each {
                logger loggerPrefix + "Set offline: " + it
                sendEvent(it.deviceNetworkId, [name: "presence", value: "offline"]) // Modify as required
	        }
        }
	}
}

