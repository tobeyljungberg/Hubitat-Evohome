/**
 * Evohome System Controller driver for Hubitat
 * Author: Tobey Ljungberg | Version: 2.0.1 | Copyright (c) 2026 Tobey Ljungberg
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under Apache License, Version 2.0. You may use, modify and redistribute
 * this work under those terms. https://www.apache.org/licenses/LICENSE-2.0
 * Hubitat-only system mode device. Do not expose this device to HomeKit.
 */
metadata {
    definition(name: "Evohome System Controller", namespace: "tljungberg", author: "Tobey Ljungberg") {
        capability "Actuator"
        capability "Sensor"
        capability "Refresh"
        attribute "evohomeSystemMode", "string"
        attribute "evohomeSystemModeDescription", "string"
        attribute "systemModeUntil", "string"
        attribute "activeFaults", "string"
        attribute "faultSummary", "string"
        attribute "loggingLevel", "string"
        command "setLogLevel", ["STRING"]
        command "setEvohomeSystemMode", [[name: "mode", type: "ENUM", constraints: ["auto", "economy", "away", "dayOff", "off", "custom"]],
                                         [name: "until", type: "STRING", description: "Optional ISO expiry, duration in days, or permanent"]]
        command "restoreAuto"
        command "parseSystemStatus", ["STRING", "OBJECT", "OBJECT"]
    }
    preferences {
        input name: "prefLogLevel", type: "enum", title: "Child-device log level",
            description: "Effective level for this child. Reset Logging in Evohome Connect also updates this preference to match the parent app.",
            options: ["error", "warn", "info", "debug"], defaultValue: "info"
    }
}

def installed() {
    state.logLevel = settings.prefLogLevel ?: "info"
    state.lastPrefLogLevel = settings.prefLogLevel ?: "info"
    state.parentLogLevelOverride = null
    sendEvent(name: "loggingLevel", value: state.logLevel)
}
def updated() {
    String configured = settings.prefLogLevel ?: "info"
    String previousConfigured = state.lastPrefLogLevel ?: configured
    if (configured != previousConfigured) {
        state.parentLogLevelOverride = null
        state.logLevel = configured
    } else {
        state.logLevel = state.parentLogLevelOverride ?: configured
    }
    state.lastPrefLogLevel = configured
    sendEvent(name: "loggingLevel", value: state.logLevel)
}

void setLogLevel(String level) {
    String selected = (level ?: "info").toLowerCase()
    if (!(selected in ["error", "warn", "info", "debug"])) selected = "info"
    state.logLevel = selected
    state.parentLogLevelOverride = selected
    try {
        device.updateSetting("prefLogLevel", [value: selected, type: "enum"])
        state.lastPrefLogLevel = selected
        state.parentLogLevelOverride = null
    } catch (Exception e) {
        logMessage("error", "The effective log level is " + selected.capitalize() +
            ", but Hubitat could not update the displayed device preference: " + e.message + ".")
    }
    sendEvent(name: "loggingLevel", value: selected)
    logMessage("debug", "The parent app set this child device's log level to " + selected.capitalize() + ".")
}

private void logMessage(String level, String message) {
    Map rank = [error: 1, warn: 2, info: 3, debug: 4]
    String selected = (state.logLevel ?: settings.prefLogLevel ?: "info").toString().toLowerCase()
    String category = (level ?: "debug").toString().toLowerCase()
    if (!rank.containsKey(selected)) selected = "info"
    if (!rank.containsKey(category)) category = "debug"
    if (rank[category] <= rank[selected]) {
        switch (category) {
            case "error": log.error(device.displayName + ": " + message); break
            case "warn": log.warn(device.displayName + ": " + message); break
            case "info": log.info(device.displayName + ": " + message); break
            default: log.debug(device.displayName + ": " + message)
        }
    }
}

void refresh() {
    logMessage("debug", "refresh() received; requesting system refresh from parent.")
    parent.refreshAfterWrite([kind: "system", id: device.getDataValue("systemId")])
}

void setEvohomeSystemMode(String mode, def until = null) {
    logMessage("debug", "setEvohomeSystemMode() received; mode=" + mode + ", until=" + (until ?: "permanent") +
        ". Forwarding to parent.")
    logMessage("info", "The Evohome system mode was requested to change to " + (mode ?: "unknown") +
        (until ? " for the requested period." : " permanently.") + " Sending the command to Evohome.")
    parent.setSystemMode(device.getDataValue("systemId"), mode, until)
}

void restoreAuto() {
    logMessage("debug", "restoreAuto() received; forwarding Auto mode request to parent.")
    setEvohomeSystemMode("auto")
}

void parseSystemStatus(String mode, Map status, List faults = []) {
    List currentFaults = faults instanceof List ? faults : []
    logMessage("debug", "System status received from parent; mode=" + mode +
        ", until=" + (status?.timeUntil ?: status?.until ?: "permanent") +
        ", faults=" + currentFaults.size() + ". Applying child-device events.")
    sendEvent(name: "evohomeSystemMode", value: mode)
    sendEvent(name: "evohomeSystemModeDescription", value: readableSystemMode(mode))
    def expiry = status?.timeUntil ?: status?.until
    String untilValue = expiry?.toString()
    if (!untilValue) untilValue = "Permanent (no expiry)"
    sendEvent(name: "systemModeUntil", value: untilValue)
    String faultNames = currentFaults.collect { readableFault(it?.faultType?.toString() ?: "UnknownFault") }.join(", ")
    sendEvent(name: "activeFaults", value: faultNames ?: "No active faults")
    sendEvent(name: "faultSummary", value: faultNames ?: "No active faults")
}

private String readableSystemMode(String mode) {
    [auto: "Auto", economy: "Economy", away: "Away", dayOff: "Day Off",
     off: "Heating Off", custom: "Custom"][mode] ?: (mode ?: "Unknown")
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
