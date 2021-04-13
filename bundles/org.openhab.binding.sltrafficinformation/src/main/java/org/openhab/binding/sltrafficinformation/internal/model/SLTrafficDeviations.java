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

import java.math.BigDecimal;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * The {@link SLTrafficDeviations} is responsible for JSON conversion.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class SLTrafficDeviations {

    @SerializedName("StatusCode")
    private int statusCode;
    @SerializedName("Message")
    private @Nullable Object message;
    @SerializedName("ExecutionTime")
    private int executionTime;
    @SerializedName("ResponseData")
    private @Nullable List<ResponseData> responseData;

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

    public @Nullable List<ResponseData> getResponseData() {
        return responseData;
    }

    public void setResponseData(List<ResponseData> responseData) {
        this.responseData = responseData;
    }

    @NonNullByDefault
    public class ResponseData {

        @SerializedName("Created")
        private @Nullable String created;
        @SerializedName("MainNews")
        private boolean mainNews;
        @SerializedName("SortOrder")
        private int sortOrder;
        @SerializedName("Header")
        private @Nullable String header;
        @SerializedName("Details")
        private @Nullable String details;
        @SerializedName("Scope")
        private @Nullable String scope;
        @SerializedName("DevCaseGid")
        private @Nullable BigDecimal devCaseGid;
        @SerializedName("DevMessageVersionNumber")
        private int devMessageVersionNumber;
        @SerializedName("ScopeElements")
        private @Nullable String scopeElements;
        @SerializedName("FromDateTime")
        private @Nullable String fromDateTime;
        @SerializedName("UpToDateTime")
        private @Nullable String upToDateTime;
        @SerializedName("Updated")
        private @Nullable String updated;

        public @Nullable String getCreated() {
            return created;
        }

        public void setCreated(String created) {
            this.created = created;
        }

        public boolean isMainNews() {
            return mainNews;
        }

        public void setMainNews(boolean mainNews) {
            this.mainNews = mainNews;
        }

        public int getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(int sortOrder) {
            this.sortOrder = sortOrder;
        }

        public @Nullable String getHeader() {
            return header;
        }

        public void setHeader(String header) {
            this.header = header;
        }

        public @Nullable String getDetails() {
            return details;
        }

        public void setDetails(String details) {
            this.details = details;
        }

        public @Nullable String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public @Nullable BigDecimal getDevCaseGid() {
            return devCaseGid;
        }

        public void setDevCaseGid(BigDecimal devCaseGid) {
            this.devCaseGid = devCaseGid;
        }

        public int getDevMessageVersionNumber() {
            return devMessageVersionNumber;
        }

        public void setDevMessageVersionNumber(int devMessageVersionNumber) {
            this.devMessageVersionNumber = devMessageVersionNumber;
        }

        public @Nullable String getScopeElements() {
            return scopeElements;
        }

        public void setScopeElements(String scopeElements) {
            this.scopeElements = scopeElements;
        }

        public @Nullable String getFromDateTime() {
            return fromDateTime;
        }

        public void setFromDateTime(String fromDateTime) {
            this.fromDateTime = fromDateTime;
        }

        public @Nullable String getUpToDateTime() {
            return upToDateTime;
        }

        public void setUpToDateTime(String upToDateTime) {
            this.upToDateTime = upToDateTime;
        }

        public @Nullable String getUpdated() {
            return updated;
        }

        public void setUpdated(String updated) {
            this.updated = updated;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getEnclosingInstance().hashCode();
            String created2 = created;
            result = prime * result + ((created2 == null) ? 0 : created2.hashCode());
            String details2 = details;
            result = prime * result + ((details2 == null) ? 0 : details2.hashCode());
            BigDecimal devCaseGid2 = devCaseGid;
            result = prime * result + ((devCaseGid2 == null) ? 0 : devCaseGid2.hashCode());
            result = prime * result + devMessageVersionNumber;
            String fromDateTime2 = fromDateTime;
            result = prime * result + ((fromDateTime2 == null) ? 0 : fromDateTime2.hashCode());
            String header2 = header;
            result = prime * result + ((header2 == null) ? 0 : header2.hashCode());
            result = prime * result + (mainNews ? 1231 : 1237);
            String scope2 = scope;
            result = prime * result + ((scope2 == null) ? 0 : scope2.hashCode());
            String scopeElements2 = scopeElements;
            result = prime * result + ((scopeElements2 == null) ? 0 : scopeElements2.hashCode());
            result = prime * result + sortOrder;
            String upToDateTime2 = upToDateTime;
            result = prime * result + ((upToDateTime2 == null) ? 0 : upToDateTime2.hashCode());
            String updated2 = updated;
            result = prime * result + ((updated2 == null) ? 0 : updated2.hashCode());
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
            String created2 = created;
            if (created2 == null) {
                if (other.created != null) {
                    return false;
                }
            } else if (!created2.equals(other.created)) {
                return false;
            }
            String details2 = details;
            if (details2 == null) {
                if (other.details != null) {
                    return false;
                }
            } else if (!details2.equals(other.details)) {
                return false;
            }
            BigDecimal devCaseGid2 = devCaseGid;
            if (devCaseGid2 == null) {
                if (other.devCaseGid != null) {
                    return false;
                }
            } else if (!devCaseGid2.equals(other.devCaseGid)) {
                return false;
            }
            if (devMessageVersionNumber != other.devMessageVersionNumber) {
                return false;
            }
            String fromDateTime2 = fromDateTime;
            if (fromDateTime2 == null) {
                if (other.fromDateTime != null) {
                    return false;
                }
            } else if (!fromDateTime2.equals(other.fromDateTime)) {
                return false;
            }
            String header2 = header;
            if (header2 == null) {
                if (other.header != null) {
                    return false;
                }
            } else if (!header2.equals(other.header)) {
                return false;
            }
            if (mainNews != other.mainNews) {
                return false;
            }
            String scope2 = scope;
            if (scope2 == null) {
                if (other.scope != null) {
                    return false;
                }
            } else if (!scope2.equals(other.scope)) {
                return false;
            }
            String scopeElements2 = scopeElements;
            if (scopeElements2 == null) {
                if (other.scopeElements != null) {
                    return false;
                }
            } else if (!scopeElements2.equals(other.scopeElements)) {
                return false;
            }
            if (sortOrder != other.sortOrder) {
                return false;
            }
            String upToDateTime2 = upToDateTime;
            if (upToDateTime2 == null) {
                if (other.upToDateTime != null) {
                    return false;
                }
            } else if (!upToDateTime2.equals(other.upToDateTime)) {
                return false;
            }
            String updated2 = updated;
            if (updated2 == null) {
                if (other.updated != null) {
                    return false;
                }
            } else if (!updated2.equals(other.updated)) {
                return false;
            }
            return true;
        }

        private SLTrafficDeviations getEnclosingInstance() {
            return SLTrafficDeviations.this;
        }

        @Override
        public String toString() {
            return "ResponseData [created=" + created + ", mainNews=" + mainNews + ", sortOrder=" + sortOrder
                    + ", header=" + header + ", details=" + details + ", scope=" + scope + ", devCaseGid=" + devCaseGid
                    + ", devMessageVersionNumber=" + devMessageVersionNumber + ", scopeElements=" + scopeElements
                    + ", fromDateTime=" + fromDateTime + ", upToDateTime=" + upToDateTime + ", updated=" + updated
                    + "]";
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + executionTime;
        Object message2 = message;
        result = prime * result + ((message2 == null) ? 0 : message2.hashCode());
        List<ResponseData> responseData2 = responseData;
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
        SLTrafficDeviations other = (SLTrafficDeviations) obj;
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
        List<ResponseData> responseData2 = responseData;
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
        return "SLTrafficDeviations [statusCode=" + statusCode + ", message=" + message + ", executionTime="
                + executionTime + ", responseData=" + responseData + "]";
    }
}
