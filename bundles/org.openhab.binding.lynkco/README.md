# Lynk&Co Binding

This binding integrates Lynk&Co vehicles into openHAB, providing both status information and control capabilities. It has been tested with the Lynk&Co 01 model.

## Prerequisites

- A Lynk&Co account with the Lynk&Co app installed on your mobile device
- Access to SMS-based Multi-Factor Authentication (MFA)
- Vehicle Identification Number (VIN) from your Lynk&Co app

## Supported Things

* `api` (Bridge) - Connection to the Lynk&Co API
* `vehicle` - A Lynk&Co vehicle

## Thing Configuration

### Bridge Configuration (`api`)

The bridge requires your Lynk&Co account credentials:

| Parameter | Description                                                           |
|-----------|-----------------------------------------------------------------------|
| email     | Email address of your Lynk&Co account                                 |
| password  | Password of your Lynk&Co account                                      |
| mfa       | MFA code (required during initial setup, received via SMS)            |

When first setting up the bridge, it will request an MFA code. 
After entering your email and password:
1. The bridge will enter OFFLINE state requesting MFA code
2. You will receive an SMS with the MFA code on your registered mobile number
3. Enter this code in the bridge configuration and press Save
4. The bridge will then complete authentication and go ONLINE

### Thing Configuration (`vehicle`)

The vehicle thing requires:

| Parameter | Description                                                            |
|-----------|------------------------------------------------------------------------|
| vin       | Vehicle Identification Number (VIN) from your Lynk&Co app              |
| refresh   | Refresh interval in minutes (default: 5, minimum: 5, maximum: 65535)   |

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
lockDoors()                                  // Lock all doors
unlockDoors()                                // Unlock all doors
honkBlink(boolean honk, boolean blink)       // Control horn and lights





# Lynkco Binding

This is an openHAB binding for Lynk&Co vehicles in Europe.

## Supported Things

This binding supports the following thing types:

- api: Bridge - Implements the API that is used to communicate with Lynk&Co cloud service

- lynkco: The Lynk&Co vehicle

## Discovery

After the configuration of the Bridge, your Lynk&Co vehicle will be automatically discovered and placed as a thing in the inbox.

### Configuration Options

Only the bridge require manual configuration. The Lynkco vehicle thing can be added by hand, or you can let the discovery mechanism automatically find it.

#### Bridge

| Parameter | Description                                                  | Type   | Default  | Required |
|-----------|--------------------------------------------------------------|--------|----------|----------|
| username  | The username used to connect to the Lynk&Co app           | String | NA       | yes      |        
| password  | The password used to connect to the Lynk&Co app           | String | NA       | yes      |
| refresh   | Specifies the refresh interval in second                     | Number | 600      | yes      |

#### Lynkco Vehicle

| Parameter | Description                                                  | Type   | Default  | Required |
|-----------|--------------------------------------------------------------|--------|----------|----------|
| vin       | Vehicle Identification Number (VIN) found in Lynk&Co app     | String | NA       | yes      |

## Channels

### Lynk&Co 01

The following channels are supported:

| Channel Type ID             | Item Type             | Description                                                                    |
|-----------------------------|-----------------------|--------------------------------------------------------------------------------|
| temperature                 | Number:Temperature    | This channel reports the current temperature.                                  |
| humidity                    | Number:Dimensionless  | This channel reports the current humidity in percentage.                       |
| tvoc                        | Number:Density        | This channel reports the total Volatile Organic Compounds in microgram/m3.     |
| pm1                         | Number:Dimensionless  | This channel reports the Particulate Matter 1 in ppb.                          |
| pm2_5                       | Number:Dimensionless  | This channel reports the Particulate Matter 2.5 in ppb.                        |
| pm10                        | Number:Dimensionless  | This channel reports the Particulate Matter 10 in ppb.                         |
| co2                         | Number:Dimensionless  | This channel reports the CO2 level in ppm.                                     |
| fanSpeed                    | Number                | This channel sets and reports the current fan speed (1-9).                     |
| filterLife                  | Number:Dimensionless  | This channel reports the remaining filter life in %.                           |
| ionizer                     | Switch                | This channel sets and reports the status of the Ionizer function (On/Off).     |
| doorOpen                    | Contact               | This channel reports the status of door (Opened/Closed).                       |
| workMode                    | String                | This channel sets and reports the current work mode (Auto, Manual, PowerOff.)  |
| uiLIght                     | Switch                | This channel sets and reports the status of the UI Light function (On/Off).    |
| safetyLock                  | Switch                | This channel sets and reports the status of the Safety Lock  function (On/Off).|

## Full Example

### Things-file

```java
// Bridge configuration
Bridge electroluxair:api:myAPI "Electrolux Delta API" [username="user@password.com", password="12345", refresh="300"] {

     Thing electroluxpurea9 myElectroluxPureA9  "Electrolux Pure A9"    [ deviceId="123456789" ]
     
}
```

