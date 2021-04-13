# SLTrafficInformation Binding

This binding uses Stockholms Lokaltrafik's (SL) open API to get information about deviations for configured line numbers and real time information of upcoming traffic for a particular configured station.


## Supported Things

This binding supports the following thing types:

- Deviations
- Realtime Information

You must create an own API key for each thing type by creating an account on:

https://www.trafiklab.se/

and creating a project that shall use the APIs:

- SL Störningsinformation 2
- SL Realtidsinformation 4

Then create your own API codes for those two APIs.

To get correct siteid for a station, use this URL:

https://www.trafiklab.se/api/sl-platsuppslag/konsol

## Discovery

NA

## Enable Debugging

To enable DEBUG logging for the binding, login to Karaf console and enter:

`openhab> log:set DEBUG org.openhab.binding.sltrafficinformation`

## Thing Configuration

You can configure the things preferably via things files or via the PaperUI.

### Deviations

#### Configuration Options

*   `apiKeyDeviation` - Your API key from SL for deviations.

*   `lineNumbers` - Line numbers of interest (comma separated list).

*   `refresh` - Specifies the refresh interval in minutes (defalt 10)

### Realtime Information

Journey direction can be either 1 or 2 and is used to filter all traffic in one direction. 
Since different site IDs can have different meaning for 1 and 2, you have to try out which direction to configure for your site ID.

If direction is not configured, destinations will be used for filtering traffic.
**NOTE**: This must be the end destination for the traffic, not a destination on route.

If neither journeyDirection nor destinations is configured, no traffic will be presented.

Line numbers can be used to filter on one or many lines.

Offset can be used to filter out departures that are too close in time to be able to catch.


#### Configuration Options 

*   `apiKeyRealTime` - Your API key from SL for real time information.

*   `siteId` - SL site ID of interest. See link above for getting correct site ID!

*   `timeWindow` - Time window in minutes (0-60).

*   `journeyDirection` - Journey direction (1 or 2).

*   `destinations` - End destination(s) of interest (comma separated list).

*   `lineNumbers` - Line number(s) of interest (comma separated list).

*   `offset` - Specifies the offset time in minutes (default 0). Used e.g. for setting a walk time to the station.

*   `refresh` - Specifies the refresh interval in minutes (default 10).


## Channels

([deviations]) supports the following channel:

| Channel Type ID     | Item Type | Description                       |
|------------|--------|-----------------------------------------------|
| deviations | String | Found deviations for configured  line numbers.|

([realTimeInformation]) supports the following channel:

| Channel Type ID           | Item Type | Description                                                          |
|---------------------------|--------|-------------------------------------------------------------------------|
| realTimeInformation       | String | Found realtime information for configured site ID and filters.          |
| nextDeparture             | String | Next departure after offset and filters for configured site ID.         |
| secondDeparture           | String | Second departure after offset and filters for configured site ID.       |
| thirdDeparture            | String | Third departure after offset and filters for configured site ID.        |
| nextDepartureDeviations   | String | Next departure deviations.                                              |
| secondDepartureDeviations | String | Second departure deviations.                                            |
| thirdDepartureDeviations  | String | Third departure deviations.                                             |


## Full Example

```
# Things file
sltrafficinformation:deviations:minaavvikelser "Avvikelser" [ apiKeyDeviation="d5gffb2cc7834bd2b242d936cfd45afg", lineNumbers="703,710,744,40,41,41X" ]
sltrafficinformation:realTimeInformation:blacksvampsvagen "Bläcksvampsvägen" [ apiKeyRealTime="569ce4711f444b73dfg32ffefe7f0007", siteId="7021", timeWindow=45, destinations="Fruängen, Skärholmen", refresh=10 ]
sltrafficinformation:realTimeInformation:balingsnas "Balingsnäs" [ apiKeyRealTime="569ce4711f444b73dfg32ffefe7f0007", siteId="7030", timeWindow=60, offset=10, destinations="Högdalen, Skärholmen", refresh=5 ]
sltrafficinformation:realTimeInformation:mellansjo "Mellansjö" [ apiKeyRealTime="569ce4711f444b73dfg32ffefe7f0007", siteId="7031", timeWindow=60, offset=10, destinations="Högdalen, Skärholmen", refresh=5 ]
sltrafficinformation:realTimeInformation:huddingestation "Huddinge Station" [ apiKeyRealTime="569ce4711f444b73dfg32ffefe7f0007", siteId="9527", timeWindow=60, journeyDirection="2", lineNumbers="40,41", refresh=10 ]

# Item for refresh
Switch SL_REFRESH_BLACKSVAMPSVAGEN "Refresh SL Realtime Bläcksvampsvägen"

# Rule to trigger refresh
import org.eclipse.smarthome.core.types.RefreshType
rule "Handle Refresh of SL Traffic Information Bläcksvampsvägen"
when
    Item SL_REFRESH_BLACKSVAMPSVAGEN received command
then
    var String command = SL_REFRESH_BLACKSVAMPSVAGEN.state.toString.toLowerCase
    logDebug("RULES","Refresh SL Traffic Information Bläcksvampsvägen Rule command: " + command)
    if (command.contains("on")) {
        sendCommand(SL_BLACKSVAMPSVAGEN, RefreshType.REFRESH)
        SL_BLACKSVAMPSVAGEN.sendCommand(RefreshType.REFRESH)
    }
    else if (command.contains("off")) {
        sendCommand(SL_BLACKSVAMPSVAGEN, RefreshType.REFRESH)
        SL_BLACKSVAMPSVAGEN.sendCommand(RefreshType.REFRESH)
    }
end


```

