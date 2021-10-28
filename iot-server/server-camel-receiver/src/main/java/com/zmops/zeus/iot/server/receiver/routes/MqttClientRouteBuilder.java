package com.zmops.zeus.iot.server.receiver.routes;

import com.zmops.zeus.iot.server.receiver.ReceiverServerRoute;
import org.apache.camel.LoggingLevel;

import java.util.Map;

/**
 * @author nantian created at 2021/10/24 0:11
 * <p>
 * mqtt client
 */
public class MqttClientRouteBuilder extends ReceiverServerRoute {

    public MqttClientRouteBuilder(String routeId, Map<String, Object> options) {
        super(routeId, options);
    }

    @Override
    public void configure() throws Exception {
        fromF("mqtt:zeus-iot-mqtt?host=tcp://%s:%s&subscribeTopicNames=123/up", options.get("hostIp"), options.get("port"))
                .routeId(routeId).log(LoggingLevel.DEBUG, log, ">>> Message received from Mqtt Client : ${body}");
    }
}