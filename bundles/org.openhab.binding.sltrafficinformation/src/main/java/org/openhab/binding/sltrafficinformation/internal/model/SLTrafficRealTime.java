/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.sltrafficinformation.internal.model;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * The {@link SLTrafficRealTime} is responsible for JSON conversion.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class SLTrafficRealTime {

    @SerializedName("StatusCode")
    private int statusCode;
    @SerializedName("Message")
    private @Nullable Object message;
    @SerializedName("ExecutionTime")
    private int executionTime;
    @SerializedName("ResponseData")
    private @Nullable ResponseData responseData;

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public @Nullable Object getMessage() {
        return message;
    }

    public void setMessage(Object message) {
        this.message = message;
    }

    public int getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(int executionTime) {
        this.executionTime = executionTime;
    }

    public @Nullable ResponseData getResponseData() {
        return responseData;
    }

    public void setResponseData(ResponseData responseData) {
        this.responseData = responseData;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + executionTime;
        Object message2 = message;
        result = prime * result + ((message2 == null) ? 0 : message2.hashCode());
        ResponseData responseData2 = responseData;
        result = prime * result + ((responseData2 == null) ? 0 : responseData2.hashCode());
        result = prime * result + statusCode;
        return result;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        SLTrafficRealTime other = (SLTrafficRealTime) obj;
        if (executionTime != other.executionTime) {
            return false;
        }
        Object message2 = message;
        if (message2 == null) {
            if (other.message != null) {
                return false;
            }
        } else if (!message2.equals(other.message)) {
            return false;
        }
        ResponseData responseData2 = responseData;
        if (responseData2 == null) {
            if (other.responseData != null) {
                return false;
            }
        } else if (!responseData2.equals(other.responseData)) {
            return false;
        }
        if (statusCode != other.statusCode) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "SLTrafficRealTime [statusCode=" + statusCode + ", message=" + message + ", executionTime="
                + executionTime + ", responseData=" + responseData + "]";
    }

    @NonNullByDefault
    public class ResponseData {

        @SerializedName("LatestUpdate")
        private @Nullable String latestUpdate;
        @SerializedName("DataAge")
        private int dataAge;
        @SerializedName("Metros")
        private @Nullable List<Metro> metros;
        @SerializedName("Buses")
        private @Nullable List<Bus> buses;
        @SerializedName("Trains")
        private @Nullable List<Train> trains;
        @SerializedName("Trams")
        private @Nullable List<Tram> trams;
        @SerializedName("Ships")
        private @Nullable List<Object> ships;
        @SerializedName("StopPointDeviations")
        private @Nullable List<StopPointDeviation> stopPointDeviations;

        public @Nullable String getLatestUpdate() {
            return latestUpdate;
        }

        public void setLatestUpdate(String latestUpdate) {
            this.latestUpdate = latestUpdate;
        }

        public int getDataAge() {
            return dataAge;
        }

        public void setDataAge(int dataAge) {
            this.dataAge = dataAge;
        }

        public @Nullable List<Metro> getMetros() {
            return metros;
        }

        public void setMetros(List<Metro> metros) {
            this.metros = metros;
        }

        public @Nullable List<Bus> getBuses() {
            return buses;
        }

        public void setBuses(List<Bus> buses) {
            this.buses = buses;
        }

        public @Nullable List<Train> getTrains() {
            return trains;
        }

        public void setTrains(List<Train> trains) {
            this.trains = trains;
        }

        public @Nullable List<Tram> getTrams() {
            return trams;
        }

        public void setTrams(List<Tram> trams) {
            this.trams = trams;
        }

        public @Nullable List<Object> getShips() {
            return ships;
        }

        public void setShips(List<Object> ships) {
            this.ships = ships;
        }

        public @Nullable List<StopPointDeviation> getStopPointDeviations() {
            return stopPointDeviations;
        }

        public void setStopPointDeviations(List<StopPointDeviation> stopPointDeviations) {
            this.stopPointDeviations = stopPointDeviations;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getEnclosingInstance().hashCode();
            List<Bus> buses2 = buses;
            result = prime * result + ((buses2 == null) ? 0 : buses2.hashCode());
            result = prime * result + dataAge;
            String latestUpdate2 = latestUpdate;
            result = prime * result + ((latestUpdate2 == null) ? 0 : latestUpdate2.hashCode());
            List<Metro> metros2 = metros;
            result = prime * result + ((metros2 == null) ? 0 : metros2.hashCode());
            List<Object> ships2 = ships;
            result = prime * result + ((ships2 == null) ? 0 : ships2.hashCode());
            List<StopPointDeviation> stopPointDeviations2 = stopPointDeviations;
            result = prime * result + ((stopPointDeviations2 == null) ? 0 : stopPointDeviations2.hashCode());
            List<Train> trains2 = trains;
            result = prime * result + ((trains2 == null) ? 0 : trains2.hashCode());
            List<Tram> trams2 = trams;
            result = prime * result + ((trams2 == null) ? 0 : trams2.hashCode());
            return result;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            ResponseData other = (ResponseData) obj;
            if (!getEnclosingInstance().equals(other.getEnclosingInstance())) {
                return false;
            }
            List<Bus> buses2 = buses;
            if (buses2 == null) {
                if (other.buses != null) {
                    return false;
                }
            } else if (!buses2.equals(other.buses)) {
                return false;
            }
            if (dataAge != other.dataAge) {
                return false;
            }
            String latestUpdate2 = latestUpdate;
            if (latestUpdate2 == null) {
                if (other.latestUpdate != null) {
                    return false;
                }
            } else if (!latestUpdate2.equals(other.latestUpdate)) {
                return false;
            }
            List<Metro> metros2 = metros;
            if (metros2 == null) {
                if (other.metros != null) {
                    return false;
                }
            } else if (!metros2.equals(other.metros)) {
                return false;
            }
            List<Object> ships2 = ships;
            if (ships2 == null) {
                if (other.ships != null) {
                    return false;
                }
            } else if (!ships2.equals(other.ships)) {
                return false;
            }
            List<StopPointDeviation> stopPointDeviations2 = stopPointDeviations;
            if (stopPointDeviations2 == null) {
                if (other.stopPointDeviations != null) {
                    return false;
                }
            } else if (!stopPointDeviations2.equals(other.stopPointDeviations)) {
                return false;
            }
            List<Train> trains2 = trains;
            if (trains2 == null) {
                if (other.trains != null) {
                    return false;
                }
            } else if (!trains2.equals(other.trains)) {
                return false;
            }
            List<Tram> trams2 = trams;
            if (trams2 == null) {
                if (other.trams != null) {
                    return false;
                }
            } else if (!trams2.equals(other.trams)) {
                return false;
            }
            return true;
        }

        private SLTrafficRealTime getEnclosingInstance() {
            return SLTrafficRealTime.this;
        }

        @Override
        public String toString() {
            return "ResponseData [latestUpdate=" + latestUpdate + ", dataAge=" + dataAge + ", metros=" + metros
                    + ", buses=" + buses + ", trains=" + trains + ", trams=" + trams + ", ships=" + ships
                    + ", stopPointDeviations=" + stopPointDeviations + "]";
        }
    }

    @NonNullByDefault
    public class StopPointDeviation {

        @SerializedName("StopInfo")
        private @Nullable StopInfo stopInfo;
        @SerializedName("Deviation")
        private @Nullable Deviation deviation;

        public @Nullable StopInfo getStopInfo() {
            return stopInfo;
        }

        public void setStopInfo(StopInfo stopInfo) {
            this.stopInfo = stopInfo;
        }

        public @Nullable Deviation getDeviation() {
            return deviation;
        }

        public void setDeviation(Deviation deviation) {
            this.deviation = deviation;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getEnclosingInstance().hashCode();
            Deviation deviation2 = deviation;
            result = prime * result + ((deviation2 == null) ? 0 : deviation2.hashCode());
            StopInfo stopInfo2 = stopInfo;
            result = prime * result + ((stopInfo2 == null) ? 0 : stopInfo2.hashCode());
            return result;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            StopPointDeviation other = (StopPointDeviation) obj;
            if (!getEnclosingInstance().equals(other.getEnclosingInstance())) {
                return false;
            }
            Deviation deviation2 = deviation;
            if (deviation2 == null) {
                if (other.deviation != null) {
                    return false;
                }
            } else if (!deviation2.equals(other.deviation)) {
                return false;
            }
            StopInfo stopInfo2 = stopInfo;
            if (stopInfo2 == null) {
                if (other.stopInfo != null) {
                    return false;
                }
            } else if (!stopInfo2.equals(other.stopInfo)) {
                return false;
            }
            return true;
        }

        private SLTrafficRealTime getEnclosingInstance() {
            return SLTrafficRealTime.this;
        }

        @Override
        public String toString() {
            return "StopPointDeviation [stopInfo=" + stopInfo + ", deviation=" + deviation + "]";
        }
    }

    @NonNullByDefault
    public abstract class TrafficType {
        public abstract @Nullable Object getGroupOfLine();

        public abstract @Nullable String getTransportMode();

        public abstract @Nullable String getLineNumber();

        public abstract @Nullable String getJourneyDirection();

        public abstract @Nullable String getDestination();

        public abstract @Nullable String getDisplayTime();

        public abstract @Nullable List<Deviation> getDeviations();

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getEnclosingInstance().hashCode();
            return result;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            TrafficType other = (TrafficType) obj;
            if (!getEnclosingInstance().equals(other.getEnclosingInstance())) {
                return false;
            }
            return true;
        }

        private SLTrafficRealTime getEnclosingInstance() {
            return SLTrafficRealTime.this;
        }
    }

    @NonNullByDefault
    public class Bus extends TrafficType {

        @SerializedName("GroupOfLine")
        private @Nullable Object groupOfLine;
        @SerializedName("TransportMode")
        private @Nullable String transportMode;
        @SerializedName("LineNumber")
        private @Nullable String lineNumber;
        @SerializedName("Destination")
        private @Nullable String destination;
        @SerializedName("JourneyDirection")
        private @Nullable String journeyDirection;
        @SerializedName("StopAreaName")
        private @Nullable String stopAreaName;
        @SerializedName("StopAreaNumber")
        private int stopAreaNumber;
        @SerializedName("StopPointNumber")
        private int stopPointNumber;
        @SerializedName("StopPointDesignation")
        private @Nullable String stopPointDesignation;
        @SerializedName("TimeTabledDateTime")
        private @Nullable String timeTabledDateTime;
        @SerializedName("ExpectedDateTime")
        private @Nullable String expectedDateTime;
        @SerializedName("DisplayTime")
        private @Nullable String displayTime;
        @SerializedName("JourneyNumber")
        private int journeyNumber;
        @SerializedName("Deviations")
        private @Nullable List<Deviation> deviations;

        @Override
        public @Nullable Object getGroupOfLine() {
            return groupOfLine;
        }

        public void setGroupOfLine(Object groupOfLine) {
            this.groupOfLine = groupOfLine;
        }

        @Override
        public @Nullable String getTransportMode() {
            return transportMode;
        }

        public void setTransportMode(String transportMode) {
            this.transportMode = transportMode;
        }

        @Override
        public @Nullable String getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(String lineNumber) {
            this.lineNumber = lineNumber;
        }

        @Override
        public @Nullable String getDestination() {
            return destination;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }

        @Override
        public @Nullable String getJourneyDirection() {
            return journeyDirection;
        }

        public void setJourneyDirection(String journeyDirection) {
            this.journeyDirection = journeyDirection;
        }

        public @Nullable String getStopAreaName() {
            return stopAreaName;
        }

        public void setStopAreaName(String stopAreaName) {
            this.stopAreaName = stopAreaName;
        }

        public int getStopAreaNumber() {
            return stopAreaNumber;
        }

        public void setStopAreaNumber(int stopAreaNumber) {
            this.stopAreaNumber = stopAreaNumber;
        }

        public int getStopPointNumber() {
            return stopPointNumber;
        }

        public void setStopPointNumber(int stopPointNumber) {
            this.stopPointNumber = stopPointNumber;
        }

        public @Nullable String getStopPointDesignation() {
            return stopPointDesignation;
        }

        public void setStopPointDesignation(String stopPointDesignation) {
            this.stopPointDesignation = stopPointDesignation;
        }

        public @Nullable String getTimeTabledDateTime() {
            return timeTabledDateTime;
        }

        public void setTimeTabledDateTime(String timeTabledDateTime) {
            this.timeTabledDateTime = timeTabledDateTime;
        }

        public @Nullable String getExpectedDateTime() {
            return expectedDateTime;
        }

        public void setExpectedDateTime(String expectedDateTime) {
            this.expectedDateTime = expectedDateTime;
        }

        @Override
        public @Nullable String getDisplayTime() {
            return displayTime;
        }

        public void setDisplayTime(String displayTime) {
            this.displayTime = displayTime;
        }

        public int getJourneyNumber() {
            return journeyNumber;
        }

        public void setJourneyNumber(int journeyNumber) {
            this.journeyNumber = journeyNumber;
        }

        @Override
        public @Nullable List<Deviation> getDeviations() {
            return deviations;
        }

        public void setDeviations(List<Deviation> deviations) {
            this.deviations = deviations;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getEnclosingInstance().hashCode();
            String destination2 = destination;
            result = prime * result + ((destination2 == null) ? 0 : destination2.hashCode());
            List<Deviation> deviations2 = deviations;
            result = prime * result + ((deviations2 == null) ? 0 : deviations2.hashCode());
            String displayTime2 = displayTime;
            result = prime * result + ((displayTime2 == null) ? 0 : displayTime2.hashCode());
            String expectedDateTime2 = expectedDateTime;
            result = prime * result + ((expectedDateTime2 == null) ? 0 : expectedDateTime2.hashCode());
            Object groupOfLine2 = groupOfLine;
            result = prime * result + ((groupOfLine2 == null) ? 0 : groupOfLine2.hashCode());
            String journeyDirection2 = journeyDirection;
            result = prime * result + ((journeyDirection2 == null) ? 0 : journeyDirection2.hashCode());
            result = prime * result + journeyNumber;
            String lineNumber2 = lineNumber;
            result = prime * result + ((lineNumber2 == null) ? 0 : lineNumber2.hashCode());
            String stopAreaName2 = stopAreaName;
            result = prime * result + ((stopAreaName2 == null) ? 0 : stopAreaName2.hashCode());
            result = prime * result + stopAreaNumber;
            String stopPointDesignation2 = stopPointDesignation;
            result = prime * result + ((stopPointDesignation2 == null) ? 0 : stopPointDesignation2.hashCode());
            result = prime * result + stopPointNumber;
            String timeTabledDateTime2 = timeTabledDateTime;
            result = prime * result + ((timeTabledDateTime2 == null) ? 0 : timeTabledDateTime2.hashCode());
            String transportMode2 = transportMode;
            result = prime * result + ((transportMode2 == null) ? 0 : transportMode2.hashCode());
            return result;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Bus other = (Bus) obj;
            if (!getEnclosingInstance().equals(other.getEnclosingInstance())) {
                return false;
            }
            String destination2 = destination;
            if (destination2 == null) {
                if (other.destination != null) {
                    return false;
                }
            } else if (!destination2.equals(other.destination)) {
                return false;
            }
            List<Deviation> deviations2 = deviations;
            if (deviations2 == null) {
                if (other.deviations != null) {
                    return false;
                }
            } else if (!deviations2.equals(other.deviations)) {
                return false;
            }
            String displayTime2 = displayTime;
            if (displayTime2 == null) {
                if (other.displayTime != null) {
                    return false;
                }
            } else if (!displayTime2.equals(other.displayTime)) {
                return false;
            }
            String expectedDateTime2 = expectedDateTime;
            if (expectedDateTime2 == null) {
                if (other.expectedDateTime != null) {
                    return false;
                }
            } else if (!expectedDateTime2.equals(other.expectedDateTime)) {
                return false;
            }
            Object groupOfLine2 = groupOfLine;
            if (groupOfLine2 == null) {
                if (other.groupOfLine != null) {
                    return false;
                }
            } else if (!groupOfLine2.equals(other.groupOfLine)) {
                return false;
            }
            String journeyDirection2 = journeyDirection;
            if (journeyDirection2 == null) {
                if (other.journeyDirection != null) {
                    return false;
                }
            } else if (!journeyDirection2.equals(other.journeyDirection)) {
                return false;
            }
            if (journeyNumber != other.journeyNumber) {
                return false;
            }
            String lineNumber2 = lineNumber;
            if (lineNumber2 == null) {
                if (other.lineNumber != null) {
                    return false;
                }
            } else if (!lineNumber2.equals(other.lineNumber)) {
                return false;
            }
            String stopAreaName2 = stopAreaName;
            if (stopAreaName2 == null) {
                if (other.stopAreaName != null) {
                    return false;
                }
            } else if (!stopAreaName2.equals(other.stopAreaName)) {
                return false;
            }
            if (stopAreaNumber != other.stopAreaNumber) {
                return false;
            }
            String stopPointDesignation2 = stopPointDesignation;
            if (stopPointDesignation2 == null) {
                if (other.stopPointDesignation != null) {
                    return false;
                }
            } else if (!stopPointDesignation2.equals(other.stopPointDesignation)) {
                return false;
            }
            if (stopPointNumber != other.stopPointNumber) {
                return false;
            }
            String timeTabledDateTime2 = timeTabledDateTime;
            if (timeTabledDateTime2 == null) {
                if (other.timeTabledDateTime != null) {
                    return false;
                }
            } else if (!timeTabledDateTime2.equals(other.timeTabledDateTime)) {
                return false;
            }
            String transportMode2 = transportMode;
            if (transportMode2 == null) {
                if (other.transportMode != null) {
                    return false;
                }
            } else if (!transportMode2.equals(other.transportMode)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "Bus [groupOfLine=" + groupOfLine + ", transportMode=" + transportMode + ", lineNumber=" + lineNumber
                    + ", destination=" + destination + ", journeyDirection=" + journeyDirection + ", stopAreaName="
                    + stopAreaName + ", stopAreaNumber=" + stopAreaNumber + ", stopPointNumber=" + stopPointNumber
                    + ", stopPointDesignation=" + stopPointDesignation + ", timeTabledDateTime=" + timeTabledDateTime
                    + ", expectedDateTime=" + expectedDateTime + ", displayTime=" + displayTime + ", journeyNumber="
                    + journeyNumber + ", deviations=" + deviations + "]";
        }

        private SLTrafficRealTime getEnclosingInstance() {
            return SLTrafficRealTime.this;
        }
    }

    @NonNullByDefault
    public class Train extends TrafficType {

        @SerializedName("SecondaryDestinationName")
        private @Nullable Object secondaryDestinationName;
        @SerializedName("GroupOfLine")
        private @Nullable String groupOfLine;
        @SerializedName("TransportMode")
        private @Nullable String transportMode;
        @SerializedName("LineNumber")
        private @Nullable String lineNumber;
        @SerializedName("Destination")
        private @Nullable String destination;
        @SerializedName("JourneyDirection")
        private @Nullable String journeyDirection;
        @SerializedName("StopAreaName")
        private @Nullable String stopAreaName;
        @SerializedName("StopAreaNumber")
        private int stopAreaNumber;
        @SerializedName("StopPointNumber")
        private int stopPointNumber;
        @SerializedName("StopPointDesignation")
        private @Nullable String stopPointDesignation;
        @SerializedName("TimeTabledDateTime")
        private @Nullable String timeTabledDateTime;
        @SerializedName("ExpectedDateTime")
        private @Nullable String expectedDateTime;
        @SerializedName("DisplayTime")
        private @Nullable String displayTime;
        @SerializedName("JourneyNumber")
        private int journeyNumber;
        @SerializedName("Deviations")
        private @Nullable List<Deviation> deviations;

        public @Nullable Object getSecondaryDestinationName() {
            return secondaryDestinationName;
        }

        public void setSecondaryDestinationName(Object secondaryDestinationName) {
            this.secondaryDestinationName = secondaryDestinationName;
        }

        @Override
        public @Nullable String getGroupOfLine() {
            return groupOfLine;
        }

        public void setGroupOfLine(String groupOfLine) {
            this.groupOfLine = groupOfLine;
        }

        @Override
        public @Nullable String getTransportMode() {
            return transportMode;
        }

        public void setTransportMode(String transportMode) {
            this.transportMode = transportMode;
        }

        @Override
        public @Nullable String getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(String lineNumber) {
            this.lineNumber = lineNumber;
        }

        @Override
        public @Nullable String getDestination() {
            return destination;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }

        @Override
        public @Nullable String getJourneyDirection() {
            return journeyDirection;
        }

        public void setJourneyDirection(String journeyDirection) {
            this.journeyDirection = journeyDirection;
        }

        public @Nullable String getStopAreaName() {
            return stopAreaName;
        }

        public void setStopAreaName(String stopAreaName) {
            this.stopAreaName = stopAreaName;
        }

        public int getStopAreaNumber() {
            return stopAreaNumber;
        }

        public void setStopAreaNumber(int stopAreaNumber) {
            this.stopAreaNumber = stopAreaNumber;
        }

        public int getStopPointNumber() {
            return stopPointNumber;
        }

        public void setStopPointNumber(int stopPointNumber) {
            this.stopPointNumber = stopPointNumber;
        }

        public @Nullable String getStopPointDesignation() {
            return stopPointDesignation;
        }

        public void setStopPointDesignation(String stopPointDesignation) {
            this.stopPointDesignation = stopPointDesignation;
        }

        public @Nullable String getTimeTabledDateTime() {
            return timeTabledDateTime;
        }

        public void setTimeTabledDateTime(String timeTabledDateTime) {
            this.timeTabledDateTime = timeTabledDateTime;
        }

        public @Nullable String getExpectedDateTime() {
            return expectedDateTime;
        }

        public void setExpectedDateTime(String expectedDateTime) {
            this.expectedDateTime = expectedDateTime;
        }

        @Override
        public @Nullable String getDisplayTime() {
            return displayTime;
        }

        public void setDisplayTime(String displayTime) {
            this.displayTime = displayTime;
        }

        public int getJourneyNumber() {
            return journeyNumber;
        }

        public void setJourneyNumber(int journeyNumber) {
            this.journeyNumber = journeyNumber;
        }

        @Override
        public @Nullable List<Deviation> getDeviations() {
            return deviations;
        }

        public void setDeviations(List<Deviation> deviations) {
            this.deviations = deviations;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getEnclosingInstance().hashCode();
            String destination2 = destination;
            result = prime * result + ((destination2 == null) ? 0 : destination2.hashCode());
            List<Deviation> deviations2 = deviations;
            result = prime * result + ((deviations2 == null) ? 0 : deviations2.hashCode());
            String displayTime2 = displayTime;
            result = prime * result + ((displayTime2 == null) ? 0 : displayTime2.hashCode());
            String expectedDateTime2 = expectedDateTime;
            result = prime * result + ((expectedDateTime2 == null) ? 0 : expectedDateTime2.hashCode());
            String groupOfLine2 = groupOfLine;
            result = prime * result + ((groupOfLine2 == null) ? 0 : groupOfLine2.hashCode());
            String journeyDirection2 = journeyDirection;
            result = prime * result + ((journeyDirection2 == null) ? 0 : journeyDirection2.hashCode());
            result = prime * result + journeyNumber;
            String lineNumber2 = lineNumber;
            result = prime * result + ((lineNumber2 == null) ? 0 : lineNumber2.hashCode());
            Object secondaryDestinationName2 = secondaryDestinationName;
            result = prime * result + ((secondaryDestinationName2 == null) ? 0 : secondaryDestinationName2.hashCode());
            String stopAreaName2 = stopAreaName;
            result = prime * result + ((stopAreaName2 == null) ? 0 : stopAreaName2.hashCode());
            result = prime * result + stopAreaNumber;
            String stopPointDesignation2 = stopPointDesignation;
            result = prime * result + ((stopPointDesignation2 == null) ? 0 : stopPointDesignation2.hashCode());
            result = prime * result + stopPointNumber;
            String timeTabledDateTime2 = timeTabledDateTime;
            result = prime * result + ((timeTabledDateTime2 == null) ? 0 : timeTabledDateTime2.hashCode());
            String transportMode2 = transportMode;
            result = prime * result + ((transportMode2 == null) ? 0 : transportMode2.hashCode());
            return result;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Train other = (Train) obj;
            if (!getEnclosingInstance().equals(other.getEnclosingInstance())) {
                return false;
            }
            String destination2 = destination;
            if (destination2 == null) {
                if (other.destination != null) {
                    return false;
                }
            } else if (!destination2.equals(other.destination)) {
                return false;
            }
            List<Deviation> deviations2 = deviations;
            if (deviations2 == null) {
                if (other.deviations != null) {
                    return false;
                }
            } else if (!deviations2.equals(other.deviations)) {
                return false;
            }
            String displayTime2 = displayTime;
            if (displayTime2 == null) {
                if (other.displayTime != null) {
                    return false;
                }
            } else if (!displayTime2.equals(other.displayTime)) {
                return false;
            }
            String expectedDateTime2 = expectedDateTime;
            if (expectedDateTime2 == null) {
                if (other.expectedDateTime != null) {
                    return false;
                }
            } else if (!expectedDateTime2.equals(other.expectedDateTime)) {
                return false;
            }
            String groupOfLine2 = groupOfLine;
            if (groupOfLine2 == null) {
                if (other.groupOfLine != null) {
                    return false;
                }
            } else if (!groupOfLine2.equals(other.groupOfLine)) {
                return false;
            }
            String journeyDirection2 = journeyDirection;
            if (journeyDirection2 == null) {
                if (other.journeyDirection != null) {
                    return false;
                }
            } else if (!journeyDirection2.equals(other.journeyDirection)) {
                return false;
            }
            if (journeyNumber != other.journeyNumber) {
                return false;
            }
            String lineNumber2 = lineNumber;
            if (lineNumber2 == null) {
                if (other.lineNumber != null) {
                    return false;
                }
            } else if (!lineNumber2.equals(other.lineNumber)) {
                return false;
            }
            Object secondaryDestinationName2 = secondaryDestinationName;
            if (secondaryDestinationName2 == null) {
                if (other.secondaryDestinationName != null) {
                    return false;
                }
            } else if (!secondaryDestinationName2.equals(other.secondaryDestinationName)) {
                return false;
            }
            String stopAreaName2 = stopAreaName;
            if (stopAreaName2 == null) {
                if (other.stopAreaName != null) {
                    return false;
                }
            } else if (!stopAreaName2.equals(other.stopAreaName)) {
                return false;
            }
            if (stopAreaNumber != other.stopAreaNumber) {
                return false;
            }
            String stopPointDesignation2 = stopPointDesignation;
            if (stopPointDesignation2 == null) {
                if (other.stopPointDesignation != null) {
                    return false;
                }
            } else if (!stopPointDesignation2.equals(other.stopPointDesignation)) {
                return false;
            }
            if (stopPointNumber != other.stopPointNumber) {
                return false;
            }
            String timeTabledDateTime2 = timeTabledDateTime;
            if (timeTabledDateTime2 == null) {
                if (other.timeTabledDateTime != null) {
                    return false;
                }
            } else if (!timeTabledDateTime2.equals(other.timeTabledDateTime)) {
                return false;
            }
            String transportMode2 = transportMode;
            if (transportMode2 == null) {
                if (other.transportMode != null) {
                    return false;
                }
            } else if (!transportMode2.equals(other.transportMode)) {
                return false;
            }
            return true;
        }

        private SLTrafficRealTime getEnclosingInstance() {
            return SLTrafficRealTime.this;
        }

        @Override
        public String toString() {
            return "Train [secondaryDestinationName=" + secondaryDestinationName + ", groupOfLine=" + groupOfLine
                    + ", transportMode=" + transportMode + ", lineNumber=" + lineNumber + ", destination=" + destination
                    + ", journeyDirection=" + journeyDirection + ", stopAreaName=" + stopAreaName + ", stopAreaNumber="
                    + stopAreaNumber + ", stopPointNumber=" + stopPointNumber + ", stopPointDesignation="
                    + stopPointDesignation + ", timeTabledDateTime=" + timeTabledDateTime + ", expectedDateTime="
                    + expectedDateTime + ", displayTime=" + displayTime + ", journeyNumber=" + journeyNumber
                    + ", deviations=" + deviations + "]";
        }
    }

    @NonNullByDefault
    public class Tram extends TrafficType {

        @SerializedName("TransportMode")
        private @Nullable String transportMode;
        @SerializedName("LineNumber")
        private @Nullable String lineNumber;
        @SerializedName("Destination")
        private @Nullable String destination;
        @SerializedName("JourneyDirection")
        private @Nullable String journeyDirection;
        @SerializedName("GroupOfLine")
        private @Nullable String groupOfLine;
        @SerializedName("StopAreaName")
        private @Nullable String stopAreaName;
        @SerializedName("StopAreaNumber")
        private int stopAreaNumber;
        @SerializedName("StopPointNumber")
        private int stopPointNumber;
        @SerializedName("StopPointDesignation")
        private @Nullable String stopPointDesignation;
        @SerializedName("TimeTabledDateTime")
        private @Nullable String timeTabledDateTime;
        @SerializedName("ExpectedDateTime")
        private @Nullable String expectedDateTime;
        @SerializedName("DisplayTime")
        private @Nullable String displayTime;
        @SerializedName("JourneyNumber")
        private int journeyNumber;
        @SerializedName("Deviations")
        private @Nullable List<Deviation> deviations;

        @Override
        public @Nullable String getTransportMode() {
            return transportMode;
        }

        public void setTransportMode(String transportMode) {
            this.transportMode = transportMode;
        }

        @Override
        public @Nullable String getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(String lineNumber) {
            this.lineNumber = lineNumber;
        }

        @Override
        public @Nullable String getDestination() {
            return destination;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }

        @Override
        public @Nullable String getJourneyDirection() {
            return journeyDirection;
        }

        public void setJourneyDirection(String journeyDirection) {
            this.journeyDirection = journeyDirection;
        }

        @Override
        public @Nullable String getGroupOfLine() {
            return groupOfLine;
        }

        public void setGroupOfLine(String groupOfLine) {
            this.groupOfLine = groupOfLine;
        }

        public @Nullable String getStopAreaName() {
            return stopAreaName;
        }

        public void setStopAreaName(String stopAreaName) {
            this.stopAreaName = stopAreaName;
        }

        public int getStopAreaNumber() {
            return stopAreaNumber;
        }

        public void setStopAreaNumber(int stopAreaNumber) {
            this.stopAreaNumber = stopAreaNumber;
        }

        public int getStopPointNumber() {
            return stopPointNumber;
        }

        public void setStopPointNumber(int stopPointNumber) {
            this.stopPointNumber = stopPointNumber;
        }

        public @Nullable String getStopPointDesignation() {
            return stopPointDesignation;
        }

        public void setStopPointDesignation(String stopPointDesignation) {
            this.stopPointDesignation = stopPointDesignation;
        }

        public @Nullable String getTimeTabledDateTime() {
            return timeTabledDateTime;
        }

        public void setTimeTabledDateTime(String timeTabledDateTime) {
            this.timeTabledDateTime = timeTabledDateTime;
        }

        public @Nullable String getExpectedDateTime() {
            return expectedDateTime;
        }

        public void setExpectedDateTime(String expectedDateTime) {
            this.expectedDateTime = expectedDateTime;
        }

        @Override
        public @Nullable String getDisplayTime() {
            return displayTime;
        }

        public void setDisplayTime(String displayTime) {
            this.displayTime = displayTime;
        }

        public int getJourneyNumber() {
            return journeyNumber;
        }

        public void setJourneyNumber(int journeyNumber) {
            this.journeyNumber = journeyNumber;
        }

        @Override
        public @Nullable List<Deviation> getDeviations() {
            return deviations;
        }

        public void setDeviations(List<Deviation> deviations) {
            this.deviations = deviations;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getEnclosingInstance().hashCode();
            String destination2 = destination;
            result = prime * result + ((destination2 == null) ? 0 : destination2.hashCode());
            List<Deviation> deviations2 = deviations;
            result = prime * result + ((deviations2 == null) ? 0 : deviations2.hashCode());
            String displayTime2 = displayTime;
            result = prime * result + ((displayTime2 == null) ? 0 : displayTime2.hashCode());
            String expectedDateTime2 = expectedDateTime;
            result = prime * result + ((expectedDateTime2 == null) ? 0 : expectedDateTime2.hashCode());
            String groupOfLine2 = groupOfLine;
            result = prime * result + ((groupOfLine2 == null) ? 0 : groupOfLine2.hashCode());
            String journeyDirection2 = journeyDirection;
            result = prime * result + ((journeyDirection2 == null) ? 0 : journeyDirection2.hashCode());
            result = prime * result + journeyNumber;
            String lineNumber2 = lineNumber;
            result = prime * result + ((lineNumber2 == null) ? 0 : lineNumber2.hashCode());
            String stopAreaName2 = stopAreaName;
            result = prime * result + ((stopAreaName2 == null) ? 0 : stopAreaName2.hashCode());
            result = prime * result + stopAreaNumber;
            String stopPointDesignation2 = stopPointDesignation;
            result = prime * result + ((stopPointDesignation2 == null) ? 0 : stopPointDesignation2.hashCode());
            result = prime * result + stopPointNumber;
            String timeTabledDateTime2 = timeTabledDateTime;
            result = prime * result + ((timeTabledDateTime2 == null) ? 0 : timeTabledDateTime2.hashCode());
            String transportMode2 = transportMode;
            result = prime * result + ((transportMode2 == null) ? 0 : transportMode2.hashCode());
            return result;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Tram other = (Tram) obj;
            if (!getEnclosingInstance().equals(other.getEnclosingInstance())) {
                return false;
            }
            String destination2 = destination;
            if (destination2 == null) {
                if (other.destination != null) {
                    return false;
                }
            } else if (!destination2.equals(other.destination)) {
                return false;
            }
            List<Deviation> deviations2 = deviations;
            if (deviations2 == null) {
                if (other.deviations != null) {
                    return false;
                }
            } else if (!deviations2.equals(other.deviations)) {
                return false;
            }
            String displayTime2 = displayTime;
            if (displayTime2 == null) {
                if (other.displayTime != null) {
                    return false;
                }
            } else if (!displayTime2.equals(other.displayTime)) {
                return false;
            }
            String expectedDateTime2 = expectedDateTime;
            if (expectedDateTime2 == null) {
                if (other.expectedDateTime != null) {
                    return false;
                }
            } else if (!expectedDateTime2.equals(other.expectedDateTime)) {
                return false;
            }
            String groupOfLine2 = groupOfLine;
            if (groupOfLine2 == null) {
                if (other.groupOfLine != null) {
                    return false;
                }
            } else if (!groupOfLine2.equals(other.groupOfLine)) {
                return false;
            }
            String journeyDirection2 = journeyDirection;
            if (journeyDirection2 == null) {
                if (other.journeyDirection != null) {
                    return false;
                }
            } else if (!journeyDirection2.equals(other.journeyDirection)) {
                return false;
            }
            if (journeyNumber != other.journeyNumber) {
                return false;
            }
            String lineNumber2 = lineNumber;
            if (lineNumber2 == null) {
                if (other.lineNumber != null) {
                    return false;
                }
            } else if (!lineNumber2.equals(other.lineNumber)) {
                return false;
            }
            String stopAreaName2 = stopAreaName;
            if (stopAreaName2 == null) {
                if (other.stopAreaName != null) {
                    return false;
                }
            } else if (!stopAreaName2.equals(other.stopAreaName)) {
                return false;
            }
            if (stopAreaNumber != other.stopAreaNumber) {
                return false;
            }
            String stopPointDesignation2 = stopPointDesignation;
            if (stopPointDesignation2 == null) {
                if (other.stopPointDesignation != null) {
                    return false;
                }
            } else if (!stopPointDesignation2.equals(other.stopPointDesignation)) {
                return false;
            }
            if (stopPointNumber != other.stopPointNumber) {
                return false;
            }
            String timeTabledDateTime2 = timeTabledDateTime;
            if (timeTabledDateTime2 == null) {
                if (other.timeTabledDateTime != null) {
                    return false;
                }
            } else if (!timeTabledDateTime2.equals(other.timeTabledDateTime)) {
                return false;
            }
            String transportMode2 = transportMode;
            if (transportMode2 == null) {
                if (other.transportMode != null) {
                    return false;
                }
            } else if (!transportMode2.equals(other.transportMode)) {
                return false;
            }
            return true;
        }

        private SLTrafficRealTime getEnclosingInstance() {
            return SLTrafficRealTime.this;
        }

        @Override
        public String toString() {
            return "Tram [transportMode=" + transportMode + ", lineNumber=" + lineNumber + ", destination="
                    + destination + ", journeyDirection=" + journeyDirection + ", groupOfLine=" + groupOfLine
                    + ", stopAreaName=" + stopAreaName + ", stopAreaNumber=" + stopAreaNumber + ", stopPointNumber="
                    + stopPointNumber + ", stopPointDesignation=" + stopPointDesignation + ", timeTabledDateTime="
                    + timeTabledDateTime + ", expectedDateTime=" + expectedDateTime + ", displayTime=" + displayTime
                    + ", journeyNumber=" + journeyNumber + ", deviations=" + deviations + "]";
        }
    }

    @NonNullByDefault
    public class Metro extends TrafficType {

        @SerializedName("GroupOfLine")
        private @Nullable String groupOfLine;
        @SerializedName("DisplayTime")
        private @Nullable String displayTime;
        @SerializedName("TransportMode")
        private @Nullable String transportMode;
        @SerializedName("LineNumber")
        private @Nullable String lineNumber;
        @SerializedName("Destination")
        private @Nullable String destination;
        @SerializedName("JourneyDirection")
        private @Nullable String journeyDirection;
        @SerializedName("StopAreaName")
        private @Nullable String stopAreaName;
        @SerializedName("StopAreaNumber")
        private int stopAreaNumber;
        @SerializedName("StopPointNumber")
        private int stopPointNumber;
        @SerializedName("StopPointDesignation")
        private @Nullable String stopPointDesignation;
        @SerializedName("TimeTabledDateTime")
        private @Nullable String timeTabledDateTime;
        @SerializedName("ExpectedDateTime")
        private @Nullable String expectedDateTime;
        @SerializedName("JourneyNumber")
        private int journeyNumber;
        @SerializedName("Deviations")
        private @Nullable List<Deviation> deviations;

        @Override
        public @Nullable String getGroupOfLine() {
            return groupOfLine;
        }

        public void setGroupOfLine(String groupOfLine) {
            this.groupOfLine = groupOfLine;
        }

        @Override
        public @Nullable String getDisplayTime() {
            return displayTime;
        }

        public void setDisplayTime(String displayTime) {
            this.displayTime = displayTime;
        }

        @Override
        public @Nullable String getTransportMode() {
            return transportMode;
        }

        public void setTransportMode(String transportMode) {
            this.transportMode = transportMode;
        }

        @Override
        public @Nullable String getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(String lineNumber) {
            this.lineNumber = lineNumber;
        }

        @Override
        public @Nullable String getDestination() {
            return destination;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }

        @Override
        public @Nullable String getJourneyDirection() {
            return journeyDirection;
        }

        public void setJourneyDirection(String journeyDirection) {
            this.journeyDirection = journeyDirection;
        }

        public @Nullable String getStopAreaName() {
            return stopAreaName;
        }

        public void setStopAreaName(String stopAreaName) {
            this.stopAreaName = stopAreaName;
        }

        public int getStopAreaNumber() {
            return stopAreaNumber;
        }

        public void setStopAreaNumber(int stopAreaNumber) {
            this.stopAreaNumber = stopAreaNumber;
        }

        public int getStopPointNumber() {
            return stopPointNumber;
        }

        public void setStopPointNumber(int stopPointNumber) {
            this.stopPointNumber = stopPointNumber;
        }

        public @Nullable String getStopPointDesignation() {
            return stopPointDesignation;
        }

        public void setStopPointDesignation(String stopPointDesignation) {
            this.stopPointDesignation = stopPointDesignation;
        }

        public @Nullable String getTimeTabledDateTime() {
            return timeTabledDateTime;
        }

        public void setTimeTabledDateTime(String timeTabledDateTime) {
            this.timeTabledDateTime = timeTabledDateTime;
        }

        public @Nullable String getExpectedDateTime() {
            return expectedDateTime;
        }

        public void setExpectedDateTime(String expectedDateTime) {
            this.expectedDateTime = expectedDateTime;
        }

        public int getJourneyNumber() {
            return journeyNumber;
        }

        public void setJourneyNumber(int journeyNumber) {
            this.journeyNumber = journeyNumber;
        }

        @Override
        public @Nullable List<Deviation> getDeviations() {
            return deviations;
        }

        public void setDeviations(List<Deviation> deviations) {
            this.deviations = deviations;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getEnclosingInstance().hashCode();
            String destination2 = destination;
            result = prime * result + ((destination2 == null) ? 0 : destination2.hashCode());
            List<Deviation> deviations2 = deviations;
            result = prime * result + ((deviations2 == null) ? 0 : deviations2.hashCode());
            String displayTime2 = displayTime;
            result = prime * result + ((displayTime2 == null) ? 0 : displayTime2.hashCode());
            String expectedDateTime2 = expectedDateTime;
            result = prime * result + ((expectedDateTime2 == null) ? 0 : expectedDateTime2.hashCode());
            String groupOfLine2 = groupOfLine;
            result = prime * result + ((groupOfLine2 == null) ? 0 : groupOfLine2.hashCode());
            String journeyDirection2 = journeyDirection;
            result = prime * result + ((journeyDirection2 == null) ? 0 : journeyDirection2.hashCode());
            result = prime * result + journeyNumber;
            String lineNumber2 = lineNumber;
            result = prime * result + ((lineNumber2 == null) ? 0 : lineNumber2.hashCode());
            String stopAreaName2 = stopAreaName;
            result = prime * result + ((stopAreaName2 == null) ? 0 : stopAreaName2.hashCode());
            result = prime * result + stopAreaNumber;
            String stopPointDesignation2 = stopPointDesignation;
            result = prime * result + ((stopPointDesignation2 == null) ? 0 : stopPointDesignation2.hashCode());
            result = prime * result + stopPointNumber;
            String timeTabledDateTime2 = timeTabledDateTime;
            result = prime * result + ((timeTabledDateTime2 == null) ? 0 : timeTabledDateTime2.hashCode());
            String transportMode2 = transportMode;
            result = prime * result + ((transportMode2 == null) ? 0 : transportMode2.hashCode());
            return result;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Metro other = (Metro) obj;
            if (!getEnclosingInstance().equals(other.getEnclosingInstance())) {
                return false;
            }
            String destination2 = destination;
            if (destination2 == null) {
                if (other.destination != null) {
                    return false;
                }
            } else if (!destination2.equals(other.destination)) {
                return false;
            }
            List<Deviation> deviations2 = deviations;
            if (deviations2 == null) {
                if (other.deviations != null) {
                    return false;
                }
            } else if (!deviations2.equals(other.deviations)) {
                return false;
            }
            String displayTime2 = displayTime;
            if (displayTime2 == null) {
                if (other.displayTime != null) {
                    return false;
                }
            } else if (!displayTime2.equals(other.displayTime)) {
                return false;
            }
            String expectedDateTime2 = expectedDateTime;
            if (expectedDateTime2 == null) {
                if (other.expectedDateTime != null) {
                    return false;
                }
            } else if (!expectedDateTime2.equals(other.expectedDateTime)) {
                return false;
            }
            String groupOfLine2 = groupOfLine;
            if (groupOfLine2 == null) {
                if (other.groupOfLine != null) {
                    return false;
                }
            } else if (!groupOfLine2.equals(other.groupOfLine)) {
                return false;
            }
            String journeyDirection2 = journeyDirection;
            if (journeyDirection2 == null) {
                if (other.journeyDirection != null) {
                    return false;
                }
            } else if (!journeyDirection2.equals(other.journeyDirection)) {
                return false;
            }
            if (journeyNumber != other.journeyNumber) {
                return false;
            }
            String lineNumber2 = lineNumber;
            if (lineNumber2 == null) {
                if (other.lineNumber != null) {
                    return false;
                }
            } else if (!lineNumber2.equals(other.lineNumber)) {
                return false;
            }
            String stopAreaName2 = stopAreaName;
            if (stopAreaName2 == null) {
                if (other.stopAreaName != null) {
                    return false;
                }
            } else if (!stopAreaName2.equals(other.stopAreaName)) {
                return false;
            }
            if (stopAreaNumber != other.stopAreaNumber) {
                return false;
            }
            String stopPointDesignation2 = stopPointDesignation;
            if (stopPointDesignation2 == null) {
                if (other.stopPointDesignation != null) {
                    return false;
                }
            } else if (!stopPointDesignation2.equals(other.stopPointDesignation)) {
                return false;
            }
            if (stopPointNumber != other.stopPointNumber) {
                return false;
            }
            String timeTabledDateTime2 = timeTabledDateTime;
            if (timeTabledDateTime2 == null) {
                if (other.timeTabledDateTime != null) {
                    return false;
                }
            } else if (!timeTabledDateTime2.equals(other.timeTabledDateTime)) {
                return false;
            }
            String transportMode2 = transportMode;
            if (transportMode2 == null) {
                if (other.transportMode != null) {
                    return false;
                }
            } else if (!transportMode2.equals(other.transportMode)) {
                return false;
            }
            return true;
        }

        private SLTrafficRealTime getEnclosingInstance() {
            return SLTrafficRealTime.this;
        }

        @Override
        public String toString() {
            return "Metro [groupOfLine=" + groupOfLine + ", displayTime=" + displayTime + ", transportMode="
                    + transportMode + ", lineNumber=" + lineNumber + ", destination=" + destination
                    + ", journeyDirection=" + journeyDirection + ", stopAreaName=" + stopAreaName + ", stopAreaNumber="
                    + stopAreaNumber + ", stopPointNumber=" + stopPointNumber + ", stopPointDesignation="
                    + stopPointDesignation + ", timeTabledDateTime=" + timeTabledDateTime + ", expectedDateTime="
                    + expectedDateTime + ", journeyNumber=" + journeyNumber + ", deviations=" + deviations + "]";
        }
    }

    @NonNullByDefault
    public class Deviation {

        @SerializedName("Text")
        private @Nullable String text;
        @SerializedName("Consequence")
        private @Nullable String consequence;
        @SerializedName("ImportanceLevel")
        private int importanceLevel;

        public @Nullable String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public @Nullable String getConsequence() {
            return consequence;
        }

        public void setConsequence(String consequence) {
            this.consequence = consequence;
        }

        public int getImportanceLevel() {
            return importanceLevel;
        }

        public void setImportanceLevel(int importanceLevel) {
            this.importanceLevel = importanceLevel;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getEnclosingInstance().hashCode();
            String consequence2 = consequence;
            result = prime * result + ((consequence2 == null) ? 0 : consequence2.hashCode());
            result = prime * result + importanceLevel;
            String text2 = text;
            result = prime * result + ((text2 == null) ? 0 : text2.hashCode());
            return result;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Deviation other = (Deviation) obj;
            if (!getEnclosingInstance().equals(other.getEnclosingInstance())) {
                return false;
            }
            String consequence2 = consequence;
            if (consequence2 == null) {
                if (other.consequence != null) {
                    return false;
                }
            } else if (!consequence2.equals(other.consequence)) {
                return false;
            }
            if (importanceLevel != other.importanceLevel) {
                return false;
            }
            String text2 = text;
            if (text2 == null) {
                if (other.text != null) {
                    return false;
                }
            } else if (!text2.equals(other.text)) {
                return false;
            }
            return true;
        }

        private SLTrafficRealTime getEnclosingInstance() {
            return SLTrafficRealTime.this;
        }

        @Override
        public String toString() {
            return "Deviation [text=" + text + ", consequence=" + consequence + ", importanceLevel=" + importanceLevel
                    + "]";
        }
    }

    @NonNullByDefault
    public class StopInfo {

        @SerializedName("StopAreaNumber")
        private int stopAreaNumber;
        @SerializedName("StopAreaName")
        private @Nullable String stopAreaName;
        @SerializedName("TransportMode")
        private @Nullable String transportMode;
        @SerializedName("GroupOfLine")
        private @Nullable String groupOfLine;

        public int getStopAreaNumber() {
            return stopAreaNumber;
        }

        public void setStopAreaNumber(int stopAreaNumber) {
            this.stopAreaNumber = stopAreaNumber;
        }

        public @Nullable String getStopAreaName() {
            return stopAreaName;
        }

        public void setStopAreaName(String stopAreaName) {
            this.stopAreaName = stopAreaName;
        }

        public @Nullable String getTransportMode() {
            return transportMode;
        }

        public void setTransportMode(String transportMode) {
            this.transportMode = transportMode;
        }

        public @Nullable String getGroupOfLine() {
            return groupOfLine;
        }

        public void setGroupOfLine(String groupOfLine) {
            this.groupOfLine = groupOfLine;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getEnclosingInstance().hashCode();
            String groupOfLine2 = groupOfLine;
            result = prime * result + ((groupOfLine2 == null) ? 0 : groupOfLine2.hashCode());
            String stopAreaName2 = stopAreaName;
            result = prime * result + ((stopAreaName2 == null) ? 0 : stopAreaName2.hashCode());
            result = prime * result + stopAreaNumber;
            String transportMode2 = transportMode;
            result = prime * result + ((transportMode2 == null) ? 0 : transportMode2.hashCode());
            return result;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            StopInfo other = (StopInfo) obj;
            if (!getEnclosingInstance().equals(other.getEnclosingInstance())) {
                return false;
            }
            String groupOfLine2 = groupOfLine;
            if (groupOfLine2 == null) {
                if (other.groupOfLine != null) {
                    return false;
                }
            } else if (!groupOfLine2.equals(other.groupOfLine)) {
                return false;
            }
            String stopAreaName2 = stopAreaName;
            if (stopAreaName2 == null) {
                if (other.stopAreaName != null) {
                    return false;
                }
            } else if (!stopAreaName2.equals(other.stopAreaName)) {
                return false;
            }
            if (stopAreaNumber != other.stopAreaNumber) {
                return false;
            }
            String transportMode2 = transportMode;
            if (transportMode2 == null) {
                if (other.transportMode != null) {
                    return false;
                }
            } else if (!transportMode2.equals(other.transportMode)) {
                return false;
            }
            return true;
        }

        private SLTrafficRealTime getEnclosingInstance() {
            return SLTrafficRealTime.this;
        }

        @Override
        public String toString() {
            return "StopInfo [stopAreaNumber=" + stopAreaNumber + ", stopAreaName=" + stopAreaName + ", transportMode="
                    + transportMode + ", groupOfLine=" + groupOfLine + "]";
        }
    }
}
