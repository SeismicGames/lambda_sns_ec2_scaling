package com.gruegames.lambda_sns_ec2_scaling.ec2.salt;

import javax.ws.rs.core.Form;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SaltPayload {
    public static class Job {
        String command;
        String target;

        public Job(String command, String target) {
            this.command = command;
            this.target = target;
        }
    }

    private String function;
    private String client;
    private String target;
    private List<Job> jobs;

    public SaltPayload(String function, String client, String target, Job jobs) {
        this.function = function;
        this.client = client;
        this.target = target;
        this.jobs = Collections.singletonList(jobs);
    }

    public SaltPayload(String function, String client, String target, List<Job> jobs) {
        this.function = function;
        this.client = client;
        this.target = target;
        this.jobs = jobs;
    }

    public SaltPayload(String function, String client, String target, Job ... jobs) {
        this.function = function;
        this.client = client;
        this.target = target;
        this.jobs = Arrays.asList(jobs);
    }


    public Form getFormFromPayload() {
        Form form = new Form();
        form.param("fun", function);
        form.param("client", client);
        form.param("tgt", target);

        for(Job job : jobs) {
            form.param(job.command, job.target);
        }

        return form;
    }
}