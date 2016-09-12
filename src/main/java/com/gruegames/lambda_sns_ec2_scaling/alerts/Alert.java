package com.gruegames.lambda_sns_ec2_scaling.alerts;

import org.apache.log4j.Level;

public interface Alert {
    void sendAlert(Level level, String instanceId, String error);
}
