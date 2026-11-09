# Evohome Hubitat Integration

**Author:** Tobey Ljungberg  
**Version:** 2.0.1
**License:** Apache License 2.0 (see [LICENSE](LICENSE))

This is a new and improved Hubitat integration for Honeywell/Resideo Evohome systems. It connects heating zones, system modes and domestic hot water, with Hubitat mode synchronisation, fault notifications and native HomeKit support for heating zones.

## Files

- EvoHomeConnect.groovy: parent app, API access, child creation, polling, debounce, optimistic state reconciliation, system-mode synchronisation and DHW commands.
- EvoHomeHeatingZone.groovy: per-zone thermostat. HomeKit Off writes a permanent 5 C override; Auto clears the zone override.
- EvoHomeHotWater.groovy: Hubitat-only DHW switch, state and temperature device.
- EvoHomeSystemController.groovy: Hubitat-only system-mode control device.

The parent app can monitor API-reported active faults for the location, gateway,
system, zones and hot water. Current faults and a readable summary are published
on the matching child device where one exists. Child-device fault attributes show
readable fault names or "No active faults", rather than a raw empty JSON array.
Override expiry attributes explain when no expiry applies, such as following the
schedule or a permanent override. New fault appearances are written
to the preserved app log and can be pushed through one or more selected Hubitat
Notification devices. Select the mobile app notification devices for the people
who should receive pushes in the parent app's **Evohome fault alerts** section.
An active fault already present on the first successful poll is also reported.
Clearing a fault updates the published list; a later reappearance can notify
again. The API reports low-battery conditions, not a battery percentage. Fault
notification delivery depends on the selected Hubitat notification devices and
mobile app push settings. The cloud REST API does not expose live boiler relay
or OpenTherm heat-demand telemetry, so the integration cannot report them.

When configuring Hubitat's native HomeKit integration, select the heating-zone children only. Do not select the system controller or DHW child.

The main app's heating-only option defaults to on. With it enabled, zone child devices ignore cooling commands and mirror the heating setpoint into `coolingSetpoint`. Auto clears a zone override and resumes its schedule; Heat creates an override at the current target using the zone's configured setpoint policy. Hubitat's native HomeKit integration may still display cooling and fan controls because its composite thermostat capability includes them. This integration does not implement evohome cooling control.

Zone child devices expose `nextScheduledTime` and `nextScheduledSetpoint` from the downloaded weekly schedule. These show the upcoming schedule transition used by the "until next schedule change" setpoint policy.

Assign one or more Window contact sensors to each zone from the parent app's **Configure window contacts by zone** page. The zone list appears after the first installation sync creates the zone child devices. If any selected contact is open, the integration applies a permanent 5 °C override to that zone and ignores manual zone setpoint or mode changes until all selected contacts are closed. It then restores the state saved before the first contact opened: schedule-following zones resume their schedule, permanent overrides are restored, and temporary overrides are restored only if their original expiry is still in the future. If Evohome is in system-wide Heating Off, contact actions are deferred; an open contact is applied when the system leaves Off, and a saved zone setting is restored when appropriate. Contact changes are handled by event subscriptions, with current sensor states checked when the app is initialised or the mapping is saved.

## API and operation notes

The EMEA evohome API is undocumented. The app's API host setting takes an origin only, such as `https://mytotalconnectcomfort.com`, without a `/WebApi` path. Authentication sends a URL-encoded form body to match the original working app; Hubitat's asynchronous HTTP methods serialise Map bodies as JSON. Validate the authentication response, status JSON and write acknowledgements on a test installation before replacing a live integration. The API calls are asynchronous; commands update child state immediately, are coalesced during the debounce window, and schedule a later refresh.

The system-mode child deliberately uses custom mode commands instead of the HomeKit-facing thermostat capability. The DHW API reports status and temperature where available, but does not expose a DHW target setpoint.

Mode sync snapshots system mode status before setting Away. The Away transition is skipped if evohome is already Away or HeatingOff. If it was already Away, a main-app option chooses whether it stays Away on return or changes to Auto. HeatingOff is always preserved. A previous-mode restore is attempted only when the parent app recorded that it initiated Away.

On the separate **Configure Away override preservation by zone** page, each zone can opt in to preserving its override during this Away transition. When enabled, the app saves that zone's permanent or temporary override before it sets the system to Away. After the previous system mode is restored, permanent overrides are reapplied and temporary overrides are reapplied only when their saved expiry is still in the future; expired or unusable temporary overrides return to the zone schedule. Zones with the option disabled retain Evohome's normal behaviour, where changing the system mode may clear their overrides. This setting is per zone because room overrides often have different purposes and should not all be preserved or cleared together.

Informational logs use complete, readable sentences describing what happened and what the integration is doing next. For example, when Hubitat enters Away, the main app reports that it is setting Evohome to Away and has saved the previous mode for restoration. Child devices also describe received zone, system-mode and hot-water actions in plain language.

Logging levels are cumulative severity thresholds. **Error** shows errors; **Warn** shows warnings and errors; **Info** adds readable activity messages; **Debug** adds API calls, responses and processing details. Every level includes all more severe categories. For a complete command trace, set **Main app log level** to Debug in Evohome Connect. Child devices have individual effective levels. Use **Reset Logging** in the parent app to set every Evohome child device's effective level and saved preference to the current main-app level. Child logs show command receipt and state application; the main app logs API requests, request IDs, response status/content type, elapsed time and a redacted response summary. Authentication credentials and tokens are never logged.

Access-token renewal is scheduled using the returned `expires_in`, five minutes before expiry (or 10% of the lifetime for shorter tokens, with a 30-second minimum lead). Polling also checks the renewal deadline as a fallback. A failed refresh-token grant clears that refresh token and retries with password authentication. Renewal scheduling, duplicate-auth suppression, refresh failures and fallback are included in Debug logs without logging token values.

