/**
 *  Sonos Favorites Support for Hubitat
 *  Schwark Satyavolu
 *
 */

 def version() {"0.1.1"}

import hubitat.helper.InterfaceUtils

def appVersion() { return "4.0" }
def appName() { return "Sonos Favorites Support" }

definition(
	name: "${appName()}",
	namespace: "schwark",
	author: "Schwark Satyavolu",
	description: "This adds support for Sonos Favorites",
	category: "Convenience",
	iconUrl: "https://play-lh.googleusercontent.com/ixBnWaJs0NdWI1w4rpAgiWlavHQZ2cMpatPoh3dwbj6ywnYIZ0g6me16prz-ABr7GA",
	iconX2Url: "https://play-lh.googleusercontent.com/ixBnWaJs0NdWI1w4rpAgiWlavHQZ2cMpatPoh3dwbj6ywnYIZ0g6me16prz-ABr7GA",
	singleInstance: true,
	importUrl: "https://raw.githubusercontent.com/schwark/hubitat-sonos-favorites/main/sonos-favorites.groovy"
)

/*
metadata {
    definition (name: "Sonos Favorites Support", namespace: "schwark", author: "Schwark Satyavolu") {
        capability "Initialize"
        capability "Refresh"
    }
}
*/

preferences {
	page(name: "mainPage")
	page(name: "configPage")
}

def getFormat(type, myText=""){
    if(type == "section") return "<div style='color:#78bf35;font-weight: bold'>${myText}</div>"
    if(type == "hlight") return "<div style='color:#78bf35'>${myText}</div>"
    if(type == "header") return "<div style='color:#ffffff;background-color:#392F2E;text-align:center'>${myText}</div>"
    if(type == "redhead") return "<div style='color:#ffffff;background-color:red;text-align:center'>${myText}</div>"
    if(type == "line") return "\n<hr style='background-color:#78bf35; height: 2px; border: 0;'></hr>"
    if(type == "centerBold") return "<div style='font-weight:bold;text-align:center'>${myText}</div>"	
	
}

def mainPage(){
	dynamicPage(name:"mainPage",install:true, uninstall:true){
        section {
            input "debugMode", "bool", title: "Enable debugging", defaultValue: true
        }
    	section(getFormat("header", "Step 1: Choose your Sonos Speakers")) {
        	input "sonoses", "capability.musicPlayer", title: "Speaker", submitOnChange: true, multiple:true, required: true
        }
        if(sonoses){
            section(getFormat("header", "Step 2: Configure/Edit Your Presets")){
                	href "configPage", title: "Presets"
          	}
        }
	}
}

def configPage(){
	initialize()
	refresh()
    dynamicPage(name: "configPage", title: "Configure/Edit Presets:") {
		section("Something"){input("numPresets", "number", title: getFormat("section", "How many presets?:"), submitOnChange: true, range: "1..25")}
			if(numPresets){
				for(i in 1..numPresets){
					section(getFormat("header", "Preset ${i}")){
						input("speaker${i}", "enum", title: getFormat("section", "Speaker:"), options: state.speakers)
						input("preset${i}", "enum", title: getFormat("section", "Preset:"), options: state.presets)
					}
				}
			}
	}
}

def installed() {
	initialize()
}

def updated() {
	initialize()
}

def initialize() {
	unschedule()
}

def uninstalled() {
	def children = getAllChildDevices()
	log.info("uninstalled: children = ${children}")
	children.each {
		deleteChildDevice(it.deviceNetworkId)
	}
}

def updateSpeakers() {
	state.speakers = state.speakers ?: [:]
	sonoses?.each()  {
		state.speakers[it.getDeviceNetworkId()] = it.getLabel()
	}
}

def updatePresets() {
	state.presets = state.presets ?: [:]
	state.favorites?.each() { k, v ->
		state.presets[k] = v.title
	}
}

def refresh() {
	updateSpeakers()
	getFavorites()
}

def getFavorites() {
	sonosRequest(sonoses[0], 'Browse', [ObjectID: 'FV:2'], 'favorites')
}

private sonosAction(String dni, String action, String service, String path, Map body = [InstanceID:0, Speed:1]) {
	debug("sonosAction($dni, $action, $service, $path, $body)")
	def ip = convertHexToIP(dni)
	def soap = new hubitat.device.HubSoapAction(
		path:    path ?: "/MediaRenderer/$service/Control",
		urn:     "urn:schemas-upnp-org:service:$service:1",
		action:  action,
		body:    body,
		headers: [Host: "${ip}:1400", CONNECTION: "close"],
		destinationAddress: ip,
		destinationPort: 1400
	)
	sendHubCommand(soap)
}

def getCommand(cmd) {
	def commands = [
		ContentDirectory : [
			class: 'ContentDirectory',
			urn : 'urn:schemas-upnp-org:service:ContentDirectory:1',
			control : '/MediaServer/ContentDirectory/Control',
			events : '/MediaServer/ContentDirectory/Event',
			commands : [
				Browse : [params : [ObjectID : "", BrowseFlag : "BrowseDirectChildren", Filter : "*", StartingIndex : 0, RequestedCount : 100, SortCriteria:""]]
			]
		],
		RenderingControl : [
			class: 'RenderingControl',
			urn : 'urn:schemas-upnp-org:service:RenderingControl:1',
			control : '/MediaRenderer/RenderingControl/Control',
			events : '/MediaRenderer/RenderingControl/Event',
			commands : [
				GetVolume : [params : [InstanceID : 0, Channel : "Master"]],
				GetMute : [params : [InstanceID : 0, Channel : "Master"]],
				SetVolume : [params : [InstanceID : 0, Channel : "Master", DesiredVolume : 50]],
				SetMute : [params : [InstanceID : 0, Channel : "Master", DesiredMute : true]],
			]
		],
		GroupRenderingControl : [
			class: 'GroupRenderingControl',
			urn : 'urn:schemas-upnp-org:service:GroupRenderingControl:1',
			control : '/MediaRenderer/GroupRenderingControl/Control',
			events : '/MediaRenderer/GroupRenderingControl/Event',
			commands : [
				GetGroupVolume : [params : [InstanceID : 0]],
				GetGroupMute : [params : [InstanceID : 0]],
				SetGroupVolume : [params : [InstanceID : 0, DesiredVolume : 50]],
				SetGroupMute : [params : [InstanceID : 0, DesiredMute : true]],
			]
		],
		ZoneGroupTopology : [
			class: 'ZoneGroupTopology',
			urn : 'urn:schemas-upnp-org:service:ZoneGroupTopology:1',
			control : '/ZoneGroupTopology/Control',
			events : '/ZoneGroupTopology/Event',
			commands : [
				GetZoneGroupState : [],
				GetZoneGroupAttributes : []
			]
		],
		AVTransport : [
			class: 'AVTransport',
			urn : 'urn:schemas-upnp-org:service:AVTransport:1',
			control : '/MediaRenderer/AVTransport/Control',
			events : '/MediaRenderer/AVTransport/Event',
			commands : [
				SetAVTransportURI : [params : [InstanceID : 0, CurrentURI : "", CurrentURIMetaData: ""]],
				RemoveAllTracksFromQueue : [params : [InstanceID : 0]],
				AddURIToQueue : [params : [InstanceID : 0, EnqueuedURI : "", EnqueuedURIMetaData: "", DesiredFirstTrackNumberEnqueued:0, EnqueueAsNext:false]],
				GetMediaInfo : [params : [InstanceID : 0]],
				GetPositionInfo : [params : [InstanceID : 0]],
				GetTransportInfo : [params : [InstanceID : 0]], // STOPPED / PLAYING / PAUSED_PLAYBACK / TRANSITIONING
				GetTransportSettings : [params : [InstanceID : 0]], // NORMAL / REPEAT_ALL / REPEAT_ONE / SHUFFLE_NOREPEAT / SHUFFLE / SHUFFLE_REPEAT_ONE
				SetPlayMode : [params : [InstanceID : 0, NewPlayMode : ""]], //NORMAL / REPEAT_ALL / REPEAT_ONE / SHUFFLE_NOREPEAT / SHUFFLE / SHUFFLE_REPEAT_ONE
				Play : [params : [InstanceID : 0, Speed : 1]],
				Pause : [params : [InstanceID : 0]],
				Stop : [params : [InstanceID : 0]],
				Next : [params : [InstanceID : 0]],
				Previous : [params : [InstanceID : 0]],
				Seek : [params : [InstanceID : 0, Unit : "", Target : ""]], // TRACK_NR / REL_TIME / TIME_DELTA // Position of track in queue (start at 1) or hh:mm:ss for REL_TIME or +/-hh:mm:ss for TIME_DELTA
			]
		],
		Queue : [
			class: 'Queue',
			urn : 'urn:schemas-sonos-com:service:Queue:1',
			control : '/MediaRenderer/Queue/Control',
			events : '/MediaRenderer/Queue/Event',
			commands : [

			]
		]
	]

	def result = null
	commands.each { cls, meta ->
		if(!result) {
			meta['commands'].each { name, cmdmeta ->
				if (name == cmd) return result = meta
			}
		}
	}

	return result
}

def fixXML(didl) {
  def fixing = (didl ==~ /<DIDL\-Lite xmlns\:dc\=&quot;/) || (didl ==~ /<[^>]+&gt;/)
  if(fixing) {
    didl = didl.replaceAll(/(<r:resMD>)(.+)(<\/r:resMD>)/) {
        def child = "<foo>${it[1]}</foo>"
        xml = (new XmlSlurper().parseText(child)) as String
        it[0] + groovy.xml.XmlUtil.escapeXml(xml) + it[2] 
    } 
  }
  /*
  if didl:match('<DIDL%-Lite xmlns%:dc%=&quot;') then
      log.warn("messed up xml - fixing &quot;")
      didl = didl:gsub('&quot;','"')
      fixed = true
  end
  if didl:match('<[^>]+&gt;') then
      log.warn("messed up xml - fixing tag&gt;")
      didl = didl:gsub('<([^>]+)&gt;','<%1>')
      fixed = true
  end
  */
  return didl
}

def parseResponse(resp, var) {
	def xml = resp.data.text
	def envelope = new XmlSlurper().parseText(xml)
	def didl = new XmlSlurper().parseText(envelope.children()[0].children()[0].children()[0].text())
	state[var] = [:] 
	didl.children().each() {
		def id = (it.@id as String).replace('FV:2/','')
		state[var][id] = [title: it.title as String, res: it.res as String, id: it.@id as String, meta: it.resMD as String, desc: it.description as String]
	}
	updatePresets()
	envelope = null
	didl = null
}

def sonosRequest(sonos, cmd, values, var) {
	def ip = convertHexToIP(sonos.getDeviceNetworkId())
	def meta = getCommand(cmd)
	def params = meta['commands'][cmd]['params']
	def paramXml = ''

	params.each {k,v ->
		paramXml = paramXml + sprintf("<%s>%s</%s>", k, 
		groovy.xml.XmlUtil.escapeXml((values.containsKey(k) ? values[k] : params[k]) as String), k)
	}

	def req = sprintf("""<?xml version="1.0" encoding="utf-8"?>
    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
      <s:Body>
        <u:%s xmlns:u="%s">
        %s
        </u:%s>
      </s:Body>
    </s:Envelope>""", cmd, meta['urn'], paramXml, cmd)

	def headers = [
        Host: ip+':1400',
        soapaction: meta['urn']+'#'+cmd
    ]

	def url = 'http://'+ip+':1400'+meta['control']
	debug("${url} -> ${headers} -> ${req}")

	httpPost([uri: url, headers: headers, body: req, contentType: 'text/xml; charset="utf-8"', textParser: true], { data -> parseResponse(data, var) } )
}

private createChildDevice(label, id, favorite) {
	def deviceId = makeChildDeviceId(id, favorite)
	def createdDevice = getChildDevice(deviceId)

	if(!createdDevice) {
		try {
			def component = 'Generic Component Switch'
			// create the child device
			addChildDevice("hubitat", component, deviceId, [label : "${label}", isComponent: false, name: "${label}"])
			createdDevice = getChildDevice(deviceId)
			def created = createdDevice ? "created" : "failed creation"
			log.info("Sonos Favorite Switch: ${type} id: ${deviceId} label: ${label} ${created}", "createChildDevice()")
		} catch (e) {
			logError("Failed to add child device with error: ${e}", "createChildDevice()")
		}
	} else {
		debug("Child device type: ${type} id: ${deviceId} already exists", "createChildDevice()")
		if(label && label != createdDevice.getLabel()) {
			createdDevice.sendEvent(name:'label', value: label, isStateChange: true)
		}
	}
	return createdDevice
}

void componentRefresh(cd) {
    debug("received refresh request from ${cd.displayName}")
    refresh()
}

def componentOn(cd) {
    debug("received on request from DN = ${cd.name}, DNI = ${cd.deviceNetworkId}")
	def idparts = cd.deviceNetworkId.split("-")
    def favorite = idparts[-1].replaceAll('_',':').replaceAll('.','/')
	def id = idparts[-2]
}

def componentOff(cd) {
    debug("received off request from DN = ${cd.name}, DNI = ${cd.deviceNetworkId}")
	def idparts = cd.deviceNetworkId.split("-")
    def favorite = idparts[-1].replaceAll('_',':').replaceAll('.','/')
	def id = idparts[-2]
}

def getCommunicationDevice() {
	def id = getCommunicationDeviceId()
	def cd = getChildDevice(id)
	def name = "Sonos Favorites Interface"

	if(!cd) {
		cd = addChildDevice("schwark", name, id, [label : "${name}", isComponent: false, name: "${name}"])
	}
	return cd
}

def makeChildDeviceId(id, favorite) {
	def hubid = 'SONOSFAVORITES'
	return "${hubid}-${id}-${favorite.replaceAll(':', '_').replaceAll('/','.')}"
}

private debug(logMessage, fromMethod="") {
    if (debugMode) {
        def fMethod = ""

        if (fromMethod) {
            fMethod = ".${fromMethod}"
        }

        log.debug("[Sonos Favorites] DEBUG: ${fMethod}: ${logMessage}")
    }
}

private logError(fromMethod, e) {
    log.error("[Sonos Favorites] ERROR: (${fromMethod}): ${e}")
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}