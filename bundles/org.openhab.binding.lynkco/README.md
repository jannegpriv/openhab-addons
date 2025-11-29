# Lynk&Co Binding

This binding integrates Lynk&Co vehicles into openHAB, providing both status information and control capabilities.
It has been tested with the Lynk&Co 01 model. It only works for the 01 model before the 2025 facelift.

## Prerequisites

- A Lynk&Co account with the Lynk&Co app installed on your mobile device
- Access to SMS-based Multi-Factor Authentication (MFA)
- Vehicle Identification Number (VIN) from your Lynk&Co app
- A web browser with Developer Tools for initial authentication

## Supported Things

* `api` (Bridge) - Connection to the Lynk&Co API
* `vehicle` - A Lynk&Co vehicle

## Thing Configuration

### Bridge Configuration (`api`)

The bridge requires your Lynk&Co account credentials and uses a manual authentication flow due to Lynk&Co's API requirements:

| Parameter | Description                                                           |
|-----------|-----------------------------------------------------------------------|
| email     | Email address of your Lynk&Co account                                 |
| password  | Password of your Lynk&Co account                                      |
| redirect  | Redirect URL from browser (required during initial setup only)        |

### Initial Authentication Setup

When first setting up the bridge, follow these steps:

1. **Configure the bridge** with your email and password
2. **Check the openHAB logs** - you will see a message like:

```text
   MANUAL LOGIN REQUIRED
   Please open this URL in your web browser:
   https://login.lynkco.com/lynkcoprod.onmicrosoft.com/...
```

3. **Open the URL** provided in the logs in your web browser
4. **Log in** with your email and password
5. **Enter the MFA code** received via SMS
6. **Open Developer Tools** in your browser (F12 or right-click → Inspect)
7. **Go to the Network tab**
8. **Look for a cancelled/failed request** to `msauth://...` after completing MFA
9. **Copy the complete URL** starting with `msauth://prod.lynkco.app.crisp.prod/...`

   - Either from the failed request in Network tab
   - Or from your browser's address bar if it shows an error

10. **Paste this URL** into the "Redirect URL" field in the bridge configuration
11. **Save the configuration**
12. The bridge will complete authentication and go ONLINE

**Note:** After initial setup, the binding will use refresh tokens automatically.
You only need to perform this manual authentication once, or when tokens expire.

### Thing Configuration (`vehicle`)

The vehicle thing requires:

| Parameter | Description                                                            |
|-----------|------------------------------------------------------------------------|
| vin       | Vehicle Identification Number (VIN) from your Lynk&Co app              |
| refresh   | Refresh interval in minutes (default: 5, minimum: 5, maximum: 65535)   |

**Important:** You must configure the VIN in the bridge thing for vehicle discovery to work.

## Channels

### Door Status (`doors`)

| Channel ID | Type | Description |
|-----------------|----------|---------------------------------------|
| last-update     | DateTime | Door status last update timestamp     |
| door-driver     | Contact  | Driver's door status (OPEN/CLOSED)    |
| door-passenger  | Contact  | Passenger's door status (OPEN/CLOSED) |
| door-rear-left  | Contact  | Rear left door status (OPEN/CLOSED)   |
| door-rear-right | Contact  | Rear right door status (OPEN/CLOSED)  |
| hood            | Contact  | Hood status (OPEN/CLOSED)             |
| trunk           | Contact  | Trunk status (OPEN/CLOSED)            |
| tank-flap       | Contact  | Fuel tank flap status (OPEN/CLOSED)   |
| locks-status    | Switch   | Central locking status (ON/OFF)       |
| alarm-status    | Switch   | Alarm system status (ON/OFF)          |

### Window Status (`windows`)

| Channel ID        | Type | Description |
|------------       |----------|----------------------------------------|
| last-update       | DateTime | Window status last update timestamp    |
| window-driver     | Contact  | Driver's window status (OPEN/CLOSED)   |
| window-passenger  | Contact  | Passenger's window status (OPEN/CLOSED)|
| window-rear-left  | Contact  | Rear left window status (OPEN/CLOSED)  |
| window-rear-right | Contact  | Rear right window status (OPEN/CLOSED) |
| sunroof           | Contact  | Sunroof status (OPEN/CLOSED)           |

### Odometer Information (`odometer`)

| Channel ID  | Type          | Description                    |
|-------------|---------------|--------------------------------|
| last-update | DateTime      | Odometer last update timestamp |
| odometer-km | Number:Length | Total distance driven          |

### Fuel Information (`fuel`)

| Channel ID            | Type          | Description                                    |
|-----------------------|---------------|------------------------------------------------|
| last-update           | DateTime      | Fuel information last update timestamp         |
| level                 | Number:Volume | Current fuel level                             |
| level-status          | Number        | Fuel level status                              |
| type                  | String        | Fuel type (e.g., "PETROL_PLUGIN_HYBRID")       |
| range                 | Number:Length | Estimated driving range on remaining fuel      |
| consumption           | Number        | Average fuel consumption (l/100km)             |
| consumption-last-trip | Number        | Last trip's average fuel consumption (l/100km) |

### Position Information (`position`)

| Channel ID       | Type     | Description                    |
|------------------|----------|--------------------------------|
| location         | Location | Current vehicle GPS position   |
| location-trusted | Switch   | Location accuracy indicator    |
| updated-at       | DateTime | Position last update timestamp |

### Battery Status (`battery`)

| Channel ID   | Type                     | Description                          |
|--------------|--------------------------|--------------------------------------|
| last-update  | DateTime                 | Battery status last update timestamp |
| charge       | String                   | Battery charge status                |
| charge-level | Number:Dimensionless     | Battery charge level (%)             |
| energy-level | Number:Dimensionless     | Battery energy level (%)             |
| health       | Number:Dimensionless     | Battery health status (%)            |
| power-level  | Number:Dimensionless     | Battery power level (%)              |
| voltage      | Number:ElectricPotential | Battery voltage                      |

### Charging Status (`charging`)

| Channel ID                | Type                 | Description                           |
|---------------------------|----------------------|---------------------------------------|
| last-update               | DateTime             | Charging status last update timestamp |
| charging-level            | Number:Dimensionless | Current charging level (%)            |
| range                     | Number:Length        | Estimated driving range on battery    |
| time-to-full              | Number:Time          | Time remaining until fully charged    |
| charger-connection-status | String               | Charger connection status             |
| charger-state             | String               | Current charging state                |
| power-mode                | String               | Power mode status                     |

Charger Connection Status options:

- CHARGER_CONNECTION_UNSPECIFIED: "Unspecified"
- CHARGER_CONNECTION_DISCONNECTED: "Disconnected"
- CHARGER_CONNECTION_CONNECTED_WITHOUT_POWER: "Connected (No Power)"
- CHARGER_CONNECTION_POWER_AVAILABLE_BUT_NOT_ACTIVATED: "Power Not Activated"
- CHARGER_CONNECTION_CONNECTED_WITH_POWER: "Connected"
- CHARGER_CONNECTION_INIT: "Initializing"
- CHARGER_CONNECTION_FAULT: "Fault"

Charger State options:

- CHARGER_STATE_UNSPECIFIED: "Unspecified"
- CHARGER_STATE_IDLE: "Idle"
- CHARGER_STATE_PRE_STRT: "Pre-Start"
- CHARGER_STATE_CHARGN: "Charging"
- CHARGER_STATE_ALRM: "Alarm"
- CHARGER_STATE_SRV: "Service"
- CHARGER_STATE_DIAG: "Diagnostics"
- CHARGER_STATE_BOOT: "Boot"
- CHARGER_STATE_RSTRT: "Restart"

### Climate Information (`climate`)

| Channel ID        | Type               | Description                          |
|-------------------|--------------------|--------------------------------------|
| last-update       | DateTime           | Climate status last update timestamp |
| temp-exterior     | Number:Temperature | External temperature                 |
| temp-interior     | Number:Temperature | Interior temperature                 |
| preclimate-active | Switch             | Pre-climatization status             |

### Maintenance Status (`maintenance`)

| Channel ID          | Type   | Description                |
|---------------------|--------|----------------------------|
| brake-fluid         | String | Brake fluid level status   |
| coolant             | String | Coolant level status       |
| engine-oil-level    | String | Engine oil level status    |
| engine-oil-pressure | String | Engine oil pressure status |
| service-warning     | String | Service warning status     |
| washer-fluid        | String | Washer fluid level status  |

Status options for fluid levels:

- NORMAL: "Normal"
- LOW: "Low"
- VERY_LOW: "Very Low"

### Light Bulb Status (`bulbs`)

| Channel ID      | Type     | Description                       |
|-----------------|----------|-----------------------------------|
| updated         | DateTime | Bulb status last update timestamp |
| daytime-running | String   | Daytime running lights status     |
| fog-front       | String   | Front fog lights status           |
| fog-rear        | String   | Rear fog lights status            |
| high-beam       | String   | High beam status                  |
| high-beam-left  | String   | Left high beam status             |
| high-beam-right | String   | Right high beam status            |
| turn-left       | String   | Left turn signal status           |
| low-beam        | String   | Low beam status                   |
| low-beam-left   | String   | Left low beam status              |
| low-beam-right  | String   | Right low beam status             |
| position        | String   | Position lights status            |
| turn-right      | String   | Right turn signal status          |
| stop            | String   | Stop lights status                |

All bulb statuses have options:

- BULB_STATUS_NO_FAULT: "OK"
- BULB_STATUS_FAULT: "Fault"

### Safety Systems (`safety`)

| Channel ID           | Type     | Description                           |
|----------------------|----------|---------------------------------------|
| airbag-updated       | DateTime | Airbag status last update timestamp   |
| airbag-status        | String   | Airbag system status                  |
| seatbelt-updated     | DateTime | Seatbelt status last update timestamp |
| seatbelt-driver      | Switch   | Driver seatbelt status                |
| seatbelt-passenger   | Switch   | Passenger seatbelt status             |
| seatbelt-rear-left   | Switch   | Rear left seatbelt status             |
| seatbelt-rear-middle | Switch   | Rear middle seatbelt status           |
| seatbelt-rear-right  | Switch   | Rear right seatbelt status            |

### Tyre Status (`tyres`)

| Channel ID   | Type     | Description                       |
|--------------|----------|-----------------------------------|
| tyre-updated | DateTime | Tyre status last update timestamp |
| front-left   | String   | Front left tyre pressure status   |
| front-right  | String   | Front right tyre pressure status  |
| rear-left    | String   | Rear left tyre pressure status    |
| rear-right   | String   | Rear right tyre pressure status   |

Tyre pressure status options:

- NO_WARNING: "Normal"
- WARNING: "Low"

### Trip Information (`trip`)

| Channel ID      | Type          | Description                            |
|-----------------|---------------|----------------------------------------|
| last-update     | DateTime      | Trip information last update timestamp |
| avg-speed       | Number:Speed  | Current trip average speed             |
| last-trip-speed | Number:Speed  | Last trip average speed                |
| trip-meter      | Number:Length | Trip meter 1 distance                  |
| trip-meter-2    | Number:Length | Trip meter 2 distance                  |

### Vehicle Status (`vehicle-status`)

| Channel ID    | Type     | Description                          |
|---------------|----------|--------------------------------------|
| last-update   | DateTime | Vehicle status last update timestamp |
| engine-status | String   | Engine status                        |
| key-status    | String   | Key status                           |
| usage-mode    | String   | Vehicle usage mode                   |

### Control Channels

#### Climate Control (`climate-control`)

| Channel ID | Type   | Description               |
|------------|--------|---------------------------|
| preclimate | Switch | Control pre-climatization |

#### Engine Control (`engine-control`)

| Channel ID | Type   | Description       |
|------------|--------|-------------------|
| start      | Switch | Start/stop engine |

#### Door Control (`doors-control`)

| Channel ID | Type   | Description       |
|------------|--------|-------------------|
| lock       | Switch | Lock/unlock doors |

#### Light Control (`lights-control`)

| Channel ID | Type   | Description  |
|------------|--------|--------------|
| flash      | Switch | Flash lights |

#### Horn Control (`horn-control`)

| Channel ID | Type   | Description                 |
|------------|--------|-----------------------------|
| honk       | Switch | Sound horn                  |
| honkflash  | Switch | Sound horn and flash lights |

## Thing Actions

The following actions are available:

```java
startClimate(int climateLevel, int duration)  // Start climate control
stopClimate()                                 // Stop climate control
startEngine(int duration)                     // Start engine
stopEngine()                                  // Stop engine
lockDoors()                                   // Lock all doors
unlockDoors()                                 // Unlock all doors
honkBlink(boolean honk, boolean blink)        // Control horn and lights
```

## Configuration Examples

### Things-file

```java
// Bridge configuration
Bridge lynkco:api:mycar "Lynk&Co API" [
    email="user@example.com",
    password="secret",
    vin="YOUR_VIN_NUMBER"
] {
    // Vehicle configuration
    Thing vehicle car "My Lynk&Co 01" [
        vin="YOUR_VIN_NUMBER",
        refresh=5
    ]
}
```

### Items-file

```java
// Status Items
Group gCar "My Lynk&Co 01"

// Doors
Contact Car_DoorDriver        "Driver Door [%s]"          (gCar) {channel="lynkco:vehicle:mycar:car:doors#door-driver"}
Contact Car_DoorPassenger     "Passenger Door [%s]"       (gCar) {channel="lynkco:vehicle:mycar:car:doors#door-passenger"}
Switch  Car_LocksStatus       "Locks [%s]"               (gCar) {channel="lynkco:vehicle:mycar:car:doors#locks-status"}

// Climate
Number:Temperature Car_TempExt "Outside Temperature [%.1f %unit%]" (gCar) {channel="lynkco:vehicle:mycar:car:climate#temp-exterior"}
Number:Temperature Car_TempInt "Inside Temperature [%.1f %unit%]"  (gCar) {channel="lynkco:vehicle:mycar:car:climate#temp-interior"}

// Battery and Charging
Number:Dimensionless Car_BatteryLevel "Battery Level [%.1f %%]" (gCar) {channel="lynkco:vehicle:mycar:car:battery#charge-level"}
String Car_ChargerState       "Charging State [%s]"      (gCar) {channel="lynkco:vehicle:mycar:car:charging#charger-state"}
Number:Time Car_ChargingTime  "Time to Full [%d %unit%]" (gCar) {channel="lynkco:vehicle:mycar:car:charging#time-to-full"}

// Location
Location Car_Location         "Car Location"              (gCar) {channel="lynkco:vehicle:mycar:car:position#location"}

// Controls
Switch Car_Lock              "Lock Car"                  (gCar) {channel="lynkco:vehicle:mycar:car:doors-control#lock"}
Switch Car_Climate           "Climate Control"           (gCar) {channel="lynkco:vehicle:mycar:car:climate-control#preclimate"}
Switch Car_Engine            "Engine"                    (gCar) {channel="lynkco:vehicle:mycar:car:engine-control#start"}
Switch Car_Horn              "Horn"                      (gCar) {channel="lynkco:vehicle:mycar:car:horn-control#honk"}
```

### Rule-example

```java
rule "Start Climate Control Morning"
when
    Time cron "0 30 7 ? * MON-FRI"
then
    val actions = getActions("lynkco", "lynkco:vehicle:mycar:car")
    actions.startClimate(2, 15)  // Level 2, 15 minutes
end
```

## Troubleshooting

### Authentication Issues

If you encounter authentication problems:

1. **Check the logs** for the login URL and follow the manual authentication process
2. **Ensure you copy the complete msauth:// URL** including the code parameter
3. **If tokens expire**, remove the redirect URL field, save, and repeat the authentication process
4. **Check that MFA is enabled** on your Lynk&Co account

### Vehicle Not Discovered

If your vehicle doesn't appear:

1. **Ensure the VIN is configured** in the bridge thing configuration
2. **Check that the bridge status is ONLINE**
3. **Verify the VIN** matches exactly what's shown in your Lynk&Co app

### Connection Problems

- **Check your internet connection**
- **Verify credentials** are correct
- **Look for error messages** in the openHAB logs
