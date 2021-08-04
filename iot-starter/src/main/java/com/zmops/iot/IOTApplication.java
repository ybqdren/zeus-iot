package com.zmops.iot;

import com.dtflys.forest.springboot.annotation.ForestScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author nantian
 * <p>
 * ### 宙斯物联网中台 ZEUS-IOT == SpringBoot + Ebean 极简架构
 */
@SpringBootApplication
@ForestScan("com.zmops.zeus.driver.service")
public class IOTApplication {

    public static void main(String[] args) {
        SpringApplication.run(IOTApplication.class, args);
    }
}
