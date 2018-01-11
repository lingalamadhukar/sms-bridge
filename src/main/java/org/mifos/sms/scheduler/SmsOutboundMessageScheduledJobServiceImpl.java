package org.mifos.sms.scheduler;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mifos.sms.domain.SmsMessageStatusType;
import org.mifos.sms.domain.SmsOutboundMessage;
import org.mifos.sms.domain.SmsOutboundMessageRepository;
import org.mifos.sms.gateway.infobip.InfoBipMessageProvider;
import org.mifos.sms.gateway.infobip.InfoBipStatus;
import org.mifos.sms.gateway.infobip.SmsGatewayHelper;
import org.mifos.sms.gateway.infobip.SmsGatewayImpl;
import org.mifos.sms.gateway.infobip.SmsGatewayMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import infobip.api.model.sms.mt.logs.SMSLog;
import infobip.api.model.sms.mt.logs.SMSLogsResponse;

@Service
public class SmsOutboundMessageScheduledJobServiceImpl implements SmsOutboundMessageScheduledJobService {

    private final SmsOutboundMessageRepository smsOutboundMessageRepository;
    private final SmsGatewayImpl smsGatewayImpl;
    private final SmsGatewayHelper smsGatewayHelper;
    private final InfoBipMessageProvider infoBipMessageProvider;

    @Autowired
    public SmsOutboundMessageScheduledJobServiceImpl(final SmsOutboundMessageRepository smsOutboundMessageRepository,
            final SmsGatewayHelper smsGatewayHelper, final SmsGatewayImpl smsGatewayImpl,
            final InfoBipMessageProvider infoBipMessageProvider) {
        this.smsOutboundMessageRepository = smsOutboundMessageRepository;
        this.smsGatewayHelper = smsGatewayHelper;
        this.smsGatewayImpl = smsGatewayImpl;
        this.infoBipMessageProvider = infoBipMessageProvider;
        // this.smsGatewayHelper.connectAndBindSession();
    }

    @Override
    @Transactional
    @Scheduled(fixedDelay = 60000)
    public void sendMessages() {
        // check if the scheduler is enabled
        if (this.smsGatewayHelper.smsGatewayConfiguration.getEnableOutboundMessageScheduler()) {

            // if(smsGatewayHelper.isConnected) {
            final Pageable pageable = new PageRequest(0, getMaximumNumberOfMessagesToBeSent());
            final List<SmsOutboundMessage> smsOutboundMessages = this.smsOutboundMessageRepository
                    .findByDeliveryStatus(SmsMessageStatusType.PENDING.getValue(), pageable);

            // only proceed if there are pending messages
            if (!CollectionUtils.isEmpty(smsOutboundMessages)) {

                for (final SmsOutboundMessage smsOutboundMessage : smsOutboundMessages) {
                    SmsGatewayMessage smsGatewayMessage = new SmsGatewayMessage(smsOutboundMessage.getId(),
                            smsOutboundMessage.getExternalId(), smsOutboundMessage.getSourceAddress(), smsOutboundMessage.getMobileNumber(),
                            smsOutboundMessage.getMessage());

                    smsGatewayMessage = this.infoBipMessageProvider.sendMessage(smsGatewayMessage);
                    // send message to SMS message gateway
                    // smsGatewayMessage =
                    // smsGatewayImpl.sendMessage(smsGatewayMessage);

                    // update the "submittedOnDate" property of the SMS message
                    // in the DB
                    smsOutboundMessage.setSubmittedOnDate(new Date());

                    // check if the returned SmsGatewayMessage object has an
                    // external ID
                    if (!StringUtils.isEmpty(smsGatewayMessage.getExternalId())) {

                        // update the external ID of the SMS message in the DB
                        smsOutboundMessage.setExternalId(smsGatewayMessage.getExternalId());

                        // update the status of the SMS message in the DB
                        smsOutboundMessage.setDeliveryStatus(SmsMessageStatusType.fromInt(smsGatewayMessage.getDeliveryStatus()));
                    }

                    else {
                        // update the status of the SMS message in the DB
                        smsOutboundMessage.setDeliveryStatus(SmsMessageStatusType.FAILED);
                    }

                    this.smsOutboundMessageRepository.save(smsOutboundMessage);
                }
            }
            /*
             * }
             *
             * else { // reconnect smsGatewayHelper.reconnectAndBindSession(); }
             */
        }
    }

    /**
     * Get the maximum number of messages to be sent to the SMS gateway
     *
     * TODO this should be configurable, add to c_configuration
     **/
    private int getMaximumNumberOfMessagesToBeSent() {
        return 5000;
    }

    @Override
    @Transactional
    @Scheduled(fixedDelay = 1800000)
    public void updateDeliveryStatus() {
        // check if the scheduler is enabled
        if (this.smsGatewayHelper.smsGatewayConfiguration.getEnableOutboundMessageScheduler()) {

            final Pageable pageable = new PageRequest(0, getMaximumNumberOfMessagesToBeSent());
            final List<SmsOutboundMessage> smsOutboundMessages = this.smsOutboundMessageRepository
                    .findByDeliveryStatus(SmsMessageStatusType.SENT.getValue(), pageable);
            smsOutboundMessages.addAll(
                    this.smsOutboundMessageRepository.findByDeliveryStatus(SmsMessageStatusType.WAITING_FOR_REPORT.getValue(), pageable));

            // only proceed if there are pending messages for status update
            if (!CollectionUtils.isEmpty(smsOutboundMessages)) {
                final Map<String, SmsOutboundMessage> messages = new HashMap<>();
                for (final SmsOutboundMessage smsOutboundMessage : smsOutboundMessages) {
                    messages.put(smsOutboundMessage.getExternalId(), smsOutboundMessage);
                }
                final SMSLogsResponse response = this.infoBipMessageProvider.getDeliveryReport(messages.keySet());
                for (final SMSLog smsLog : response.getResults()) {
                    final SmsOutboundMessage message = messages.get(smsLog.getMessageId());
                    message.setDeliveryStatus(InfoBipStatus.smsStatus(smsLog.getStatus().getGroupId()));
                }
                this.smsOutboundMessageRepository.save(smsOutboundMessages);
            }
        }
    }
}
