/**
 *  Sonos Favorites Support for Hubitat
 *  Schwark Satyavolu
 *
 */

def version() {"1.0.4"}

import hubitat.helper.InterfaceUtils

def appVersion() { return version() }
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
    refresh()
    dynamicPage(name: "configPage", title: "Configure/Edit Presets:") {
        section(""){input("numPresets", "number", title: getFormat("section", "How many presets?:"), submitOnChange: true, range: "1..25")}
            if(numPresets){
                for(i in 1..numPresets){
                    section(getFormat("header", "Preset ${i}")){
                        input("speaker${i}", "enum", title: getFormat("section", "Speaker:"), options: state.speakers)
                        input("preset${i}", "enum", title: getFormat("section", "Preset:"), options: state.presets, submitOnChange: true)
                        input("volume${i}", "number", title: getFormat("section", "Volume:"), range: "0..100", submitOnChange: true)
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
    updateDevices()
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

def updateDevices() {
    for(i in 1..numPresets) {
        def label = state.speakers[settings."speaker${i}"]?.replaceAll(/(?i) (sonos|speaker)/,'')+" Fave ${i}"
        log.info("${label} Sonos favorite switch being updated/created")
        createChildDevice(label, i)
    }
}

def getUDN(sonos) {
    return sonos.getDataValue('subscriptionId')?.replaceAll(/(uuid:|_[^_]+$)/,'')
}

def isRadio(uri) {
    return uri ==~ /(x\-sonosapi\-stream|x\-sonosapi\-radio|pndrradio|x\-sonosapi\-hls|hls\-radio|m3u8|x\-sonosprog\-http)/
}

def updateSpeakers() {
    state.speakers = [:]
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
  return didl
}

def parseResponse(cmd, resp, var) {
    def xml = resp.data.text
    def envelope = new XmlSlurper().parseText(xml)

    if('Browse' == cmd) {
        def didl = new XmlSlurper().parseText(envelope.children()[0].children()[0].children()[0].text())
        state[var] = [:] 
        didl.children().each() {
            def id = (it.@id as String).replace('FV:2/','')
            state[var][id] = [title: it.title as String, uri: it.res as String, id: it.@id as String, meta: it.resMD as String, desc: it.description as String]
        }
        updatePresets()
    }
    envelope = null
    didl = null
}

def sonosRequest(sonos, cmd, values=null, var=null) {
    def ip = convertHexToIP(sonos.getDeviceNetworkId())
    def meta = getCommand(cmd)
    def params = meta['commands'][cmd]['params']
    def paramXml = ''

    params.each {k,v ->
        paramXml = paramXml + sprintf("<%s>%s</%s>", k, 
        groovy.xml.XmlUtil.escapeXml((values?.containsKey(k) ? values[k] : params[k]) as String), k)
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

    try {
        httpPost([uri: url, headers: headers, body: req, contentType: 'text/xml; charset="utf-8"', textParser: true], { parseResponse(cmd, it, var) } )
    } catch (groovyx.net.http.HttpResponseException e) {
        logError('sonosRequest', "${e.statusCode}: ${e.response.data}")
    }      
}

private createChildDevice(label, id) {
    def deviceId = makeChildDeviceId(id)
    def createdDevice = getChildDevice(deviceId)
    def name = "Sonos Favorite"

    if(!createdDevice) {
        try {
            def component = 'Generic Component Switch'
            // create the child device
            addChildDevice("hubitat", component, deviceId, [label : "${label}", isComponent: false, name: "${name}"])
            createdDevice = getChildDevice(deviceId)
            def created = createdDevice ? "created" : "failed creation"
            log.info("Sonos Favorite Switch: id: ${deviceId} label: ${label} ${created}")
        } catch (e) {
            logError("Failed to add child device with error: ${e}", "createChildDevice()")
        }
    } else {
        debug("Child device type: ${type} id: ${deviceId} already exists", "createChildDevice()")
        if(label && label != createdDevice.getLabel()) {
            createdDevice.setLabel(label)
            createdDevice.sendEvent(name:'label', value: label, isStateChange: true)
        }
        if(name && name != createdDevice.getName()) {
            createdDevice.setName(name)
            createdDevice.sendEvent(name:'name', value: name, isStateChange: true)
        }
    }
    return createdDevice
}

def addURItoQueue(sonos, uri, meta="") {
    debug("adding ${uri} to queue")
    sonosRequest(sonos, 'AddURIToQueue', [EnqueuedURI : uri, EnqueuedURIMetaData: meta, DesiredFirstTrackNumberEnqueued: 1])
}

def setURI(sonos, uri, meta="") {
    debug("setting current uri to ${uri}")
    sonosRequest(sonos, 'SetAVTransportURI', [CurrentURI : uri, CurrentURIMetaData: meta])
}

def setVolume(sonos, volume) {
    debug("setting current volume to ${volume}")
    sonosRequest(sonos, 'SetVolume', [DesiredVolume : volume])
}

def play(sonos, uri, meta="") {
    if(!isRadio(uri)) {
        addURItoQueue(sonos, uri, meta)
        def udn = getUDN(sonos)
        uri = "x-rincon-queue:${udn}#0"
        meta = ""
    } 
    setURI(sonos, uri, meta)
    pauseExecution(1000)
    sonosRequest(sonos, 'Play')
}

def stop(sonos) {
    sonosRequest(sonos, 'Stop')
}

def getSonosById(id) {
    debug("finding sonos for ${id}...")
    def result = null
    sonoses?.each() {
        if(it.getDeviceNetworkId() == id) result = it
    }
    debug(result)
    return result
}

void componentRefresh(cd) {
    debug("received refresh request from ${cd.displayName}")
    refresh()
}

def componentOn(cd) {
    debug("received on request from DN = ${cd.name}, DNI = ${cd.deviceNetworkId}")
    def idparts = cd.deviceNetworkId.split("-")
    def num = idparts[-1]
    debug("preset num is ${num}")
    def sonos = getSonosById(settings."speaker${num}")
    if(sonos) {
        def volume = settings."volume${num}" ?: 3
        if(volume) {
            setVolume(sonos, volume)
            pauseExecution(2000)
        }
        def fave = state.favorites[settings."preset${num}"]
        if(fave) {
            play(sonos, fave.uri, fave.meta)
            cd.sendEvent(name: 'switch', value: 'on')
        }
    }
}

def componentOff(cd) {
    debug("received off request from DN = ${cd.name}, DNI = ${cd.deviceNetworkId}")
    def idparts = cd.deviceNetworkId.split("-")
    def num = idparts[-1]
    def sonos = getSonosById(settings."speaker${num}")
    if(sonos) {
        stop(sonos)
        cd.sendEvent(name: 'switch', value: 'off')
    }
}

def makeChildDeviceId(id) {
    def hubid = 'SONOSFAVORITES'
    return "${hubid}-${id}"
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
