package com.gruegames.lambda_sns_ec2_scaling.pojo;

import com.amazonaws.services.ec2.model.Tag;

import java.util.List;

public class EC2Details {
    public String instanceIp;
    public String instanceName;
    public List<Tag> tags;

    public EC2Details(String instanceIp, String instanceName, List<Tag> tags) {
        this.instanceIp = instanceIp;
        this.instanceName = instanceName;
        this.tags = tags;
    }
}
