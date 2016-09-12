package com.gruegames.lambda_sns_ec2_scaling.ec2;

import com.gruegames.lambda_sns_ec2_scaling.alerts.AlertHandler;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.ArrayList;

public class EC2InstanceHandler {
    private static final Logger logger = LogManager.getLogger(EC2InstanceHandler.class);
    private static ArrayList<EC2Instance> instanceList = new ArrayList<>();

    private EC2InstanceHandler() {}

    public static void register(Class<? extends EC2Instance> clazz) {
        try {
            instanceList.add(clazz.newInstance());
        } catch (IllegalAccessException | InstantiationException e) {
            logger.error(String.format("Could not register instance impl %s", clazz.getSimpleName()), e);
        }
    }

    public static void processEvent(String event, String instanceId, String availabilityZone) {
        switch (event) {
            case "autoscaling:EC2_INSTANCE_LAUNCH":
                for(EC2Instance instance : instanceList) {
                    instance.create(instanceId, availabilityZone);
                }
                break;
            case "autoscaling:EC2_INSTANCE_LAUNCH_ERROR":
                AlertHandler.alert(Level.WARN, instanceId, String.format("Instance scaling issue: %s", event));
                break;
            case "autoscaling:EC2_INSTANCE_TERMINATE":
                for(EC2Instance instance : instanceList) {
                    instance.terminate(instanceId, availabilityZone);
                }
                break;
            case "autoscaling:EC2_INSTANCE_TERMINATE_ERROR":
                AlertHandler.alert(Level.WARN, instanceId, String.format("Instance scaling issue: %s", event));
                instanceList.stream().filter(EC2Instance::processTerminateOnFail)
                        .forEach(instance -> instance.create(instanceId, availabilityZone));
                break;
        }
    }
}
