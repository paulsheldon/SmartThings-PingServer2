metadata {
    definition (name: "PingServer2 Sensor", namespace: "GvnCampbell", author: "Gavin Campbell") {
        capability "presenceSensor"
        capability "Sensor"
        capability "Refresh"
        capability "polling"
        capability "Health Check"
        command "refresh"
        command "arrived"
        command "departed"
    }

	preferences {
    	input(name: "debugLogging", type: "bool", defaultValue: "true", title: "Enable Debug Logging", description: "Enable extra logging for debugging.")
    }

    tiles {
        standardTile("presence", "device.presence", width: 2, height: 2, canChangeBackground: true) {
            state "not present", label: "Not Present", labelIcon: "st.presence.tile.mobile-not-present", backgroundColor: "#ffffff", action: "arrived"
            state "present", label: "Present", labelIcon: "st.presence.tile.mobile-present", backgroundColor: "#00A0DC", action: "departed"
            state "offline", label: "Offline", backgroundColor:"#ff0000"
        }
        standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat", width: 1, height: 2) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        main(["presence"])
        details(["presence", "refresh"])
    }
}

// Device Communication Methods
def parse(description) {
  	def logprefix = "[parse] "
  	logger logprefix + "description: " + description // Example description: [name:presence, value:present, isStateChange:true, displayed:true, linkText:PingServer2, descriptionText:PingServer2 presence is present]

	def results = []	// The event to return
  	def map = description	// The event details

  	if (description instanceof String)  {
    	//logger logprefix + "stringToMap: " + map
    	map = stringToMap(description)	// convert the event details from a string to a map
  	}

  	if (map?.name && map?.value) {  // If the event details are a map then we can create an event
    	results << createEvent(name: "${map?.name}", value: "${map?.value}")
  	}
  	results
}

// Device Conotrol Methods
def arrived() {
    def logprefix = "[arrived] "
    logger logprefix + "Executed."
	sendEvent(name: "presence", value: "present")
}
def departed() {
    def logprefix = "[departed] "
    logger logprefix + "Executed."
    sendEvent(name: "presence", value: "not present")
}
def refresh() { // Executes a full refresh of all child devices
    def logprefix = "[refresh] "
    logger logprefix + "Executed."
    parent.refresh()
}

// Device Configuration Methods
def installed() { // executed when device is first installed
	def logprefix = "[installed] "
    initialize()
}
def updated() { // executed when device settings are updated
	def logprefix = "[updated] "
	initialize()
}
def initialize() {
    refresh()
}

// Miscelaneous Methods
private logger(text) { // Log data based on the logging switch
    if (debugLogging) {
		log.debug  text
	}
}

