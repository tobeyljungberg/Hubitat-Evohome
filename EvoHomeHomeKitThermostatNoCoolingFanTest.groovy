/**
 * Evohome HomeKit Thermostat No Cooling/Fan Test driver for Hubitat
 * Author: Tobey Ljungberg | Version: 2.0.0 | Copyright (c) 2026 Tobey Ljungberg
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under Apache License, Version 2.0. You may use, modify and redistribute
 * this work under those terms. https://www.apache.org/licenses/LICENSE-2.0
 * Standalone virtual thermostat used to probe Hubitat native HomeKit export.
 * Unlike EvohomeHomeKitHeatOnlyTest, this test declares the composite
 * Thermostat capability, but it deliberately does not publish coolingSetpoint,
 * supportedThermostatFanModes, or thermostatFanMode values.
 */
metadata {
    definition(name: "Evohome HomeKit Thermostat No Cooling/Fan Test", namespace: "tljungberg", author: "Tobey Ljungberg") {
        capability "Actuator"
        capability "Sensor"
        capability "TemperatureMeasurement"
        capability "Thermostat"

        command "setTestTemperature", [[name: "temperature", type: "NUMBER", description: "Set the simulated room temperature"]]
    }
    preferences {
        input name: "prefLogLevel", type: "enum", title: "Log level",
            options: ["error", "warn", "info", "debug"], defaultValue: "debug"
    }
}

def installed() {
    logMessage("debug", "Installing composite-capability test; deliberately omitting cooling and fan state events.")
    sendEvent(name: "supportedThermostatModes", value: '["heat","auto","off"]')
    sendEvent(name: "temperature", value: 18.0, unit: "°C")
    sendEvent(name: "heatingSetpoint", value: 20.0, unit: "°C")
    sendEvent(name: "thermostatSetpoint", value: 20.0, unit: "°C")
    sendEvent(name: "thermostatMode", value: "auto")
    sendEvent(name: "thermostatOperatingState", value: "idle")
    // Intentionally do not send coolingSetpoint, supportedThermostatFanModes,
    // or thermostatFanMode. Those characteristics are the variables under test.
}

def updated() { logMessage("debug", "Driver preferences updated.") }

private void logMessage(String level, String message) {
    Map rank = [error: 1, warn: 2, info: 3, debug: 4]
    String selected = (settings.prefLogLevel ?: "debug").toLowerCase()
    if ((rank[level] ?: 4) <= (rank[selected] ?: 4)) {
        switch (level) {
            case "error": log.error("HomeKit no-cooling/fan test: " + message); break
            case "warn": log.warn("HomeKit no-cooling/fan test: " + message); break
            case "info": log.info("HomeKit no-cooling/fan test: " + message); break
            default: log.debug("HomeKit no-cooling/fan test: " + message)
        }
    }
}

void setThermostatMode(String mode) {
    String requested = (mode ?: "").toLowerCase()
    logMessage("debug", "setThermostatMode(" + requested + ") received.")
    if (!(requested in ["heat", "auto", "off"])) {
        logMessage("warn", "Ignoring unsupported test mode: " + requested + ".")
        return
    }
    sendEvent(name: "thermostatMode", value: requested, isStateChange: true)
    sendEvent(name: "thermostatOperatingState", value: "idle")
}

void heat() { setThermostatMode("heat") }
void auto() { setThermostatMode("auto") }
void off() { setThermostatMode("off") }
void cool() { logMessage("debug", "cool() received and ignored; cooling is not implemented.") }
void emergencyHeat() { logMessage("debug", "emergencyHeat() received and ignored.") }
void fanAuto() { logMessage("debug", "fanAuto() received and ignored; fan state is not implemented.") }
void fanOn() { logMessage("debug", "fanOn() received and ignored; fan state is not implemented.") }
void fanCirculate() { logMessage("debug", "fanCirculate() received and ignored; fan state is not implemented.") }
void setThermostatFanMode(String mode) { logMessage("debug", "setThermostatFanMode(" + mode + ") received and ignored.") }
void setCoolingSetpoint(BigDecimal value) { logMessage("debug", "setCoolingSetpoint(" + value + ") received and ignored.") }

void setHeatingSetpoint(BigDecimal requested) {
    BigDecimal value = requested.max(5.0G).min(35.0G).setScale(1, BigDecimal.ROUND_HALF_UP)
    logMessage("debug", "setHeatingSetpoint(" + requested + ") received; publishing " + value + " C.")
    sendEvent(name: "heatingSetpoint", value: value, unit: "°C", isStateChange: true)
    sendEvent(name: "thermostatSetpoint", value: value, unit: "°C", isStateChange: true)
}

void setTestTemperature(BigDecimal value) {
    logMessage("debug", "setTestTemperature(" + value + ") received.")
    sendEvent(name: "temperature", value: value, unit: "°C", isStateChange: true)
}

void setSchedule(Map schedule) { logMessage("debug", "setSchedule() received and ignored.") }
