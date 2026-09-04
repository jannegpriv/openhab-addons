# Sigenergy

This extension adds support for plant-level monitoring of Sigenergy SigenStor energy storage systems (energy controller, battery and PV) via the Sigenergy Modbus TCP interface.

The binding is strictly **read-only**: it only issues Modbus "read input registers" (FC04) requests and defines no writable channels.
It cannot change any setting of the installation, and the Sigen AI / EMS logic of the system remains fully in control.

## Prerequisites

- Modbus TCP must be enabled for your system in the MySigen app (ask your installer if the option is not visible).
- The plant is addressed via unit ID 247 (the default plant address of Sigenergy systems).

## Supported Things

This bundle adds the following thing type to the Modbus binding:

| Thing Type      | Description                                                                       |
| --------------- | --------------------------------------------------------------------------------- |
| sigenergy-plant | Plant-level values of a Sigenergy system (read-only, unit ID 247)                 |
| sigenergy-evac  | Read-only monitoring of a Sigen EVAC charger (own unit ID 1-246, set in mySigen)  |

The things must be created as children of a generic Modbus `tcp` (or `serial`) bridge.
No generic poller or data things are needed; the things poll and decode all channels by themselves.

Sigenergy requires third-party access to the EVAC through the hybrid inverter's TCP server: the charger is **not** a separate IP endpoint but a separate Modbus unit ID on the same bridge.
The charger's Modbus unit ID does not exist until it has been configured in the mySigen app; until then the EVAC thing stays offline with a communication error (without affecting the plant thing).

## Thing Configuration

| Parameter    | Type    | Default | Description                                                                          |
| ------------ | ------- | ------- | ------------------------------------------------------------------------------------ |
| pollInterval | integer | 5000    | Time between fast polls in milliseconds. Minimum 1000 ms (Sigenergy device limit).   |
| maxTries     | integer | 3       | Number of read attempts before a poll is considered failed. Minimum 1.               |

A poll interval below 1000 ms puts the thing in a configuration error state.

## Polling Behavior

The binding issues a small number of consolidated FC04 reads, staggered so that requests are spaced out:

| Block | Registers      | Interval        | Content                                            |
| ----- | -------------- | --------------- | -------------------------------------------------- |
| Core  | 30003, len 65  | pollInterval    | running data, powers, phases, SOC, capacities      |
| Load  | 30282, len 5   | pollInterval    | load powers + cell temperature (protocol V2.8+)    |
| Slow 1 | 30083, len 15 | 60 s            | battery limits, SOH, PV/load counters              |
| Slow 2 | 30200, len 24 | 60 s            | battery/grid cumulative energy counters            |
| Slow 3 | 30272, len 4  | 60 s            | daily PV generation                                |

Only a failure of the core block takes the plant thing offline.
Optional blocks that the firmware rejects are disabled individually; their channels stay undefined while everything else keeps working.
The `overview#last-successful-update` channel is refreshed only after a fully decoded core poll — if it stops moving, the data shown is stale.

The EVAC thing issues exactly one additional FC04 read per poll (start 32000, length 15) with its own poll offset, staggered relative to the plant's poll slots.
For a hard guarantee that no two requests to the shared endpoint are initiated less than 1000 ms apart regardless of unit ID, set `timeBetweenTransactionsMillis=1000` on the generic TCP bridge — the Modbus transport serializes and spaces all transactions per endpoint.
An EVAC failure never affects the plant thing; the EVAC thing recovers automatically on the next successful read and its `status#last-successful-update` channel only advances on complete successful polls.

## EVAC Channels

Groups `status`, `charging` and `ratings` (registers per Sigenergy Modbus Protocol V2.9, table 5-5):

| Group    | Channel               | Type                    | Register | Description                                                            |
| -------- | --------------------- | ----------------------- | -------- | ----------------------------------------------------------------------- |
| status   | system-state          | String                  | 32000    | IEC 61851-1 state (Appendix 7): SYSTEM_INIT, A1_A2, B1, B2, C1, C2, F, E; unknown as `UNKNOWN_n` |
| status   | raw-state             | Number                  | 32000    | Raw state code (advanced)                                              |
| status   | alarm-active          | Switch                  | 32012-14 | ON if any documented alarm bit is set                                  |
| status   | alarm-summary         | String                  | 32012-14 | Canonical identifiers of active documented alarms, or `NONE`           |
| status   | alarm-mask-1/2/3      | Number                  | 32012-14 | Raw bit masks (advanced)                                               |
| status   | last-successful-update | DateTime               | -        | Timestamp of the last complete successful poll                         |
| charging | total-energy          | Number:Energy           | 32001    | Cumulative charger energy (kWh)                                        |
| charging | charging-power        | Number:Power            | 32003    | Current charging power (kW), positive while charging                   |
| ratings  | rated-power           | Number:Power            | 32005    | Charger rated power (advanced)                                         |
| ratings  | rated-current         | Number:ElectricCurrent  | 32007    | Charger rated current (advanced)                                       |
| ratings  | rated-voltage         | Number:ElectricPotential | 32009   | Charger rated voltage (advanced)                                       |
| ratings  | input-breaker-current | Number:ElectricCurrent  | 32010    | The charger's own input-breaker rating — NOT the household main fuse or the DLM limit (advanced) |

The alarm summary covers exactly the bits documented in appendices 8-10 (9 + 6 + 3 alarms); undocumented bits stay visible in the raw masks and are logged at debug level once per change.
The charger control registers (42000+) are never accessed.
Session energy is intentionally not derived in the binding — use the cumulative `total-energy` counter with persistence at the item/rule layer if needed.

## Channels

Channels are grouped into `overview`, `grid`, `battery` and `energy`.

| Group    | Channel                        | Type                 | Register | Description                                                                              |
| -------- | ------------------------------ | -------------------- | -------- | ---------------------------------------------------------------------------------------- |
| overview | ems-mode                       | String               | 30003    | EMS work mode (state options with EN/SV labels; unknown values shown as `Unknown (n)`)    |
| overview | on-off-grid-status             | String               | 30009    | On grid / off grid (auto) / off grid (manual)                                             |
| overview | plant-running-state            | String               | 30051    | Standby / Running / Fault / Shutdown / Environmental abnormality                          |
| overview | plant-power                    | Number:Power         | 30031    | Total plant active power (W)                                                              |
| overview | pv-power                       | Number:Power         | 30035    | PV generation power (W)                                                                   |
| overview | load-power                     | Number:Power         | 30284    | Total load power; derived as PV + grid - battery power on firmware without the register   |
| overview | general-load-power             | Number:Power         | 30282    | General load power (advanced); stays undefined on firmware without the register           |
| overview | last-successful-update         | DateTime             | -        | Timestamp of the last fully decoded core poll                                             |
| grid     | grid-sensor-status             | String               | 30004    | Whether the grid power sensor is communicating                                            |
| grid     | grid-power                     | Number:Power         | 30005    | Grid exchange power: positive = buy from grid, negative = sell to grid (W)                |
| grid     | grid-phase-a-power             | Number:Power         | 30052    | Grid phase A active power, same sign convention                                           |
| grid     | grid-phase-b-power             | Number:Power         | 30054    | Grid phase B active power                                                                 |
| grid     | grid-phase-c-power             | Number:Power         | 30056    | Grid phase C active power                                                                 |
| battery  | battery-soc                    | Number:Dimensionless | 30014    | Aggregate battery state of charge in %                                                    |
| battery  | battery-power                  | Number:Power         | 30037    | Battery power: positive = charging, negative = discharging (W)                            |
| battery  | battery-soh                    | Number:Dimensionless | 30087    | Battery state of health in %                                                              |
| battery  | charge-cutoff-soc              | Number:Dimensionless | 30085    | Configured charge cut-off SOC (advanced)                                                  |
| battery  | discharge-cutoff-soc           | Number:Dimensionless | 30086    | Configured discharge cut-off SOC                                                          |
| battery  | cell-temperature               | Number:Temperature   | 30286    | Average ESS cell temperature (protocol V2.8+)                                             |
| battery  | rated-capacity                 | Number:Energy        | 30083    | Rated ESS energy capacity (advanced)                                                      |
| battery  | available-charge-capacity      | Number:Energy        | 30064    | Currently chargeable energy (advanced)                                                    |
| battery  | available-discharge-capacity   | Number:Energy        | 30066    | Currently dischargeable energy (advanced)                                                 |
| energy   | pv-generation-today            | Number:Energy        | 30272    | PV generation today (kWh)                                                                 |
| energy   | pv-generation-yesterday        | Number:Energy        | 30274    | PV generation of the previous day (advanced)                                              |
| energy   | load-consumption-today         | Number:Energy        | 30092    | Load consumption today (kWh)                                                              |
| energy   | total-pv-generation            | Number:Energy        | 30088    | Cumulative PV generation (U64 counter)                                                    |
| energy   | total-load-consumption         | Number:Energy        | 30094    | Cumulative load consumption (U64 counter)                                                 |
| energy   | total-battery-charged-energy   | Number:Energy        | 30200    | Cumulative energy charged into the ESS (U64 counter)                                      |
| energy   | total-battery-discharged-energy | Number:Energy       | 30204    | Cumulative energy discharged from the ESS (U64 counter)                                   |
| energy   | total-imported-energy          | Number:Energy        | 30216    | Cumulative energy imported from the grid (U64 counter)                                    |
| energy   | total-exported-energy          | Number:Energy        | 30220    | Cumulative energy exported to the grid (U64 counter)                                      |

Enum channels publish stable canonical tokens (e.g. `MAX_SELF_CONSUMPTION`) with English and Swedish display labels via state options; unknown values are preserved and shown as `Unknown (n)`.
Power values are published in watt; use a state pattern such as `%.1f kW` on the item to display kilowatt.
Energy values are published in kWh; the cumulative counters are suitable for persistence-based daily-delta calculations (import/export/battery per day).
The newer duplicate statistics counters at 30228-30268 are intentionally not polled: the V2.9 document warns that they reset on firmware upgrade.

The load power registers 30282/30284 and cell temperature 30286 were added in Sigenergy Modbus Protocol V2.8 (2025-11-28).
On older firmware the device rejects that request; the binding then stops polling those registers and derives `load-power` as PV power + grid power - battery power instead (published once all three sources have been read at least once).

## Full Example

### Things

```java
Bridge modbus:tcp:sigenstor [ host="192.168.1.236", port=502, id=247, enableDiscovery=false, rtuEncoded=false, timeBetweenTransactionsMillis=1000 ] {
    Thing sigenergy-plant plant "SigenStor Plant" [ pollInterval=5000, maxTries=3 ]
    Thing sigenergy-evac evac "EV Charger" [ unitId=2, pollInterval=5000, maxTries=3 ]
}
```

The EVAC `unitId` is whatever Modbus slave address was configured for the charger in the mySigen installer app (an installer-level setting; the end-user app does not show it).

### Items

```java
String        SigenStor_EMS_Mode      "EMS Mode"              { channel="modbus:sigenergy-plant:sigenstor:plant:overview#ems-mode" }
Number:Power  SigenStor_Plant_Power   "Plant Power [%.1f kW]" { channel="modbus:sigenergy-plant:sigenstor:plant:overview#plant-power" }
Number:Power  SigenStor_PV_Power      "PV Power [%.1f kW]"    { channel="modbus:sigenergy-plant:sigenstor:plant:overview#pv-power" }
Number:Power  SigenStor_Load_Power    "Load Power [%.1f kW]"  { channel="modbus:sigenergy-plant:sigenstor:plant:overview#load-power" }
Number:Power  SigenStor_Gen_Load      "General Load [%.1f kW]" { channel="modbus:sigenergy-plant:sigenstor:plant:overview#general-load-power" }
Number:Power  SigenStor_Grid_Power    "Grid Power [%.1f kW]"  { channel="modbus:sigenergy-plant:sigenstor:plant:grid#grid-power" }
Number        SigenStor_Battery_SOC   "Battery SOC [%.1f %%]" { channel="modbus:sigenergy-plant:sigenstor:plant:battery#battery-soc" }
Number:Power  SigenStor_Battery_Power "Battery [%.1f kW]"     { channel="modbus:sigenergy-plant:sigenstor:plant:battery#battery-power" }
```

## Register Addressing

Sigenergy documents PDU addresses: the binding sends the documented address (e.g. 30014 for the battery SOC) on the wire as-is, without subtracting one.
This differs from bindings that use one-based register numbers.
The addressing was verified against a live SigenStor plant: FC04 at address 30014 divided by 10 matched the SOC shown in MySigen.

32-bit values are signed big-endian with the high word first.

## Compatibility

The register map follows the official _Sigenergy Modbus Protocol V2.9_ (release date 2026-05-13), table 5-1 "Plant running information register definition".
Power registers are documented there as S32 with gain 1000 and unit kW, which means the raw register value equals watts; the SOC register 30014 has gain 10 and unit %.
The addressing and SOC scaling were additionally validated live on a SigenStor with a three-phase 10 kW energy controller, 18 kWh battery and 4.12 kWp PV.
Firmware older than protocol V2.8 lacks the direct load registers 30282/30284; the binding falls back automatically as described above.

## Troubleshooting

- **Bridge is ONLINE but the plant thing stays OFFLINE with a communication error**: Modbus TCP is likely not enabled in MySigen, the unit ID of the bridge is not 247, or a firewall blocks port 502. The generic bridge only verifies that the TCP connection can be opened; actual register reads happen through this thing.
- **Thing shows a configuration error**: check that `pollInterval` is at least 1000 and `maxTries` at least 1.
- **Values look implausible**: verify that the bridge is configured with `rtuEncoded=false` (normal Modbus TCP) and unit `id=247`.

Temporary read failures set the thing OFFLINE with a communication error; it recovers automatically on the next successful poll.
