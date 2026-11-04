/**
 * Evohome HomeKit Heat-Only Test driver for Hubitat
 * Author: Tobey Ljungberg | Version: 2.0.0 | Copyright (c) 2026 Tobey Ljungberg
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under Apache License, Version 2.0. You may use, modify and redistribute
 * this work under those terms. https://www.apache.org/licenses/LICENSE-2.0
 * Standalone virtual thermostat used to test Hubitat native HomeKit export.
 * It deliberately uses the split thermostat capabilities rather than the
 * composite Thermostat capability, so it has no cooling-setpoint or fan-mode
 * capability. This is a test driver, not an Evohome child driver.
 */
metadata {
    definition(name: "Evohome HomeKit Heat-Only Test", namespace: "tljungberg", author: "Tobey Ljungberg") {
        capability "Actuator"
        capability "Sensor"
        capability "TemperatureMeasurement"
        capability "ThermostatMode"
        capability "ThermostatHeatingSetpoint"
        capability "ThermostatSetpoint"
        capability "ThermostatOperatingState"

        attribute "supportedThermostatModes", "JSON_OBJECT"
        command "setTestTemperature", [[name: "temperature", type: "NUMBER", description: "Set the simulated room temperature"]]
    }
    preferences {
        input name: "prefLogLevel", type: "enum", title: "Log level",
            options: ["error", "warn", "info", "debug"], defaultValue: "debug"
    }
}

def installed() {
    logMessage("debug", "Installing virtual heat-only HomeKit test thermostat.")
    sendEvent(name: "supportedThermostatModes", value: '["heat","auto","off"]')
    sendEvent(name: "temperature", value: 18.0, unit: "°C")
    sendEvent(name: "heatingSetpoint", value: 20.0, unit: "°C")
    sendEvent(name: "thermostatSetpoint", value: 20.0, unit: "°C")
    sendEvent(name: "thermostatMode", value: "auto")
    sendEvent(name: "thermostatOperatingState", value: "idle")
}

def updated() { logMessage("debug", "Driver preferences updated.") }

private void logMessage(String level, String message) {
    Map rank = [error: 1, warn: 2, info: 3, debug: 4]
    String selected = (settings.prefLogLevel ?: "debug").toLowerCase()
    if ((rank[level] ?: 4) <= (rank[selected] ?: 4)) {
        switch (level) {
            case "error": log.error("HomeKit heat-only test: " + message); break
            case "warn": log.warn("HomeKit heat-only test: " + message); break
            case "info": log.info("HomeKit heat-only test: " + message); break
            default: log.debug("HomeKit heat-only test: " + message)
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

// Required by ThermostatMode capability, but deliberately unsupported here.
void cool() { logMessage("debug", "cool() received and ignored; this test thermostat has no cooling capability.") }
void emergencyHeat() { logMessage("debug", "emergencyHeat() received and ignored.") }

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
