package com.marudham.web.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Opens a second port for the user site (admin site stays on server.port). */
@Configuration
public class DualPortConfig {
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> userPortConnector(
            @Value("${app.user.port:8081}") int userPort) {
        return factory -> {
            Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
            connector.setPort(userPort);
            factory.addAdditionalTomcatConnectors(connector);
        };
    }
}
