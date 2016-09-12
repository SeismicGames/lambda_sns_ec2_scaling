package com.gruegames.lambda_sns_ec2_scaling.ec2.salt;

import com.amazonaws.services.ec2.model.*;
import com.amazonaws.util.StringUtils;
import com.google.gson.Gson;
import com.gruegames.lambda_sns_ec2_scaling.alerts.AlertHandler;
import com.gruegames.lambda_sns_ec2_scaling.ec2.EC2Instance;
import com.gruegames.lambda_sns_ec2_scaling.helper.AWSHelper;
import com.gruegames.lambda_sns_ec2_scaling.pojo.*;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class SaltEC2InstanceImpl implements EC2Instance {
    private static final Logger logger = Logger.getLogger(SaltEC2InstanceImpl.class);

    private Properties properties = new Properties();
    private Client client = ClientBuilder.newClient();
    private String baseUrl;
    private String saltMasterMinion;
    private String ec2Username;
    private String ec2Password;

    public SaltEC2InstanceImpl() throws IOException {
        try {
            properties.load(SaltEC2InstanceImpl.class.getResourceAsStream("/salt.properties"));
        } catch (IOException e) {
            logger.error("Can't load properties file: ", e);
            throw e;
        }

        baseUrl = properties.getProperty("salt.url");
        saltMasterMinion = properties.getProperty("salt.master.minion");
        ec2Username = properties.getProperty("ec2.username");
        ec2Password = properties.getProperty("ec2.password");
    }

    // Initial EC2 instance setup with Salt
    @Override
    public void create(String instanceId, String availabilityZone) {
        EC2Details details = AWSHelper.getInstanceDetails(instanceId, availabilityZone);
        if(details.instanceName == null || details.instanceIp == null) {
            throw new RuntimeException(String.format("Instance id %s has no name!", instanceId));
        }

        // wait until we can access SSH
        SSHClient ssh = AWSHelper.waitForEC2Instance(details.instanceIp, ec2Username, ec2Password);

        // start SSH session
        logger.info("Starting ssh transactions");
        Session.Command command;

        String fileCommand = String.format("if [ -f \"/var/lib/cloud/instances/%s/boot-finished\" ]; then echo \"true\"; else echo \"false\"; fi;", instanceId);
        String fileExists;

        try {
            do {
                try (Session session = ssh.startSession()) {
                    // install salt minion
                    command = session.exec(fileCommand);
                    fileExists = IOUtils.readFully(command.getInputStream()).toString().trim();

                    if (Boolean.parseBoolean(fileExists)) {
                        logger.info("Ok, cloud-init is finished, moving on");
                    } else {
                        logger.info("cloud-init is still working, sleeping");
                        try {
                            Thread.sleep(15000);
                        } catch (InterruptedException e) {
                        }
                    }
                }
            } while (!Boolean.parseBoolean(fileExists));

            ssh.disconnect();
        } catch (IOException e) {
            AlertHandler.alert(Level.ERROR, instanceId, "Could not SSH. Error: "+e.getLocalizedMessage());
        }

        // login to salt
        String token = saltLogin(instanceId);
        SaltPayload payload = new SaltPayload("event.send", "local", saltMasterMinion,
                new SaltPayload.Job("arg", String.format("tag=salt/minion/ec2/%s/auth", instanceId)));
        makeSaltCall(instanceId, token, null, payload);
    }

    // Remove EC2 instance from Salt
    @Override
    public void terminate(String instanceId, String availabilityZone) {
        EC2Details details = AWSHelper.getInstanceDetails(instanceId, availabilityZone);
        Map<String, String > tags = details.tags.stream()
                .collect(Collectors.toMap(Tag::getKey, Tag::getValue));

        String tag = String.format("tag=salt/minion/ec2/%s/terminated", instanceId);
        String data = String.format("data=%s", new Gson().toJson(tags));

        String token = saltLogin(instanceId);

        SaltPayload payload = new SaltPayload("event.send", "local", saltMasterMinion, new SaltPayload.Job("arg", tag),
                new SaltPayload.Job("arg", data));
        makeSaltCall(instanceId, token, null, payload);

        payload = new SaltPayload("key.delete", "wheel", "'*'", new SaltPayload.Job("match", instanceId));
        makeSaltCall(instanceId, token, null, payload);
    }

    @Override
    public boolean processTerminateOnFail() {
        // we need to remove the instance from salt even if the termination fails
        return true;
    }

    // Salt login helper
    private String saltLogin(String instanceId) {
        logger.info("Starting Salt login flow");

        Form form = new Form();
        form.param("username", properties.getProperty("ec2.username"));
        form.param("password", properties.getProperty("ec2.password"));
        form.param("eauth", "pam");

        Response response = client.target(baseUrl)
                .path("login")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE));

        if(response.getStatus() != 200) {
            String message = String.format("Can't authenicate with Salt: code %s", response.getStatus());
            AlertHandler.alert(Level.ERROR, instanceId, message);
            throw new RuntimeException(message);
        }

        String body = response.readEntity(String.class);
        SaltLogin saltLogin = new Gson().fromJson(body, SaltLogin.class);

        if(saltLogin.returns.size() < 1) {
            String message = String.format("No token returned from Salt! Body: %s", body);
            AlertHandler.alert(Level.ERROR, instanceId, message);
            throw new UnsupportedOperationException(message);
        }

        String token = saltLogin.returns.get(0).token;
        logger.info("Logged into salt successfully");

        return token;
    }

    // Salt API helper
    private String makeSaltCall(String instanceId, String token, String path, SaltPayload payload) {
        if(StringUtils.isNullOrEmpty(path)) {
            path = "/";
        }

        Response response = client.target(baseUrl)
                .path(path)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header("X-Auth-Token", token)
                .post(Entity.entity(payload.getFormFromPayload(), MediaType.APPLICATION_FORM_URLENCODED_TYPE));

        if(response.getStatus() != 200) {
            AlertHandler.alert(Level.ERROR, instanceId, String.format("Failed to update Salt! Code: %s", response.getStatus()));
            throw new RuntimeException();
        } else {
            String result = response.readEntity(String.class);
            logger.debug(String.format("Event fired to Salt. Response: %s", result));
            return result;
        }
    }
}
