/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.mifos.sms.gateway.infobip;

import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import org.mifos.sms.data.ConfigurationData;
import org.mifos.sms.service.ReadConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import infobip.api.client.GetSentSmsLogs;
import infobip.api.client.SendMultipleTextualSmsAdvanced;
import infobip.api.config.BasicAuthConfiguration;
import infobip.api.model.Destination;
import infobip.api.model.sms.mt.logs.SMSLogsResponse;
import infobip.api.model.sms.mt.send.Message;
import infobip.api.model.sms.mt.send.SMSResponse;
import infobip.api.model.sms.mt.send.SMSResponseDetails;
import infobip.api.model.sms.mt.send.textual.SMSAdvancedTextualRequest;

@Service
public class InfoBipMessageProvider {

    private static final Logger logger = LoggerFactory.getLogger(InfoBipMessageProvider.class);

    private final HashMap<String, SendMultipleTextualSmsAdvanced> sendSMSCliets = new HashMap<>();
    private final HashMap<String, GetSentSmsLogs> receiveSMSClients = new HashMap<>();
    private final String callBackUrl;

    private final ReadConfigurationService readConfigurationService;
    public final SmsGatewayConfiguration smsGatewayConfiguration;

    @Autowired
    public InfoBipMessageProvider(final ReadConfigurationService readConfigurationService) {
        this.readConfigurationService = readConfigurationService;
        final Collection<ConfigurationData> configurationDataCollection = this.readConfigurationService.findAll();

        this.smsGatewayConfiguration = new SmsGatewayConfiguration(configurationDataCollection);
        this.callBackUrl = this.smsGatewayConfiguration.getCallBackURL() + "/infobip/report/"; // ex:http://106.51.39.37:9090/infobip/report/
        logger.info("Registering call back to InfoBip:" + this.callBackUrl);
    }

    public SmsGatewayMessage sendMessage(final SmsGatewayMessage message) {
        final String statusCallback = this.callBackUrl + message.getId();
        // Based on message id, register call back. so that we get notification
        // from Infobip about message status
        final SendMultipleTextualSmsAdvanced client = getSendSMSRestClient();
        final Destination destination = new Destination();
        final String mobile = message.getMobileNumber();
        logger.info("Sending SMS to " + mobile + " ...");
        destination.setTo(mobile);
        final Message infoBipMessage = new Message();
        infoBipMessage.setDestinations(Collections.singletonList(destination));
        infoBipMessage.setText(message.getMessage());
        infoBipMessage.setNotifyUrl(statusCallback);
        infoBipMessage.setNotifyContentType("application/json");
        infoBipMessage.setNotify(true);
        final SMSAdvancedTextualRequest requestBody = new SMSAdvancedTextualRequest();
        requestBody.setMessages(Collections.singletonList(infoBipMessage));
        final SMSResponse response = client.execute(requestBody);
        final SMSResponseDetails sentMessageInfo = response.getMessages().get(0);
        message.setExternalId(sentMessageInfo.getMessageId());
        message.setDeliveryStatus(InfoBipStatus.smsStatus(sentMessageInfo.getStatus().getGroupId()).getValue());
        logger.debug(
                "InfoBipMessageProvider.sendMessage():" + InfoBipStatus.smsStatus(sentMessageInfo.getStatus().getGroupId()).getValue());
        return message;
    }

    public SMSLogsResponse getDeliveryReport(final Set<String> messageIds) {
        // Based on message id, we get notification from Infobip about message
        // status
        final GetSentSmsLogs client = getDeliveryReportsSMSRestClient();
        return client.execute(null, null, null, messageIds.toArray(new String[0]), null, null, null, null, null, null);
    }

    private SendMultipleTextualSmsAdvanced getSendSMSRestClient() {
        final String authorizationKey = encodeBase64();
        SendMultipleTextualSmsAdvanced client = this.sendSMSCliets.get(authorizationKey);
        if (client == null) {
            client = createSendSMSClient();
            this.sendSMSCliets.put(authorizationKey, client);
        }
        return client;
    }

    SendMultipleTextualSmsAdvanced createSendSMSClient() {
        logger.debug("Creating a new InfoBip Client ....");
        final String userName = this.smsGatewayConfiguration.getSystemId();
        final String password = this.smsGatewayConfiguration.getPassword();
        final SendMultipleTextualSmsAdvanced client = new SendMultipleTextualSmsAdvanced(new BasicAuthConfiguration(userName, password));
        return client;
    }

    private String encodeBase64() {
        final String userName = this.smsGatewayConfiguration.getSystemId();
        final String password = this.smsGatewayConfiguration.getPassword();
        final String userPass = userName + ":" + password;
        return Base64.getEncoder().encodeToString(userPass.getBytes());
    }

    private GetSentSmsLogs getDeliveryReportsSMSRestClient() {
        final String authorizationKey = encodeBase64();
        GetSentSmsLogs client = this.receiveSMSClients.get(authorizationKey);
        if (client == null) {
            client = createDeliveryReportsSMSClient();
            this.receiveSMSClients.put(authorizationKey, client);
        }
        return client;
    }

    private GetSentSmsLogs createDeliveryReportsSMSClient() {
        logger.debug("Creating a new InfoBip Client ....");
        final String userName = this.smsGatewayConfiguration.getSystemId();
        final String password = this.smsGatewayConfiguration.getPassword();
        final GetSentSmsLogs client = new GetSentSmsLogs(new BasicAuthConfiguration(userName, password));
        return client;
    }
}
