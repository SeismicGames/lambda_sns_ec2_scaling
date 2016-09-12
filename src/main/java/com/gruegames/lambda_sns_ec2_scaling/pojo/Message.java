package com.gruegames.lambda_sns_ec2_scaling.pojo;

import com.google.gson.annotations.SerializedName;

public class Message {
    public class Details {
        @SerializedName("Subnet ID")
        public String subnetId;

        @SerializedName("Availability Zone")
        public String availabilityZone;
    }

    @SerializedName("Progress")
    public String progress;

    @SerializedName("AccountId")
    public String accountId;

    @SerializedName("Description")
    public String description;

    @SerializedName("RequestId")
    public String requestId;

    @SerializedName("EndTime")
    public String endTime;

    @SerializedName("AutoScalingGroupARN")
    public String autoScalingGroupARN;

    @SerializedName("ActivityId")
    public String activityId;

    @SerializedName("StartTime")
    public String startTime;

    @SerializedName("Service")
    public String service;

    @SerializedName("Time")
    public String time;

    @SerializedName("EC2InstanceId")
    public String EC2InstanceId;

    @SerializedName("StatusCode")
    public String statusCode;

    @SerializedName("StatusMessage")
    public String statusMessage;

    @SerializedName("AutoScalingGroupName")
    public String autoScalingGroupName;

    @SerializedName("Cause")
    public String cause;

    @SerializedName("Event")
    public String event;

    @SerializedName("Details")
    public Details details;
}