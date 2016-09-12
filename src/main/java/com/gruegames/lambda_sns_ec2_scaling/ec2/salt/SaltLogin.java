package com.gruegames.lambda_sns_ec2_scaling.ec2.salt;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class SaltLogin {
    public class Return {
        @SerializedName("perms")
        public List<String> permissions;

        @SerializedName("start")
        public float start;

        @SerializedName("expire")
        public float expire;

        @SerializedName("token")
        public String token;

        @SerializedName("user")
        public String user;

        @SerializedName("eauth")
        public String eauth;
    }

    @SerializedName("return")
    public List<Return> returns;
}
