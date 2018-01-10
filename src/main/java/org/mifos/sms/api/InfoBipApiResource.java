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
package org.mifos.sms.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.mifos.sms.domain.SmsOutboundMessage;
import org.mifos.sms.domain.SmsOutboundMessageRepository;
import org.mifos.sms.gateway.infobip.InfoBipStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import infobip.api.model.sms.mt.reports.SMSReport;
import infobip.api.model.sms.mt.reports.SMSReportResponse;

@Path("/infobip/report")
@Consumes({ MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_JSON })
@Component
@Scope("singleton")
public class InfoBipApiResource {

    private static final Logger logger = LoggerFactory.getLogger(InfoBipApiResource.class);

    private final SmsOutboundMessageRepository smsOutboundMessageRepository;

    @Autowired
    public InfoBipApiResource(final SmsOutboundMessageRepository smsOutboundMessageRepository) {
        this.smsOutboundMessageRepository = smsOutboundMessageRepository;
    }

    @POST
    @Path("{messageId}")
    public ResponseEntity<Void> updateDeliveryStatus(@PathParam("messageId") final Long messageId,
            @RequestBody final SMSReportResponse payload) {
        final SmsOutboundMessage message = this.smsOutboundMessageRepository.findOne(messageId);
        if (message != null) {
            final SMSReport report = payload.getResults().get(0);
            logger.debug("Status Callback received from InfoBip for " + messageId + " with status:" + report.getStatus());
            message.setDeliveryStatus(InfoBipStatus.smsStatus(report.getStatus().getGroupId()));
            this.smsOutboundMessageRepository.save(message);
        } else {
            logger.info("Message with Message id " + messageId + " Not found");
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

}
