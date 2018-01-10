
-- This is the sms-bridge url to be exposed outside, Infobip server will call this URL to update the delivery status of SMS
INSERT INTO `configuration` (`name`, `value`) VALUES ('SMS_GATEWAY_CALLBACK_URL', 'http://106.51.39.37:9090/sms-bridge/api/v1');
