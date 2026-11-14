/**
 * Evohome Connect for Hubitat
 * Author: Tobey Ljungberg
 * Version: 2.0.1
 * Copyright (c) 2026 Tobey Ljungberg
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0. You may use, modify and
 * redistribute this work under those terms. See https://www.apache.org/licenses/LICENSE-2.0
 * The EMEA TCC API used here is an undocumented vendor API.
 */
definition(name: "Evohome Connect", namespace: "tljungberg", author: "Tobey Ljungberg",
    description: "Connect evohome heating, system modes and DHW to Hubitat.", category: "Convenience",
    singleInstance: true, singleThreaded: true)

preferences {
    page(name: "mainPage", title: "Evohome Connect", install: true, uninstall: true) {
        section("Evohome account and connection") {
            paragraph "Connects Hubitat to your Evohome account. The app creates child devices for heating zones, the system controller and hot water when these are available."
            input "username", "text", title: "Evohome account email or username",
                description: "Enter the same account name you use to sign in to the Evohome app or website.", required: true
            input "password", "password", title: "Evohome account password",
                description: "Enter the password for this Evohome account.", required: true
            input "apiHost", "text", title: "Evohome API host",
                description: "Enter only the server origin, for example https://mytotalconnectcomfort.com. Do not add /WebApi, /api or another path.",
                defaultValue: "https://mytotalconnectcomfort.com", required: true
        }
        section("Status updates and temperature changes") {
            paragraph "Evohome commands do not confirm immediately that a change has taken effect. The app waits, then polls Evohome and updates the child devices with the reported state."
            input "pollMinutes", "number", title: "Regular status check interval (minutes)",
                description: "How often Hubitat asks Evohome for current temperatures, modes, faults and setpoints. A shorter interval gives fresher status and makes more API requests.",
                range: "1..60", defaultValue: 5, required: true
            input "debounceSeconds", "number", title: "Wait for temperature changes to settle (seconds)",
                description: "After the last temperature adjustment, wait this long before sending it. Repeated HomeKit or Hubitat adjustments are combined into one final Evohome command.",
                range: "1..15", defaultValue: 3, required: true
            input "settleSeconds", "number", title: "Check Evohome after a command (seconds)",
                description: "How long to wait before polling after a command, giving Evohome time to apply it. The child device updates immediately, then is corrected if Evohome reports a different value.",
                range: "2..60", defaultValue: 8, required: true
            href name: "zoneSetpointSetup", page: "zoneSetpointPage", title: "Configure setpoint behaviour by zone",
                description: "Choose separately for each zone how temperature overrides should expire."
        }
        section("Heating-only system compatibility") {
            paragraph "Evohome zones control heating, not cooling or a fan. In HomeKit, export the heating-zone child devices only. Hubitat's native HomeKit integration may still show cooling and fan controls because it exports the complete Thermostat capability."
            input "heatingOnly", "bool", title: "My Evohome system provides heating only",
                description: "Turn on for a system without cooling. Zone devices keep the cooling target equal to the heating target and ignore cooling changes, so the two displayed temperatures stay aligned. This setting cannot hide controls that Hubitat's HomeKit integration adds.",
                defaultValue: true
        }
        section("Window contact protection") {
            paragraph "Configure one or more contact sensors for each heating zone. The per-zone selectors appear after Evohome's installation sync has created the zone child devices."
            href name: "windowContactSetup", page: "windowContactPage", title: "Configure window contacts by zone",
                description: "Open the zone mapping page. Any open selected contact holds that zone at 5 °C."
        }
        section("Away mode zone override preservation") {
            paragraph "Choose separately for each zone whether Evohome overrides should be restored after the integration changes the system to Away and then restores its previous mode."
            href name: "awayOverrideSetup", page: "awayOverridePage", title: "Configure Away override preservation by zone",
                description: "Choose which zones should restore saved permanent or unexpired temporary overrides after Away."
        }
        section("Synchronise Hubitat and Evohome modes") {
            paragraph "These options apply when mode synchronisation is enabled. When Hubitat changes to Away, this app checks the current Evohome mode first. It does nothing if Evohome is already Off or Away. If it changes Evohome to Away, it can restore the previous mode when Hubitat leaves Away."
            input "modeSyncEnabled", "bool", title: "Set Evohome to Away when Hubitat changes to Away",
                description: "Enable automatic Away synchronisation. If Evohome is Off, it stays Off. If it is already Away, the app records that fact and does not claim to have changed it.",
                defaultValue: false
            input "restoreMode", "bool", title: "Restore the previous Evohome mode on return",
                description: "When enabled, restore the mode saved before this app changed Evohome to Away. If Evohome was changed to Off while Hubitat was Away, it remains Off. When disabled, Evohome stays Away on return.",
                defaultValue: true
            input "preserveExistingAway", "bool",
                title: "Keep Evohome Away on return if it was already Away when Hubitat left",
                description: "Applies only when Evohome was already Away before Hubitat changed to Away. Leave on to keep Away on return; turn off to change Evohome to Auto on return. Heating Off is always preserved.",
                defaultValue: true
            input "awayDurationDays", "number", title: "How long Evohome stays in Away (days)",
                description: "Use 0 for no expiry. A positive value gives the Away mode set by this app an expiry after that many days.",
                range: "0..99", defaultValue: 0
        }
        section("Evohome fault notifications") {
            paragraph "The app checks faults reported by Evohome and logs new faults. Select Hubitat mobile app notification devices to send a push when a new supported fault appears."
            input "faultNotifyDevices", "capability.notification",
                title: "Send new fault notifications to",
                description: "Select one or more Hubitat mobile app notification devices. Existing faults found on the first successful check are reported too. Evohome does not provide boiler relay or OpenTherm status through this API.",
                multiple: true, required: false
        }
        section("Logging") {
            input "prefLogLevel", "enum", title: "Main app log level",
                description: "Error shows errors only. Warn adds warnings; Info adds readable activity messages; Debug adds API calls, responses and processing details. Each level includes all more severe categories. Child devices keep their own levels until you use Reset Logging.",
                options: ["error", "warn", "info", "debug"], defaultValue: "info"
            input "resetChildLogging", "button", title: "Reset Logging",
                description: "Set the effective log level of every Evohome child device to the Main app log level selected above. Use this after changing child log levels individually."
        }
    }
    page(name: "windowContactPage", title: "Window contacts by zone", install: false, uninstall: false)
    page(name: "awayOverridePage", title: "Away override preservation by zone", install: false, uninstall: false)
    page(name: "zoneSetpointPage", title: "Setpoint behaviour by zone", install: false, uninstall: false)
}

def zoneSetpointPage(params) {
    List zones = windowContactZoneRows()
    dynamicPage(name: "zoneSetpointPage", title: "Setpoint behaviour by zone", install: false, uninstall: false) {
        if (!zones) {
            section("Zones not available yet") {
                paragraph "Evohome zone details are loaded during the first installation sync. Save and install the app, wait for its first successful sync and for the zone child devices to appear, then reopen this page."
            }
        } else {
            section("Choose how each zone's setpoint changes expire") {
                paragraph "These settings are per zone and are managed here in the parent app. They apply to temperature changes and Heat-mode requests from Hubitat or HomeKit. Off remains a permanent 5 °C override, and Auto clears the zone override."
            }
            zones.each { zone ->
                section(zone.locationName + " / " + zone.zoneName) {
                    String policySetting = "zoneSetpointPolicy_" + zone.zoneId
                    String durationSetting = "zoneSetpointDuration_" + zone.zoneId
                    input(name: policySetting, type: "enum", title: "Setpoint change behaviour",
                        description: "Preserve keeps an existing override's expiry or permanence; if this zone follows its schedule, the new override lasts until the next schedule point.",
                        options: ["preserve": "Preserve existing override; otherwise until next schedule change",
                                  "next": "Until next schedule change", "permanent": "Permanent override",
                                  "duration": "For a fixed duration"],
                        defaultValue: "preserve", required: true)
                    if ((settings[policySetting] ?: "preserve") == "duration") {
                        input(name: durationSetting, type: "number", title: "Override duration (minutes)",
                            description: "Used when this zone's setpoint change behaviour is For a fixed duration.",
                            range: "1..1440", defaultValue: 60, required: true)
                    }
                }
            }
        }
    }
}

def windowContactPage(params) {
    List zones = windowContactZoneRows()
    dynamicPage(name: "windowContactPage", title: "Window contacts by zone", install: false, uninstall: false) {
        if (!zones) {
            section("Zones not available yet") {
                paragraph "Evohome zone details are loaded from the account during the first installation sync. Save and install the app, wait for its first successful sync and for the zone child devices to appear, then reopen this page. There is no sensor ID or text value to enter here."
            }
        } else {
            section("How contacts control each zone") {
                paragraph "Select any contact sensors assigned to each zone. If any selected sensor is open, that zone is held at a permanent 5 °C override. The original schedule or override is restored when all selected sensors close. While Evohome is in system-wide Heating Off, contact actions wait until the system leaves Off."
            }
            zones.each { zone ->
                section(zone.locationName + " / " + zone.zoneName + " window contacts") {
                    String settingName = "windowContacts_" + zone.zoneId
                    input(name: settingName, type: "capability.contactSensor",
                        title: "Contact sensors", description: "Select one or more sensors for this zone.",
                        multiple: true, required: false, showFilter: true)
                }
            }
        }
    }
}

def awayOverridePage(params) {
    List zones = windowContactZoneRows()
    dynamicPage(name: "awayOverridePage", title: "Away override preservation by zone", install: false, uninstall: false) {
        if (!zones) {
            section("Zones not available yet") {
                paragraph "Evohome zone details are loaded during the first installation sync. Save and install the app, wait for its first successful sync and for zone child devices to appear, then reopen this page."
            }
        } else {
            section("Choose which zone overrides to restore") {
                paragraph "When Hubitat changes to Away and the integration changes Evohome to Away, Evohome may clear room overrides. Enable this for each zone whose override should be saved and restored when the previous system mode returns. Permanent overrides are restored permanently. Temporary overrides are restored only while their original expiry is still in the future; otherwise the zone returns to its schedule."
            }
            zones.each { zone ->
                section(zone.locationName + " / " + zone.zoneName) {
                    input(name: "preserveAwayOverride_" + zone.zoneId, type: "bool",
                        title: "Preserve this zone's override when Away is enabled",
                        description: "Save and restore this zone's current permanent or unexpired temporary override.",
                        defaultValue: false)
                }
            }
        }
    }
}

private List windowContactZoneRows() {
    List rows = []
    (state.installation ?: []).each { loc ->
        String locationName = loc.locationInfo?.name?.toString() ?: "Evohome location"
        (loc.gateways ?: []).each { gw ->
            (gw.temperatureControlSystems ?: []).each { sys ->
                (sys.zones ?: []).each { zone ->
                    String zid = zone.zoneId?.toString()
                    if (zid && getChildDevice(zdni(zid))) {
                        rows << [zoneId: zid, zoneName: zone.name?.toString() ?: ("Zone " + zid), locationName: locationName]
                    }
                }
            }
        }
    }
    rows.sort { a, b -> (a.locationName + a.zoneName).compareToIgnoreCase(b.locationName + b.zoneName) }
    rows
}

private List windowContactsForZone(String zoneId) {
    def configured = settings["windowContacts_" + zoneId]
    if (configured instanceof List) return configured
    configured ? [configured] : []
}

private void subscribeAppEvents(Boolean refreshZones) {
    unsubscribe()
    subscribe(location, "mode", "locationModeChanged")
    Map sensorZones = [:]
    Map sensorDevices = [:]
    Map openStates = state.windowContactOpen ?: [:]
    Map snapshots = state.windowSnapshots ?: [:]
    Set zoneIds = (windowContactZoneRows().collect { it.zoneId } + openStates.keySet() + snapshots.keySet()) as Set
    zoneIds.each { zidValue ->
        String zid = zidValue.toString()
        List contacts = windowContactsForZone(zid)
        contacts.each { contact ->
            String sensorId = contact.id.toString()
            List assignedZones = sensorZones[sensorId] ?: []
            if (!assignedZones.contains(zid)) assignedZones << zid
            sensorZones[sensorId] = assignedZones
            sensorDevices[sensorId] = contact
        }
        updateWindowZoneState(zid, contacts)
    }
    state.windowSensorZones = sensorZones
    sensorDevices.each { sensorId, sensor -> subscribe(sensor, "contact", "windowSensorEvent") }
    logMessage("debug", "Subscribed to " + sensorDevices.size() + " unique window contact sensor(s) across " + zoneIds.size() + " zone(s).")
    if (refreshZones && state.token && state.tokenExpiry && now() < tokenRenewAt()) {
        Set activeZoneIds = ((sensorZones.values().flatten() ?: []) + snapshots.keySet()) as Set
        List locationIds = activeZoneIds.collect { getChildDevice(zdni(it.toString()))?.getDataValue("locationId") }.findAll { it }.unique()
        locationIds.each { getLocation(it.toString(), "windowContacts") }
    }
}

private Boolean updateWindowZoneState(String zoneId, List contacts = null) {
    List selected = contacts == null ? windowContactsForZone(zoneId) : contacts
    Boolean configured = !selected.isEmpty()
    Boolean anyOpen = selected.any { it.currentValue("contact")?.toString()?.equalsIgnoreCase("open") }
    Map openStates = state.windowContactOpen ?: [:]
    Map snapshots = state.windowSnapshots ?: [:]
    Map pending = state.pending ?: [:]
    Boolean previouslyTracked = openStates.containsKey(zoneId)
    Boolean previousOpen = openStates[zoneId] == true
    if (configured || previouslyTracked || snapshots[zoneId]) {
        openStates[zoneId] = anyOpen
        state.windowContactOpen = openStates
    } else {
        openStates.remove(zoneId)
    }
    if (!configured && !anyOpen && !snapshots[zoneId] && pending[zoneId]?.action == "windowOpen") {
        clearPendingZoneRequest(zoneId, pending[zoneId]?.version)
    }
    def child = getChildDevice(zdni(zoneId))
    child?.setWindowContactOpen(configured, anyOpen)
    if (previousOpen != anyOpen && (previouslyTracked || anyOpen)) {
        String zoneLabel = child?.displayName ?: ("zone " + zoneId)
        if (anyOpen) logMessage("info", "A selected window contact for " + zoneLabel + " opened. The zone will be held at a permanent 5 °C override once Evohome allows the change.")
        else logMessage("info", "All selected window contacts for " + zoneLabel + " are closed. The saved zone setting will be restored once Evohome allows the change.")
    } else {
        logMessage("debug", "Window contact state evaluated for zone " + zoneId + "; selected=" + selected.size() +
            ", anyOpen=" + anyOpen + ".")
    }
    anyOpen
}

void windowSensorEvent(evt) {
    String sensorId = evt?.deviceId?.toString()
    List zones = (state.windowSensorZones ?: [:])[sensorId] ?: []
    logMessage("debug", "Window contact event received; sensorId=" + sensorId + ", contact=" + evt?.value +
        ", affectedZones=" + zones.size() + ".")
    zones.each { updateWindowZoneState(it.toString()) }
    List locationIds = zones.collect { getChildDevice(zdni(it.toString()))?.getDataValue("locationId") }.findAll { it }.unique()
    locationIds.each { getLocation(it.toString(), "windowContacts") }
}

def appButtonHandler(String buttonName) {
    if (buttonName == "resetChildLogging") resetChildLogging()
}

private void resetChildLogging() {
    String level = (settings.prefLogLevel ?: "info").toLowerCase()
    List children = getChildDevices() ?: []
    Integer updatedCount = 0
    children.each { child ->
        try {
            child.setLogLevel(level)
            updatedCount++
        } catch (Exception e) {
            logMessage("warn", "Could not reset logging for " + child.displayName + ": " + e.message + ".")
        }
    }
    logMessage("info", "Reset Logging set the effective log level to " + level.capitalize() + " for " +
        updatedCount + " of " + children.size() + " Evohome child device(s).")
}

def installed() { initialize() }
def updated() { initialize() }
def uninstalled() { unschedule(); unsubscribe(); getChildDevices()?.each { deleteChildDevice(it.deviceNetworkId) } }

private void initialize() {
    unschedule()
    unsubscribe()
    state.root = (settings.apiHost ?: "https://mytotalconnectcomfort.com").trim().replaceAll('/+$', '')
    state.pending = state.pending ?: [:]
    state.zoneRequestVersions = state.zoneRequestVersions ?: [:]
    state.modeSnapshots = state.modeSnapshots ?: [:]
    state.awayZoneSnapshots = state.awayZoneSnapshots ?: [:]
    state.modeSyncOwned = state.modeSyncOwned ?: [:]
    state.modeSyncPreExistingAway = state.modeSyncPreExistingAway ?: [:]
    state.faultSnapshot = state.faultSnapshot ?: [:]
    state.windowContactOpen = state.windowContactOpen ?: [:]
    state.windowSnapshots = state.windowSnapshots ?: [:]
    state.windowOverrideApplied = state.windowOverrideApplied ?: [:]
    state.windowRestoreInFlight = state.windowRestoreInFlight ?: [:]
    state.windowSensorZones = state.windowSensorZones ?: [:]
    state.requestCounter = state.requestCounter ?: 0
    recoverPendingZoneRequests()
    Boolean heatingOnly = settings.heatingOnly != false
    logMessage("debug", "Initialising; API host=" + state.root + ", poll=" + (settings.pollMinutes ?: 5) +
        "m, debounce=" + (settings.debounceSeconds ?: 3) + "s, settle=" + (settings.settleSeconds ?: 8) +
        "s, heatingOnly=" + heatingOnly + ".")
    (getChildDevices() ?: []).findAll { it.deviceNetworkId?.startsWith("Evohome-Z-") }.each {
        it.setHeatingOnly(heatingOnly)
    }
    subscribeAppEvents(true)
    runIn(Math.max(1, (settings.pollMinutes ?: 5) as Integer) * 60, "poll", [overwrite: true])
    runIn(5, "start")
}

private void logMessage(String level, String message) {
    Map rank = [error: 1, warn: 2, info: 3, debug: 4]
    String selected = (settings.prefLogLevel ?: "info").toString().toLowerCase()
    String category = (level ?: "debug").toString().toLowerCase()
    if (!rank.containsKey(selected)) selected = "info"
    if (!rank.containsKey(category)) category = "debug"
    if (rank[category] <= rank[selected]) {
        switch (category) {
            case "error": log.error "Evohome Connect: " + message; break
            case "warn": log.warn "Evohome Connect: " + message; break
            case "info": log.info "Evohome Connect: " + message; break
            default: log.debug "Evohome Connect: " + message
        }
    }
}

String zoneSetpointPolicy(String zoneId) {
    String policySetting = "zoneSetpointPolicy_" + zoneId
    String policy = (settings[policySetting] ?: "preserve").toString()
    if (policy == "duration") {
        def configuredMinutes = settings["zoneSetpointDuration_" + zoneId]
        Integer minutes = configuredMinutes != null ? (configuredMinutes as Integer) : 60
        minutes = Math.max(1, Math.min(1440, minutes))
        return "duration:" + minutes
    }
    policy in ["preserve", "next", "permanent"] ? policy : "preserve"
}

def start() {
    if (state.token && state.tokenExpiry && now() < tokenRenewAt()) {
        scheduleTokenRenewal()
        logMessage("debug", "Start requested with a valid cached token; token renewal is scheduled. Refreshing the user account.")
        apiGet("/WebAPI/emea/api/v1/userAccount", "account")
    } else {
        logMessage("debug", "Start requested; token missing, expired, or due for renewal. Beginning " +
            (state.refreshToken ? "refresh-token" : "password") + " authentication.")
        auth(state.refreshToken ? "refresh_token" : "password")
    }
}

def poll() {
    runIn(Math.max(1, (settings.pollMinutes ?: 5) as Integer) * 60, "poll", [overwrite: true])
    logMessage("debug", "Poll started; tokenPresent=" + (state.token != null) + ", locations=" + (state.locations ?: []).size() + ".")
    if (!state.token || !state.tokenExpiry || now() >= tokenRenewAt()) {
        logMessage("debug", "Poll requires authentication or token renewal; renewalAt=" +
            (state.tokenRenewAt ? new Date(state.tokenRenewAt as Long).toString() : "not scheduled") + ".")
        start()
        return
    }
    (state.locations ?: []).each { getLocation(it.toString(), "poll") }
    refreshNextScheduledTimes()
    if (state.zoneSchedules == null || !state.scheduleUpdatedAt ||
        now() - (state.scheduleUpdatedAt as Long) > 3600000L) refreshSchedules()
}

void renewToken() {
    if (!state.token || !state.tokenExpiry) {
        logMessage("debug", "Scheduled token renewal fired without a usable access token; starting authentication.")
        start()
        return
    }
    Long renewAt = tokenRenewAt()
    if (now() < renewAt) {
        logMessage("debug", "Token renewal fired early; rescheduling for " + new Date(renewAt).toString() + ".")
        scheduleTokenRenewal()
        return
    }
    String grant = state.refreshToken ? "refresh_token" : "password"
    logMessage("debug", "Scheduled token renewal starting; grant=" + grant + ", expiresAt=" +
        new Date(state.tokenExpiry as Long).toString() + ".")
    auth(grant)
}

private Long tokenRenewalLeadMs() {
    Long lifetimeMs = ((state.tokenExpiresIn ?: 3600) as Long) * 1000L
    Math.min(300000L, Math.max(30000L, (long)(lifetimeMs / 10L)))
}

private Long tokenRenewAt() {
    if (state.tokenRenewAt) return state.tokenRenewAt as Long
    if (!state.tokenExpiry) return now()
    ((state.tokenExpiry as Long) - tokenRenewalLeadMs()) as Long
}

private void scheduleTokenRenewal() {
    if (!state.token || !state.tokenExpiry) {
        logMessage("debug", "Token renewal not scheduled because no access token expiry is available.")
        return
    }
    Long renewAt = (state.tokenExpiry as Long) - tokenRenewalLeadMs()
    state.tokenRenewAt = renewAt
    Long delaySeconds = Math.max(1L, (long)Math.ceil((renewAt - now()) / 1000.0d))
    runIn(delaySeconds as Integer, "renewToken", [overwrite: true])
    logMessage("debug", "Token renewal scheduled for " + new Date(renewAt).toString() +
        " (" + delaySeconds + "s from now, " + (tokenRenewalLeadMs() / 1000L) + "s before expiry).")
}

private void clearAccessToken(String reason) {
    logMessage("debug", "Clearing cached access token; reason=" + reason + ".")
    state.token = null
    state.tokenExpiry = null
    state.tokenRenewAt = null
    unschedule("renewToken")
}

private void fallbackToPasswordAuth(String reason) {
    logMessage("debug", "Refresh-token authentication failed; clearing cached refresh token and scheduling password authentication. reason=" + reason + ".")
    clearAccessToken("refresh-token authentication failed")
    state.refreshToken = null
    runIn(2, "start", [overwrite: true])
}

private void auth(String grant) {
    if (state.authInProgress && state.authStartedAt && now() - (state.authStartedAt as Long) < 120000L) {
        logMessage("debug", "Authentication request skipped; grant=" + grant +
            ", requestId=" + (state.authRequestId ?: "unknown") + " is already in progress.")
        return
    }
    if (state.authInProgress) logMessage("warn", "Previous authentication request appears stalled; starting a new " + grant + " request.")
    Map body = [grant_type: grant, scope: "EMEA-V1-Basic EMEA-V1-Anonymous EMEA-V1-Get-Current-User-Account EMEA-V1-Get-Location-Installation-Info-By-UserId"]
    if (grant == "password") { body.Username = settings.username; body.Password = settings.password }
    else body.refresh_token = state.refreshToken
    // Hubitat's asynchronous HTTP methods serialise Map bodies as JSON.
    // The evohome token endpoint expects application/x-www-form-urlencoded,
    // so send an encoded string rather than the Map used by the old sync call.
    String formBody = body.collect { key, value ->
        java.net.URLEncoder.encode(key.toString(), "UTF-8") + "=" +
            java.net.URLEncoder.encode(value == null ? "" : value.toString(), "UTF-8")
    }.join("&")
    String requestId = nextRequestId()
    String authPath = "/WebApi/Auth/OAuth/Token"
    state.authInProgress = true
    state.authRequestId = requestId
    state.authGrant = grant
    state.authStartedAt = now()
    logMessage("debug", "[" + requestId + "] POST " + state.root + authPath + "; grant=" + grant +
        ", form-urlencoded body prepared; username, password, refresh token and API key are redacted.")
    try {
        asynchttpPost("apiResponse", [
            uri: apiBase(), path: "/Auth/OAuth/Token",
            headers: [Authorization: "Basic YjAxM2FhMjYtOTcyNC00ZGJkLTg4OTctMDQ4YjlhYWRhMjQ5OnRlc3Q=",
                      Accept: "application/json, application/xml, text/json, text/x-json, text/javascript, text/xml",
                      "Content-Type": "application/x-www-form-urlencoded; charset=utf-8"],
            body: formBody, timeout: 30, followRedirects: true
        ], [action: "auth", grant: grant, requestId: requestId, method: "POST", path: authPath, requestAt: now()])
    } catch (Exception e) {
        state.authInProgress = false
        state.authRequestId = null
        logMessage("error", "[" + requestId + "] Could not queue " + grant + " authentication request.")
        if (grant == "refresh_token") fallbackToPasswordAuth("refresh request could not be queued")
        else clearAccessToken("password request could not be queued")
    }
}

private String apiBase() {
    state.root + "/WebApi"
}

private Map headers() {
    [Authorization: "bearer " + state.token, applicationId: "b013aa26-9724-4dbd-8897-048b9aada249", Accept: "application/json"]
}

private String nextRequestId() {
    state.requestCounter = ((state.requestCounter ?: 0) as Integer) + 1
    "req-" + state.requestCounter
}

private String debugJson(def value, Integer maxLength = 1800) {
    String text
    try { text = groovy.json.JsonOutput.toJson(value) }
    catch (Exception ignored) { text = String.valueOf(value) }
    if (text.length() > maxLength) text = text.substring(0, maxLength) + "... [truncated; " + text.length() + " chars]"
    text
}

private String responseSummary(String action, def payload) {
    if (action == "auth") {
        return "access_token=" + (payload?.access_token ? "present (redacted)" : "missing") +
            ", refresh_token=" + (payload?.refresh_token ? "present (redacted)" : "missing") +
            ", expires_in=" + (payload?.expires_in ?: "unknown")
    }
    if (action == "account") return "userId=" + (payload?.userId ?: "missing") + " (other account fields omitted)"
    if (action == "installation" && payload instanceof List) {
        return "locations=" + payload.size() + ", IDs=" +
            payload.collect { it?.locationInfo?.locationId?.toString() }.findAll { it }.join(",")
    }
    debugJson(payload)
}

private void apiGet(String path, String action, Map meta = [:], Map query = [:]) {
    Map params = [uri: apiBase(), path: path, headers: headers(), contentType: "application/json", timeout: 30]
    if (query) params.query = query
    String requestId = nextRequestId()
    logMessage("debug", "[" + requestId + "] GET " + apiBase() + path + (query ? " query=" + debugJson(query, 500) : "") +
        "; action=" + action + (meta ? ", context=" + debugJson(meta, 500) : ""))
    asynchttpGet("apiResponse", params,
        [action: action, requestId: requestId, method: "GET", path: path, requestAt: now()] + meta)
}

private void apiPut(String path, Map body, String action, Map meta = [:]) {
    String requestId = nextRequestId()
    logMessage("debug", "[" + requestId + "] PUT " + apiBase() + path + "; action=" + action +
        ", body=" + debugJson(body, 1200) + (meta ? ", context=" + debugJson(meta, 500) : ""))
    asynchttpPut("apiResponse", [uri: apiBase(), path: path, headers: headers(), contentType: "application/json", body: body, timeout: 30],
        [action: action, requestId: requestId, method: "PUT", path: path, requestAt: now()] + meta)
}

void apiResponse(resp, Map meta) {
    String action = meta?.action ?: "unknown"
    String requestId = meta?.requestId ?: action
    Map responseHeaders = resp?.getHeaders() instanceof Map ? resp.getHeaders() : [:]
    def responseTypeEntry = responseHeaders.find { key, value -> key?.toString()?.equalsIgnoreCase("Content-Type") }
    String responseType = responseTypeEntry?.value?.toString() ?: "unknown"
    Long elapsed = meta?.requestAt ? now() - (meta.requestAt as Long) : null
    logMessage("debug", "[" + requestId + "] response received; method=" + (meta?.method ?: "unknown") +
        ", action=" + action + (meta?.grant ? ", grant=" + meta.grant : "") +
        ", HTTP " + (resp?.status ?: "transport error") + ", Content-Type=" + responseType +
        (elapsed != null ? ", elapsed=" + elapsed + "ms." : "."))
    if (action == "auth") {
        if (requestId != state.authRequestId) {
            logMessage("debug", "[" + requestId + "] Ignoring stale authentication response; current requestId=" +
                (state.authRequestId ?: "none") + ".")
            return
        }
        state.authInProgress = false
        state.authRequestId = null
    }
    if (resp?.hasError() || !(resp?.status >= 200 && resp?.status < 300)) {
        def errorLocation = responseHeaders.find { key, value -> key?.toString()?.equalsIgnoreCase("Location") }
        String failure = "[" + requestId + "] " + action + " failed; HTTP " + (resp?.status ?: "transport error")
        if (errorLocation?.value) failure += ", redirect: " + errorLocation.value
        logMessage("error", failure)
        if (action == "auth") {
            if (meta?.grant == "refresh_token") fallbackToPasswordAuth("HTTP " + (resp?.status ?: "transport error"))
            else clearAccessToken("password authentication failed with HTTP " + (resp?.status ?: "transport error"))
            return
        }
        if (resp?.status == 401) {
            clearAccessToken("API returned HTTP 401")
            logMessage("debug", "Scheduling authentication after HTTP 401; refresh token will be tried first if available.")
            runIn(2, "start", [overwrite: true])
        }
        if (action == "zoneBeforeWrite") {
            failPendingZonePreflight(meta, "HTTP " + (resp?.status ?: "transport error"))
        } else if (action == "location" && meta?.reason?.toString() == "zonePreflight") {
            failPendingZonePreflight(meta, "HTTP " + (resp?.status ?: "transport error"))
        } else if (action in ["zoneWrite", "zoneClear", "systemWrite", "dhwWrite"]) {
            if (action in ["zoneWrite", "zoneClear"]) handleWindowWriteResponse(meta, false)
            runIn(5, "poll", [overwrite: true])
        }
        return
    }
    def json
    try {
        json = resp.json
    } catch (Exception e) {
        def locationEntry = responseHeaders.find { key, value -> key?.toString()?.equalsIgnoreCase("Location") }
        String detail = "[" + requestId + "] " + action + " returned a non-JSON response; HTTP " + resp?.status +
            ", Content-Type: " + responseType
        if (locationEntry?.value) detail += ", redirect: " + locationEntry.value
        logMessage("error", detail)
        if (action == "zoneBeforeWrite") {
            failPendingZonePreflight(meta, "non-JSON response")
        } else if (action == "location" && meta?.reason?.toString() == "zonePreflight") {
            failPendingZonePreflight(meta, "non-JSON response")
        }
        if (action in ["zoneWrite", "zoneClear"] && meta?.windowAction) handleWindowWriteResponse(meta, false)
        if (action == "auth" && meta?.grant == "refresh_token") {
            fallbackToPasswordAuth("non-JSON authentication response")
        } else if (action == "auth") {
            clearAccessToken("password authentication returned non-JSON")
        }
        return
    }
    logMessage("debug", "[" + requestId + "] parsed " + action + " response: " + responseSummary(action, json))
    Map obj = json instanceof Map ? json : [:]
    switch (action) {
        case "auth":
            state.token = obj.access_token
            if (obj.refresh_token) state.refreshToken = obj.refresh_token
            if (obj.expires_in) state.tokenExpiresIn = obj.expires_in as Long
            else state.tokenExpiresIn = 3600L
            state.tokenExpiry = now() + (state.tokenExpiresIn as Long) * 1000L
            state.tokenRenewAt = null
            if (state.token) {
                logMessage("debug", "[" + requestId + "] Authentication token received; expires_in=" +
                    (obj.expires_in ?: "unknown") + "s; token values withheld. Requesting user account.")
                scheduleTokenRenewal()
                apiGet("/WebAPI/emea/api/v1/userAccount", "account")
            } else {
                logMessage("error", "[" + requestId + "] Auth response contained no access_token.")
                if (meta?.grant == "refresh_token") fallbackToPasswordAuth("refresh response had no access token")
                else clearAccessToken("password response had no access token")
            }
            break
        case "account":
            state.userId = obj.userId?.toString()
            if (state.userId) {
                logMessage("debug", "User account response received; userId=" + state.userId + ". Requesting installation configuration.")
                apiGet("/WebAPI/emea/api/v1/location/installationInfo", "installation", [:],
                    [userId: state.userId, includeTemperatureControlSystems: "True"])
            } else {
                logMessage("error", "[" + requestId + "] User account response contained no userId; installation request not sent.")
            }
            break
        case "installation":
            installChildren(json)
            subscribeAppEvents(false)
            (state.locations ?: []).each { getLocation(it.toString(), "poll") }
            refreshSchedules()
            break
        case "location":
            acceptLocation(json, meta.locationId?.toString(), meta.reason?.toString(), meta)
            break
        case "zoneBeforeWrite":
            writePendingZone(meta, obj)
            break
        case "zoneWrite":
        case "zoneClear":
        case "systemWrite":
        case "dhwWrite":
            if (action in ["zoneWrite", "zoneClear"]) handleWindowWriteResponse(meta, true)
            logMessage("debug", "[" + requestId + "] Write response received for kind=" + meta.kind +
                ", id=" + meta.id + "; scheduling state reconciliation in " + (settings.settleSeconds ?: 8) + "s.")
            runIn(Math.max(2, (settings.settleSeconds ?: 8) as Integer), "refreshAfterWrite",
                [data: [kind: meta.kind, id: meta.id, restoreZoneOverrides: meta.restoreZoneOverrides == true], overwrite: true])
            break
        case "zoneStatus":
            publishZone(obj, meta.zoneId.toString())
            break
        case "dhwStatus":
            def dhwChild = getChildDevice(hdni(meta.id.toString()))
            processFaults("dhw", meta.id.toString(), dhwChild?.displayName ?: "Hot water", obj.activeFaults)
            dhwChild?.parseDhwStatus(obj)
            break
        case "schedule":
            acceptSchedule(json, meta.zoneId.toString())
            break
    }
}

private void installChildren(def payload) {
    List locs = payload instanceof List ? payload : []
    logMessage("debug", "Installing/refreshing child devices from configuration: " + locs.size() + " location(s).")
    state.installation = locs
    state.locations = locs.collect { it.locationInfo?.locationId?.toString() }.findAll { it }
    locs.each { loc ->
        logMessage("debug", "Location configuration: locationId=" + loc.locationInfo?.locationId +
            ", name=" + loc.locationInfo?.name + ", gatewayCount=" + (loc.gateways ?: []).size() + ".")
        (loc.gateways ?: []).each { gw ->
            (gw.temperatureControlSystems ?: []).each { sys ->
                String sid = sys.systemId.toString()
                logMessage("debug", "System configuration: systemId=" + sid +
                    ", zoneCount=" + (sys.zones ?: []).size() + ", dhwId=" + sys.dhw?.dhwId + ".")
                def controller = getChildDevice(sdni(sid)) ?: addChildDevice("tljungberg", "Evohome System Controller", sdni(sid), [label: (loc.locationInfo?.name ?: "Evohome") + " System"])
                controller?.updateDataValue("systemId", sid)
                controller?.updateDataValue("locationId", loc.locationInfo.locationId.toString())
                (sys.zones ?: []).each { z ->
                    String zid = z.zoneId.toString()
                    def zone = getChildDevice(zdni(zid)) ?: addChildDevice("tljungberg", "Evohome Heating Zone", zdni(zid), [label: (z.name ?: "Zone") + " (evohome)"])
                    zone?.updateDataValue("zoneId", zid)
                    zone?.updateDataValue("systemId", sid)
                    zone?.updateDataValue("locationId", loc.locationInfo.locationId.toString())
                    zone?.updateDataValue("minSetpoint", (z.heatSetpointCapabilities?.minHeatSetpoint ?: 5).toString())
                    zone?.updateDataValue("maxSetpoint", (z.heatSetpointCapabilities?.maxHeatSetpoint ?: 35).toString())
                    zone?.setHeatingOnly(settings.heatingOnly != false)
                }
                if (sys.dhw?.dhwId) {
                    String hid = sys.dhw.dhwId.toString()
                    def hot = getChildDevice(hdni(hid)) ?: addChildDevice("tljungberg", "Evohome Hot Water", hdni(hid), [label: "Evohome Hot Water"])
                    hot?.updateDataValue("dhwId", hid)
                    hot?.updateDataValue("systemId", sid)
                    hot?.updateDataValue("locationId", loc.locationInfo.locationId.toString())
                }
            }
        }
    }
    logMessage("info", "Evohome installation configuration loaded. Found " + state.locations.size() + " location(s).")
}

private String zdni(String id) { "Evohome-Z-" + id }
private String sdni(String id) { "Evohome-S-" + id }
private String hdni(String id) { "Evohome-H-" + id }

private void refreshSchedules() {
    state.zoneSchedules = state.zoneSchedules ?: [:]
    (state.installation ?: []).each { loc ->
        (loc.gateways ?: []).each { gw ->
            (gw.temperatureControlSystems ?: []).each { sys ->
                (sys.zones ?: []).each { zone ->
                    apiGet("/WebAPI/emea/api/v1/temperatureZone/" + zone.zoneId + "/schedule",
                        "schedule", [zoneId: zone.zoneId.toString()])
                }
            }
        }
    }
    state.scheduleUpdatedAt = now()
}

private void acceptSchedule(def schedule, String zoneId) {
    Map scheduleData = schedule instanceof Map ? schedule as Map : [:]
    if (scheduleData.schedule instanceof Map) scheduleData = scheduleData.schedule as Map
    else if (scheduleData.data instanceof Map && scheduleData.data.dailySchedules) scheduleData = scheduleData.data as Map
    List dailySchedules = scheduleData.dailySchedules instanceof List ? scheduleData.dailySchedules :
        (schedule instanceof List ? schedule as List : [])
    List entries = []
    dailySchedules.each { day ->
        (day.switchpoints ?: []).each { point ->
            String dayName = (day.dayOfWeek ?: day.day ?: "").toString()
            String time = (point.timeOfDay ?: point.time ?: "").toString()
            def temp = point.heatSetpoint != null ? point.heatSetpoint :
                (point.temperature != null ? point.temperature : point.setpoint)
            if (dayName && time) entries << [day: dayName, time: time, temperature: temp]
        }
    }
    if (!entries) {
        logMessage("debug", "Schedule contained no usable switchpoints; zoneId=" + zoneId +
            ", responseKeys=" + (scheduleData.keySet()?.join(",") ?: "none") +
            ", dayCount=" + dailySchedules.size() + ".")
        return
    }
    Map schedules = state.zoneSchedules ?: [:]
    schedules[zoneId] = entries.collect { [day: it.day.toString(), time: it.time.toString(), temperature: it.temperature] }
    state.zoneSchedules = schedules
    updateNextScheduledTime(zoneId)
}

private void refreshNextScheduledTimes() {
    Map schedules = state.zoneSchedules ?: [:]
    schedules.keySet().each { zoneId -> updateNextScheduledTime(zoneId.toString()) }
}

private Map calculateNextSchedulePoint(List entries, TimeZone tz, long referenceTime) {
    Calendar nowCal = Calendar.getInstance(tz)
    nowCal.setTimeInMillis(referenceTime)
    Calendar candidate = null
    Map candidatePoint = null
    Map dayNumbers = [sunday: Calendar.SUNDAY, monday: Calendar.MONDAY, tuesday: Calendar.TUESDAY,
                      wednesday: Calendar.WEDNESDAY, thursday: Calendar.THURSDAY, friday: Calendar.FRIDAY, saturday: Calendar.SATURDAY]
    entries.each { point ->
        Integer targetDay = dayNumbers[point.day?.toString()?.toLowerCase()]
        if (!targetDay) return
        String[] parts = point.time?.toString()?.split(":")
        if (!parts || parts.length < 2) return
        Calendar c = Calendar.getInstance(tz)
        c.setTimeInMillis(referenceTime)
        c.set(Calendar.HOUR_OF_DAY, parts[0] as Integer)
        c.set(Calendar.MINUTE, parts[1] as Integer)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        int addDays = (targetDay - c.get(Calendar.DAY_OF_WEEK) + 7) % 7
        c.add(Calendar.DATE, addDays)
        if (!c.after(nowCal)) c.add(Calendar.DATE, 7)
        if (candidate == null || c.before(candidate)) {
            candidate = c
            candidatePoint = point
        }
    }
    if (!candidate) return null
    [time: candidate.getTime().format("yyyy-MM-dd'T'HH:mm:ssXXX", tz), temperature: candidatePoint?.temperature]
}

private Map updateNextScheduledTime(String zoneId, long referenceTime = now()) {
    List entries = (state.zoneSchedules ?: [:])[zoneId] as List
    if (!entries) return null
    TimeZone tz = location.timeZone ?: TimeZone.getTimeZone("Europe/London")
    Map nextPoint = calculateNextSchedulePoint(entries, tz, referenceTime)
    if (!nextPoint) return null
    def child = getChildDevice(zdni(zoneId))
    child?.setNextScheduledTime(nextPoint.time.toString())
    if (nextPoint.temperature != null) child?.setNextScheduledSetpoint(nextPoint.temperature as BigDecimal)
    logMessage("debug", "Next schedule point calculated from the current time; zoneId=" + zoneId +
        ", time=" + nextPoint.time + ", setpoint=" + (nextPoint.temperature ?: "not supplied") + " C.")
    nextPoint
}

private void getLocation(String id, String reason, Map extra = [:]) {
    apiGet("/WebAPI/emea/api/v1/location/" + id + "/status", "location",
        [locationId: id, reason: reason] + extra, [includeTemperatureControlSystems: "True"])
}

private void acceptLocation(def payload, String locationId, String reason, Map meta = [:]) {
    if (!(payload instanceof Map)) {
        if (reason == "zonePreflight") failPendingZonePreflight(meta, "location response was not an object")
        return
    }
    logMessage("debug", "Processing location status; locationId=" + locationId + ", reason=" + reason +
        ", gateways=" + (payload.gateways ?: []).size() + ".")
    state.lastStatus = state.lastStatus ?: [:]
    state.lastStatus[locationId] = payload
    processFaults("location", locationId, payload.locationName ?: "Evohome location", payload.activeFaults)
    Boolean preflightZoneFound = false
    (payload.gateways ?: []).each { gw ->
        processFaults("gateway", gw.gatewayId?.toString(), "Evohome gateway", gw.activeFaults)
        (gw.temperatureControlSystems ?: []).each { sys ->
            String sid = sys.systemId.toString()
            Map ms = sys.systemModeStatus ?: [:]
            String mode = normalMode(ms.mode?.toString())
            logMessage("debug", "Status for system " + sid + ": mode=" + mode +
                ", zones=" + (sys.zones ?: []).size() + ", dhwPresent=" + (sys.dhw?.dhwId != null) + ".")
            def controller = getChildDevice(sdni(sid))
            processFaults("system", sid, controller?.displayName ?: "Evohome system", sys.activeFaults)
            controller?.parseSystemStatus(mode, ms, sys.activeFaults)
            (sys.zones ?: []).each {
                String zid = it.zoneId.toString()
                publishZone(it as Map, zid, mode)
                reconcileWindowOverride(zid, mode)
            }
            if (sys.dhw?.dhwId) {
                String hid = sys.dhw.dhwId.toString()
                def hot = getChildDevice(hdni(hid))
                processFaults("dhw", hid, hot?.displayName ?: "Evohome hot water", sys.dhw.activeFaults)
                hot?.parseDhwStatus(sys.dhw as Map)
            }
            if (reason == "zonePreflight" && (sys.zones ?: []).any { it.zoneId.toString() == meta.zoneId?.toString() }) {
                preflightZoneFound = true
                preflightZoneWrite(meta.zoneId.toString(), mode, meta.version)
            }
            if (reason == "syncEnter") syncEnter(sid, mode, ms, sys)
            if (reason == "syncLeave") syncLeave(sid, mode)
            if (reason == "syncRestore" && meta.systemId?.toString() == sid) restoreAwayZoneOverrides(sid, sys, mode)
        }
    }
    if (reason == "zonePreflight" && !preflightZoneFound) {
        failPendingZonePreflight(meta, "zone was absent from the location response")
    }
}

private void preflightZoneWrite(String zid, String mode, def version) {
    Map pending = (state.pending ?: [:])[zid]
    if (!pending) return
    if (version == null || pending.version?.toString() != version.toString()) {
        logMessage("debug", "Ignoring stale location preflight for zone " + zid + "; response version=" + version +
            ", current version=" + pending.version + ".")
        return
    }
    if (mode == "off") {
        clearPendingZoneRequest(zid, pending.version)
        String zoneLabel = getChildDevice(pending.dni)?.displayName ?: ("zone " + zid)
        if (pending.action in ["windowOpen", "windowRestore"]) {
            logMessage("info", "Evohome is in Heating Off, so the window contact action for " + zoneLabel + " is deferred until the system leaves Off.")
        } else {
            getChildDevice(pending.dni)?.refresh()
            logMessage("info", "Evohome is in Heating Off, so the requested change for " + zoneLabel + " was ignored.")
        }
        return
    }
    Map current = beginPendingZoneStage(zid, pending.version, "zonePreflight")
    if (!current) return
    try {
        apiGet("/WebAPI/emea/api/v1/temperatureZone/" + zid + "/status", "zoneBeforeWrite",
            [zoneId: zid, version: current.version])
    } catch (Exception e) {
        failPendingZonePreflight([zoneId: zid, version: current.version], "request could not be queued: " + e.message)
    }
}

private void publishZone(Map z, String zid, String systemMode = null) {
    def child = getChildDevice(zdni(zid))
    if (!child) return
    Map sp = z.heatSetpointStatus ?: z.setpointStatus ?: [:]
    Map ts = z.temperatureStatus ?: [:]
    Map values = [temperature: ts.temperature, heatingSetpoint: sp.targetTemperature ?: sp.targetHeatTemperature,
        setpointMode: sp.setpointMode, until: sp.until ?: sp.timeUntil, systemMode: systemMode ?: currentSystemMode(child.getDataValue("systemId")),
        available: ts.isAvailable, activeFaults: z.activeFaults, nextScheduledTime: child.currentValue("nextScheduledTime")]
    processFaults("zone", zid, child.displayName ?: ("Evohome zone " + zid), z.activeFaults)
    if ((state.pending ?: [:])[zid]) {
        values.heatingSetpoint = null
        values.setpointMode = null
        values.until = null
    }
    if ((state.windowOverrideApplied ?: [:])[zid] == true) {
        values.heatingSetpoint = 5.0
        values.setpointMode = "PermanentOverride"
        values.until = null
    }
    child.parseEvohomeStatus(values)
}

private void processFaults(String kind, String id, String label, def faults) {
    if (!id || !(faults instanceof List)) return
    String key = kind + ":" + id
    List normalised = faults.collect { f ->
        [faultType: f?.faultType?.toString() ?: "UnknownFault", since: f?.since?.toString()]
    }
    Map snapshots = state.faultSnapshot ?: [:]
    List previous = snapshots[key] instanceof List ? snapshots[key] : []
    Set previousKeys = previous.collect { it.faultType.toString() + "|" + (it.since ?: "") } as Set
    List added = normalised.findAll { fault ->
        String signature = fault.faultType.toString() + "|" + (fault.since ?: "")
        !previousKeys.contains(signature)
    }
    snapshots[key] = normalised
    state.faultSnapshot = snapshots
    if (!added) return
    added.each { fault ->
        String text = readableFault(fault.faultType.toString())
        String message = "Evohome reported " + text + " for " + label
        if (fault.since) message += " since " + fault.since
        message += "."
        logMessage("warn", message)
        (settings.faultNotifyDevices ?: []).each { notifier ->
            try { notifier.deviceNotification(message) }
            catch (Exception e) { logMessage("error", "Could not send fault notification to " + notifier.displayName + ": " + e.message) }
        }
    }
}

private String readableFault(String faultType) {
    Map names = [
        TempZoneActuatorLowBattery: "actuator low battery",
        TempZoneSensorLowBattery: "sensor low battery",
        TempZoneActuatorCommunicationLost: "actuator communication lost",
        TempZoneSensorCommunicationLost: "sensor communication lost"
    ]
    names[faultType] ?: faultType.replaceAll(/([a-z])([A-Z])/, '$1 $2').toLowerCase()
}

private String currentSystemMode(String sid) {
    def all = (state.lastStatus ?: [:]).values().find { x -> (x.gateways ?: []).any { g -> (g.temperatureControlSystems ?: []).any { it.systemId.toString() == sid } } }
    def sys = all?.gateways?.collectMany { it.temperatureControlSystems ?: [] }?.find { it.systemId.toString() == sid }
    normalMode(sys?.systemModeStatus?.mode?.toString())
}

private String normalMode(String raw) {
    [Auto: "auto", AutoWithEco: "economy", Away: "away", DayOff: "dayOff", HeatingOff: "off", Custom: "custom", AutoWithReset: "auto"][raw] ?: (raw ?: "unknown").toLowerCase()
}

void refreshAfterWrite(Map d) {
    logMessage("debug", "Post-write refresh requested; kind=" + d.kind + ", id=" + d.id + ".")
    if (d.kind == "zone") {
        apiGet("/WebAPI/emea/api/v1/temperatureZone/" + d.id + "/status", "zoneStatus", [zoneId: d.id])
    } else {
        String dni = d.kind == "system" ? sdni(d.id) : hdni(d.id)
        def c = getChildDevice(dni)
        String lid = c?.getDataValue("locationId")
        if (lid) getLocation(lid, d.restoreZoneOverrides == true ? "syncRestore" : "poll",
            d.restoreZoneOverrides == true ? [systemId: d.id.toString()] : [:])
        if (d.kind == "dhw") apiGet("/WebAPI/emea/api/v1/domesticHotWater/" + d.id + "/status", "dhwStatus", [id: d.id])
    }
}

private void reconcileWindowOverride(String zid, String systemMode) {
    Map openStates = state.windowContactOpen ?: [:]
    Map snapshots = state.windowSnapshots ?: [:]
    Map appliedStates = state.windowOverrideApplied ?: [:]
    Map restoreInFlight = state.windowRestoreInFlight ?: [:]
    if (!openStates.containsKey(zid) && !snapshots.containsKey(zid)) return
    Boolean open = openStates[zid] == true
    Map pending = (state.pending ?: [:])[zid]
    if (systemMode == "off") {
        if (open || snapshots[zid]) {
            logMessage("debug", "Window contact action for zone " + zid + " is deferred while Evohome is in Heating Off.")
        }
        return
    }
    if (open) {
        if (!snapshots[zid] || appliedStates[zid] != true) {
            queueWindowAction(zid, "windowOpen")
        }
        return
    }
    if (!snapshots[zid]) {
        if (pending?.action == "windowOpen") {
            clearPendingZoneRequest(zid, pending.version)
            logMessage("debug", "Cancelled the pending window-open override for zone " + zid + " because all contacts closed before the override was applied.")
        }
        return
    }
    if (appliedStates[zid] == true && restoreInFlight[zid] != true) {
        queueWindowAction(zid, "windowRestore")
    } else if (appliedStates[zid] != true) {
        snapshots.remove(zid)
        state.windowSnapshots = snapshots
        logMessage("debug", "Discarded the saved window restore point for zone " + zid + " because its 5 C override was never applied.")
    }
}

private Integer zonePreflightTimeoutSeconds() { 40 }

private Integer nextZoneRequestVersion(String zid, Map pending = null) {
    Map versions = new LinkedHashMap(state.zoneRequestVersions ?: [:])
    Map currentPending = pending != null ? pending[zid] as Map : (state.pending ?: [:])[zid] as Map
    Integer lastVersion = (versions[zid] ?: 0) as Integer
    Integer pendingVersion = (currentPending?.version ?: 0) as Integer
    Integer nextVersion = Math.max(lastVersion, pendingVersion) + 1
    versions[zid] = nextVersion
    state.zoneRequestVersions = versions
    nextVersion
}

private void scheduleZoneFlush(String zid, Integer version, Integer delaySeconds) {
    runIn(Math.max(1, delaySeconds ?: 1), "flushZone",
        [data: [zoneId: zid, version: version], overwrite: false])
}

private Map beginPendingZoneStage(String zid, def version, String phase) {
    Map pending = new LinkedHashMap(state.pending ?: [:])
    Map current = pending[zid] instanceof Map ? new LinkedHashMap(pending[zid] as Map) : null
    if (!current || version == null || current.version?.toString() != version.toString()) return null
    current.phase = phase
    current.stageStartedAt = now()
    pending[zid] = current
    state.pending = pending
    schedulePendingZoneWatchdog()
    current
}

private Map clearPendingZoneRequest(String zid, def expectedVersion = null) {
    Map pending = new LinkedHashMap(state.pending ?: [:])
    Map current = pending[zid] instanceof Map ? pending[zid] as Map : null
    if (!current) return null
    if (expectedVersion != null && current.version?.toString() != expectedVersion.toString()) return null
    pending.remove(zid)
    state.pending = pending
    schedulePendingZoneWatchdog()
    current
}

private void failPendingZonePreflight(Map meta, String reason) {
    String zid = meta?.zoneId?.toString()
    if (!zid || meta?.version == null) {
        logMessage("debug", "Ignoring zone preflight failure without a zone ID and request version; reason=" + reason + ".")
        return
    }
    Map cleared = clearPendingZoneRequest(zid, meta.version)
    if (!cleared) {
        logMessage("debug", "Ignoring failed zone preflight for zone " + zid +
            " because its pending request has already changed or cleared; reason=" + reason + ".")
        return
    }
    logMessage("warn", "The preflight request for zone " + zid + " did not complete (" + reason +
        "). The pending Hubitat change was cancelled and Evohome status will be refreshed.")
    getChildDevice(cleared.dni ?: zdni(zid))?.refresh()
}

private void schedulePendingZoneWatchdog() {
    Long earliestDeadline = null
    (state.pending ?: [:]).each { zid, item ->
        if (item instanceof Map && item.phase in ["locationPreflight", "zonePreflight"] && item.stageStartedAt) {
            Long deadline = (item.stageStartedAt as Long) + zonePreflightTimeoutSeconds() * 1000L
            if (earliestDeadline == null || deadline < earliestDeadline) earliestDeadline = deadline
        }
    }
    unschedule("checkPendingZoneRequests")
    if (earliestDeadline != null) {
        Integer delay = Math.max(1, Math.ceil((earliestDeadline - now()) / 1000.0d) as Integer)
        runIn(delay, "checkPendingZoneRequests", [overwrite: true])
    }
}

void checkPendingZoneRequests() {
    Long currentTime = now()
    List expired = []
    (state.pending ?: [:]).each { zid, item ->
        if (item instanceof Map && item.phase in ["locationPreflight", "zonePreflight"] && item.stageStartedAt) {
            Long deadline = (item.stageStartedAt as Long) + zonePreflightTimeoutSeconds() * 1000L
            if (deadline <= currentTime) expired << [zoneId: zid.toString(), version: item.version, phase: item.phase]
        }
    }
    expired.each { request ->
        Map cleared = clearPendingZoneRequest(request.zoneId, request.version)
        if (cleared) {
            logMessage("warn", "The " + request.phase + " request for zone " + request.zoneId +
                " exceeded " + zonePreflightTimeoutSeconds() + " seconds. Its pending state was cleared; refreshing Evohome status.")
            getChildDevice(cleared.dni ?: zdni(request.zoneId))?.refresh()
        }
    }
    schedulePendingZoneWatchdog()
}

private void recoverPendingZoneRequests() {
    Map pending = new LinkedHashMap(state.pending ?: [:])
    Map versions = new LinkedHashMap(state.zoneRequestVersions ?: [:])
    Long currentTime = now()
    List toRefresh = []
    List toRemove = []
    pending.each { zidValue, itemValue ->
        String zid = zidValue.toString()
        Map item = itemValue instanceof Map ? itemValue as Map : [:]
        Integer lastVersion = (versions[zid] ?: 0) as Integer
        Integer pendingVersion = (item.version ?: 0) as Integer
        if (pendingVersion > lastVersion) versions[zid] = pendingVersion
        Boolean valid = item.version != null && item.action != null
        if (!valid) {
            toRemove << zidValue
            toRefresh << zid
        } else if (item.phase == "debounce" && item.debounceUntil) {
            Long due = item.debounceUntil as Long
            Integer delay = Math.max(1, Math.ceil((due - currentTime) / 1000.0d) as Integer)
            scheduleZoneFlush(zid, item.version as Integer, delay)
        } else if (item.phase in ["locationPreflight", "zonePreflight"] && item.stageStartedAt) {
            Long deadline = (item.stageStartedAt as Long) + zonePreflightTimeoutSeconds() * 1000L
            if (deadline <= currentTime) {
                toRemove << zidValue
                toRefresh << zid
            }
        } else {
            toRemove << zidValue
            toRefresh << zid
        }
    }
    toRemove.each { pending.remove(it) }
    state.pending = pending
    state.zoneRequestVersions = versions
    toRefresh.unique().each { zid ->
        logMessage("warn", "Cleared an abandoned pending request for zone " + zid +
            " during app startup. The normal installation status sync will reconcile the child device.")
    }
    schedulePendingZoneWatchdog()
}

private void queueWindowAction(String zid, String action) {
    Map pending = state.pending ?: [:]
    Map previous = pending[zid] ?: [version: 0]
    if (previous.action == action) return
    String dni = zdni(zid)
    Integer version = nextZoneRequestVersion(zid, pending)
    Long requestedAt = now()
    pending[zid] = [zoneId: zid, dni: dni, action: action, version: version,
        createdAt: requestedAt, phase: "debounce", debounceUntil: requestedAt + 1000L]
    state.pending = pending
    logMessage("debug", "Queued window contact action for zone " + zid + "; action=" + action + ", version=" + version + ".")
    scheduleZoneFlush(zid, version, 1)
}

private Long parseWindowExpiry(def value) {
    if (value instanceof Date) return value.time
    String text = value?.toString()
    if (!text) return null
    for (String pattern : ["yyyy-MM-dd'T'HH:mm:ssX", "yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ss'Z'"]) {
        try {
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(pattern)
            format.setTimeZone(TimeZone.getTimeZone("UTC"))
            return format.parse(text).time
        } catch (Exception ignored) { }
    }
    null
}

private void handleWindowWriteResponse(Map meta, Boolean success) {
    String windowAction = meta?.windowAction?.toString()
    String zid = meta?.id?.toString()
    if (!windowAction || !zid) return
    Map applied = state.windowOverrideApplied ?: [:]
    Map snapshots = state.windowSnapshots ?: [:]
    Map restoreInFlight = state.windowRestoreInFlight ?: [:]
    if (windowAction == "open") {
        applied[zid] = success == true
        state.windowOverrideApplied = applied
        if (!success) logMessage("warn", "The window-open 5 °C override for " + (getChildDevice(zdni(zid))?.displayName ?: ("zone " + zid)) +
            " was not accepted. It will be retried while a contact remains open.")
        else if (state.windowContactOpen[zid] != true) reconcileWindowOverride(zid, currentSystemMode(getChildDevice(zdni(zid))?.getDataValue("systemId")))
    } else if (windowAction == "restore") {
        restoreInFlight[zid] = false
        state.windowRestoreInFlight = restoreInFlight
        if (success) {
            applied[zid] = false
            state.windowOverrideApplied = applied
            if (state.windowContactOpen[zid] == true) {
                reconcileWindowOverride(zid, currentSystemMode(getChildDevice(zdni(zid))?.getDataValue("systemId")))
            } else {
                snapshots.remove(zid)
                state.windowSnapshots = snapshots
                logMessage("debug", "Cleared the saved window restore point for zone " + zid + " after Evohome accepted the restored setting.")
            }
        } else {
            applied[zid] = true
            state.windowOverrideApplied = applied
            logMessage("warn", "The saved setting for " + (getChildDevice(zdni(zid))?.displayName ?: ("zone " + zid)) +
                " was not restored. It will be retried while all contacts remain closed.")
        }
    }
}

void requestZoneSetpoint(String dni, BigDecimal value, String policy) {
    def child = getChildDevice(dni)
    String zid = child?.getDataValue("zoneId")
    if (!zid) return
    logMessage("info", "A target temperature of " + value + " °C was requested for " +
        (child.displayName ?: "zone " + zid) + ". The request will be sent after the input settles.")
    logMessage("debug", "Child request received: zone setpoint; zoneId=" + zid + ", requested=" + value +
        " C, policy=" + (policy ?: "preserve") + ", debounce=" + (settings.debounceSeconds ?: 3) + "s.")
    Map pending = state.pending ?: [:]
    Integer version = nextZoneRequestVersion(zid, pending)
    Long requestedAt = now()
    Integer debounce = Math.max(1, (settings.debounceSeconds ?: 3) as Integer)
    pending[zid] = [zoneId: zid, dni: dni, value: value, policy: policy ?: "preserve", action: "set",
        version: version, createdAt: requestedAt, phase: "debounce", debounceUntil: requestedAt + debounce * 1000L]
    state.pending = pending
    scheduleZoneFlush(zid, version, debounce)
}

void setZoneOff(String dni) { requestZoneSetpoint(dni, 5.0G, "permanent") }

void clearZone(String dni) {
    def c = getChildDevice(dni)
    String zid = c?.getDataValue("zoneId")
    if (!zid) return
    logMessage("info", "The schedule was requested for " + (c.displayName ?: "zone " + zid) +
        ". Clearing its override in Evohome.")
    logMessage("debug", "Child request received: clear zone override; zoneId=" + zid + ".")
    Map pending = state.pending ?: [:]
    Integer version = nextZoneRequestVersion(zid, pending)
    Long requestedAt = now()
    pending[zid] = [zoneId: zid, dni: dni, action: "clear", version: version,
        createdAt: requestedAt, phase: "debounce", debounceUntil: requestedAt + 1000L]
    state.pending = pending
    scheduleZoneFlush(zid, version, 1)
}

private void flushZone(Map d) {
    Map p = (state.pending ?: [:])[d.zoneId.toString()]
    if (!p) return
    if (d.version != null && p.version?.toString() != d.version.toString()) {
        logMessage("debug", "Ignoring obsolete scheduled zone command for zone " + d.zoneId +
            "; scheduled version=" + d.version + ", current version=" + p.version + ".")
        return
    }
    if (p.phase != "debounce") return
    Long debounceUntil = p.debounceUntil as Long
    if (debounceUntil && debounceUntil > now()) {
        Integer remaining = Math.max(1, Math.ceil((debounceUntil - now()) / 1000.0d) as Integer)
        scheduleZoneFlush(d.zoneId.toString(), p.version as Integer, remaining)
        return
    }
    logMessage("debug", "Debounce elapsed; preparing zone command. zoneId=" + d.zoneId +
        ", action=" + p.action + ", value=" + (p.value ?: "n/a") + ", policy=" + (p.policy ?: "n/a") +
        ", version=" + p.version + ". Fetching fresh system and zone state before write.")
    def c = getChildDevice(p.dni)
    String locationId = c?.getDataValue("locationId")
    if (!locationId) {
        failPendingZonePreflight([zoneId: d.zoneId.toString(), version: p.version], "zone has no location ID")
        return
    }
    Map current = beginPendingZoneStage(d.zoneId.toString(), p.version, "locationPreflight")
    if (!current) return
    try {
        getLocation(locationId, "zonePreflight", [zoneId: d.zoneId.toString(), version: current.version])
    } catch (Exception e) {
        failPendingZonePreflight([zoneId: d.zoneId.toString(), version: current.version],
            "location request could not be queued: " + e.message)
    }
}

private void writeWindowOpen(String zid, Map pending, Map fresh) {
    if (state.windowContactOpen[zid] != true) {
        clearPendingZoneRequest(zid, pending.version)
        logMessage("debug", "Cancelled window-open write for zone " + zid + " because all selected contacts are now closed.")
        return
    }
    Map snapshots = state.windowSnapshots ?: [:]
    if (!snapshots[zid]) {
        Map sp = fresh.heatSetpointStatus ?: fresh.setpointStatus ?: [:]
        snapshots[zid] = [mode: sp.setpointMode?.toString() ?: "FollowSchedule",
            target: sp.targetTemperature ?: sp.targetHeatTemperature,
            until: sp.until ?: sp.timeUntil]
        state.windowSnapshots = snapshots
        logMessage("debug", "Saved pre-window state for zone " + zid + "; mode=" + snapshots[zid].mode +
            ", target=" + (snapshots[zid].target ?: "not reported") + " C, until=" + (snapshots[zid].until ?: "none") + ".")
    }
    Map applied = state.windowOverrideApplied ?: [:]
    applied[zid] = true
    state.windowOverrideApplied = applied
    getChildDevice(pending.dni)?.setWindowContactOverride(true, 5.0G, "off", true)
    apiPut("/WebAPI/emea/api/v1/temperatureZone/" + zid + "/heatSetpoint",
        [HeatSetpointValue: 5.0, SetpointMode: 1, TimeUntil: null], "zoneWrite",
        [kind: "zone", id: zid, version: pending.version, windowAction: "open"])
    String label = getChildDevice(pending.dni)?.displayName ?: ("zone " + zid)
    logMessage("info", "A selected window contact is open for " + label + ". Setting the zone to a permanent 5 °C override.")
    logMessage("debug", "Window-open override sent; zoneId=" + zid + ", setpoint=5.0 C, permanent=true.")
    clearPendingZoneRequest(zid, pending.version)
}

private void writeWindowRestore(String zid, Map pending) {
    if (state.windowContactOpen[zid] == true) {
        clearPendingZoneRequest(zid, pending.version)
        logMessage("debug", "Cancelled window restore for zone " + zid + " because a selected contact is open again.")
        return
    }
    Map snapshot = (state.windowSnapshots ?: [:])[zid]
    if (!snapshot) {
        clearPendingZoneRequest(zid, pending.version)
        logMessage("debug", "No saved pre-window state exists for zone " + zid + "; no restore command is needed.")
        return
    }
    String savedMode = snapshot.mode?.toString() ?: "FollowSchedule"
    def until = snapshot.until
    Long expiry = savedMode == "TemporaryOverride" ? parseWindowExpiry(until) : null
    Boolean restoreTemporary = savedMode == "TemporaryOverride" && expiry != null && expiry > now()
    Boolean restorePermanent = savedMode == "PermanentOverride"
    if (restoreTemporary && snapshot.target == null) {
        logMessage("warn", "The saved temporary target for " + (getChildDevice(zdni(zid))?.displayName ?: ("zone " + zid)) +
            " is missing. Returning the zone to its schedule.")
        restoreTemporary = false
    }
    if (savedMode == "TemporaryOverride" && !restoreTemporary) {
        if (expiry != null && expiry <= now()) {
            logMessage("info", "The saved temporary override for " + (getChildDevice(zdni(zid))?.displayName ?: ("zone " + zid)) +
                " expired while the window was open. Returning the zone to its schedule.")
        } else if (expiry == null) {
            logMessage("warn", "The saved temporary override for " + (getChildDevice(zdni(zid))?.displayName ?: ("zone " + zid)) +
                " has no usable expiry. Returning the zone to its schedule.")
        }
    }
    if (restorePermanent && snapshot.target == null) {
        logMessage("warn", "The saved permanent target for " + (getChildDevice(zdni(zid))?.displayName ?: ("zone " + zid)) +
            " is missing. Returning the zone to its schedule.")
        restorePermanent = false
    }
    Map restoreInFlight = state.windowRestoreInFlight ?: [:]
    restoreInFlight[zid] = true
    state.windowRestoreInFlight = restoreInFlight
    String zoneLabel = getChildDevice(zdni(zid))?.displayName ?: ("zone " + zid)
    if (restorePermanent || restoreTemporary) {
        Integer mode = restorePermanent ? 1 : 2
        def target = snapshot.target as BigDecimal
        def restoreUntil = restorePermanent ? null : until
        String thermostatMode = restorePermanent && target.compareTo(5.0G) == 0 ? "off" : "heat"
        getChildDevice(zdni(zid))?.setWindowContactOverride(false, target, thermostatMode, true)
        apiPut("/WebAPI/emea/api/v1/temperatureZone/" + zid + "/heatSetpoint",
            [HeatSetpointValue: target, SetpointMode: mode, TimeUntil: restoreUntil], "zoneWrite",
            [kind: "zone", id: zid, version: pending.version, windowAction: "restore"])
        logMessage("info", "All selected window contacts are closed. Restoring " + zoneLabel + " to " + target +
            " °C with its saved " + (restorePermanent ? "permanent" : "temporary") + " override.")
    } else {
        BigDecimal scheduledTarget = snapshot.target != null ? snapshot.target as BigDecimal : null
        getChildDevice(zdni(zid))?.setWindowContactOverride(false, scheduledTarget, "auto", true)
        apiPut("/WebAPI/emea/api/v1/temperatureZone/" + zid + "/heatSetpoint",
            [HeatSetpointValue: 0.0, SetpointMode: 0, TimeUntil: null], "zoneClear",
            [kind: "zone", id: zid, version: pending.version, windowAction: "restore"])
        logMessage("info", "All selected window contacts are closed. Clearing the override for " + zoneLabel + " and returning it to its schedule.")
    }
    clearPendingZoneRequest(zid, pending.version)
}

private void writePendingZone(Map meta, Map fresh) {
    String zid = meta.zoneId.toString()
    Map p = (state.pending ?: [:])[zid]
    if (!p) return
    if (meta.version == null || p.version?.toString() != meta.version.toString()) {
        logMessage("debug", "Ignoring stale zone preflight response for zone " + zid + "; response version=" +
            (meta.version ?: "unknown") + ", current version=" + p.version + ".")
        return
    }
    if (p.phase != "zonePreflight") {
        logMessage("debug", "Ignoring zone preflight response for zone " + zid +
            " because the pending request is in phase " + (p.phase ?: "unknown") + ".")
        return
    }
    logMessage("debug", "Fresh zone state received before write; zoneId=" + zid +
        ", currentSetpointMode=" + ((fresh.heatSetpointStatus ?: fresh.setpointStatus ?: [:]).setpointMode ?: "unknown") +
        ", pendingAction=" + p.action + ", pendingValue=" + (p.value ?: "n/a") + ".")
    if (p.action == "windowOpen") {
        writeWindowOpen(zid, p, fresh)
        return
    }
    if (p.action == "windowRestore") {
        writeWindowRestore(zid, p)
        return
    }
    if (p.action == "clear") {
        String zoneLabel = getChildDevice(p.dni)?.displayName ?: ("zone " + zid)
        logMessage("info", "The override for " + zoneLabel + " was cleared. Sending the zone back to its schedule in Evohome.")
        logMessage("debug", "Sending FollowSchedule clear for zone " + zid + ".")
        apiPut("/WebAPI/emea/api/v1/temperatureZone/" + zid + "/heatSetpoint",
            [HeatSetpointValue: 0.0, SetpointMode: 0, TimeUntil: null], "zoneClear", [kind: "zone", id: zid])
        clearPendingZoneRequest(zid, p.version)
        return
    }
    Map sp = fresh.heatSetpointStatus ?: fresh.setpointStatus ?: [:]
    String current = sp.setpointMode?.toString() ?: "FollowSchedule"
    String policy = p.policy
    Integer mode = 2
    def until = null
    if (policy == "permanent" || (policy == "preserve" && current == "PermanentOverride")) {
        mode = 1
    } else if (policy.startsWith("duration:")) {
        mode = 2
        Integer minutes = policy.substring("duration:".length()).isInteger() ? policy.substring("duration:".length()).toInteger() : 60
        until = new Date(now() + Math.max(1, minutes) * 60000L).format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC"))
    } else if (policy == "preserve" && current == "TemporaryOverride" && (sp.until || sp.timeUntil)) {
        mode = 2
        until = sp.until ?: sp.timeUntil
    } else {
        mode = policy == "permanent" ? 1 : 2
        Map nextPoint = updateNextScheduledTime(zid)
        until = nextPoint?.time
        if (!until) {
            def storedUntil = getChildDevice(zdni(zid))?.currentValue("nextScheduledTime")
            Date storedDate = storedUntil ? parseIsoTimestamp(storedUntil.toString()) : null
            if (storedDate && storedDate.time > now()) until = storedUntil
        }
        if (!until && policy == "preserve" && (sp.until || sp.timeUntil)) until = sp.until ?: sp.timeUntil
        if (!until && mode == 2) {
            logMessage("warn", "No next scheduled change is known for " + (getChildDevice(zdni(zid))?.displayName ?: ("zone " + zid)) +
                ". The temporary setpoint request was cancelled.")
            clearPendingZoneRequest(zid, p.version)
            getChildDevice(p.dni)?.refresh()
            return
        }
    }
    if (mode == 2) {
        Date expiryDate = until ? parseIsoTimestamp(until.toString()) : null
        if (!expiryDate || expiryDate.time <= now()) {
            logMessage("warn", "The temporary override expiry for " +
                (getChildDevice(zdni(zid))?.displayName ?: ("zone " + zid)) +
                " is missing, invalid, or already past. The request was cancelled instead of sending an unusable expiry.")
            clearPendingZoneRequest(zid, p.version)
            getChildDevice(p.dni)?.refresh()
            return
        }
    }
    apiPut("/WebAPI/emea/api/v1/temperatureZone/" + zid + "/heatSetpoint",
        [HeatSetpointValue: p.value, SetpointMode: mode, TimeUntil: until], "zoneWrite", [kind: "zone", id: zid, version: p.version])
    String zoneLabel = getChildDevice(p.dni)?.displayName ?: ("zone " + zid)
    logMessage("info", "The target temperature for " + zoneLabel + " is being set to " + p.value +
        " °C in Evohome with a " + (mode == 1 ? "permanent" : "temporary") + " override.")
    logMessage("debug", "Zone write queued; zoneId=" + zid + ", setpoint=" + p.value + " C, mode=" + mode + ", until=" + (until ?: "permanent") + ".")
    clearPendingZoneRequest(zid, p.version)
}

void setSystemMode(String sid, String mode, def until = null, Boolean restoreZoneOverrides = false) {
    logMessage("debug", "System mode command received from child; systemId=" + sid + ", mode=" + mode + ", until=" + (until ?: "permanent") + ".")
    Map indices = [auto: 0, off: 1, economy: 2, away: 3, dayoff: 4, custom: 6]
    String m = (mode ?: "").toLowerCase()
    if (!indices.containsKey(m)) { logMessage("error", "Unsupported system mode: " + mode); return }
    logMessage("info", "Setting the Evohome system mode to " + m.capitalize() +
        (until ? " for the requested period." : " permanently."))
    Boolean permanent = !until || until == 0 || until == "permanent"
    def expiry = until
    if (!permanent && until instanceof Number) expiry = new Date(now() + (until as Integer) * 86400000L)
    if (expiry instanceof Date) expiry = expiry.format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC"))
    if (permanent) expiry = null
    apiPut("/WebAPI/emea/api/v1/temperatureControlSystem/" + sid + "/mode",
        [SystemMode: indices[m], TimeUntil: expiry, Permanent: permanent], "systemWrite",
        [kind: "system", id: sid, restoreZoneOverrides: restoreZoneOverrides])
}

void setDhw(String id, String desired, def until = null) {
    String v = (desired ?: "").toLowerCase()
    logMessage("debug", "Hot-water command received from child; dhwId=" + id + ", requestedState=" + v +
        ", duration=" + (until ?: "permanent") + ".")
    if (v == "auto") {
        logMessage("info", "Hot water was set to Auto. Clearing the override and returning it to its schedule in Evohome.")
        logMessage("debug", "Mapping hot-water Auto to FollowSchedule clear; dhwId=" + id + ".")
        apiPut("/WebAPI/emea/api/v1/domesticHotWater/" + id + "/state",
            [Mode: "FollowSchedule", State: "", UntilTime: null], "dhwWrite", [kind: "dhw", id: id]); return
    }
    if (!(v in ["on", "off"])) {
        logMessage("warn", "Ignoring unsupported hot-water state request: " + v + ".")
        return
    }
    logMessage("info", "Hot water was requested to turn " + v +
        (until ? " for " + until + " minute(s)." : " permanently."))
    Boolean permanent = !until || until == 0 || until == "permanent"
    def expiry = until
    if (!permanent && until instanceof Number) expiry = new Date(now() + (until as Integer) * 60000L)
    if (expiry instanceof Date) expiry = expiry.format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC"))
    logMessage("debug", "Mapping hot-water " + v + " to " + (permanent ? "permanent" : "temporary") +
        " override; dhwId=" + id + ", until=" + (permanent ? "permanent" : expiry) + ".")
    apiPut("/WebAPI/emea/api/v1/domesticHotWater/" + id + "/state",
        [Mode: permanent ? "PermanentOverride" : "TemporaryOverride", State: v.capitalize(), UntilTime: permanent ? null : expiry],
        "dhwWrite", [kind: "dhw", id: id])
}

void locationModeChanged(evt) {
    logMessage("debug", "Hubitat location mode event received: " + evt?.value + ".")
    if (!settings.modeSyncEnabled || !state.locations) return
    String reason = evt.value?.toString()?.equalsIgnoreCase("Away") ? "syncEnter" : "syncLeave"
    state.locations.each { getLocation(it.toString(), reason) }
}

private void syncEnter(String sid, String mode, Map snapshot, Map systemStatus) {
    state.modeSyncPreExistingAway[sid] = false
    if (mode == "off") {
        logMessage("debug", "Mode sync skipped for system " + sid + ": evohome is HeatingOff; leaving it Off.")
        return
    }
    if (mode == "away") {
        state.modeSyncPreExistingAway[sid] = true
        logMessage("debug", "Mode sync skipped for system " + sid + ": evohome is already Away; no previous mode was captured.")
        return
    }
    if (mode == "unknown") {
        logMessage("debug", "Mode sync skipped for system " + sid + ": current evohome mode is unknown.")
        return
    }
    Map savedZones = [:]
    (systemStatus?.zones ?: []).each { zone ->
        String zid = zone.zoneId?.toString()
        if (!zid || settings["preserveAwayOverride_" + zid] != true) return
        Map setpoint = zone.heatSetpointStatus ?: zone.setpointStatus ?: [:]
        String zoneMode = setpoint.setpointMode?.toString()
        if (!(zoneMode in ["TemporaryOverride", "PermanentOverride"])) return
        savedZones[zid] = [mode: zoneMode, target: setpoint.targetTemperature ?: setpoint.targetHeatTemperature,
            until: setpoint.until ?: setpoint.timeUntil]
        logMessage("debug", "Saved the " + zoneMode + " for zone " + zid + " before Away; target=" +
            (savedZones[zid].target ?: "not reported") + " C, until=" + (savedZones[zid].until ?: "none") + ".")
    }
    state.awayZoneSnapshots[sid] = savedZones
    state.modeSnapshots[sid] = snapshot
    state.modeSyncOwned[sid] = true
    logMessage("info", "Hubitat changed to Away. Setting Evohome to Away and saving its current system mode for return; " +
        "saved current overrides for " + savedZones.size() + " zone(s).")
    setSystemMode(sid, "away", (settings.awayDurationDays ?: 0) as Integer)
}

private void syncLeave(String sid, String mode) {
    if (!state.modeSyncOwned[sid]) {
        if (mode == "off") {
            logMessage("debug", "Mode sync left system " + sid + " in HeatingOff on return home; preserving Off.")
            state.modeSyncPreExistingAway.remove(sid)
            return
        }
        if (state.modeSyncPreExistingAway[sid]) {
            if (mode == "away" && settings.preserveExistingAway == false) {
                logMessage("info", "Evohome was already Away when Hubitat left. The setting says to change Evohome to Auto when Hubitat returns, so I am doing that.")
                setSystemMode(sid, "auto")
            } else if (mode == "away") {
                logMessage("debug", "Evohome was already Away before Hubitat left; preserving Away on return, as configured.")
            } else {
                logMessage("debug", "Evohome was already Away before Hubitat left, but is now " + mode + "; leaving that mode unchanged.")
            }
            state.modeSyncPreExistingAway.remove(sid)
        }
        return
    }
    if (mode != "away") {
        if (mode == "off") logMessage("debug", "Mode sync left system " + sid + " in HeatingOff on return home; the mode had changed to Off while Away.")
        else logMessage("debug", "Mode sync did not restore system " + sid + ": it is no longer in Away mode.")
        state.modeSyncOwned[sid] = false
        state.modeSnapshots.remove(sid)
        state.awayZoneSnapshots.remove(sid)
        state.modeSyncPreExistingAway.remove(sid)
        return
    }
    if (!settings.restoreMode) {
        logMessage("debug", "Mode sync restore is disabled; system " + sid + " remains in Away.")
        state.modeSyncOwned[sid] = false
        state.modeSnapshots.remove(sid)
        state.awayZoneSnapshots.remove(sid)
        state.modeSyncPreExistingAway.remove(sid)
        return
    }
    Map snap = state.modeSnapshots[sid] ?: [:]
    String restore = normalMode(snap.mode?.toString())
    def end = snap.timeUntil ?: snap.until
    Date expiry = end ? parseDate(end.toString()) : null
    String restoreTarget = restore
    if (snap.isPermanent == false && expiry) {
        if (expiry.after(new Date())) setSystemMode(sid, restore, expiry, true)
        else {
            restoreTarget = "auto"
            setSystemMode(sid, restoreTarget, null, true)
        }
    } else setSystemMode(sid, restoreTarget, null, true)
    logMessage("info", "Hubitat left Away, so I am restoring the Evohome system to " + restoreTarget + ".")
    state.modeSyncOwned[sid] = false
    state.modeSnapshots.remove(sid)
    state.modeSyncPreExistingAway.remove(sid)
}

private void restoreAwayZoneOverrides(String sid, Map systemStatus, String mode) {
    Map savedZones = (state.awayZoneSnapshots ?: [:])[sid] ?: [:]
    if (!savedZones) return
    if (mode in ["off", "away"] || mode == "unknown") {
        logMessage("debug", "Saved zone overrides for system " + sid + " were not restored because its current mode is " + mode + ".")
        return
    }
    (systemStatus?.zones ?: []).each { zone ->
        String zid = zone.zoneId?.toString()
        Map saved = savedZones[zid]
        if (!saved) return
        String savedMode = saved.mode?.toString()
        def target = saved.target
        Long expiry = savedMode == "TemporaryOverride" ? parseWindowExpiry(saved.until) : null
        Boolean permanent = savedMode == "PermanentOverride"
        Boolean temporary = savedMode == "TemporaryOverride" && expiry != null && expiry > now()
        String zoneLabel = getChildDevice(zdni(zid))?.displayName ?: ("zone " + zid)
        if ((permanent || temporary) && target != null) {
            def until = permanent ? null : new Date(expiry).format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC"))
            apiPut("/WebAPI/emea/api/v1/temperatureZone/" + zid + "/heatSetpoint",
                [HeatSetpointValue: target as BigDecimal, SetpointMode: permanent ? 1 : 2, TimeUntil: until],
                "zoneWrite", [kind: "zone", id: zid])
            logMessage("info", "Evohome returned from Away. Restoring " + (permanent ? "the permanent" : "the unexpired temporary") +
                " override for " + zoneLabel + " to " + target + " °C.")
        } else {
            apiPut("/WebAPI/emea/api/v1/temperatureZone/" + zid + "/heatSetpoint",
                [HeatSetpointValue: 0.0, SetpointMode: 0, TimeUntil: null], "zoneClear", [kind: "zone", id: zid])
            if (savedMode == "TemporaryOverride" && expiry != null && expiry <= now()) {
                logMessage("info", "The saved temporary override for " + zoneLabel +
                    " expired during Away. Returning the zone to its schedule.")
            } else if (savedMode == "TemporaryOverride" && expiry == null) {
                logMessage("warn", "The saved temporary override for " + zoneLabel +
                    " has no usable expiry. Returning the zone to its schedule.")
            } else {
                logMessage("warn", "The saved override for " + zoneLabel +
                    " has no usable target. Returning the zone to its schedule.")
            }
        }
    }
    state.awayZoneSnapshots.remove(sid)
}

private Date parseIsoTimestamp(String value) {
    if (!value) return null
    for (String pattern : ["yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX",
                           "yyyy-MM-dd'T'HH:mm:ss.SSSXX", "yyyy-MM-dd'T'HH:mm:ssXX",
                           "yyyy-MM-dd'T'HH:mm:ss'Z'"]) {
        try {
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(pattern)
            format.setLenient(false)
            format.setTimeZone(TimeZone.getTimeZone("UTC"))
            return format.parse(value)
        } catch (Exception ignored) { }
    }
    null
}

private Date parseDate(String value) {
    try { Date.parse("yyyy-MM-dd'T'HH:mm:ssXX", value) } catch (ignored) { null }
}
