package com.gruegames.lambda_sns_ec2_scaling.alerts;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.ArrayList;

public class AlertHandler {
    private static final Logger logger = LogManager.getLogger(AlertHandler.class);
    private static ArrayList<Alert> alertList = new ArrayList<>();

    private AlertHandler() {}

    public static void register(Class<? extends Alert> clazz) {
        try {
            alertList.add(clazz.newInstance());
        } catch (IllegalAccessException | InstantiationException e) {
            logger.error(String.format("Could not register alert %s", clazz.getSimpleName()), e);
        }
    }

    public static void alert(Level level, String instanceId, String error) {
        for(Alert ai : alertList) {
            ai.sendAlert(level, instanceId, error);
        }
    }
}
