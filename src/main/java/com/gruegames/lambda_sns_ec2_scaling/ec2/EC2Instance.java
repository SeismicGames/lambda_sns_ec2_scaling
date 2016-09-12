package com.gruegames.lambda_sns_ec2_scaling.ec2;

public interface EC2Instance {
    void create(String instanceId, String availabilityZone);

    void terminate(String instanceId, String availabilityZone);

    boolean processTerminateOnFail();
}
