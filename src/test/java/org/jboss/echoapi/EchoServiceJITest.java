package org.jboss.echoapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.echoapi.EchoService.HOSTS_URL;
import static org.jboss.echoapi.EchoService.SERVICE_PORT;
import static org.jboss.echoapi.EchoService.HYPERFOIL_CONFIG_URL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import static org.jboss.echoapi.EchoService.Params.*;

@ExtendWith(VertxExtension.class)
public class EchoServiceJITest {

   private static Vertx vertx;
   private static DeploymentOptions options = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", SERVICE_PORT)
   );
   private static final String CSV_FILE_NAME = "sample.upload.csv";
   private static final Path CSV_PATH_NAME = Paths.get("src/test/resources", CSV_FILE_NAME);
   private static final String HYPERFOIL_FILE_NAME = "sample.hf.yaml";
   private static final Path HYPERFOIL_PATH_NAME = Paths.get("src/test/resources", HYPERFOIL_FILE_NAME);
   private static final Logger LOGGER = LoggerFactory.getLogger(EchoServiceJITest.class);

   @BeforeEach
   @DisplayName("Set up then Deploy the Verticle")
   void deploy_verticle(VertxTestContext context) 
         throws IOException {
      LOGGER.info("set up the integration test");
      vertx = Vertx.vertx();
      System.out.println("deploying the integration test");
      vertx.deployVerticle(EchoService.class.getName(), options, context.succeeding( ar -> {
         context.completeNow();
      }));
   }

   @AfterEach
   @DisplayName("Check the Verticle still present")
   void lastChecks() 
         throws IOException{
      assertThat(vertx.deploymentIDs())
      .isNotEmpty()
      .hasSize(1);
      LOGGER.info("Check Verticle still present");
      vertx.close();
   }

   @Test
   @DisplayName("Test hosts are parsed correctly")
   void testHostsCorrectlyParsed(VertxTestContext context) {
      LOGGER.info("running the integration test");

      MultipartForm form = MultipartForm.create()
         .textFileUpload("data", CSV_FILE_NAME, CSV_PATH_NAME.toString(), "text/csv");
      WebClient.create(vertx).post(SERVICE_PORT, "localhost", HOSTS_URL)
         .sendMultipartForm(form, context.succeeding(
            ar -> context.verify(() -> {
               System.out.println("now asserting test expressions");
               assertEquals(200, ar.statusCode());
               String body = ar.bodyAsString();
               assertNotNull(body);
               assertEquals("aa20ba69-a86e-4294-981b-41cd258aff47.benchmark,73642bdb-78ef-4054-aa2f-9a880f09dd39.benchmark,d09c5600-c546-4012-b81a-49b0eadaea1a.benchmark", body);
               context.completeNow();
            })
         ));
   }

   @Test
   @DisplayName("Test Hyperfoil configuration is parsed correctly")
   void testHyperfoilParsedCorrect(VertxTestContext context) {
      LOGGER.info("buddhi parsing integration test");
      MultipartForm form = MultipartForm.create()
         .textFileUpload("data", HYPERFOIL_FILE_NAME, HYPERFOIL_PATH_NAME.toString(), "text/plain");
      WebClient.create(vertx).post(SERVICE_PORT, "localhost", HYPERFOIL_CONFIG_URL)
         .addQueryParam(hosts.toString(), "aa20ba69-a86e-4294-981b-41cd258aff47.benchmark,73642bdb-78ef-4054-aa2f-9a880f09dd39.benchmark,d09c5600-c546-4012-b81a-49b0eadaea1a.benchmark")
         .addQueryParam(port.toString(), "8080")
         .addQueryParam(connections.toString(), "4")
         .addQueryParam(csv.toString(), CSV_FILE_NAME)
         .addQueryParam(rampUpUsers.toString(), "10")
         .addQueryParam(steadyStateUsers.toString(), "10")
         .addQueryParam(rampUpDuration.toString(), "60")
         .addQueryParam(steadyStateDuration.toString(), "300")
         .addQueryParam(rampDownDuration.toString(), "60")
         .sendMultipartForm(form, context.succeeding(
             ar -> context.verify(() -> {
                LOGGER.info("now asserting test expressions");
                assertEquals(200, ar.statusCode());
                String body = ar.bodyAsString();
                assertNotNull(body);
                assertFalse(body.contains(hosts.TEXT));
                assertFalse(body.contains(csv.TEXT));
                assertFalse(body.contains(pattern.TEXT));
                assertFalse(body.contains(rampUpUsers.TEXT));
                assertFalse(body.contains(steadyStateUsers.TEXT));
                assertFalse(body.contains(rampUpDuration.TEXT));
                assertFalse(body.contains(steadyStateDuration.TEXT));
                assertFalse(body.contains(rampDownDuration.TEXT));
                assertTrue(body.contains("             file: "), "data file line not found");
                assertTrue(body.contains("              pattern: "), "pattern not found");
                assertTrue(body.contains("      targetUsersPerSec: "), "targetUsersPerSec not found");
                assertTrue(body.contains("      usersPerSec:"), "usersPerSec not found");
                assertTrue(body.contains("      duration: 60"), "ramp up/down duration not found");
                assertTrue(body.contains("      duration: 300"), "steady state duration not found");
                context.completeNow();
             })
          ));
   }
}
