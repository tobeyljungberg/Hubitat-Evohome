/**
 * Evohome Heating Zone driver for Hubitat
 * Author: Tobey Ljungberg | Version: 2.0.0 | Copyright (c) 2026 Tobey Ljungberg
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under Apache License, Version 2.0. You may use, modify and redistribute
 * this work under those terms. https://www.apache.org/licenses/LICENSE-2.0
 * HomeKit zone Off is a permanent 5 C setpoint; Auto clears the override.
 */
metadata {
    definition(name: "Evohome Heating Zone", namespace: "tljungberg", author: "Tobey Ljungberg") {
        capability "Actuator"
        capability "Sensor"
        capability "Refresh"
        capability "TemperatureMeasurement"
        capability "Thermostat"
        attribute "evohomeSystemMode", "string"
        attribute "evohomeSystemModeDescription", "string"
        attribute "evohomeSetpointMode", "string"
        attribute "evohomeSetpointModeDescription", "string"
        attribute "evohomeUntil", "string"
        attribute "heatingOnly", "string"
        attribute "nextScheduledTime", "string"
        attribute "nextScheduledSetpoint", "number"
        attribute "thermostatSetpointStatus", "string"
        attribute "activeFaults", "string"
        attribute "faultSummary", "string"
        attribute "temperatureAvailable", "string"
        attribute "loggingLevel", "string"
        command "setLogLevel", ["STRING"]
        command "setZoneOff"
        command "resumeSchedule"
        command "setNextScheduledTime", ["STRING"]
        command "setNextScheduledSetpoint", ["NUMBER"]
        command "parseEvohomeStatus", ["OBJECT"]
        command "setSchedule", ["JSON_OBJECT"]
    }
    preferences {
        input name: "setpointPolicy", type: "enum", title: "Setpoint change behaviour",
            options: ["preserve": "Preserve an existing override; otherwise until next schedule change",
                      "next": "Until next schedule change", "permanent": "Permanent override",
                      "duration": "For a fixed duration"],
            defaultValue: "preserve"
        input name: "setpointDuration", type: "number", title: "Duration in minutes", range: "1..1440", defaultValue: 60
        input name: "prefLogLevel", type: "enum", title: "Child-device log level",
            description: "Local level for this zone. Reset Logging in Evohome Connect sets all child devices to the parent's selected level.",
            options: ["error", "warn", "info", "debug"], defaultValue: "info"
    }
}

def installed() {
    sendEvent(name: "thermostatOperatingState", value: "idle")
    state.logLevel = settings.prefLogLevel ?: "info"
    state.lastPrefLogLevel = settings.prefLogLevel ?: "info"
    sendEvent(name: "loggingLevel", value: state.logLevel)
    setHeatingOnly(true)
}

def updated() {
    String configured = settings.prefLogLevel ?: "info"
    if (configured != (state.lastPrefLogLevel ?: configured)) state.logLevel = configured
    if (!state.logLevel) state.logLevel = configured
    state.lastPrefLogLevel = configured
    sendEvent(name: "loggingLevel", value: state.logLevel)
}

void setLogLevel(String level) {
    String selected = (level ?: "info").toLowerCase()
    if (!(selected in ["error", "warn", "info", "debug"])) selected = "info"
    state.logLevel = selected
    sendEvent(name: "loggingLevel", value: selected)
    logMessage("debug", "The parent app set this child device's log level to " + selected.capitalize() + ".")
}

private void logMessage(String level, String message) {
    Map rank = [error: 1, warn: 2, info: 3, debug: 4]
    String selected = (state.logLevel ?: settings.prefLogLevel ?: "info").toLowerCase()
    if ((rank[level] ?: 4) <= (rank[selected] ?: 3)) {
        switch (level) {
            case "error": log.error(device.displayName + ": " + message); break
            case "warn": log.warn(device.displayName + ": " + message); break
            case "info": log.info(device.displayName + ": " + message); break
            default: log.debug(device.displayName + ": " + message)
        }
    }
}

void refresh() {
    logMessage("debug", "refresh() received; requesting a parent-app zone refresh.")
    parent.refreshAfterWrite([kind: "zone", id: device.getDataValue("zoneId")])
}

void setHeatingOnly(Boolean enabled) {
    state.heatingOnly = enabled == true
    sendEvent(name: "heatingOnly", value: state.heatingOnly.toString())
    // Keep valid thermostat and fan mode attributes for native HomeKit. The
    // driver does not advertise a cooling mode; heating-only status still
    // reports Auto while following the evohome schedule.
    sendEvent(name: "supportedThermostatModes", value: '["heat","off","auto"]')
    sendEvent(name: "supportedThermostatFanModes", value: '["auto"]')
    sendEvent(name: "thermostatFanMode", value: "auto")
    if (state.heatingOnly) {
        def heat = device.currentValue("heatingSetpoint")
        if (heat != null) sendEvent(name: "coolingSetpoint", value: heat, unit: "°C")
    }
    logMessage("debug", "Heating-only compatibility set to " + state.heatingOnly +
        "; cool mode is not advertised, fan mode is Auto only" +
        (state.heatingOnly ? ", and coolingSetpoint mirrors heatingSetpoint." : "."))
}

void setNextScheduledTime(String value) {
    logMessage("debug", "Next schedule time received from parent: " + value + ".")
    sendEvent(name: "nextScheduledTime", value: value)
}

void setNextScheduledSetpoint(Number value) {
    logMessage("debug", "Next scheduled setpoint received from parent: " + value + " C.")
    sendEvent(name: "nextScheduledSetpoint", value: value, unit: "°C")
}

void setSchedule(Map schedule) {
    logMessage("debug", "setSchedule() received; schedule editing is unsupported. Request ignored.")
    logMessage("warn", "Schedule editing is not supported by this driver.")
}

void parseEvohomeStatus(Map values) {
    List incomingFaults = values.activeFaults instanceof List ? values.activeFaults : []
    logMessage("debug", "Status received from parent; temperature=" + values.temperature +
        " C, setpoint=" + values.heatingSetpoint + " C, setpointMode=" + values.setpointMode +
        ", systemMode=" + values.systemMode + ", available=" + values.available +
        ", faults=" + incomingFaults.size() + ". Applying child-device events.")
    if (values.temperature != null) sendEvent(name: "temperature", value: values.temperature, unit: "°C")
    if (values.heatingSetpoint != null) {
        sendEvent(name: "heatingSetpoint", value: values.heatingSetpoint, unit: "°C")
        sendEvent(name: "thermostatSetpoint", value: values.heatingSetpoint, unit: "°C")
        if (heatingOnlyEnabled()) sendEvent(name: "coolingSetpoint", value: values.heatingSetpoint, unit: "°C")
    }
    if (values.systemMode != null) {
        sendEvent(name: "evohomeSystemMode", value: values.systemMode)
        sendEvent(name: "evohomeSystemModeDescription", value: readableSystemMode(values.systemMode.toString()))
    }
    String mode = (values.setpointMode ?: "").toString()
    if (values.setpointMode != null) {
        String modeValue = mode == "FollowSchedule" ? "Auto" : mode
        sendEvent(name: "evohomeSetpointMode", value: modeValue)
        sendEvent(name: "evohomeSetpointModeDescription", value: readableSetpointMode(mode))
    }
    String untilValue = values.until?.toString()
    if (!untilValue) {
        untilValue = mode == "FollowSchedule" ? "No override expiry (following schedule)" :
            mode == "PermanentOverride" ? "Permanent (no expiry)" :
            mode == "TemporaryOverride" ? "Not reported by API" : "No expiry reported"
    }
    sendEvent(name: "evohomeUntil", value: untilValue)
    if (values.nextScheduledTime != null) sendEvent(name: "nextScheduledTime", value: values.nextScheduledTime.toString())
    List faults = values.activeFaults instanceof List ? values.activeFaults : []
    String faultNames = faults.collect { readableFault(it?.faultType?.toString() ?: "UnknownFault") }.join(", ")
    sendEvent(name: "activeFaults", value: faultNames ?: "No active faults")
    sendEvent(name: "faultSummary", value: faultNames ?: "No active faults")
    if (values.available != null) sendEvent(name: "temperatureAvailable", value: values.available.toString())
    Boolean isOff = state.zoneOff == true && mode == "PermanentOverride"
    if (mode == "FollowSchedule") state.zoneOff = false
    String hubitatMode = isOff ? "off" : (mode == "FollowSchedule" ? "auto" : "heat")
    sendEvent(name: "thermostatMode", value: hubitatMode)
    sendEvent(name: "thermostatOperatingState", value: "idle")
    sendEvent(name: "thermostatSetpointStatus", value: values.available == false ? "Unavailable" : "Current")
}

private String readableSetpointMode(String mode) {
    [FollowSchedule: "Auto (following schedule)", TemporaryOverride: "Temporary override",
     PermanentOverride: "Permanent override"][mode] ?: (mode ?: "Unknown")
}

private String readableSystemMode(String mode) {
    [auto: "Auto", economy: "Economy", away: "Away", dayOff: "Day Off",
     off: "Heating Off", custom: "Custom"][mode] ?: mode
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

void setHeatingSetpoint(BigDecimal requested) {
    logMessage("debug", "setHeatingSetpoint() received; requested=" + requested + " C, systemMode=" +
        device.currentValue("evohomeSystemMode") + ".")
    if (systemHeatingOff()) {
        logMessage("info", "Evohome is in Heating Off, so the requested temperature change for this zone was ignored.")
        refresh()
        return
    }
    BigDecimal min = new BigDecimal(device.getDataValue("minSetpoint") ?: "5")
    BigDecimal max = new BigDecimal(device.getDataValue("maxSetpoint") ?: "35")
    BigDecimal value = requested.max(min).min(max).setScale(1, BigDecimal.ROUND_HALF_UP)
    logMessage("info", "The target temperature for " + device.displayName + " changed to " + value +
        " °C. Sending the new setpoint to Evohome.")
    state.zoneOff = false
    sendEvent(name: "heatingSetpoint", value: value, unit: "°C")
    sendEvent(name: "thermostatSetpoint", value: value, unit: "°C")
    if (heatingOnlyEnabled()) sendEvent(name: "coolingSetpoint", value: value, unit: "°C")
    sendEvent(name: "thermostatMode", value: "heat")
    sendEvent(name: "thermostatSetpointStatus", value: "Pending")
    String policy = settings.setpointPolicy ?: "preserve"
    if (policy == "duration") policy = "duration:" + (settings.setpointDuration ?: 60)
    logMessage("debug", "Optimistic thermostat update sent; clampedSetpoint=" + value +
        " C, policy=" + policy + ". Forwarding setpoint request to parent.")
    parent.requestZoneSetpoint(device.deviceNetworkId, value, policy)
}

void setThermostatSetpoint(BigDecimal value) { setHeatingSetpoint(value) }

void setThermostatMode(String mode) {
    String m = (mode ?: "").toLowerCase()
    logMessage("debug", "setThermostatMode() received; requestedMode=" + m +
        ", systemMode=" + device.currentValue("evohomeSystemMode") + ".")
    if (systemHeatingOff()) {
        logMessage("info", "Evohome is in Heating Off, so the requested mode change for this zone was ignored.")
        refresh()
        return
    }
    if (m == "off") {
        logMessage("info", "" + device.displayName + " was set to Off. Applying a permanent 5 °C override in Evohome.")
        logMessage("debug", "Mapping zone Off to permanent 5 C override; forwarding to parent.")
        state.zoneOff = true
        sendEvent(name: "thermostatMode", value: "off")
        sendEvent(name: "heatingSetpoint", value: 5.0, unit: "°C")
        sendEvent(name: "thermostatSetpoint", value: 5.0, unit: "°C")
        if (heatingOnlyEnabled()) sendEvent(name: "coolingSetpoint", value: 5.0, unit: "°C")
        sendEvent(name: "thermostatSetpointStatus", value: "Pending")
        parent.setZoneOff(device.deviceNetworkId)
    } else if (m == "auto") {
        logMessage("info", "" + device.displayName + " was set to Auto. Clearing its override and returning it to the Evohome schedule.")
        logMessage("debug", "Mapping zone Auto to clear override and resume FollowSchedule; forwarding to parent.")
        state.zoneOff = false
        sendEvent(name: "thermostatMode", value: "auto")
        sendEvent(name: "thermostatSetpointStatus", value: "Pending")
        parent.clearZone(device.deviceNetworkId)
    } else if (m == "heat") {
        BigDecimal currentTarget = device.currentValue("heatingSetpoint") != null ?
            new BigDecimal(device.currentValue("heatingSetpoint").toString()) : null
        if (currentTarget == null) {
            logMessage("warn", "Heat mode requested, but no current heating setpoint is available to override.")
            refresh()
            return
        }
        String policy = settings.setpointPolicy ?: "preserve"
        if (policy == "duration") policy = "duration:" + (settings.setpointDuration ?: 60)
        logMessage("info", "" + device.displayName + " was set to Heat. Applying an override at " + currentTarget + " °C in Evohome.")
        logMessage("debug", "Mapping zone Heat to an override at the current target=" + currentTarget +
            " C, policy=" + policy + ".")
        state.zoneOff = false
        sendEvent(name: "thermostatMode", value: "heat")
        sendEvent(name: "thermostatSetpointStatus", value: "Pending")
        parent.requestZoneSetpoint(device.deviceNetworkId, currentTarget, policy)
    } else {
        logMessage("warn", "Unsupported zone thermostat mode: " + mode)
    }
}

void setZoneOff() { setThermostatMode("off") }
void off() { setThermostatMode("off") }
void auto() { setThermostatMode("auto") }
void heat() { setThermostatMode("heat") }
void resumeSchedule() { setThermostatMode("auto") }
void cool() {
    logMessage("debug", "cool() received; evohome zone cooling is unsupported.")
    logMessage("warn", "Cooling is not supported by evohome.")
}
void emergencyHeat() { setThermostatMode("heat") }
void setCoolingSetpoint(BigDecimal value) {
    if (heatingOnlyEnabled()) {
        logMessage("debug", "setCoolingSetpoint() received; requested=" + value +
            " C in heating-only mode. Treating the HomeKit cooling target as the requested heating target.")
        // Hubitat's native HomeKit integration can deliver its single
        // temperature target through setCoolingSetpoint(), even when the
        // thermostat is in Heat mode. Route it through the same clamp,
        // optimistic update and debounced Evohome write as a heating target.
        // A preceding stale setHeatingSetpoint event is coalesced by the
        // parent's debounce, so the final HomeKit value wins.
        setHeatingSetpoint(value)
        return
    }
    logMessage("debug", "setCoolingSetpoint() received; requested=" + value + "; evohome cooling is unsupported.")
    logMessage("warn", "Cooling is not supported by evohome.")
}
void fanAuto() {
    logMessage("debug", "fanAuto() received; setting local thermostatFanMode=auto (the only supported fan mode).")
    sendEvent(name: "thermostatFanMode", value: "auto")
}
void fanOn() {
    logMessage("debug", "fanOn() received; evohome fan control is unsupported.")
    logMessage("warn", "Fan control is not supported by evohome.")
}
void fanCirculate() {
    logMessage("debug", "fanCirculate() received; evohome fan control is unsupported.")
    logMessage("warn", "Fan control is not supported by evohome.")
}
void setThermostatFanMode(String value) {
    logMessage("debug", "setThermostatFanMode() received; requested=" + value + ".")
    if (value == "auto") fanAuto()
    else logMessage("warn", "Fan mode " + value + " is not supported by evohome.")
}

private Boolean systemHeatingOff() { device.currentValue("evohomeSystemMode") == "off" }
private Boolean heatingOnlyEnabled() { state.heatingOnly == true }
