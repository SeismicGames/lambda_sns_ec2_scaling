package com.gruegames.lambda_sns_ec2_scaling.alerts.slack;

import com.gruegames.lambda_sns_ec2_scaling.alerts.Alert;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Properties;

public class SlackAlertImpl implements Alert {
    private static Logger logger = LogManager.getLogger(SlackAlertImpl.class);

    // private POJO class
    private class SlackPayload {
        public String token;
        public String channel;
        public String username;
        public String text;

        public SlackPayload(String token, String channel, String username, String text) {
            this.token = token;
            this.channel = channel;
            this.username = username;
            this.text = text;
        }

        public Form getFormFromPayload() {
            Form form = new Form();
            form.param("token", token);
            form.param("channel", channel);
            form.param("username", username);
            form.param("text", text);
            return form;
        }
    }

    // parameters
    private Properties properties = new Properties();
    private Client client = ClientBuilder.newClient();
    private String apiToken;
    private String url;
    private String channel;
    private String userName;

    public SlackAlertImpl() throws IOException {
        try {
            properties.load(SlackAlertImpl.class.getResourceAsStream("/salt.properties"));
        } catch (IOException e) {
            logger.error("Can't load properties file: ", e);
            throw e;
        }

        apiToken = properties.getProperty("slack.api_key");
        url = properties.getProperty("slack.url");
        channel = properties.getProperty("slack.channel");
        userName = properties.getProperty("slack.username");
    }

    @Override
    public void sendAlert(Level level, String instanceId, String error) {
        String message = String.format("[%s] %s: Message: %s", level.toString(), instanceId, error);

        SlackPayload payload = new SlackPayload(apiToken, channel, userName, message);

        Response response = client.target(url)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(payload.getFormFromPayload(), MediaType.APPLICATION_FORM_URLENCODED_TYPE));

        if(response.getStatus() != 200) {
            logger.error(String.format("Could not talk to Slack. HTTP Code: %s", response.getStatus()));
        } else {
            String responseJSON = response.readEntity(String.class);
            logger.info(responseJSON);
        }
    }
}
