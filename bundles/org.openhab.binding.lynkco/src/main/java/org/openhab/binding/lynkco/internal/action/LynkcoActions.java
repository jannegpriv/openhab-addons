/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
package org.openhab.binding.lynkco.internal.action;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.lynkco.internal.handler.LynkcoVehicleHandler;
import org.openhab.core.automation.annotation.ActionInput;
import org.openhab.core.automation.annotation.RuleAction;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.thing.binding.ThingActionsScope;
import org.openhab.core.thing.binding.ThingHandler;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LynkcoActions} is responsible for handling actions.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = LynkcoActions.class)
@ThingActionsScope(name = "lynkco")
@NonNullByDefault
public class LynkcoActions implements ThingActions {
    private final Logger logger = LoggerFactory.getLogger(LynkcoActions.class);
    private @Nullable LynkcoVehicleHandler handler;

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        if (handler instanceof LynkcoVehicleHandler vehicleHandler) {
            this.handler = vehicleHandler;
        }
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return handler;
    }

    @RuleAction(label = "Start Climate", description = "Start vehicle pre-climatization")
    public void startClimate(
            @ActionInput(name = "climateLevel", label = "Climate Level") @Nullable Integer climateLevel,
            @ActionInput(name = "duration", label = "Duration in minutes") @Nullable Integer duration) {
        LynkcoVehicleHandler handler = this.handler;
        if (handler != null) {
            handler.actionClimate(true, climateLevel != null ? climateLevel : 2, duration != null ? duration : 30);
        } else {
            logger.warn("Lynkco Action service ThingHandler is null!");
        }
    }

    @RuleAction(label = "Stop Climate", description = "Stop vehicle pre-climatization")
    public void stopClimate() {
        LynkcoVehicleHandler handler = this.handler;
        if (handler != null) {
            handler.actionClimate(false, 0, 0);
        } else {
            logger.warn("Lynkco Action service ThingHandler is null!");
        }
    }

    @RuleAction(label = "Start Engine", description = "Start the vehicle engine")
    public void startEngine(@ActionInput(name = "duration", label = "Duration in minutes") @Nullable Integer duration) {
        LynkcoVehicleHandler handler = this.handler;
        if (handler != null) {
            handler.actionEngine(true, duration != null ? duration : 15);
        } else {
            logger.warn("Lynkco Action service ThingHandler is null!");
        }
    }

    @RuleAction(label = "Stop Engine", description = "Stop the vehicle engine")
    public void stopEngine() {
        LynkcoVehicleHandler handler = this.handler;
        if (handler != null) {
            handler.actionEngine(false, 0);
        } else {
            logger.warn("Lynkco Action service ThingHandler is null!");
        }
    }

    @RuleAction(label = "Lock Doors", description = "Lock all vehicle doors")
    public void lockDoors() {
        LynkcoVehicleHandler handler = this.handler;
        if (handler != null) {
            handler.actionDoors(true);
        } else {
            logger.warn("Lynkco Action service ThingHandler is null!");
        }
    }

    @RuleAction(label = "Unlock Doors", description = "Unlock all vehicle doors")
    public void unlockDoors() {
        LynkcoVehicleHandler handler = this.handler;
        if (handler != null) {
            handler.actionDoors(false);
        } else {
            logger.warn("Lynkco Action service ThingHandler is null!");
        }
    }

    @RuleAction(label = "Honk and Flash", description = "Control vehicle horn and lights")
    public void honkBlink(@ActionInput(name = "honk", label = "Honk horn") Boolean honk,
            @ActionInput(name = "blink", label = "Flash lights") Boolean blink) {
        LynkcoVehicleHandler handler = this.handler;
        if (handler != null) {
            handler.actionHonkBlink(honk, blink);
        } else {
            logger.warn("Lynkco Action service ThingHandler is null!");
        }
    }
}
