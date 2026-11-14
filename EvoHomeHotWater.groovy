/**
 * Evohome Hot Water driver for Hubitat
 * Author: Tobey Ljungberg | Version: 2.0.2 | Copyright (c) 2026 Tobey Ljungberg
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under Apache License, Version 2.0. You may use, modify and redistribute
 * this work under those terms. https://www.apache.org/licenses/LICENSE-2.0
 * Hubitat-only domestic hot water control. Not intended for HomeKit.
 */
metadata {
    definition(name: "Evohome Hot Water", namespace: "tljungberg", author: "Tobey Ljungberg") {
        capability "Actuator"
        capability "Sensor"
        capability "Switch"
        capability "TemperatureMeasurement"
        capability "Refresh"
        attribute "evohomeDhwMode", "string"
        attribute "evohomeDhwModeDescription", "string"
        attribute "evohomeDhwUntil", "string"
        attribute "activeFaults", "string"
        attribute "faultSummary", "string"
        attribute "loggingLevel", "string"
        command "setLogLevel", ["STRING"]
        command "auto"
        command "onFor", [[name: "minutes", type: "NUMBER", description: "Temporary on duration in minutes"]]
        command "offFor", [[name: "minutes", type: "NUMBER", description: "Temporary off duration in minutes"]]
        command "parseDhwStatus", ["OBJECT"]
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
    logMessage("debug", "refresh() received; requesting hot-water status refresh from parent.")
    parent.refreshAfterWrite([kind: "dhw", id: device.getDataValue("dhwId")])
}

void on() {
    logMessage("debug", "on() received; requesting hot water On.")
    logMessage("info", "Hubitat requested hot water On. Sending the command to Evohome.")
    setState("on")
}
void off() {
    logMessage("debug", "off() received; requesting hot water Off.")
    logMessage("info", "Hubitat requested hot water Off. Sending the command to Evohome.")
    setState("off")
}
void auto() {
    logMessage("debug", "auto() received; requesting hot water FollowSchedule.")
    logMessage("info", "Hot water was set to Auto. Returning it to the Evohome schedule.")
    parent.setDhw(device.getDataValue("dhwId"), "auto")
}
void onFor(Number minutes) {
    logMessage("debug", "onFor() received; duration=" + minutes + " minute(s).")
    logMessage("info", "Hubitat requested hot water On for " + minutes + " minute(s). Sending the timed command to Evohome.")
    parent.setDhw(device.getDataValue("dhwId"), "on", minutes)
}
void offFor(Number minutes) {
    logMessage("debug", "offFor() received; duration=" + minutes + " minute(s).")
    logMessage("info", "Hubitat requested hot water Off for " + minutes + " minute(s). Sending the timed command to Evohome.")
    parent.setDhw(device.getDataValue("dhwId"), "off", minutes)
}
private void setState(String value) {
    logMessage("debug", "Sending hot-water " + value + " request to parent; dhwId=" + device.getDataValue("dhwId") + ".")
    sendEvent(name: "switch", value: value, isStateChange: true)
    parent.setDhw(device.getDataValue("dhwId"), value)
}

void parseDhwStatus(Map status) {
    // The location status response nests DHW operating state in stateStatus.
    // Keep the older/top-level fallbacks for variations in API responses.
    Map stateStatus = (status?.stateStatus ?: status?.dhwStateStatus ?: status?.dhwStatus?.stateStatus ?: [:]) as Map
    String mode = (stateStatus.mode ?: status?.mode ?: status?.dhwStatus?.mode)?.toString()
    String stateValue = (stateStatus.state ?: status?.state ?: status?.dhwStatus?.state)?.toString()
    if (stateValue) sendEvent(name: "switch", value: stateValue.toString().toLowerCase())
    if (mode) {
        String modeValue = mode == "FollowSchedule" ? "Auto" : mode
        String modeDescription = [
            FollowSchedule: "Auto (following schedule)",
            TemporaryOverride: "Temporary override",
            PermanentOverride: "Permanent override"
        ][mode] ?: mode
        sendEvent(name: "evohomeDhwMode", value: modeValue)
        sendEvent(name: "evohomeDhwModeDescription", value: modeDescription)
    }
    def until = stateStatus.until ?: stateStatus.timeUntil ?: status?.until ?: status?.timeUntil
    String untilValue = until?.toString()
    if (!untilValue) {
        untilValue = mode == "FollowSchedule" ? "No override expiry (following schedule)" :
            mode == "PermanentOverride" ? "Permanent (no expiry)" :
            mode == "TemporaryOverride" ? "Not reported by API" : "No expiry reported"
    }
    sendEvent(name: "evohomeDhwUntil", value: untilValue)
    def temp = status?.temperatureStatus?.temperature ?: status?.temperature
    List faults = status?.activeFaults instanceof List ? status.activeFaults : []
    logMessage("debug", "Hot-water status received from parent; state=" + stateValue +
        ", mode=" + mode + ", temperature=" + temp + " C, until=" + untilValue +
        ", faults=" + faults.size() + ". Applying child-device events.")
    if (temp != null) sendEvent(name: "temperature", value: temp, unit: "°C")
    String faultNames = faults.collect { readableFault(it?.faultType?.toString() ?: "UnknownFault") }.join(", ")
    sendEvent(name: "activeFaults", value: faultNames ?: "No active faults")
    sendEvent(name: "faultSummary", value: faultNames ?: "No active faults")
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
