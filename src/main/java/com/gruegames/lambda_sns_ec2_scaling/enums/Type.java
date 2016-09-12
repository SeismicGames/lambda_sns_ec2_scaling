package com.gruegames.lambda_sns_ec2_scaling.enums;

import org.apache.log4j.Logger;

public enum Type {
    Notification,
    SubscriptionConfirmation,
    UnsubscribeConfirmation,
    Unknown;

    public static Type getFromString(String type) {
        try {
            return Type.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException e) {
            final Logger logger = Logger.getLogger(Type.class);
            logger.error("Invalid SNS message type: ", e);
            return Type.Unknown;
        }
    }
}
