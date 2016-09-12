package com.gruegames.lambda_sns_ec2_scaling;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.util.StringUtils;
import com.google.gson.Gson;
import com.gruegames.lambda_sns_ec2_scaling.alerts.AlertHandler;
import com.gruegames.lambda_sns_ec2_scaling.alerts.slack.SlackAlertImpl;
import com.gruegames.lambda_sns_ec2_scaling.ec2.EC2InstanceHandler;
import com.gruegames.lambda_sns_ec2_scaling.ec2.salt.SaltEC2InstanceImpl;
import com.gruegames.lambda_sns_ec2_scaling.pojo.Message;
import com.gruegames.lambda_sns_ec2_scaling.enums.Type;
import com.gruegames.lambda_sns_ec2_scaling.pojo.Response;

import java.io.InputStream;
import java.net.URL;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;

public class LambdaFunction implements RequestHandler<SNSEvent, Response> {
    private static final Logger logger = Logger.getLogger(LambdaFunction.class);

    @Override
    public Response handleRequest(SNSEvent event, Context context) {
        if(event.getRecords().size() < 1) {
            return new Response("Invalid SNS Message");
        }

        registerAlerts();
        registerEC2Instances();

        SNSEvent.SNSRecord record = event.getRecords().get(0);
        Type type = Type.getFromString(record.getSNS().getType());
        switch (type) {
            case Notification:
                if (record.getSNS().getSignatureVersion().equals("1")) {
                    // Check the signature and throw an exception if the signature verification fails.
                    if (isMessageSignatureValid(record.getSNS())) {
                        logger.info("Signature verification succeeded");
                        Message message = new Gson().fromJson(record.getSNS().getMessage(), Message.class);

                        if(!StringUtils.isNullOrEmpty(message.event) && message.event.equals("autoscaling:TEST_NOTIFICATION")) {
                            logger.info("Ignoring test notification");
                            return new Response("Success");
                        }

                        EC2InstanceHandler.processEvent(message.event, message.EC2InstanceId, message.details.availabilityZone);
                    } else {
                        logger.warn("Signature verification failed");
                        throw new SecurityException("Signature verification failed.");
                    }
                } else {
                    logger.warn("Unexpected signature version. Unable to verify signature.");
                    throw new SecurityException("Unexpected signature version. Unable to verify signature.");
                }
                break;
            case SubscriptionConfirmation:
                logger.info(String.format("Subscribe SNS %s", record.getSNS().getUnsubscribeUrl()));
                break;
            case UnsubscribeConfirmation:
                logger.info(String.format("Unsubscribe SNS %s", record.getSNS().getUnsubscribeUrl()));
                break;
        }

        return new Response("Success");
    }

    private static boolean isMessageSignatureValid(SNSEvent.SNS msg) {
        try {
            URL url = new URL(msg.getSigningCertUrl());
            InputStream inStream = url.openStream();
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(inStream);
            inStream.close();

            Signature sig = Signature.getInstance("SHA1withRSA");
            sig.initVerify(cert.getPublicKey());
            sig.update(buildNotificationStringToSign(msg).getBytes());
            return sig.verify(Base64.decodeBase64(msg.getSignature()));
        } catch (Exception e) {
            throw new SecurityException("Verify method failed.", e);
        }
    }

    //Build the string to sign for Notification messages.
    private static String buildNotificationStringToSign(SNSEvent.SNS msg) {
        String stringToSign;

        //Build the string to sign from the values in the message.
        //Name and values separated by newline characters
        //The name value pairs are sorted by name
        //in byte sort order.
        stringToSign = "Message\n";
        stringToSign += msg.getMessage() + "\n";
        stringToSign += "MessageId\n";
        stringToSign += msg.getMessageId() + "\n";
        if (msg.getSubject() == null) {
            msg.setSubject("");
        }
        stringToSign += "Subject\n";
        stringToSign += msg.getSubject() + "\n";
        stringToSign += "Timestamp\n";
        stringToSign += msg.getTimestamp() + "\n";
        stringToSign += "TopicArn\n";
        stringToSign += msg.getTopicArn() + "\n";
        stringToSign += "Type\n";
        stringToSign += msg.getType() + "\n";
        return stringToSign;
    }

    private void registerAlerts()
    {
        AlertHandler.register(SlackAlertImpl.class);
    }

    private void registerEC2Instances()
    {
        EC2InstanceHandler.register(SaltEC2InstanceImpl.class);
    }
}