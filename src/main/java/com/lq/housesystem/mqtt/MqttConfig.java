package com.lq.housesystem.mqtt;

import com.lq.housesystem.bean.*;
import com.lq.housesystem.service.DataService;
import com.lq.housesystem.service.UserService;
import com.lq.housesystem.tools.SwitchType;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.stereotype.Component;

@Component
@Configuration
public class MqttConfig {

    @Autowired
    private UserService userService;

    @Autowired
    private DataService dataService;

    @Bean
    public MqttPahoClientFactory mqttClientFactory(){
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();

        options.setServerURIs(new String[]{"tcp://127.0.0.1:1883"});

        options.setUserName("admin");

        String pwd = "super";
        options.setPassword(pwd.toCharArray());

        factory.setConnectionOptions(options);

        return factory;
    }

    @Bean
    public MessageChannel inputChannel(){
        return new DirectChannel();
    }

    @Bean
    public MessageProducer inBound(){
        String[] topics = {"DATA-TH","DATA-LIGHT","DATA-MQ4","DATA-SWITCH"};

        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter("CLIENT-JAVA",mqttClientFactory(),topics);

        adapter.setCompletionTimeout(5000);

        DefaultPahoMessageConverter converter = new DefaultPahoMessageConverter();

        adapter.setConverter(converter);
        adapter.setQos(0);
        adapter.setOutputChannel(inputChannel());

        return adapter;
   }
   //??????MQTT????????????????????????
    @Bean
    @ServiceActivator(inputChannel = "inputChannel")
    public MessageHandler inputHandler() {
       return message -> {
           String payload = message.getPayload().toString();
           Object o = message.getHeaders().get("mqtt_receivedTopic");

           if (o != null){
               String[] target = payload.split("-");
               //????????????IP
               String equipmentIp = target[1];
               //??????????????????Ip??????
               Equipment e = userService.selectEquipmentByIp(equipmentIp);
               //????????????????????????,????????????
               if (e == null){
                   //0,?????????????????????????????????????????????????????????
                   //1,????????????????????????
                   userService.insertEquipment(new Equipment(equipmentIp,SwitchType.typeId(o.toString()),"0"));
               }else {
                   //???????????????,????????????
                   Integer value = Integer.parseInt(target[0]);
                   long time = System.currentTimeMillis();
                   String ip = target[1];

                   //????????????????????????????????????????????????????????????
                   switch (o.toString()){
                       case "DATA-MQ4":
                           dataService.insertGas(new Gas(null,value,ip,time));
                           break;
                       case "DATA-LIGHT":
                           dataService.insertLight(new Light(null,value,ip,time));
                       case "DATA-TH":
                           dataService.insertHum(new Humidity(null,value,ip,time));
                           dataService.insertTemp(new Temperature(null,value,ip,time));
                   }
               }
           }
       };
    }

    @Bean
    public MessageChannel outputChannel(){
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "outputChannel")
    public MessageHandler outputHandler(){
        MqttPahoMessageHandler messageHandler = new MqttPahoMessageHandler("DATA-MQ4",mqttClientFactory());

        messageHandler.setAsync(true);

        messageHandler.setDefaultTopic("SKS");
        messageHandler.setDefaultQos(1);

        DefaultPahoMessageConverter messageConverter = new DefaultPahoMessageConverter();

        messageHandler.setConverter(messageConverter);

        return messageHandler;
    }
}
