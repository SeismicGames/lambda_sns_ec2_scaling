package com.gruegames.lambda_sns_ec2_scaling.helper;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.gruegames.lambda_sns_ec2_scaling.pojo.EC2Details;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.UserAuthException;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.Security;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class AWSHelper {
    private static Logger logger = LogManager.getLogger(AWSHelper.class);
    private static BasicAWSCredentials awsCredentials;
    private static Properties properties = new Properties();

    static {
        try {
            properties.load(AWSHelper.class.getResourceAsStream("/ec2.properties"));
        } catch (IOException e) {
            logger.error("Can't load properties file: ", e);
            throw new RuntimeException();
        }

        awsCredentials = new BasicAWSCredentials(properties.getProperty("aws.access.key.id"),
                properties.getProperty("aws.secret.access.key"));
    }

    private AWSHelper() {}

    // Get EC2 instance details from AWS
    public static EC2Details getInstanceDetails(String instanceId, String azone) {
        AmazonEC2Client client = new AmazonEC2Client(awsCredentials);
        client.setRegion(AWSHelper.getRegionFromAZ(client, azone));

        DescribeInstancesRequest request = new DescribeInstancesRequest();
        request.setInstanceIds(Collections.singletonList(instanceId));
        DescribeInstancesResult result = client.describeInstances(request);

        if(result == null) {
            throw new RuntimeException(String.format("Instance id %s was not found!", instanceId));
        }

        Instance instance = result.getReservations().get(0).getInstances().get(0);
        logger.info(String.format("Found EC2 instance: %s", instance.getInstanceId()));

        String ip = instance.getPrivateIpAddress();
        String instanceName = instance.getPrivateDnsName();
        List<Tag> tags  = instance.getTags();

        logger.info(String.format("Found instance ip %s and name %s", ip, instanceName));
        return new EC2Details(ip, instanceName, tags);
    }

    // Test the SSH connection and sleep until it is ready
    public static SSHClient waitForEC2Instance(String instanceIp, String username, String password) {
        // see if machine is up yet
        logger.info(String.format("Trying to reach %s", instanceIp));
        while (!AWSHelper.isReachable(instanceIp)) {
            try {
                logger.info("Sleeping...");
                Thread.sleep(5000);
            } catch (InterruptedException e) { }
        }

        Security.addProvider(new BouncyCastleProvider());
        SSHClient ssh = null;
        while(true) {
            try {
                ssh = new SSHClient();
                ssh.addHostKeyVerifier(new PromiscuousVerifier());
                ssh.setConnectTimeout(5000);

                logger.info(String.format("Trying to SSH into %s", instanceIp));
                ssh.connect(instanceIp);
                ssh.authPassword(username, password);

                try(Session session = ssh.startSession()) {
                    logger.info("SSH Session able to start");
                    return ssh;
                }
            } catch (ConnectionException | UserAuthException e) {
                logger.info(String.format("%s ssh is not ready, sleeping", instanceIp));

                try {
                    ssh.disconnect();
                } catch (IOException ee) {}

                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ee) {}
            } catch (TransportException e) {
                logger.error("SSH Transport Exception", e);
                throw new RuntimeException(e);
            } catch (IOException e) {
                logger.error("SSH IO Exception", e);
                throw new RuntimeException(e);
            }
        }
    }

    // Get AWS EC2 region from availability zone
    public static Region getRegionFromAZ(AmazonEC2Client client, String azone) {
        Region region = null;

        DescribeAvailabilityZonesResult azResult = client.describeAvailabilityZones();
        for(AvailabilityZone zone : azResult.getAvailabilityZones()) {
            if(zone.getZoneName().equals(azone)) {
                region = Region.getRegion(Regions.fromName(zone.getRegionName()));
                break;
            }
        }

        if (region == null) {
            throw new RuntimeException(String.format("AZ %s region was not found!", azone));
        }

        return region;
    }

    // from http://stackoverflow.com/a/34228756
    public static boolean isReachable(String addr) {
        int openPort = 22;
        int timeOutMillis = 2000;

        try {
            try (Socket soc = new Socket()) {
                soc.connect(new InetSocketAddress(addr, openPort), timeOutMillis);
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
}
